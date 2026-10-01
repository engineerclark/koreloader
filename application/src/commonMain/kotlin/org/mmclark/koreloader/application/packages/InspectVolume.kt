package org.mmclark.koreloader.application.packages

import kotlinx.coroutines.CancellationException
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeException
import org.mmclark.koreloader.application.port.ReceiptStore
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.install.InstallPlan
import org.mmclark.koreloader.domain.packages.InstalledState
import org.mmclark.koreloader.domain.packages.InstalledStateResolver
import org.mmclark.koreloader.domain.packages.Packages

/** What is on a Kobo volume. */
data class VolumeInventory(
    val installed: Map<PackageId, InstalledState>,
    /** A `.kobo/KoboRoot.tgz` is waiting: Nickel will process it (and reboot) when the Kobo is unplugged. */
    val koboUpdatePending: Boolean,
)

sealed interface InventoryResult {
    data class Ready(val inventory: VolumeInventory) : InventoryResult
    data class Unavailable(val message: String) : InventoryResult
}

/** Detects which packages are installed, and their versions, from files on the volume. */
class InspectVolume(private val receipts: ReceiptStore) {

    suspend operator fun invoke(volume: KoboVolume): InventoryResult = try {
        InventoryResult.Ready(inspect(volume))
    } catch (e: CancellationException) {
        throw e
    } catch (e: KoboVolumeException) {
        InventoryResult.Unavailable(e.message ?: "volume not accessible")
    }

    internal suspend fun inspect(volume: KoboVolume): VolumeInventory {
        val receiptsById = receipts.load(volume).associateBy { it.packageId }
        val installed = Packages.all.associate { definition ->
            val markerPresent = volume.entryType(definition.marker) == EntryType.FILE
            val versionFile = definition.versionFile
                ?.takeIf { markerPresent && volume.entryType(it) == EntryType.FILE }
                ?.let { volume.read(it).decodeToString() }
            definition.id to InstalledStateResolver.resolve(markerPresent, versionFile, receiptsById[definition.id])
        }
        return VolumeInventory(installed, volume.entryType(InstallPlan.KOBO_ROOT_TGZ) == EntryType.FILE)
    }
}
