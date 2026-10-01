package org.mmclark.koreloader.application.install

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.port.Archive
import org.mmclark.koreloader.application.port.ArchiveException
import org.mmclark.koreloader.application.port.ArchiveReader
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeException
import org.mmclark.koreloader.application.port.exists
import org.mmclark.koreloader.domain.catalog.InstallTarget
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.install.InstallPlan
import org.mmclark.koreloader.domain.install.NickelConfig
import org.mmclark.koreloader.domain.install.PlanResult
import org.mmclark.koreloader.domain.volume.KoboPath

/**
 * Installs a one-click package onto a Kobo volume, mirroring KFMon's `tools/install.sh`:
 * check the device, download and verify the archive, append `ExcludeSyncFolders` to Nickel's
 * config, unpack everything to the volume root, check that `KoboRoot.tgz` and `kfmon.png`
 * exist. Unlike the script, `.kobo/KoboRoot.tgz` is written last, under a temporary name
 * that is renamed once complete, and a stale one is removed before anything else is written.
 */
class InstallPackage(
    private val detectKoboDevice: DetectKoboDevice,
    private val downloadPackage: DownloadPackage,
    private val archiveReader: ArchiveReader,
) {
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

        if (release.installTarget != InstallTarget.VOLUME_ROOT) {
            send(InstallProgress.Failed(InstallError.UnreadablePackage("Only one-click packages are supported")))
            return@channelFlow
        }

        val download = downloadPackage(
            release.artifact,
            onProgress = { done, total -> send(InstallProgress.Downloading(done, total)) },
            onVerifying = { send(InstallProgress.VerifyingDownload) },
        )
        val file = when (download) {
            is DownloadResult.Ready -> download.file
            is DownloadResult.Failed -> {
                send(InstallProgress.Failed(download.error))
                return@channelFlow
            }
        }

        val outcome = try {
            archiveReader.open(file) { archive -> install(archive, volume, device, release) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ArchiveException) {
            InstallProgress.Failed(InstallError.UnreadablePackage(e.message ?: "unreadable archive"))
        }
        send(outcome)
    }

    private suspend fun ProducerScope<InstallProgress>.install(
        archive: Archive,
        volume: KoboVolume,
        device: KoboDevice,
        release: PackageRelease,
    ): InstallProgress {
        val plan = when (val result = InstallPlan.forOneClickPackage(archive.entries)) {
            is PlanResult.Valid -> result.plan
            is PlanResult.Invalid -> return InstallProgress.Failed(InstallError.InvalidPackage(result.problem))
        }

        send(InstallProgress.PreparingDevice)
        var current: KoboPath? = null
        try {
            // A stale KoboRoot.tgz must not be processed if this install fails halfway.
            current = InstallPlan.KOBO_ROOT_TGZ
            volume.delete(InstallPlan.KOBO_ROOT_TGZ)
            volume.delete(KOBO_ROOT_TEMP)

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
                archive.read(file.entryName) { source -> volume.write(file.target, source) }
                bytesDone += file.size.coerceAtLeast(0)
            }

            for (file in plan.deferredFiles) {
                current = file.target
                send(InstallProgress.Writing(total - 1, total, bytesDone, plan.totalBytes, file.target))
                val temp = (file.target.parent ?: KoboPath.ROOT).resolve(InstallPlan.KOBO_ROOT_TEMP_NAME)
                archive.read(file.entryName) { source -> volume.write(temp, source, durable = true) }
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
            return InstallProgress.Succeeded(device, release)
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
