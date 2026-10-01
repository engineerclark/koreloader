package org.mmclark.koreloader.domain.volume

/**
 * A path on the Kobo's USB volume, relative to the volume root, using `/` separators.
 *
 * Always normalised and safe: never absolute, never contains `.`/`..` segments or empty
 * segments, so it cannot escape the volume root. [ROOT] is the volume root itself.
 */
class KoboPath private constructor(val segments: List<String>) {

    val isRoot: Boolean get() = segments.isEmpty()

    /** Last segment; the empty string for [ROOT]. */
    val name: String get() = segments.lastOrNull() ?: ""

    /** Parent directory, or `null` for [ROOT]. */
    val parent: KoboPath? get() = if (isRoot) null else KoboPath(segments.dropLast(1))

    /** Appends a relative path (which may itself contain `/`). */
    fun resolve(relative: String): KoboPath = KoboPath(segments + parseSegments(relative))

    fun startsWith(other: KoboPath): Boolean =
        segments.size >= other.segments.size && segments.subList(0, other.segments.size) == other.segments

    override fun equals(other: Any?): Boolean = other is KoboPath && other.segments == segments
    override fun hashCode(): Int = segments.hashCode()
    override fun toString(): String = segments.joinToString("/")

    companion object {
        val ROOT = KoboPath(emptyList())

        /**
         * Parses [raw] (e.g. a zip entry name). Backslashes are treated as separators and a
         * trailing `/` is ignored.
         *
         * @throws IllegalArgumentException if the path is empty, absolute, or contains `.`/`..`.
         */
        fun of(raw: String): KoboPath = KoboPath(parseSegments(raw))

        fun parseOrNull(raw: String): KoboPath? = try {
            of(raw)
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun parseSegments(raw: String): List<String> {
            val normalised = raw.replace('\\', '/')
            require(normalised.isNotBlank()) { "Path is empty" }
            require(!normalised.startsWith("/")) { "Path is absolute: $raw" }
            require(!DRIVE_LETTER.matches(normalised.substringBefore('/'))) { "Path is absolute: $raw" }
            val segments = normalised.trimEnd('/').split('/')
            for (segment in segments) {
                require(segment.isNotEmpty()) { "Path has an empty segment: $raw" }
                require(segment != "." && segment != "..") { "Path has a relative segment: $raw" }
                require(segment.none { it.code < 0x20 }) { "Path has a control character: $raw" }
            }
            return segments
        }

        private val DRIVE_LETTER = Regex("^[A-Za-z]:$")
    }
}
