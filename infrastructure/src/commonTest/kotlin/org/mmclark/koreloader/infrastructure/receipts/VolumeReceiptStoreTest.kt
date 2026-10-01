package org.mmclark.koreloader.infrastructure.receipts

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Source
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.device.FirmwareVersion
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VolumeReceiptStoreTest {

    private class MapVolume : KoboVolume {
        val files = HashMap<KoboPath, ByteArray>()
        override suspend fun entryType(path: KoboPath) = if (path in files) EntryType.FILE else null
        override suspend fun list(dir: KoboPath) = emptyList<String>()
        override suspend fun read(path: KoboPath) = files.getValue(path)
        override suspend fun createDirectories(dir: KoboPath) {}
        override suspend fun write(path: KoboPath, source: Source, durable: Boolean) {
            files[path] = Buffer().apply { writeAll(source) }.readByteArray()
        }
        override suspend fun append(path: KoboPath, bytes: ByteArray, durable: Boolean) {}
        override suspend fun rename(path: KoboPath, newName: String) {}
        override suspend fun delete(path: KoboPath) = files.remove(path) != null
    }

    @Test
    fun roundTrips() = runTest {
        val volume = MapVolume()
        val store = VolumeReceiptStore()
        assertTrue(store.load(volume).isEmpty())
        val receipts = listOf(
            InstallReceipt(PackageId.KFMON, "1.4.6-191-gca31869", FirmwareVersion.of(4, 38, 21908)),
            InstallReceipt(PackageId.KOREADER, "v2026.07.1", FirmwareVersion.of(4, 38, 21908)),
        )
        store.save(volume, receipts)
        assertEquals(receipts, store.load(volume))
        assertTrue("\"koreader\"" in volume.files.getValue(VolumeReceiptStore.PATH).decodeToString())
    }

    @Test
    fun corruptFileReadsAsEmpty() = runTest {
        val volume = MapVolume().apply { files[VolumeReceiptStore.PATH] = "{not json".encodeToByteArray() }
        assertTrue(VolumeReceiptStore().load(volume).isEmpty())
    }
}
