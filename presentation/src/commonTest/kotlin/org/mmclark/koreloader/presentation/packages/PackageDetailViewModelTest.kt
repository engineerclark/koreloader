package org.mmclark.koreloader.presentation.packages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.Buffer
import okio.Source
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.install.DownloadPackage
import org.mmclark.koreloader.application.install.InstallError
import org.mmclark.koreloader.application.install.InstallPackage
import org.mmclark.koreloader.application.install.InstallProgress
import org.mmclark.koreloader.application.packages.FetchReleases
import org.mmclark.koreloader.application.packages.GetPackageOverview
import org.mmclark.koreloader.application.packages.InspectVolume
import org.mmclark.koreloader.application.port.Archive
import org.mmclark.koreloader.application.port.ArchiveReader
import org.mmclark.koreloader.application.port.BundledAssets
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.application.port.PackageDownloadException
import org.mmclark.koreloader.application.port.PackageDownloader
import org.mmclark.koreloader.application.port.ReceiptStore
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.application.uninstall.RemovalPreview
import org.mmclark.koreloader.application.uninstall.UninstallPackage
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.PackageCatalog
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.packages.PackageStatus
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PackageDetailViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun release(id: PackageId, version: String, verified: Boolean) = PackageRelease(
        id, version, "2026-01-01",
        Artifact("$id-$version.zip", "https://example.org/$id.zip", 1, if (verified) listOf(Checksum.of(HashAlgorithm.SHA256, "0".repeat(64))) else emptyList()),
    )

    private val releases = mapOf(
        PackageId.KFMON to listOf(release(PackageId.KFMON, "1.4.6", true)),
        PackageId.KOREADER to listOf(release(PackageId.KOREADER, "v2026.07.1", true), release(PackageId.KOREADER, "v2024.11", false)),
    )

    private val volume = MapVolume().apply {
        files[KoboPath.of(".kobo/version")] = "N1,4.1,4.38.21908,4.1,4.1,00000000-0000-0000-0000-000000000391"
        files[KoboPath.of(".adds/koreader/koreader.sh")] = "#!/bin/sh"
        files[KoboPath.of(".adds/koreader/git-rev")] = "v2024.11"
        files[KoboPath.of(".adds/koreader/settings.reader.lua")] = "settings"
    }

    private fun viewModel(): PackageDetailViewModel {
        val catalog = object : PackageCatalog {
            override suspend fun releases(id: PackageId) = releases[id].orEmpty()
        }
        val receipts = object : ReceiptStore {
            override suspend fun load(volume: KoboVolume) = emptyList<InstallReceipt>()
            override suspend fun save(volume: KoboVolume, receipts: List<InstallReceipt>) {}
        }
        val downloader = object : PackageDownloader {
            override suspend fun fetch(artifact: Artifact, onProgress: suspend (Long, Long?) -> Unit): LocalFile =
                throw PackageDownloadException("offline")
            override suspend fun discard(file: LocalFile) {}
        }
        val archives = object : ArchiveReader {
            override suspend fun <T> open(file: LocalFile, block: suspend (Archive) -> T): T = error("unused")
        }
        val assets = object : BundledAssets {
            override fun size(asset: String) = 1L
            override suspend fun <T> open(asset: String, block: suspend (Source) -> T): T = block(Buffer().writeUtf8("x"))
        }
        val detect = DetectKoboDevice()
        val fetch = FetchReleases(catalog)
        val inspect = InspectVolume(receipts)
        return PackageDetailViewModel(
            PackageId.KOREADER, VolumeRef("content://kobo"), { volume },
            GetPackageOverview(detect, inspect, fetch),
            InstallPackage(detect, inspect, fetch, DownloadPackage(downloader) { _, _ -> emptyMap() }, archives, assets, receipts),
            UninstallPackage(detect, assets, receipts),
        )
    }

    @Test
    fun showsInstalledVersionAndDefaultsToLatest() {
        val state = viewModel().state.value
        assertFalse(state.loading)
        val status = assertIs<PackageStatus.Installed>(state.status)
        assertEquals("v2024.11", status.version)
        assertTrue(status.updateAvailable)
        assertEquals("v2026.07.1", state.selectedRelease?.version)
        assertEquals(listOf(PackageId.KFMON), state.packageOverview?.dependenciesToInstall?.map { it.id })
    }

    @Test
    fun unverifiedVersionNeedsConfirmation() {
        val vm = viewModel()
        vm.onSelectVersion("v2024.11")
        vm.onInstall()
        assertIs<DetailDialog.ConfirmUnverified>(vm.state.value.dialog)
        assertNull(vm.state.value.operation)

        vm.onConfirmUnverified()
        assertNull(vm.state.value.dialog)
        val operation = assertIs<Operation.Install>(vm.state.value.operation)
        // Fails at download (KFMon, the dependency, comes first): no network in tests.
        assertEquals(InstallError.DownloadFailed("offline"), assertIs<InstallProgress.Failed>(operation.progress).error)
    }

    @Test
    fun deleteKeepsUserDataUnlessOptedIn() {
        val vm = viewModel()
        vm.onDelete()
        val dialog = assertIs<DetailDialog.ConfirmDelete>(vm.state.value.dialog)
        assertFalse(dialog.removeUserData)
        val preview = assertIs<RemovalPreview.Files>(dialog.preview)
        assertEquals(listOf(KoboPath.of(".adds/koreader/settings.reader.lua")), preview.userData)

        vm.onConfirmDelete()
        assertTrue(vm.state.value.operation!!.isFinished)
        assertEquals("settings", volume.files[KoboPath.of(".adds/koreader/settings.reader.lua")])
        assertNull(volume.files[KoboPath.of(".adds/koreader/koreader.sh")])
    }

    /** Flat map volume: directories are implied by file paths. */
    private class MapVolume : KoboVolume {
        val files = LinkedHashMap<KoboPath, String>()
        private fun isDir(path: KoboPath) = path.isRoot || files.keys.any { it != path && it.startsWith(path) }
        override suspend fun entryType(path: KoboPath) = when {
            path in files -> EntryType.FILE
            isDir(path) -> EntryType.DIRECTORY
            else -> null
        }
        override suspend fun list(dir: KoboPath) =
            files.keys.filter { it.startsWith(dir) && it != dir }.map { it.segments[dir.segments.size] }.distinct()
        override suspend fun read(path: KoboPath) = files.getValue(path).encodeToByteArray()
        override suspend fun createDirectories(dir: KoboPath) {}
        override suspend fun write(path: KoboPath, source: Source, durable: Boolean) {
            files[path] = Buffer().apply { writeAll(source) }.readUtf8()
        }
        override suspend fun append(path: KoboPath, bytes: ByteArray, durable: Boolean) {
            files[path] = (files[path] ?: "") + bytes.decodeToString()
        }
        override suspend fun rename(path: KoboPath, newName: String) {
            files[(path.parent ?: KoboPath.ROOT).resolve(newName)] = files.remove(path)!!
        }
        override suspend fun delete(path: KoboPath): Boolean {
            val doomed = files.keys.filter { it == path || it.startsWith(path) }
            doomed.forEach { files.remove(it) }
            return doomed.isNotEmpty()
        }
    }
}
