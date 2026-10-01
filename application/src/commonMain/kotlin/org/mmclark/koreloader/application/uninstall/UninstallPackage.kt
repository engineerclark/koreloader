package org.mmclark.koreloader.application.uninstall

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.port.BundledAssets
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeException
import org.mmclark.koreloader.application.port.ReceiptStore
import org.mmclark.koreloader.application.port.exists
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.install.InstallPlan
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.packages.Packages
import org.mmclark.koreloader.domain.packages.Removal
import org.mmclark.koreloader.domain.packages.RemovalPlan
import org.mmclark.koreloader.domain.volume.KoboPath

/** What deleting a package will do, for the confirmation dialog. Only paths that exist are listed. */
sealed interface RemovalPreview {
    /**
     * [keepingUserData] applies by default; [removingUserData] when the user opts to delete
     * settings and history too. [userData] is what the opt-in additionally removes.
     */
    data class Files(
        val keepingUserData: RemovalPlan,
        val removingUserData: RemovalPlan,
        val userData: List<KoboPath>,
    ) : RemovalPreview

    /** Removal runs on the Kobo after unplugging; [removedOnDevice] lists what its script deletes. */
    data class OnDevice(val removedOnDevice: List<KoboPath>) : RemovalPreview
}

sealed interface UninstallProgress {
    data class Removing(val done: Int, val total: Int, val current: KoboPath?) : UninstallProgress

    /** When [koboUpdateRequired], the removal happens on the Kobo after it is ejected and unplugged. */
    data class Succeeded(val koboUpdateRequired: Boolean) : UninstallProgress
    data class Failed(val message: String) : UninstallProgress

    val isFinished: Boolean get() = this is Succeeded || this is Failed
}

/** Removes a package, following its [Removal] rule (see [Packages]). */
class UninstallPackage(
    private val detectKoboDevice: DetectKoboDevice,
    private val bundledAssets: BundledAssets,
    private val receiptStore: ReceiptStore,
) {

    suspend fun preview(volume: KoboVolume, id: PackageId): RemovalPreview =
        when (val removal = Packages.get(id).removal) {
            is Removal.KoboRootUninstaller -> RemovalPreview.OnDevice(removal.removedOnDevice.filter { volume.exists(it) })
            is Removal.DeleteFiles -> {
                val entries = programEntries(volume, removal)
                val keep = RemovalPlan.forPackage(removal, entries, removeUserData = false).existing(volume)
                val all = RemovalPlan.forPackage(removal, entries, removeUserData = true).existing(volume)
                val userData = keep.preserved + removal.userDataOutside.filter { volume.exists(it) }
                RemovalPreview.Files(keep, all, userData)
            }
        }

    operator fun invoke(volume: KoboVolume, id: PackageId, removeUserData: Boolean): Flow<UninstallProgress> = flow {
        try {
            when (val detection = detectKoboDevice(volume)) {
                is DeviceDetection.Detected -> {
                    val removal = Packages.get(id).removal
                    if (removal is Removal.KoboRootUninstaller && detection.compatibility is DeviceCompatibility.Unsupported) {
                        emit(UninstallProgress.Failed("This Kobo's firmware isn't supported, so KFMon can't be removed from here."))
                        return@flow
                    }
                    when (removal) {
                        is Removal.DeleteFiles -> deleteFiles(volume, removal, removeUserData)
                        is Removal.KoboRootUninstaller -> placeUninstaller(volume, removal)
                    }
                    updateReceipt(volume, id, removal)
                    emit(UninstallProgress.Succeeded(koboUpdateRequired = removal is Removal.KoboRootUninstaller))
                }
                else -> emit(UninstallProgress.Failed("The Kobo drive isn't accessible. Is it still connected?"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KoboVolumeException) {
            emit(UninstallProgress.Failed(e.message ?: "delete failed"))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<UninstallProgress>.deleteFiles(
        volume: KoboVolume,
        removal: Removal.DeleteFiles,
        removeUserData: Boolean,
    ) {
        val plan = RemovalPlan.forPackage(removal, programEntries(volume, removal), removeUserData)
        val targets = plan.deletions.filter { volume.exists(it) }
        targets.forEachIndexed { index, path ->
            emit(UninstallProgress.Removing(index, targets.size, path))
            volume.delete(path)
        }
        emit(UninstallProgress.Removing(targets.size, targets.size, null))
        for (dir in plan.pruneIfEmpty + listOfNotNull(removal.programDir.takeIf { !removeUserData })) {
            if (volume.entryType(dir) == EntryType.DIRECTORY && volume.list(dir).isEmpty()) volume.delete(dir)
        }
    }

    /** Places the official KFMon uninstaller as `.kobo/KoboRoot.tgz` (temp name, then rename). */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<UninstallProgress>.placeUninstaller(
        volume: KoboVolume,
        removal: Removal.KoboRootUninstaller,
    ) {
        emit(UninstallProgress.Removing(0, 1, InstallPlan.KOBO_ROOT_TGZ))
        val temp = (InstallPlan.KOBO_ROOT_TGZ.parent ?: KoboPath.ROOT).resolve(InstallPlan.KOBO_ROOT_TEMP_NAME)
        try {
            volume.delete(temp)
            bundledAssets.open(removal.koboRootAsset) { source -> volume.write(temp, source, durable = true) }
            volume.rename(temp, InstallPlan.KOBO_ROOT_TGZ.name)
        } catch (e: KoboVolumeException) {
            runCatching { volume.delete(temp) }
            throw e
        }
        if (volume.entryType(InstallPlan.KOBO_ROOT_TGZ) != EntryType.FILE) {
            throw KoboVolumeException("The uninstaller wasn't written")
        }
        emit(UninstallProgress.Removing(1, 1, null))
    }

    private suspend fun programEntries(volume: KoboVolume, removal: Removal.DeleteFiles): List<String> =
        if (volume.entryType(removal.programDir) == EntryType.DIRECTORY) volume.list(removal.programDir) else emptyList()

    private suspend fun RemovalPlan.existing(volume: KoboVolume) = copy(
        deletions = deletions.filter { volume.exists(it) },
        preserved = preserved.filter { volume.exists(it) },
    )

    /** Forgets the package, or for an on-device removal, records that it is pending. */
    private suspend fun updateReceipt(volume: KoboVolume, id: PackageId, removal: Removal) {
        try {
            val receipts = receiptStore.load(volume)
            val others = receipts.filterNot { it.packageId == id }
            val updated = when (removal) {
                is Removal.DeleteFiles -> others
                is Removal.KoboRootUninstaller -> {
                    val firmware = (detectKoboDevice(volume) as? DeviceDetection.Detected)?.device?.firmware ?: return
                    val previous = receipts.firstOrNull { it.packageId == id }
                    others + InstallReceipt(id, previous?.version ?: "unknown", previous?.firmware ?: firmware, removalPending = true)
                }
            }
            if (updated != receipts) receiptStore.save(volume, updated)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
