package org.mmclark.koreloader.application.device

import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.fake.InMemoryKoboVolume
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DetectKoboDeviceTest {

    private val detect = DetectKoboDevice()

    @Test
    fun detectsSupportedKobo() = runTest {
        val volume = InMemoryKoboVolume().apply {
            putFile(".kobo/version", "N123,4.1.15,4.38.21908,4.1.15,4.1.15,00000000-0000-0000-0000-000000000391")
        }
        val result = assertIs<DeviceDetection.Detected>(detect(volume))
        assertEquals("Kobo Clara BW", result.device.model.name)
        assertEquals(DeviceCompatibility.Supported, result.compatibility)
    }

    @Test
    fun notAKoboWithoutKoboFolder() = runTest {
        assertEquals(DeviceDetection.NotAKobo, detect(InMemoryKoboVolume()))
    }

    @Test
    fun versionMissing() = runTest {
        val volume = InMemoryKoboVolume().apply { putFile(".kobo/Kobo/Kobo eReader.conf", "") }
        assertEquals(DeviceDetection.VersionUnreadable(null), detect(volume))
    }

    @Test
    fun versionGarbage() = runTest {
        val volume = InMemoryKoboVolume().apply { putFile(".kobo/version", "garbage") }
        assertEquals(DeviceDetection.VersionUnreadable("garbage"), detect(volume))
    }

    @Test
    fun volumeErrorsAreReported() = runTest {
        val volume = InMemoryKoboVolume().apply {
            putFile(".kobo/version", "x")
            failOn = { op, _ -> op == "read" }
        }
        assertIs<DeviceDetection.VolumeUnavailable>(detect(volume))
    }
}
