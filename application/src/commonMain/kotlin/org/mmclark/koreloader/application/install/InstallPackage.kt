package org.mmclark.koreloader.application.install

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okio.Source
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.packages.FetchReleases
import org.mmclark.koreloader.application.packages.InspectVolume
import org.mmclark.koreloader.application.packages.ReleasesLookup
import org.mmclark.koreloader.application.port.Archive
import org.mmclark.koreloader.application.port.ArchiveException
import org.mmclark.koreloader.application.port.ArchiveReader
import org.mmclark.koreloader.application.port.BundledAssets
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeException
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.application.port.ReceiptStore
import org.mmclark.koreloader.application.port.exists
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.install.FileSource
import org.mmclark.koreloader.domain.install.InstallPlan
import org.mmclark.koreloader.domain.install.NickelConfig
import org.mmclark.koreloader.domain.install.PlanInput
import org.mmclark.koreloader.domain.install.PlanResult
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.packages.InstalledState
import org.mmclark.koreloader.domain.packages.PackageDefinition
import org.mmclark.koreloader.domain.packages.PackageStatusPolicy
import org.mmclark.koreloader.domain.packages.Packages
import org.mmclark.koreloader.domain.volume.KoboPath

/**
 * Installs a package release onto a Kobo volume, together with any dependency that is
 * missing or disabled (KFMon after a firmware update), mirroring KFMon's `tools/install.sh`:
 * check the device, download and verify everything, append `ExcludeSyncFolders` to Nickel's
 * config, unpack, check the result, and record receipts.
 *
 * Unlike the script, `.kobo/KoboRoot.tgz` is written last, under a temporary name that is
 * renamed once complete, and a stale one is removed before anything else is written.
 */
class InstallPackage(
    private val detectKoboDevice: DetectKoboDevice,
    private val inspectVolume: InspectVolume,
    private val fetchReleases: FetchReleases,
    private val downloadPackage: DownloadPackage,
    private val archiveReader: ArchiveReader,
    private val bundledAssets: BundledAssets,
    private val receiptStore: ReceiptStore,
) {
    private data class Component(val definition: PackageDefinition, val release: PackageRelease)

    operator fun invoke(volume: KoboVolume, release: PackageRelease): Flow<InstallProgress> = channelFlow {
        send(InstallProgress.CheckingDevice)
        val device = when (val detection = detectKoboDevice(volume)) {
            is DeviceDetection.Detected -> {
                val compatibility = detection.compatibility
                if (compatibility is DeviceCompatibility.Unsupported) {
                    send(InstallProgress.Failed(InstallError.DeviceUnsupported(compatibility)))
                    return@channelFlow
                }
                detection.device
            }
            else -> {
                send(InstallProgress.Failed(InstallError.DeviceNotReady(detection)))
                return@channelFlow
            }
        }

        val components = try {
            resolveComponents(volume, device, release)
        } catch (e: DependencyException) {
            send(InstallProgress.Failed(e.error))
            return@channelFlow
        } catch (e: KoboVolumeException) {
            send(InstallProgress.Failed(InstallError.WriteFailed(null, e.message ?: "volume not accessible")))
            return@channelFlow
        }

        val files = ArrayList<LocalFile>()
        for ((index, component) in components.withIndex()) {
            val id = component.definition.id
            val download = downloadPackage(
                component.release.artifact,
                onProgress = { done, total -> send(InstallProgress.Downloading(id, index + 1, components.size, done, total)) },
                onVerifying = { send(InstallProgress.VerifyingDownload(id)) },
            )
            when (download) {
                is DownloadResult.Ready -> files += download.file
                is DownloadResult.Failed -> {
                    send(InstallProgress.Failed(download.error))
                    return@channelFlow
                }
            }
        }

        val outcome = try {
            openAll(files) { archives -> install(archives, components, volume, device) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ArchiveException) {
            InstallProgress.Failed(InstallError.UnreadablePackage(e.message ?: "unreadable archive"))
        }
        send(outcome)
    }

    /** The requested release, preceded by the latest release of each dependency that needs (re)installing. */
    private suspend fun resolveComponents(volume: KoboVolume, device: KoboDevice, release: PackageRelease): List<Component> {
        val definition = Packages.get(release.packageId)
        if (definition.dependencies.isEmpty()) return listOf(Component(definition, release))
        val inventory = inspectVolume.inspect(volume)
        val dependencies = definition.dependencies.mapNotNull { dependencyId ->
            val state = inventory.installed[dependencyId] ?: InstalledState.NotInstalled
            val status = PackageStatusPolicy.status(state, emptyList(), device.firmware, inventory.koboUpdatePending)
            if (!PackageStatusPolicy.dependencyNeedsInstall(status)) return@mapNotNull null
            val latest = when (val lookup = fetchReleases(dependencyId)) {
                is ReleasesLookup.Found -> lookup.releases.firstOrNull()
                    ?: throw DependencyException(InstallError.DependencyUnavailable(dependencyId, "no release available"))
                is ReleasesLookup.Unavailable ->
                    throw DependencyException(InstallError.DependencyUnavailable(dependencyId, lookup.message))
            }
            Component(Packages.get(dependencyId), latest)
        }
        return dependencies + Component(definition, release)
    }

    private class DependencyException(val error: InstallError) : Exception()

    private suspend fun <T> openAll(files: List<LocalFile>, block: suspend (List<Archive>) -> T): T {
        suspend fun open(index: Int, opened: List<Archive>): T =
            if (index == files.size) block(opened)
            else archiveReader.open(files[index]) { archive -> open(index + 1, opened + archive) }
        return open(0, emptyList())
    }

    private suspend fun ProducerScope<InstallProgress>.install(
        archives: List<Archive>,
        components: List<Component>,
        volume: KoboVolume,
        device: KoboDevice,
    ): InstallProgress {
        val inputs = components.flatMapIndexed { index, component ->
            val launchers = component.definition.launcherFiles
            listOf(
                PlanInput.Archive(index, archives[index].entries, component.definition.layout),
                PlanInput.Bundled(launchers, launchers.associate { it.asset to bundledAssets.size(it.asset) }),
            )
        }
        val required = components.flatMap { it.definition.requiredFiles }
        val plan = when (val result = InstallPlan.compose(inputs, required)) {
            is PlanResult.Valid -> result.plan
            is PlanResult.Invalid -> return InstallProgress.Failed(InstallError.InvalidPackage(result.problem))
        }

        send(InstallProgress.PreparingDevice)
        var current: KoboPath? = null
        try {
            if (plan.triggersKoboUpdate) {
                // A stale KoboRoot.tgz must not be processed if this install fails halfway.
                current = InstallPlan.KOBO_ROOT_TGZ
                volume.delete(InstallPlan.KOBO_ROOT_TGZ)
                volume.delete(KOBO_ROOT_TEMP)
            }

            current = NickelConfig.PATH
            excludeHiddenFoldersFromLibrary(volume)

            for (dir in plan.directories) {
                current = dir
                volume.createDirectories(dir)
            }

            val total = plan.allFiles.size
            var bytesDone = 0L
            plan.files.forEachIndexed { index, file ->
                current = file.target
                send(InstallProgress.Writing(index, total, bytesDone, plan.totalBytes, file.target))
                read(file.source, archives) { source -> volume.write(file.target, source) }
                bytesDone += file.size.coerceAtLeast(0)
            }

            for (file in plan.deferredFiles) {
                current = file.target
                send(InstallProgress.Writing(total - 1, total, bytesDone, plan.totalBytes, file.target))
                val temp = (file.target.parent ?: KoboPath.ROOT).resolve(InstallPlan.KOBO_ROOT_TEMP_NAME)
                read(file.source, archives) { source -> volume.write(temp, source, durable = true) }
                volume.rename(temp, file.target.name)
                bytesDone += file.size.coerceAtLeast(0)
            }
            send(InstallProgress.Writing(total, total, bytesDone, plan.totalBytes, null))

            send(InstallProgress.VerifyingInstall)
            current = null
            val missing = plan.requiredFiles.filter { volume.entryType(it) != EntryType.FILE }
            if (missing.isNotEmpty()) {
                return InstallProgress.Failed(InstallError.SanityCheckFailed(missing), leftovers(volume))
            }
            recordReceipts(volume, components, device)
            // An update left pending by an earlier install (e.g. KFMon pulled in by KOReader, then
            // Plato installed in the same session) also makes the Kobo restart on unplug.
            val koboUpdateRequired = plan.triggersKoboUpdate || volume.entryType(InstallPlan.KOBO_ROOT_TGZ) == EntryType.FILE
            return InstallProgress.Succeeded(device, components.map { it.release }, koboUpdateRequired)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { removeTempFile(volume) }
            throw e
        } catch (e: KoboVolumeException) {
            removeTempFile(volume)
            return InstallProgress.Failed(InstallError.WriteFailed(current, e.message ?: "write failed"), leftovers(volume))
        } catch (e: ArchiveException) {
            removeTempFile(volume)
            return InstallProgress.Failed(InstallError.UnreadablePackage(e.message ?: "unreadable archive"), leftovers(volume))
        }
    }

    private suspend fun <T> read(source: FileSource, archives: List<Archive>, block: suspend (Source) -> T): T = when (source) {
        is FileSource.FromArchive -> archives[source.archive].read(source.entryName, block)
        is FileSource.FromBundle -> bundledAssets.open(source.asset, block)
    }

    private suspend fun excludeHiddenFoldersFromLibrary(volume: KoboVolume) {
        val path = NickelConfig.PATH
        val before = if (volume.entryType(path) == EntryType.FILE) volume.read(path).decodeToString() else null
        if (!NickelConfig.needsHiddenFolderExclusion(before)) return
        volume.append(path, NickelConfig.HIDDEN_FOLDER_EXCLUSION.encodeToByteArray(), durable = true)
        val after = volume.read(path).decodeToString()
        if (NickelConfig.needsHiddenFolderExclusion(after)) {
            throw KoboVolumeException("Nickel config update was ineffective")
        }
    }

    /** Best effort: the files are installed either way; a missing receipt only costs version info. */
    private suspend fun recordReceipts(volume: KoboVolume, components: List<Component>, device: KoboDevice) {
        try {
            val updated = components.map { InstallReceipt(it.definition.id, it.release.version, device.firmware) }
            val ids = updated.map { it.packageId }.toSet()
            receiptStore.save(volume, receiptStore.load(volume).filterNot { it.packageId in ids } + updated)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun removeTempFile(volume: KoboVolume) {
        try {
            volume.delete(KOBO_ROOT_TEMP)
        } catch (_: KoboVolumeException) {
            // Harmless if it stays: Nickel only processes the exact name KoboRoot.tgz.
        }
    }

    /** Files and folders from KFMon's install.sh failure recap that exist on the volume. */
    private suspend fun leftovers(volume: KoboVolume): List<KoboPath> = try {
        LEFTOVER_CANDIDATES.filter { volume.exists(it) }
    } catch (_: KoboVolumeException) {
        emptyList()
    }

    private companion object {
        val KOBO_ROOT_TEMP: KoboPath =
            (InstallPlan.KOBO_ROOT_TGZ.parent ?: KoboPath.ROOT).resolve(InstallPlan.KOBO_ROOT_TEMP_NAME)

        val LEFTOVER_CANDIDATES: List<KoboPath> = listOf(
            ".adds/kfmon", ".adds/koreader", ".adds/plato",
            ".kobo/KoboRoot.tgz", "kfmon.png", "koreader.png", "icons/plato.png",
        ).map(KoboPath::of)
    }
}
