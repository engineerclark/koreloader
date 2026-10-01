package org.mmclark.koreloader.domain.device

/** A Kobo e-reader as described by its `.kobo/version` file. */
data class KoboDevice(
    val serial: String,
    val firmware: FirmwareVersion,
    val model: DeviceModel,
) {
    companion object {
        /**
         * Parses the contents of `.kobo/version`: one line of comma-separated fields where
         * `[0]` is the serial, `[2]` the firmware version and the last field the model ID
         * string (same interpretation as calibre's Kobo driver).
         */
        fun fromVersionFile(contents: String): KoboDevice? {
            val fields = contents.trim().lineSequence().firstOrNull()?.split(',')?.map { it.trim() }
                ?: return null
            if (fields.size < 4) return null
            val firmware = FirmwareVersion.parseOrNull(fields[2]) ?: return null
            val modelId = fields.last().substringAfterLast('-').toIntOrNull() ?: return null
            return KoboDevice(serial = fields[0], firmware = firmware, model = DeviceModel.fromId(modelId))
        }
    }
}
