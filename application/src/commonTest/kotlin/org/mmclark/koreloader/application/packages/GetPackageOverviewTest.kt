package org.mmclark.koreloader.application.packages

import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.fake.FakeCatalog
import org.mmclark.koreloader.application.fake.InMemoryReceiptStore
import org.mmclark.koreloader.application.fake.kfmonRelease
import org.mmclark.koreloader.application.fake.koboVolume
import org.mmclark.koreloader.application.fake.release
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.packages.PackageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetPackageOverviewTest {

    @Test
    fun combinesInstalledStateReleasesAndDependencies() = runTest {
        val volume = koboVolume().apply {
            putFile(".adds/koreader/koreader.sh", "#!/bin/sh")
            putFile(".adds/koreader/git-rev", "v2026.03")
        }
        val catalog = FakeCatalog(
            mapOf(
                PackageId.KFMON to listOf(kfmonRelease),
                PackageId.KOREADER to listOf(release(PackageId.KOREADER, "v2026.07.1"), release(PackageId.KOREADER, "v2026.03")),
            ),
        )
        val overview = GetPackageOverview(DetectKoboDevice(), InspectVolume(InMemoryReceiptStore()), FetchReleases(catalog))(volume)

        val ready = assertIs<OverviewResult.Ready>(overview)
        val koreader = ready.get(PackageId.KOREADER)
        val status = assertIs<PackageStatus.Installed>(koreader.status)
        assertEquals("v2026.03", status.version)
        assertTrue(status.updateAvailable)
        assertEquals(listOf(PackageId.KFMON), koreader.dependenciesToInstall.map { it.id })
        assertEquals(PackageStatus.NotInstalled, ready.get(PackageId.PLATO).status)
    }
}
