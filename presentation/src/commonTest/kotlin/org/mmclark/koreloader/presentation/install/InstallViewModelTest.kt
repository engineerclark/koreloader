package org.mmclark.koreloader.presentation.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import okio.Source
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.install.DownloadPackage
import org.mmclark.koreloader.application.install.FetchPackageRelease
import org.mmclark.koreloader.application.install.InstallError
import org.mmclark.koreloader.application.install.InstallPackage
import org.mmclark.koreloader.application.install.InstallProgress
import org.mmclark.koreloader.application.install.ReleaseLookup
import org.mmclark.koreloader.application.port.Archive
import org.mmclark.koreloader.application.port.ArchiveReader
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.application.port.PackageDownloadException
import org.mmclark.koreloader.application.port.PackageDownloader
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.InstallTarget
import org.mmclark.koreloader.domain.catalog.PackageCatalog
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InstallViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val release = PackageRelease(
        PackageId.KOREADER, "v1",
        Artifact("a.zip", "https://example.org/a.zip", 1, listOf(Checksum.of(HashAlgorithm.SHA256, "0".repeat(64)))),
        InstallTarget.VOLUME_ROOT,
    )

    private fun viewModel(firmware: String = "4.38.21908", catalogFails: Boolean = false): InstallViewModel {
        val catalog = object : PackageCatalog {
            override suspend fun latestRelease(id: PackageId) = if (catalogFails) error("offline") else release
        }
        val downloader = object : PackageDownloader {
            override suspend fun fetch(artifact: Artifact, onProgress: suspend (Long, Long?) -> Unit): LocalFile =
                throw PackageDownloadException("no network in tests")
            override suspend fun discard(file: LocalFile) {}
        }
        val archives = object : ArchiveReader {
            override suspend fun <T> open(file: LocalFile, block: suspend (Archive) -> T): T = error("unused")
        }
        val detect = DetectKoboDevice()
        return InstallViewModel(
            PackageId.KOREADER,
            { VersionOnlyVolume(firmware) },
            FetchPackageRelease(catalog),
            detect,
            InstallPackage(detect, DownloadPackage(downloader, { _, _ -> emptyMap() }), archives),
        )
    }

    @Test
    fun loadsReleaseOnStart() {
        assertEquals(ReleaseLookup.Found(release), viewModel().state.value.release)
        assertIs<ReleaseLookup.Unavailable>(viewModel(catalogFails = true).state.value.release)
    }

    @Test
    fun installRequiresASupportedDevice() {
        val vm = viewModel()
        assertFalse(vm.state.value.canInstall)
        vm.onVolumeSelected(VolumeRef("content://kobo"))
        assertTrue(vm.state.value.canInstall)

        val old = viewModel(firmware = "5.0.1")
        old.onVolumeSelected(VolumeRef("content://kobo"))
        assertFalse(old.state.value.canInstall)
    }

    @Test
    fun installRunsToAFinalStateAndCanBeDismissed() = runTest {
        val vm = viewModel()
        vm.onVolumeSelected(VolumeRef("content://kobo"))
        vm.onInstall()
        val failed = assertIs<InstallProgress.Failed>(vm.state.value.progress)
        assertEquals(InstallError.DownloadFailed("no network in tests"), failed.error)
        assertFalse(vm.state.value.installing)

        vm.onDismissResult()
        assertEquals(null, vm.state.value.progress)
        assertTrue(vm.state.value.canInstall)
    }

    private class VersionOnlyVolume(private val firmware: String) : KoboVolume {
        override suspend fun entryType(path: KoboPath) = when (path.toString()) {
            ".kobo" -> EntryType.DIRECTORY
            ".kobo/version" -> EntryType.FILE
            else -> null
        }
        override suspend fun list(dir: KoboPath) = emptyList<String>()
        override suspend fun read(path: KoboPath) =
            "N1,4.1,$firmware,4.1,4.1,00000000-0000-0000-0000-000000000391".encodeToByteArray()
        override suspend fun createDirectories(dir: KoboPath) {}
        override suspend fun write(path: KoboPath, source: Source, durable: Boolean) { Buffer().writeAll(source) }
        override suspend fun append(path: KoboPath, bytes: ByteArray, durable: Boolean) {}
        override suspend fun rename(path: KoboPath, newName: String) {}
        override suspend fun delete(path: KoboPath) = false
    }
}
