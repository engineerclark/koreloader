package org.mmclark.koreloader.application.install

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.fake.FakeArchiveReader
import org.mmclark.koreloader.application.fake.FakeChecksums
import org.mmclark.koreloader.application.fake.FakeDownloader
import org.mmclark.koreloader.application.fake.InMemoryKoboVolume
import org.mmclark.koreloader.application.fake.koboVolume
import org.mmclark.koreloader.application.fake.ocpArchive
import org.mmclark.koreloader.application.fake.testRelease
import org.mmclark.koreloader.domain.catalog.ArtifactVerification
import org.mmclark.koreloader.domain.install.NickelConfig
import org.mmclark.koreloader.domain.install.PlanProblem
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallPackageTest {

    private fun installer(
        downloader: FakeDownloader = FakeDownloader(),
        checksums: FakeChecksums = FakeChecksums(),
        archive: FakeArchiveReader = ocpArchive(),
    ) = InstallPackage(DetectKoboDevice(), DownloadPackage(downloader, checksums), archive)

    private suspend fun InstallPackage.run(volume: InMemoryKoboVolume) = this(volume, testRelease).toList()

    @Test
    fun installsEverythingWithKoboRootLast() = runTest {
        val volume = koboVolume()
        val progress = installer().run(volume)

        assertIs<InstallProgress.Succeeded>(progress.last())
        assertEquals("NEW-KOBOROOT", volume.fileText(".kobo/KoboRoot.tgz"))
        assertEquals("#!/bin/sh", volume.fileText(".adds/koreader/koreader.sh"))
        assertEquals("png-kfmon", volume.fileText("kfmon.png"))
        assertEquals("[General]\nfoo=bar\n" + NickelConfig.HIDDEN_FOLDER_EXCLUSION, volume.fileText(".kobo/Kobo/Kobo eReader.conf"))
        assertNull(volume.fileText(".kobo/KoboRoot.tgz.koreloader-tmp"))

        // Config first, KoboRoot written under a temp name and renamed as the very last operation.
        assertEquals("append .kobo/Kobo/Kobo eReader.conf", volume.log.first())
        assertEquals(
            listOf("write .kobo/KoboRoot.tgz.koreloader-tmp", "rename .kobo/KoboRoot.tgz.koreloader-tmp -> KoboRoot.tgz"),
            volume.log.takeLast(2),
        )

        val kinds = progress.map { it::class.simpleName }.distinct()
        assertEquals(
            listOf("CheckingDevice", "Downloading", "VerifyingDownload", "PreparingDevice", "Writing", "VerifyingInstall", "Succeeded"),
            kinds,
        )
        val lastWriting = progress.filterIsInstance<InstallProgress.Writing>().last()
        assertEquals(6, lastWriting.filesDone)
        assertEquals(lastWriting.bytesTotal, lastWriting.bytesDone)
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
    fun configIsCreatedWhenMissing() = runTest {
        val volume = koboVolume(conf = null)
        assertIs<InstallProgress.Succeeded>(installer().run(volume).last())
        assertEquals(NickelConfig.HIDDEN_FOLDER_EXCLUSION, volume.fileText(".kobo/Kobo/Kobo eReader.conf"))
    }

    @Test
    fun unsupportedFirmwareTouchesNothing() = runTest {
        val volume = koboVolume(firmware = "5.1.0")
        val downloader = FakeDownloader()
        val result = installer(downloader = downloader).run(volume).last()
        assertIs<InstallError.DeviceUnsupported>(assertIs<InstallProgress.Failed>(result).error)
        assertEquals(0, downloader.fetches)
        assertTrue(volume.log.isEmpty())
    }

    @Test
    fun notAKobo() = runTest {
        val result = installer().run(InMemoryKoboVolume()).last()
        val error = assertIs<InstallError.DeviceNotReady>(assertIs<InstallProgress.Failed>(result).error)
        assertEquals(DeviceDetection.NotAKobo, error.detection)
    }

    @Test
    fun downloadFailure() = runTest {
        val volume = koboVolume()
        val result = installer(downloader = FakeDownloader(failWith = "offline")).run(volume).last()
        assertEquals(InstallError.DownloadFailed("offline"), assertIs<InstallProgress.Failed>(result).error)
        assertTrue(volume.log.isEmpty())
    }

    @Test
    fun corruptCacheIsDiscardedAndDownloadedAgain() = runTest {
        val downloader = FakeDownloader()
        val result = installer(downloader = downloader, checksums = FakeChecksums("cd", "ab")).run(koboVolume()).last()
        assertIs<InstallProgress.Succeeded>(result)
        assertEquals(2, downloader.fetches)
        assertEquals(1, downloader.discards)
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
    fun unsafeArchiveFailsBeforeTouchingTheKobo() = runTest {
        val volume = koboVolume()
        val result = installer(archive = ocpArchive("../../evil" to "x")).run(volume).last()
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
        val error = assertIs<InstallError.WriteFailed>(result.error)
        assertEquals(KoboPath.of(".adds/koreader/koreader.sh"), error.path)
        assertNull(volume.fileText(".kobo/KoboRoot.tgz"))
        assertTrue(KoboPath.of("kfmon.png") in result.leftovers)
        assertTrue(KoboPath.of(".kobo/KoboRoot.tgz") !in result.leftovers)
    }

    @Test
    fun renameFailureRemovesTheTempFile() = runTest {
        val volume = koboVolume().apply { failOn = { op, _ -> op == "rename" } }
        val result = assertIs<InstallProgress.Failed>(installer().run(volume).last())
        assertIs<InstallError.WriteFailed>(result.error)
        assertNull(volume.fileText(".kobo/KoboRoot.tgz"))
        assertNull(volume.fileText(".kobo/KoboRoot.tgz.koreloader-tmp"))
    }

    @Test
    fun corruptEntryIsReportedAsUnreadablePackage() = runTest {
        val archive = ocpArchive().apply { failOnRead = ".kobo/KoboRoot.tgz" }
        val volume = koboVolume()
        val result = assertIs<InstallProgress.Failed>(installer(archive = archive).run(volume).last())
        assertIs<InstallError.UnreadablePackage>(result.error)
        assertNull(volume.fileText(".kobo/KoboRoot.tgz"))
    }
}
