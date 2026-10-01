package org.mmclark.koreloader.domain.device

/** Whether the one-click packages can be installed on a device. */
sealed interface DeviceCompatibility {
    data object Supported : DeviceCompatibility
    data class SupportedWithWarnings(val warnings: List<CompatibilityIssue>) : DeviceCompatibility
    data class Unsupported(val reasons: List<CompatibilityIssue>) : DeviceCompatibility
}

sealed interface CompatibilityIssue {
    /** Firmware 5.x needs KOReader's separate `kobov5` build and a different delivery mechanism. */
    data class FirmwareTooNew(val firmware: FirmwareVersion) : CompatibilityIssue

    /** KFMon needs Nickel 2.9.0+, NickelMenu needs 4.6+. */
    data class FirmwareTooOld(val firmware: FirmwareVersion, val minimum: FirmwareVersion) : CompatibilityIssue

    /** The model ID isn't in our table. KFMon is device-agnostic, so this is only a warning. */
    data class UnknownModel(val model: DeviceModel) : CompatibilityIssue
}

/** Rules deciding whether koreloader may install the one-click packages on a device. */
object DeviceCompatibilityPolicy {

    val MINIMUM_FIRMWARE: FirmwareVersion = FirmwareVersion.of(4, 6)
    const val FIRST_UNSUPPORTED_MAJOR: Int = 5

    fun evaluate(device: KoboDevice): DeviceCompatibility {
        val blocking = buildList {
            if (device.firmware.major >= FIRST_UNSUPPORTED_MAJOR) add(CompatibilityIssue.FirmwareTooNew(device.firmware))
            if (device.firmware < MINIMUM_FIRMWARE) add(CompatibilityIssue.FirmwareTooOld(device.firmware, MINIMUM_FIRMWARE))
        }
        val warnings = buildList {
            if (!device.model.isKnown) add(CompatibilityIssue.UnknownModel(device.model))
        }
        return when {
            blocking.isNotEmpty() -> DeviceCompatibility.Unsupported(blocking + warnings)
            warnings.isNotEmpty() -> DeviceCompatibility.SupportedWithWarnings(warnings)
            else -> DeviceCompatibility.Supported
        }
    }
}
