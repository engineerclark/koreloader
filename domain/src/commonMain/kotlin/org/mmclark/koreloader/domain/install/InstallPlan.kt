package org.mmclark.koreloader.domain.install

import org.mmclark.koreloader.domain.packages.ArchiveLayout
import org.mmclark.koreloader.domain.packages.BundledFile
import org.mmclark.koreloader.domain.volume.KoboPath

/** An entry in a package archive, as listed by the archive reader. [size] is -1 if unknown. */
data class ArchiveEntry(val name: String, val isDirectory: Boolean, val size: Long)

/** One source of files for an [InstallPlan], in the order they should be written. */
sealed interface PlanInput {
    /** The entries of archive number [archive] (an index chosen by the caller), placed per [layout]. */
    data class Archive(val archive: Int, val entries: List<ArchiveEntry>, val layout: ArchiveLayout) : PlanInput

    /** Files bundled with koreloader; [sizes] maps asset name to byte size where known. */
    data class Bundled(val files: List<BundledFile>, val sizes: Map<String, Long> = emptyMap()) : PlanInput
}

sealed interface FileSource {
    data class FromArchive(val archive: Int, val entryName: String) : FileSource
    data class FromBundle(val asset: String) : FileSource
}

/**
 * The validated, ordered list of things to write to the Kobo.
 *
 * Built only through [compose], which guarantees: every target path is safe and unique
 * (case-insensitively, as on FAT32); `.kobo/KoboRoot.tgz` is never in [files] but in
 * [deferredFiles], to be written last via a temporary name; and the files the installer
 * must find afterwards are listed in [requiredFiles].
 */
class InstallPlan private constructor(
    val directories: List<KoboPath>,
    val files: List<PlannedFile>,
    val deferredFiles: List<PlannedFile>,
    val requiredFiles: List<KoboPath>,
) {
    data class PlannedFile(val source: FileSource, val target: KoboPath, val size: Long)

    val allFiles: List<PlannedFile> get() = files + deferredFiles
    val totalBytes: Long get() = allFiles.sumOf { it.size.coerceAtLeast(0) }

    /** Whether Nickel will process an update (and reboot) when the Kobo is unplugged. */
    val triggersKoboUpdate: Boolean get() = deferredFiles.any { it.target == KOBO_ROOT_TGZ }

    companion object {
        /** Nickel installs this tarball (then deletes it) when the device is unplugged. */
        val KOBO_ROOT_TGZ: KoboPath = KoboPath.of(".kobo/KoboRoot.tgz")

        /** Temporary name for [KOBO_ROOT_TGZ] while it is being written. Nickel ignores it. */
        const val KOBO_ROOT_TEMP_NAME: String = "KoboRoot.tgz.koreloader-tmp"

        fun compose(inputs: List<PlanInput>, requiredFiles: List<KoboPath>): PlanResult {
            val directories = LinkedHashMap<String, KoboPath>()
            val files = LinkedHashMap<String, PlannedFile>()

            fun addDirectory(path: KoboPath, entryName: String): PlanProblem? {
                var current: KoboPath? = path
                val chain = ArrayList<KoboPath>()
                while (current != null && !current.isRoot) {
                    chain += current
                    current = current.parent
                }
                for (dir in chain.asReversed()) {
                    val key = dir.toString().lowercase()
                    if (key in files) return PlanProblem.Conflict(entryName)
                    directories.getOrPut(key) { dir }
                }
                return null
            }

            fun addFile(file: PlannedFile, entryName: String): PlanProblem? {
                val key = file.target.toString().lowercase()
                if (key in directories) return PlanProblem.Conflict(entryName)
                val existing = files[key]
                if (existing != null) {
                    // The same bundled file requested twice (e.g. a shared launcher config) is fine.
                    return if (existing.source == file.source) null else PlanProblem.Conflict(entryName)
                }
                file.target.parent?.let { parent -> addDirectory(parent, entryName)?.let { return it } }
                files[key] = file
                return null
            }

            for (input in inputs) {
                when (input) {
                    is PlanInput.Archive -> {
                        if (input.entries.none { !it.isDirectory }) return PlanResult.Invalid(PlanProblem.EmptyArchive)
                        for (entry in input.entries) {
                            val relative = KoboPath.parseOrNull(entry.name)
                                ?: return PlanResult.Invalid(PlanProblem.UnsafeEntry(entry.name))
                            val mapped = input.layout.map(relative.toString(), entry.isDirectory) ?: continue
                            val problem = if (entry.isDirectory) {
                                addDirectory(mapped, entry.name)
                            } else {
                                addFile(PlannedFile(FileSource.FromArchive(input.archive, entry.name), mapped, entry.size), entry.name)
                            }
                            if (problem != null) return PlanResult.Invalid(problem)
                        }
                    }
                    is PlanInput.Bundled -> for (bundled in input.files) {
                        val file = PlannedFile(FileSource.FromBundle(bundled.asset), bundled.target, input.sizes[bundled.asset] ?: -1)
                        addFile(file, bundled.asset)?.let { return PlanResult.Invalid(it) }
                    }
                }
            }

            val all = files.values.toList()
            val planned = all.map { it.target }.toSet()
            requiredFiles.firstOrNull { it !in planned }?.let { return PlanResult.Invalid(PlanProblem.MissingRequiredFile(it)) }

            val (deferred, regular) = all.partition { it.target == KOBO_ROOT_TGZ }
            return PlanResult.Valid(InstallPlan(directories.values.toList(), regular, deferred, requiredFiles.distinct()))
        }

        /** Maps an archive entry onto the volume, or null if the layout ignores it. */
        private fun ArchiveLayout.map(entry: String, isDirectory: Boolean): KoboPath? {
            val relative = if (stripPrefix == null) {
                entry
            } else {
                val prefix = stripPrefix.trimEnd('/')
                when {
                    entry == prefix -> return null
                    entry.startsWith("$prefix/") -> entry.removePrefix("$prefix/")
                    else -> return null
                }
            }
            if (relative.isEmpty()) return null
            return target.resolve(relative).takeUnless { isDirectory && it == target }
        }
    }
}

sealed interface PlanResult {
    data class Valid(val plan: InstallPlan) : PlanResult
    data class Invalid(val problem: PlanProblem) : PlanResult
}

sealed interface PlanProblem {
    data object EmptyArchive : PlanProblem

    /** Absolute path, `..` segment, or otherwise unsafe entry name. */
    data class UnsafeEntry(val entryName: String) : PlanProblem

    /** Duplicate path (case-insensitive) or a file/directory clash. */
    data class Conflict(val entryName: String) : PlanProblem

    /** The plan lacks a file the package must contain. */
    data class MissingRequiredFile(val path: KoboPath) : PlanProblem
}
