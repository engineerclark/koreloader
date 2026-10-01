package org.mmclark.koreloader.application.storagecheck

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.fake.InMemoryKoboVolume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunStorageCheckTest {

    @Test
    fun allStepsPassAndCleanUp() = runTest {
        val volume = InMemoryKoboVolume().apply { putFile(".kobo/version", "v") }
        val steps = RunStorageCheck()(volume).toList()
        assertEquals(9, steps.size)
        steps.forEach { assertIs<StorageCheckStep.Outcome.Passed>(it.outcome, it.name) }
        assertTrue(volume.allPaths.none { it.startsWith(".adds/koreloader-test") }, "${volume.allPaths}")
    }

    @Test
    fun stopsAtFirstFailureAndStillCleansUp() = runTest {
        val volume = InMemoryKoboVolume().apply { failOn = { op, _ -> op == "rename" } }
        val steps = RunStorageCheck()(volume).toList()
        val failed = assertIs<StorageCheckStep.Outcome.Failed>(steps.last().outcome)
        assertTrue("rename" in failed.message)
        assertEquals(7, steps.size)
        assertTrue(volume.allPaths.none { it.startsWith(".adds/koreloader-test") }, "${volume.allPaths}")
    }
}
