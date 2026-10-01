package org.mmclark.koreloader.application.packages

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.fake.FakeCatalog
import org.mmclark.koreloader.application.fake.InMemoryKoboVolume
import org.mmclark.koreloader.application.fake.InMemoryReceiptStore
import org.mmclark.koreloader.application.fake.kfmonRelease
import org.mmclark.koreloader.application.fake.koboVolume
import org.mmclark.koreloader.application.fake.release
import org.mmclark.koreloader.application.port.OverviewSnapshot
import org.mmclark.koreloader.application.port.OverviewSnapshotStore
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.packages.PackageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservePackageOverviewTest {

    private class MemoryStore : OverviewSnapshotStore {
        val snapshots = HashMap<String, OverviewSnapshot>()
        override suspend fun load(key: String) = snapshots[key]
        override suspend fun save(key: String, snapshot: OverviewSnapshot) {
            snapshots[key] = snapshot
        }
    }

    private val ref = VolumeRef("content://kobo")
    private val koreaderReleases = listOf(release(PackageId.KOREADER, "v2026.07.1"), release(PackageId.KOREADER, "v2026.03"))
    private val catalog = FakeCatalog(mapOf(PackageId.KFMON to listOf(kfmonRelease), PackageId.KOREADER to koreaderReleases))
    private val store = MemoryStore()
    private val observe = ObservePackageOverview(
        GetPackageOverview(DetectKoboDevice(), InspectVolume(InMemoryReceiptStore()), FetchReleases(catalog)),
        store,
    )

    private fun koreaderStatus(update: OverviewUpdate) =
        assertIs<OverviewResult.Ready>(update.result).get(PackageId.KOREADER).status

    @Test
    fun firstLoadHasNoCacheThenSavesOne() = runTest {
        val updates = observe(ref, koboVolume()).toList()
        assertEquals(1, updates.size)
        assertFalse(updates.single().fromCache)
        assertTrue(ref.value in store.snapshots)
    }

    @Test
    fun cachedOverviewComesFirstThenTheFreshOne() = runTest {
        val volume = koboVolume()
        observe(ref, volume).toList() // primes the cache: nothing installed
        volume.putFile(".adds/koreader/koreader.sh", "#!/bin/sh")
        volume.putFile(".adds/koreader/git-rev", "v2026.03")

        val updates = observe(ref, volume).toList()
        assertEquals(listOf(true, false), updates.map { it.fromCache })
        assertEquals(PackageStatus.NotInstalled, koreaderStatus(updates[0]))
        val fresh = assertIs<PackageStatus.Installed>(koreaderStatus(updates[1]))
        assertTrue(fresh.updateAvailable) // statuses are re-derived from the cached releases too
    }

    @Test
    fun unpluggedKoboKeepsTheCachedOverviewWithAProblem() = runTest {
        observe(ref, koboVolume()).toList()
        val updates = observe(ref, InMemoryKoboVolume()).toList() // no .kobo: not a Kobo / unplugged
        assertEquals(2, updates.size)
        assertTrue(updates.all { it.fromCache })
        assertEquals("The Kobo isn't connected.", updates.last().refreshProblem)
        assertIs<OverviewResult.Ready>(updates.last().result)
    }

    @Test
    fun offlineRefreshKeepsCachedReleases() = runTest {
        val volume = koboVolume()
        observe(ref, volume).toList()
        catalog.failWith = "offline"

        val fresh = observe(ref, volume).toList().last()
        assertFalse(fresh.fromCache)
        assertNull(fresh.refreshProblem)
        val koreader = assertIs<OverviewResult.Ready>(fresh.result).get(PackageId.KOREADER)
        assertEquals(koreaderReleases, assertIs<ReleasesLookup.Found>(koreader.releases).releases)
    }
}
