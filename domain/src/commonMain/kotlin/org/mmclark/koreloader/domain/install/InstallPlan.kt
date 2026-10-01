package org.mmclark.koreloader.domain.install

import org.mmclark.koreloader.domain.catalog.InstallTarget
import org.mmclark.koreloader.domain.volume.KoboPath

/** An entry in a package archive, as listed by the archive reader. [size] is -1 if unknown. */
data class ArchiveEntry(val name: String, val isDirectory: Boolean, val size: Long)

/**
 * The validated, ordered list of things to write to the Kobo for one archive.
 *
 * Built only through [forOneClickPackage], which guarantees: every target path is safe and
 * unique (case-insensitively, as on FAT32); `.kobo/KoboRoot.tgz` is never in [files] but in
 * [deferredFiles], to be written last via a temporary name; and the files the installer
 * must find afterwards are listed in [requiredFiles].
 */
class InstallPlan private constructor(
    val directories: List<KoboPath>,
    val files: List<PlannedFile>,
    val deferredFiles: List<PlannedFile>,
    val requiredFiles: List<KoboPath>,
) {
    data class PlannedFile(val entryName: String, val target: KoboPath, val size: Long)

    val allFiles: List<PlannedFile> get() = files + deferredFiles
    val totalBytes: Long get() = allFiles.sumOf { it.size.coerceAtLeast(0) }

    companion object {
        /** Nickel installs this tarball (then deletes it) when the device is unplugged. */
        val KOBO_ROOT_TGZ: KoboPath = KoboPath.of(".kobo/KoboRoot.tgz")

        /** Temporary name for [KOBO_ROOT_TGZ] while it is being written. Nickel ignores it. */
        const val KOBO_ROOT_TEMP_NAME: String = "KoboRoot.tgz.koreloader-tmp"

        /** KFMon's icon: its presence is install.sh's sanity check that unpacking worked. */
        val KFMON_ICON: KoboPath = KoboPath.of("kfmon.png")

        /** Plans a one-click package (KOReader/Plato/KFMon OCP), extracted to the volume root. */
        fun forOneClickPackage(entries: List<ArchiveEntry>): PlanResult =
            plan(entries, InstallTarget.VOLUME_ROOT, required = listOf(KOBO_ROOT_TGZ, KFMON_ICON))

        private fun plan(entries: List<ArchiveEntry>, target: InstallTarget, required: List<KoboPath>): PlanResult {
            if (entries.none { !it.isDirectory }) return PlanResult.Invalid(PlanProblem.EmptyArchive)
            val base = when (target) {
                InstallTarget.VOLUME_ROOT -> KoboPath.ROOT
                InstallTarget.KOBO_DIR -> KoboPath.of(".kobo")
            }
            val directories = LinkedHashMap<String, KoboPath>()
            val files = ArrayList<PlannedFile>()
            val deferred = ArrayList<PlannedFile>()
            val seenFiles = HashSet<String>()

            for (entry in entries) {
                val relative = KoboPath.parseOrNull(entry.name)
                    ?: return PlanResult.Invalid(PlanProblem.UnsafeEntry(entry.name))
                if (relative.isRoot) return PlanResult.Invalid(PlanProblem.UnsafeEntry(entry.name))
                val path = base.resolve(relative.toString())
                val key = path.toString().lowercase()
                if (entry.isDirectory) {
                    if (key in seenFiles) return PlanResult.Invalid(PlanProblem.Conflict(entry.name))
                    directories.getOrPut(key) { path }
                    continue
                }
                if (key in directories || !seenFiles.add(key)) return PlanResult.Invalid(PlanProblem.Conflict(entry.name))
                val planned = PlannedFile(entry.name, path, entry.size)
                if (path == KOBO_ROOT_TGZ) deferred += planned else files += planned
            }

            // A file may not sit where a directory is needed (e.g. "a" as a file and "a/b").
            for (file in files + deferred) {
                var parent = file.target.parent
                while (parent != null && !parent.isRoot) {
                    if (parent.toString().lowercase() in seenFiles) return PlanResult.Invalid(PlanProblem.Conflict(file.entryName))
                    parent = parent.parent
                }
            }

            val planned = (files + deferred).map { it.target }.toSet()
            required.firstOrNull { it !in planned }?.let { return PlanResult.Invalid(PlanProblem.MissingRequiredFile(it)) }

            return PlanResult.Valid(InstallPlan(directories.values.toList(), files, deferred, required))
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

    /** The archive lacks a file this kind of package must contain. */
    data class MissingRequiredFile(val path: KoboPath) : PlanProblem
}
