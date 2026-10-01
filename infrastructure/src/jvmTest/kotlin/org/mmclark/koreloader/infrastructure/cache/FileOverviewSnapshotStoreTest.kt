package org.mmclark.koreloader.infrastructure.cache

import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.port.OverviewSnapshot
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceModel
import org.mmclark.koreloader.domain.device.FirmwareVersion
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.packages.InstalledState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileOverviewSnapshotStoreTest {

    private val fw = FirmwareVersion.of(4, 38, 21908)
    private val snapshot = OverviewSnapshot(
        device = KoboDevice("N1", fw, DeviceModel.fromId(391)),
        koboUpdatePending = true,
        installed = mapOf(
            PackageId.KFMON to InstalledState.Installed("1.4.6", InstallReceipt(PackageId.KFMON, "1.4.6", fw, removalPending = true)),
            PackageId.KOREADER to InstalledState.Installed(null, null),
            PackageId.PLATO to InstalledState.NotInstalled,
        ),
        releases = mapOf(
            PackageId.KOREADER to listOf(
                PackageRelease(
                    PackageId.KOREADER, "v2026.07.1", "2026-08-01",
                    Artifact("k.zip", "https://example.org/k.zip", 42, listOf(Checksum.of(HashAlgorithm.SHA256, "a".repeat(64)))),
                ),
                PackageRelease(PackageId.KOREADER, "v2024.11", null, Artifact("o.zip", "https://example.org/o.zip", 41, emptyList())),
            ),
        ),
    )

    @Test
    fun survivesARestart() = runTest {
        val file = File(Files.createTempDirectory("cache").toFile(), "overview-cache.json")
        FileOverviewSnapshotStore(file).save("content://kobo", snapshot)
        assertEquals(snapshot, FileOverviewSnapshotStore(file).load("content://kobo")) // new instance = after restart
        assertNull(FileOverviewSnapshotStore(file).load("content://other"))
        assertTrue(!File(file.path + ".tmp").exists())
    }

    @Test
    fun corruptOrMissingFileIsAnEmptyCache() = runTest {
        val dir = Files.createTempDirectory("cache").toFile()
        assertNull(FileOverviewSnapshotStore(File(dir, "missing.json")).load("k"))
        val corrupt = File(dir, "corrupt.json").apply { writeText("{not json") }
        assertNull(FileOverviewSnapshotStore(corrupt).load("k"))
        assertTrue(OverviewSnapshotCodec.decode("""{"version": 99, "volumes": {}}""").isEmpty())
    }
}
