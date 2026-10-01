package org.mmclark.koreloader.domain.catalog

enum class HashAlgorithm(val hexLength: Int, val isStrong: Boolean) {
    MD5(hexLength = 32, isStrong = false),
    SHA256(hexLength = 64, isStrong = true),
    BLAKE2B_512(hexLength = 128, isStrong = true),
}

/** An expected digest of a file, as lower-case hex. */
class Checksum private constructor(val algorithm: HashAlgorithm, val hex: String) {

    fun matches(actualHex: String): Boolean = hex == actualHex.lowercase()

    override fun equals(other: Any?): Boolean = other is Checksum && other.algorithm == algorithm && other.hex == hex
    override fun hashCode(): Int = 31 * algorithm.hashCode() + hex.hashCode()
    override fun toString(): String = "${algorithm.name}:$hex"

    companion object {
        fun of(algorithm: HashAlgorithm, hex: String): Checksum {
            val normalised = hex.trim().lowercase()
            require(normalised.length == algorithm.hexLength && normalised.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Invalid ${algorithm.name} checksum: $hex"
            }
            return Checksum(algorithm, normalised)
        }
    }
}
