package org.mmclark.koreloader.application.install

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.fake.FW
import org.mmclark.koreloader.application.fake.FakeArchiveReader
import org.mmclark.koreloader.application.fake.FakeBundledAssets
import org.mmclark.koreloader.application.fake.FakeCatalog
import org.mmclark.koreloader.application.fake.FakeChecksums
import org.mmclark.koreloader.application.fake.FakeDownloader
import org.mmclark.koreloader.application.fake.InMemoryKoboVolume
import org.mmclark.koreloader.application.fake.InMemoryReceiptStore
import org.mmclark.koreloader.application.fake.archivesFor
import org.mmclark.koreloader.application.fake.kfmonRelease
import org.mmclark.koreloader.application.fake.koboVolume
import org.mmclark.koreloader.application.fake.koboVolumeWithKfmon
import org.mmclark.koreloader.application.fake.koreaderRelease
import org.mmclark.koreloader.application.fake.koreaderZipEntries
import org.mmclark.koreloader.application.fake.ocpKfmonEntries
import org.mmclark.koreloader.application.fake.release
import org.mmclark.koreloader.application.packages.FetchReleases
import org.mmclark.koreloader.application.packages.InspectVolume
import org.mmclark.koreloader.domain.catalog.ArtifactVerification
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.FirmwareVersion
import org.mmclark.koreloader.domain.install.NickelConfig
import org.mmclark.koreloader.domain.install.PlanProblem
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallPackageTest {

    private val receipts = InMemoryReceiptStore()

    private fun installer(
        downloader: FakeDownloader = FakeDownloader(),
        checksums: FakeChecksums = FakeChecksums(),
        archives: FakeArchiveReader = archivesFor(kfmonRelease, koreaderRelease),
        catalog: FakeCatalog = FakeCatalog(),
    ): InstallPackage {
        val detect = DetectKoboDevice()
        return InstallPackage(
            detect, InspectVolume(receipts), FetchReleases(catalog), DownloadPackage(downloader, checksums),
            archives, FakeBundledAssets(), receipts,
        )
    }

    private suspend fun InstallPackage.run(volume: InMemoryKoboVolume, release: PackageRelease = koreaderRelease) =
        this(volume, release).toList()

    @Test
    fun koreaderWithoutKfmonInstallsBothWithKoboRootLast() = runTest {
        val volume = koboVolume()
        val downloader = FakeDownloader()
        val progress = installer(downloader = downloader).run(volume)

        val success = assertIs<InstallProgress.Succeeded>(progress.last())
        assertEquals(listOf(kfmonRelease, koreaderRelease), success.installed)
        assertTrue(success.koboUpdateRequired)
        assertEquals(listOf("kfmon-1.4.6.zip", "koreader-v2026.07.1.zip"), downloader.fetched)

        assertEquals("KFMON-KOBOROOT", volume.fileText(".kobo/KoboRoot.tgz"))
        assertEquals("#!/bin/sh", volume.fileText(".adds/koreader/koreader.sh"))
        assertEquals("bundled:kfmon/koreader.png", volume.fileText("koreader.png")) // not the zip's fmon relic
        assertEquals("bundled:kfmon/koreader.ini", volume.fileText(".adds/kfmon/config/koreader.ini"))
        assertEquals("bundled:nm/kfmon", volume.fileText(".adds/nm/kfmon"))
        assertEquals("[General]\nfoo=bar\n" + NickelConfig.HIDDEN_FOLDER_EXCLUSION, volume.fileText(".kobo/Kobo/Kobo eReader.conf"))
        assertNull(volume.fileText(".kobo/KoboRoot.tgz.koreloader-tmp"))
        assertEquals(
            listOf("write .kobo/KoboRoot.tgz.koreloader-tmp", "rename .kobo/KoboRoot.tgz.koreloader-tmp -> KoboRoot.tgz"),
            volume.log.takeLast(2),
        )
        assertEquals(
            listOf(InstallReceipt(PackageId.KFMON, "1.4.6", FW), InstallReceipt(PackageId.KOREADER, "v2026.07.1", FW)),
            receipts.receipts,
        )
        val downloads = progress.filterIsInstance<InstallProgress.Downloading>().map { it.packageId to it.part }
        assertEquals(listOf(PackageId.KFMON to 1, PackageId.KOREADER to 2), downloads)
    }

    @Test
    fun koreaderWithWorkingKfmonSkipsItAndLeavesAPendingUpdateAlone() = runTest {
        val volume = koboVolumeWithKfmon(receipts).apply { putFile(".kobo/KoboRoot.tgz", "PENDING-UNINSTALLER") }
        val downloader = FakeDownloader()
        val success = assertIs<InstallProgress.Succeeded>(installer(downloader = downloader).run(volume).last())
        assertEquals(listOf(koreaderRelease), success.installed)
        assertTrue(success.koboUpdateRequired) // the pending KoboRoot.tgz still runs on unplug
        assertEquals(listOf("koreader-v2026.07.1.zip"), downloader.fetched)
        // A pending KoboRoot.tgz that this install doesn't replace is left alone.
        assertEquals("PENDING-UNINSTALLER", volume.fileText(".kobo/KoboRoot.tgz"))
        assertEquals("1.4.5", receipts.receipts.single { it.packageId == PackageId.KFMON }.version)
    }

    @Test
    fun secondInstallBeforeEjectKeepsThePendingKfmonInstall() = runTest {
        val plato = release(PackageId.PLATO, "0.9.45")
        val platoZip = listOf("plato.sh" to "#!/bin/sh", "css/" to null, "css/epub.css" to "css")
        val volume = koboVolume()
        val downloader = FakeDownloader()
        val archives = archivesFor(
            kfmonRelease, koreaderRelease, plato,
            entries = mapOf(PackageId.KFMON to ocpKfmonEntries, PackageId.KOREADER to koreaderZipEntries, PackageId.PLATO to platoZip),
        )
        assertIs<InstallProgress.Succeeded>(installer(downloader = downloader, archives = archives).run(volume).last())
        val writesAfterFirst = volume.log.size

        val second = assertIs<InstallProgress.Succeeded>(installer(downloader = downloader, archives = archives).run(volume, plato).last())
        assertEquals(listOf(plato), second.installed) // KFMon not reinstalled
        assertTrue(second.koboUpdateRequired) // the first install's KoboRoot.tgz still makes the Kobo restart
        assertEquals("KFMON-KOBOROOT", volume.fileText(".kobo/KoboRoot.tgz"))
        assertTrue(volume.log.drop(writesAfterFirst).none { "KoboRoot" in it }, "${volume.log.drop(writesAfterFirst)}")
        assertEquals("#!/bin/sh", volume.fileText(".adds/koreader/koreader.sh"))
        assertEquals("#!/bin/sh", volume.fileText(".adds/plato/plato.sh"))
        assertEquals("bundled:nm/plato", volume.fileText(".adds/nm/plato"))
        assertEquals(setOf(PackageId.KFMON, PackageId.KOREADER, PackageId.PLATO), receipts.receipts.map { it.packageId }.toSet())
        assertEquals(listOf("kfmon-1.4.6.zip", "koreader-v2026.07.1.zip", "plato-0.9.45.zip"), downloader.fetched)
    }

    @Test
    fun kfmonIsReinstalledAfterAFirmwareUpdate() = runTest {
        val volume = koboVolumeWithKfmon(receipts, firmware = FirmwareVersion.of(4, 37, 1))
        val success = assertIs<InstallProgress.Succeeded>(installer().run(volume).last())
        assertEquals(listOf(kfmonRelease, koreaderRelease), success.installed)
    }

    @Test
    fun kfmonAloneInstallsTheOcp() = runTest {
        val volume = koboVolume()
        val success = assertIs<InstallProgress.Succeeded>(installer().run(volume, kfmonRelease).last())
        assertEquals(listOf(kfmonRelease), success.installed)
        assertEquals("bundled:nm/kfmon", volume.fileText(".adds/nm/kfmon"))
        assertNull(volume.fileText(".adds/koreader/koreader.sh"))
    }

    @Test
    fun unverifiableReleaseIsSizeCheckedOnly() = runTest {
        val old = release(PackageId.KOREADER, "v2024.11", verifiable = false)
        val volume = koboVolumeWithKfmon(receipts)
        val result = installer(checksums = FakeChecksums("cd"), archives = archivesFor(old)).run(volume, old).last()
        assertIs<InstallProgress.Succeeded>(result)
    }

    @Test
    fun configIsAppendedOnlyOnce() = runTest {
        val volume = koboVolume()
        installer().run(volume)
        installer().run(volume)
        val conf = volume.fileText(".kobo/Kobo/Kobo eReader.conf")!!
        assertEquals(1, conf.split(NickelConfig.EXCLUDE_SYNC_FOLDERS_LINE).size - 1)
    }

    @Test
    fun unsupportedFirmwareTouchesNothing() = runTest {
        val volume = koboVolume(firmware = "5.1.0")
        val downloader = FakeDownloader()
        val result = installer(downloader = downloader).run(volume).last()
        assertIs<InstallError.DeviceUnsupported>(assertIs<InstallProgress.Failed>(result).error)
        assertTrue(downloader.fetched.isEmpty())
        assertTrue(volume.log.isEmpty())
    }

    @Test
    fun notAKobo() = runTest {
        val error = assertIs<InstallError.DeviceNotReady>(assertIs<InstallProgress.Failed>(installer().run(InMemoryKoboVolume()).last()).error)
        assertEquals(DeviceDetection.NotAKobo, error.detection)
    }

    @Test
    fun dependencyCatalogFailure() = runTest {
        val catalog = FakeCatalog().apply { failWith = "offline" }
        val result = installer(catalog = catalog).run(koboVolume()).last()
        assertEquals(InstallError.DependencyUnavailable(PackageId.KFMON, "offline"), assertIs<InstallProgress.Failed>(result).error)
    }

    @Test
    fun persistentChecksumMismatchFailsBeforeTouchingTheKobo() = runTest {
        val volume = koboVolume()
        val downloader = FakeDownloader()
        val result = installer(downloader = downloader, checksums = FakeChecksums("cd")).run(volume).last()
        val error = assertIs<InstallError.VerificationFailed>(assertIs<InstallProgress.Failed>(result).error)
        assertIs<ArtifactVerification.ChecksumMismatch>(error.verification)
        assertEquals(2, downloader.discards)
        assertTrue(volume.log.isEmpty())
    }

    @Test
    fun corruptCacheIsDiscardedAndDownloadedAgain() = runTest {
        val downloader = FakeDownloader()
        val result = installer(downloader = downloader, checksums = FakeChecksums("cd", "ab")).run(koboVolume(), kfmonRelease).last()
        assertIs<InstallProgress.Succeeded>(result)
        assertEquals(2, downloader.fetched.size)
        assertEquals(1, downloader.discards)
    }

    @Test
    fun unsafeArchiveFailsBeforeTouchingTheKobo() = runTest {
        val volume = koboVolume()
        val archives = archivesFor(
            kfmonRelease, koreaderRelease,
            entries = mapOf(PackageId.KFMON to ocpKfmonEntries + ("../../evil" to "x"), PackageId.KOREADER to koreaderZipEntries),
        )
        val result = installer(archives = archives).run(volume).last()
        val error = assertIs<InstallError.InvalidPackage>(assertIs<InstallProgress.Failed>(result).error)
        assertEquals(PlanProblem.UnsafeEntry("../../evil"), error.problem)
        assertTrue(volume.log.isEmpty())
    }

    @Test
    fun writeFailureLeavesNoKoboRootAndReportsLeftovers() = runTest {
        val volume = koboVolume().apply {
            putFile(".kobo/KoboRoot.tgz", "STALE") // from an earlier aborted attempt
            failOn = { op, path -> op == "write" && path.toString() == ".adds/koreader/koreader.sh" }
        }
        val result = assertIs<InstallProgress.Failed>(installer().run(volume).last())
        assertEquals(KoboPath.of(".adds/koreader/koreader.sh"), assertIs<InstallError.WriteFailed>(result.error).path)
        assertNull(volume.fileText(".kobo/KoboRoot.tgz"))
        assertTrue(KoboPath.of("kfmon.png") in result.leftovers)
        assertTrue(receipts.receipts.isEmpty())
    }

    @Test
    fun renameFailureRemovesTheTempFile() = runTest {
        val volume = koboVolume().apply { failOn = { op, _ -> op == "rename" } }
        assertIs<InstallError.WriteFailed>(assertIs<InstallProgress.Failed>(installer().run(volume).last()).error)
        assertNull(volume.fileText(".kobo/KoboRoot.tgz"))
        assertNull(volume.fileText(".kobo/KoboRoot.tgz.koreloader-tmp"))
    }
}

class InstallWithoutPendingUpdateTest {
    @Test
    fun noKoboRootMeansNoRestart() = runTest {
        val receipts = InMemoryReceiptStore()
        val volume = koboVolumeWithKfmon(receipts)
        val installer = InstallPackage(
            DetectKoboDevice(), InspectVolume(receipts), FetchReleases(FakeCatalog()),
            DownloadPackage(FakeDownloader(), FakeChecksums()), archivesFor(koreaderRelease), FakeBundledAssets(), receipts,
        )
        val success = assertIs<InstallProgress.Succeeded>(installer(volume, koreaderRelease).toList().last())
        assertFalse(success.koboUpdateRequired)
    }
}
