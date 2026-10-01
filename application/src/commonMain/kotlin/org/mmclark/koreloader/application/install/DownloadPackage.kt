package org.mmclark.koreloader.application.install

import kotlinx.coroutines.CancellationException
import org.mmclark.koreloader.application.port.ChecksumCalculator
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.application.port.PackageDownloadException
import org.mmclark.koreloader.application.port.PackageDownloader
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.ArtifactVerification

sealed interface DownloadResult {
    data class Ready(val file: LocalFile) : DownloadResult
    data class Failed(val error: InstallError) : DownloadResult
}

/**
 * Downloads an artifact to the local cache and verifies its size and checksums.
 * A cached copy that fails verification is discarded and downloaded once more.
 */
class DownloadPackage(
    private val downloader: PackageDownloader,
    private val checksums: ChecksumCalculator,
) {
    suspend operator fun invoke(
        artifact: Artifact,
        onProgress: suspend (Long, Long?) -> Unit = { _, _ -> },
        onVerifying: suspend () -> Unit = {},
    ): DownloadResult {
        var verification: ArtifactVerification = ArtifactVerification.Verified
        repeat(MAX_ATTEMPTS) {
            val file = try {
                downloader.fetch(artifact, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PackageDownloadException) {
                return DownloadResult.Failed(InstallError.DownloadFailed(e.message ?: "download failed"))
            }
            onVerifying()
            val digests = checksums.compute(file, artifact.requiredAlgorithms)
            verification = artifact.verify(file.sizeBytes, digests)
            if (verification == ArtifactVerification.Verified) return DownloadResult.Ready(file)
            downloader.discard(file)
        }
        return DownloadResult.Failed(InstallError.VerificationFailed(verification))
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
