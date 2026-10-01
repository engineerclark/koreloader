package org.mmclark.koreloader.domain.catalog

/** Identifies an installable package independently of its version. */
data class PackageId(val value: String) {
    companion object {
        /** One-click package: KOReader + KFMon + NickelMenu. */
        val KOREADER = PackageId("koreader")

        /** One-click package: Plato + KFMon + NickelMenu. */
        val PLATO = PackageId("plato")

        /** One-click package: KOReader + Plato + KFMon + NickelMenu. */
        val KOREADER_AND_PLATO = PackageId("koreader+plato")

        /** One-click package: KFMon + NickelMenu only (e.g. after a firmware update). */
        val KFMON = PackageId("kfmon")
    }
}

/** A downloadable file with its expected size and digests. */
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
        require(checksums.any { it.algorithm.isStrong }) { "Artifact needs a SHA-256 or BLAKE2b checksum" }
    }

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

/** Where an archive's entries are rooted on the Kobo volume. */
enum class InstallTarget {
    /** Entries are relative to the volume root (one-click install packages). */
    VOLUME_ROOT,

    /** Entries are relative to `.kobo/` (the KFMon uninstaller). */
    KOBO_DIR,
}

/** A specific version of a package, ready to download. */
data class PackageRelease(
    val packageId: PackageId,
    val version: String,
    val artifact: Artifact,
    val installTarget: InstallTarget,
)

/** Repository of installable package releases. Implementations may throw on network errors. */
interface PackageCatalog {
    suspend fun latestRelease(id: PackageId): PackageRelease?
}
