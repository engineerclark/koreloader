package org.mmclark.koreloader.application.packages

import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.fake.InMemoryReceiptStore
import org.mmclark.koreloader.application.fake.koboVolume
import org.mmclark.koreloader.application.fake.koboVolumeWithKfmon
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.packages.InstalledState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InspectVolumeTest {

    @Test
    fun detectsPackagesAndVersions() = runTest {
        val receipts = InMemoryReceiptStore()
        val volume = koboVolumeWithKfmon(receipts).apply {
            putFile(".adds/koreader/koreader.sh", "#!/bin/sh")
            putFile(".adds/koreader/git-rev", "v2026.03\n")
            putFile(".adds/plato/plato.sh", "#!/bin/sh")
        }
        val inventory = assertIs<InventoryResult.Ready>(InspectVolume(receipts)(volume)).inventory
        assertEquals("1.4.5", (inventory.installed.getValue(PackageId.KFMON) as InstalledState.Installed).version)
        assertEquals("v2026.03", (inventory.installed.getValue(PackageId.KOREADER) as InstalledState.Installed).version)
        assertEquals(InstalledState.Installed(null, null), inventory.installed.getValue(PackageId.PLATO))
        assertFalse(inventory.koboUpdatePending)
    }

    @Test
    fun emptyKoboAndPendingUpdate() = runTest {
        val volume = koboVolume().apply { putFile(".kobo/KoboRoot.tgz", "x") }
        val inventory = assertIs<InventoryResult.Ready>(InspectVolume(InMemoryReceiptStore())(volume)).inventory
        assertTrue(inventory.installed.values.all { it == InstalledState.NotInstalled })
        assertTrue(inventory.koboUpdatePending)
    }
}
