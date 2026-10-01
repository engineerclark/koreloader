package org.mmclark.koreloader.domain.device

/**
 * A Kobo hardware model, identified by the numeric device ID at the end of `.kobo/version`
 * (`00000000-0000-0000-0000-000000000NNN`).
 *
 * Names and IDs come from pgaskin/koboutils `kobo/device.go`.
 */
data class DeviceModel(val id: Int, val name: String?) {

    val isKnown: Boolean get() = name != null

    val displayName: String get() = name ?: "Unknown Kobo (ID $id)"

    companion object {
        fun fromId(id: Int): DeviceModel = DeviceModel(id, KNOWN_MODELS[id])

        private val KNOWN_MODELS: Map<Int, String> = mapOf(
            310 to "Kobo Touch A/B",
            320 to "Kobo Touch C",
            330 to "Kobo Glo",
            340 to "Kobo Mini",
            350 to "Kobo Aura HD",
            360 to "Kobo Aura",
            370 to "Kobo Aura H2O",
            371 to "Kobo Glo HD",
            372 to "Kobo Touch 2.0",
            373 to "Kobo Aura ONE",
            374 to "Kobo Aura H2O Edition 2 v1",
            375 to "Kobo Aura Edition 2 v1",
            376 to "Kobo Clara HD",
            377 to "Kobo Forma",
            378 to "Kobo Aura H2O Edition 2 v2",
            379 to "Kobo Aura Edition 2 v2",
            380 to "Kobo Forma 32GB",
            381 to "Kobo Aura ONE Limited Edition",
            382 to "Kobo Nia",
            383 to "Kobo Sage",
            384 to "Kobo Libra H2O",
            386 to "Kobo Clara 2E",
            387 to "Kobo Elipsa",
            388 to "Kobo Libra 2",
            389 to "Kobo Elipsa 2E",
            390 to "Kobo Libra Colour",
            391 to "Kobo Clara BW",
            393 to "Kobo Clara Colour",
            676 to "Tolino Shine 3",
            677 to "Tolino Epos 2",
            690 to "Tolino Vision Colour",
            691 to "Tolino Shine",
            693 to "Tolino Shine Color",
        )
    }
}
