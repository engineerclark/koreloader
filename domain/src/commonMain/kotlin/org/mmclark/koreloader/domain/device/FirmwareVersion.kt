package org.mmclark.koreloader.domain.device

/** A Kobo firmware version such as `4.38.21908`. Compared numerically, part by part. */
class FirmwareVersion private constructor(val parts: List<Int>) : Comparable<FirmwareVersion> {

    val major: Int get() = parts[0]
    val minor: Int get() = parts.getOrElse(1) { 0 }

    override fun compareTo(other: FirmwareVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val cmp = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is FirmwareVersion && compareTo(other) == 0
    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()
    override fun toString(): String = parts.joinToString(".")

    companion object {
        fun parseOrNull(raw: String): FirmwareVersion? {
            val parts = raw.trim().split('.').map { it.toIntOrNull() ?: return null }
            if (parts.isEmpty() || parts.any { it < 0 }) return null
            return FirmwareVersion(parts)
        }

        fun of(vararg parts: Int): FirmwareVersion {
            require(parts.isNotEmpty() && parts.all { it >= 0 })
            return FirmwareVersion(parts.toList())
        }
    }
}
