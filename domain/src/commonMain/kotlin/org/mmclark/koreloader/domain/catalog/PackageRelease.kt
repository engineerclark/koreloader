package org.mmclark.koreloader.domain.catalog

/** Identifies an installable package independently of its version. */
data class PackageId(val value: String) {
    override fun toString(): String = value

    companion object {
        /** KFMon launcher (with NickelMenu), from NiLuJe's OCP-KFMon one-click package. */
        val KFMON = PackageId("kfmon")

        /** KOReader, from its GitHub releases. */
        val KOREADER = PackageId("koreader")

        /** Plato, from its GitHub releases. */
        val PLATO = PackageId("plato")
    }
}

/**
 * A downloadable file with its expected size and digests. Some upstream releases publish no
 * checksum at all; such an artifact is [isVerifiable] == false and can only be size-checked.
 */
data class Artifact(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val checksums: List<Checksum>,
) {
    init {
        require(fileName.isNotBlank() && fileName != "." && fileName != ".." && '/' !in fileName && '\\' !in fileName) {
            "Unsafe artifact file name: $fileName"
        }
        require(url.startsWith("https://")) { "Artifact must be downloaded over HTTPS: $url" }
        require(sizeBytes > 0) { "Artifact size must be positive" }
    }

    /** Whether a SHA-256 or BLAKE2b digest is known, so the download's integrity can be proven. */
    val isVerifiable: Boolean get() = checksums.any { it.algorithm.isStrong }

    /** Algorithms whose digests must be computed to call [verify]. */
    val requiredAlgorithms: Set<HashAlgorithm> get() = checksums.map { it.algorithm }.toSet()

    /** Checks a downloaded file's size and digests (keyed by algorithm, hex) against the expected ones. */
    fun verify(actualSize: Long, actualDigests: Map<HashAlgorithm, String>): ArtifactVerification {
        if (actualSize != sizeBytes) return ArtifactVerification.SizeMismatch(sizeBytes, actualSize)
        for (checksum in checksums) {
            val actual = actualDigests[checksum.algorithm] ?: return ArtifactVerification.DigestMissing(checksum.algorithm)
            if (!checksum.matches(actual)) return ArtifactVerification.ChecksumMismatch(checksum, actual)
        }
        return ArtifactVerification.Verified
    }
}

sealed interface ArtifactVerification {
    data object Verified : ArtifactVerification
    data class SizeMismatch(val expected: Long, val actual: Long) : ArtifactVerification
    data class ChecksumMismatch(val expected: Checksum, val actualHex: String) : ArtifactVerification
    data class DigestMissing(val algorithm: HashAlgorithm) : ArtifactVerification
}

/** A specific version of a package, ready to download. */
data class PackageRelease(
    val packageId: PackageId,
    val version: String,
    /** Publication date as ISO-8601 (`2026-08-01`), if known. */
    val publishedOn: String?,
    val artifact: Artifact,
) {
    val isVerifiable: Boolean get() = artifact.isVerifiable
}

/** Repository of installable package releases. Implementations may throw on network errors. */
interface PackageCatalog {
    /** All installable releases of [id], newest first (may be empty). */
    suspend fun releases(id: PackageId): List<PackageRelease>
}
