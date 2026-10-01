package org.mmclark.koreloader.domain.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoboDeviceTest {

    private val claraBw = "N4181C1234567,4.1.15,4.38.21908,4.1.15,4.1.15,00000000-0000-0000-0000-000000000391\n"

    @Test
    fun parsesVersionFile() {
        val device = KoboDevice.fromVersionFile(claraBw)!!
        assertEquals("N4181C1234567", device.serial)
        assertEquals(FirmwareVersion.of(4, 38, 21908), device.firmware)
        assertEquals(391, device.model.id)
        assertEquals("Kobo Clara BW", device.model.name)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(KoboDevice.fromVersionFile(""))
        assertNull(KoboDevice.fromVersionFile("hello"))
        assertNull(KoboDevice.fromVersionFile("a,b,not.a.version,d,e,00000000-0000-0000-0000-000000000391"))
    }

    @Test
    fun firmwareComparesNumerically() {
        assertTrue(FirmwareVersion.parseOrNull("4.10")!! > FirmwareVersion.parseOrNull("4.9.99")!!)
        assertEquals(FirmwareVersion.parseOrNull("4.6"), FirmwareVersion.parseOrNull("4.6.0"))
        assertNull(FirmwareVersion.parseOrNull("4.x"))
    }

    @Test
    fun policySupportsFirmware4() {
        assertEquals(DeviceCompatibility.Supported, DeviceCompatibilityPolicy.evaluate(KoboDevice.fromVersionFile(claraBw)!!))
    }

    @Test
    fun policyBlocksFirmware5() {
        val device = KoboDevice("S", FirmwareVersion.of(5, 1, 0), DeviceModel.fromId(393))
        val result = assertIs<DeviceCompatibility.Unsupported>(DeviceCompatibilityPolicy.evaluate(device))
        assertIs<CompatibilityIssue.FirmwareTooNew>(result.reasons.single())
    }

    @Test
    fun policyBlocksOldFirmware() {
        val device = KoboDevice("S", FirmwareVersion.of(4, 5, 9), DeviceModel.fromId(376))
        assertIs<DeviceCompatibility.Unsupported>(DeviceCompatibilityPolicy.evaluate(device))
    }

    @Test
    fun policyWarnsOnUnknownModel() {
        val device = KoboDevice("S", FirmwareVersion.of(4, 41), DeviceModel.fromId(999))
        val result = assertIs<DeviceCompatibility.SupportedWithWarnings>(DeviceCompatibilityPolicy.evaluate(device))
        assertIs<CompatibilityIssue.UnknownModel>(result.warnings.single())
    }
}
