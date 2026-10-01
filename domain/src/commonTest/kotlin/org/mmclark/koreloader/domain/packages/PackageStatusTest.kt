package org.mmclark.koreloader.domain.packages

import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.FirmwareVersion
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageStatusTest {

    private val fw = FirmwareVersion.of(4, 38, 21908)
    private fun release(version: String) = PackageRelease(
        PackageId.KOREADER, version, null, Artifact("k-$version.zip", "https://example.org/k.zip", 1, emptyList()),
    )
    private val releases = listOf(release("v2026.07.1"), release("v2026.07"), release("v2026.03"))

    @Test
    fun resolverPrefersTheVersionFile() {
        val receipt = InstallReceipt(PackageId.KOREADER, "v2026.03", fw)
        assertEquals(InstalledState.NotInstalled, InstalledStateResolver.resolve(false, "v2026.07", receipt))
        assertEquals(InstalledState.Installed("v2026.07", receipt), InstalledStateResolver.resolve(true, "v2026.07\n", receipt))
        assertEquals(InstalledState.Installed("v2026.03", receipt), InstalledStateResolver.resolve(true, null, receipt))
        assertEquals(InstalledState.Installed(null, null), InstalledStateResolver.resolve(true, "  ", null))
    }

    @Test
    fun updateAvailableOnlyForKnownOlderReleases() {
        fun status(v: String?) = PackageStatusPolicy.status(InstalledState.Installed(v, null), releases, fw) as PackageStatus.Installed
        assertTrue(status("v2026.03").updateAvailable)
        assertFalse(status("v2026.07.1").updateAvailable)
        assertFalse(status("v2026.07.2-206-g322551a").updateAvailable) // nightly: unknown
        assertFalse(status(null).updateAvailable)
        assertEquals("v2026.07.1", status(null).latestVersion)
        assertEquals(PackageStatus.NotInstalled, PackageStatusPolicy.status(InstalledState.NotInstalled, releases, fw))
    }

    @Test
    fun kfmonNeedsReinstallAfterAFirmwareUpdate() {
        val receipt = InstallReceipt(PackageId.KFMON, "1.4.6", FirmwareVersion.of(4, 37))
        val status = PackageStatusPolicy.status(InstalledState.Installed("1.4.6", receipt), emptyList(), fw) as PackageStatus.Installed
        assertTrue(status.needsReinstall)
        assertTrue(PackageStatusPolicy.dependencyNeedsInstall(status))
        val same = PackageStatusPolicy.status(InstalledState.Installed("1.4.6", receipt.copy(firmware = fw)), emptyList(), fw)
        assertFalse(PackageStatusPolicy.dependencyNeedsInstall(same))
        assertTrue(PackageStatusPolicy.dependencyNeedsInstall(PackageStatus.NotInstalled))
        // A pending uninstall means installing KOReader must reinstall KFMon (replacing the uninstaller).
        val pending = PackageStatusPolicy.status(InstalledState.Installed("1.4.6", receipt.copy(firmware = fw, removalPending = true)), emptyList(), fw)
        assertTrue((pending as PackageStatus.Installed).removalPending)
        assertTrue(PackageStatusPolicy.dependencyNeedsInstall(pending))
    }

    @Test
    fun removalKeepsUserDataByDefault() {
        val removal = Packages.KOREADER.removal as Removal.DeleteFiles
        val entries = listOf("koreader.sh", "plugins", "settings", "settings.reader.lua", "History.lua", "cache")
        val keep = RemovalPlan.forPackage(removal, entries, removeUserData = false)
        assertEquals(
            listOf(".adds/koreader/koreader.sh", ".adds/koreader/plugins", ".adds/koreader/cache") +
                listOf("koreader.png", ".adds/kfmon/config/koreader.ini", ".adds/nm/koreader"),
            keep.deletions.map { it.toString() },
        )
        assertEquals(listOf(".adds/koreader/settings", ".adds/koreader/settings.reader.lua", ".adds/koreader/History.lua"), keep.preserved.map { it.toString() })

        val all = RemovalPlan.forPackage(removal, entries, removeUserData = true)
        assertEquals(KoboPath.of(".adds/koreader"), all.deletions.first())
        assertTrue(all.preserved.isEmpty())
    }

    @Test
    fun platoRemoveAllIncludesLibraryMetadataButNeverTrash() {
        val removal = Packages.PLATO.removal as Removal.DeleteFiles
        val all = RemovalPlan.forPackage(removal, listOf("plato.sh"), removeUserData = true).deletions.map { it.toString() }
        assertTrue(".reading-states" in all && ".kobo/kepub/.metadata.json" in all)
        assertTrue(all.none { it.endsWith(".trash") })
    }
}
