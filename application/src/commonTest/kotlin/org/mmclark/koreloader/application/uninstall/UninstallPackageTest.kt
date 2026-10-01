package org.mmclark.koreloader.application.uninstall

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.fake.FW
import org.mmclark.koreloader.application.fake.FakeBundledAssets
import org.mmclark.koreloader.application.fake.InMemoryReceiptStore
import org.mmclark.koreloader.application.fake.koboVolume
import org.mmclark.koreloader.application.fake.koboVolumeWithKfmon
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UninstallPackageTest {

    private val receipts = InMemoryReceiptStore()
    private val uninstall = UninstallPackage(DetectKoboDevice(), FakeBundledAssets(), receipts)

    private fun koreaderVolume() = koboVolumeWithKfmon(receipts).apply {
        putFile(".adds/koreader/koreader.sh", "#!/bin/sh")
        putFile(".adds/koreader/plugins/x.lua", "x")
        putFile(".adds/koreader/settings.reader.lua", "settings")
        putFile(".adds/koreader/settings/statistics.sqlite3", "stats")
        putFile("koreader.png", "png")
        putFile(".adds/kfmon/config/koreader.ini", "ini")
        putFile(".adds/nm/koreader", "nm")
        receipts.receipts += InstallReceipt(PackageId.KOREADER, "v2026.07.1", FW)
    }

    @Test
    fun deleteKeepsSettingsAndHistoryByDefault() = runTest {
        val volume = koreaderVolume()
        val last = uninstall(volume, PackageId.KOREADER, removeUserData = false).toList().last()
        assertEquals(UninstallProgress.Succeeded(koboUpdateRequired = false), last)
        assertNull(volume.fileText(".adds/koreader/koreader.sh"))
        assertNull(volume.fileText(".adds/koreader/plugins/x.lua"))
        assertNull(volume.fileText("koreader.png"))
        assertNull(volume.fileText(".adds/nm/koreader"))
        assertEquals("settings", volume.fileText(".adds/koreader/settings.reader.lua"))
        assertEquals("stats", volume.fileText(".adds/koreader/settings/statistics.sqlite3"))
        assertEquals("[kfmon]", volume.fileText(".adds/kfmon/config/kfmon.ini")) // KFMon untouched
        assertEquals(listOf(PackageId.KFMON), receipts.receipts.map { it.packageId })
    }

    @Test
    fun deleteCanRemoveUserDataToo() = runTest {
        val volume = koreaderVolume()
        uninstall(volume, PackageId.KOREADER, removeUserData = true).toList()
        assertTrue(volume.allPaths.none { it.startsWith(".adds/koreader") }, "${volume.allPaths}")
    }

    @Test
    fun previewListsWhatEachChoiceRemoves() = runTest {
        val preview = assertIs<RemovalPreview.Files>(uninstall.preview(koreaderVolume(), PackageId.KOREADER))
        assertEquals(
            listOf(".adds/koreader/settings.reader.lua", ".adds/koreader/settings").sorted(),
            preview.userData.map { it.toString() }.sorted(),
        )
        assertTrue(KoboPath.of(".adds/koreader/plugins") in preview.keepingUserData.deletions)
        assertEquals(KoboPath.of(".adds/koreader"), preview.removingUserData.deletions.first())
    }

    @Test
    fun kfmonDeletePlacesTheOfficialUninstaller() = runTest {
        val volume = koboVolumeWithKfmon(receipts)
        val last = uninstall(volume, PackageId.KFMON, removeUserData = false).toList().last()
        assertEquals(UninstallProgress.Succeeded(koboUpdateRequired = true), last)
        assertEquals("bundled:kfmon-uninstaller/KoboRoot.tgz", volume.fileText(".kobo/KoboRoot.tgz"))
        assertNull(volume.fileText(".kobo/KoboRoot.tgz.koreloader-tmp"))
        assertEquals("[kfmon]", volume.fileText(".adds/kfmon/config/kfmon.ini")) // removed by the Kobo, not by us
        assertTrue(receipts.receipts.single { it.packageId == PackageId.KFMON }.removalPending)
        val preview = assertIs<RemovalPreview.OnDevice>(uninstall.preview(volume, PackageId.KFMON))
        assertTrue(KoboPath.of("kfmon.png") in preview.removedOnDevice)
    }

    @Test
    fun kfmonDeleteRefusedOnUnsupportedFirmware() = runTest {
        val volume = koboVolume(firmware = "5.1.0")
        assertIs<UninstallProgress.Failed>(uninstall(volume, PackageId.KFMON, removeUserData = false).toList().last())
        assertNull(volume.fileText(".kobo/KoboRoot.tgz"))
    }
}
