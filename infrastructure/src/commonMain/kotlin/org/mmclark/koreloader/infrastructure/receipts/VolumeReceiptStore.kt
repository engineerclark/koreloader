package org.mmclark.koreloader.infrastructure.receipts

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.ReceiptStore
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.device.FirmwareVersion
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.volume.KoboPath

/** Stores receipts as JSON in `.adds/koreloader/receipts.json` on the Kobo (a hidden folder Nickel ignores). */
class VolumeReceiptStore : ReceiptStore {

    override suspend fun load(volume: KoboVolume): List<InstallReceipt> = try {
        if (volume.entryType(PATH) != EntryType.FILE) {
            emptyList()
        } else {
            json.decodeFromString<ReceiptsDto>(volume.read(PATH).decodeToString()).receipts.mapNotNull { it.toDomain() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList() // unreadable or corrupt: behave as if koreloader never installed anything
    }

    override suspend fun save(volume: KoboVolume, receipts: List<InstallReceipt>) {
        val dto = ReceiptsDto(version = 1, receipts = receipts.map { ReceiptDto(it.packageId.value, it.version, it.firmware.toString(), it.removalPending) })
        volume.write(PATH, Buffer().writeUtf8(json.encodeToString(ReceiptsDto.serializer(), dto)), durable = true)
    }

    private fun ReceiptDto.toDomain(): InstallReceipt? {
        val firmware = FirmwareVersion.parseOrNull(firmware) ?: return null
        return InstallReceipt(PackageId(packageId), version, firmware, removalPending)
    }

    companion object {
        val PATH: KoboPath = KoboPath.of(".adds/koreloader/receipts.json")
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    }
}

@Serializable
private class ReceiptsDto(val version: Int, val receipts: List<ReceiptDto>)

@Serializable
private class ReceiptDto(val packageId: String, val version: String, val firmware: String, val removalPending: Boolean = false)
