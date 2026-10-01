package org.mmclark.koreloader.infrastructure.catalog

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease

/**
 * Lists releases of a GitHub repository that carry a Kobo build. GitHub publishes a SHA-256
 * `digest` per asset only for uploads since mid-2025; older releases come without one.
 */
class GitHubReleasesCatalog(
    private val client: HttpClient,
    private val apiBase: String = "https://api.github.com",
) {
    /**
     * @param repo e.g. "koreader/koreader".
     * @param assetName the Kobo asset's name for a tag, e.g. `{ "koreader-kobo-$it.zip" }`.
     */
    suspend fun releases(id: PackageId, repo: String, assetName: (tag: String) -> String): List<PackageRelease> {
        val response = client.get("$apiBase/repos/$repo/releases?per_page=100") {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        when {
            response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.TooManyRequests ->
                throw IllegalStateException("GitHub's request limit was reached; try again in a while")
            !response.status.isSuccess() -> throw IllegalStateException("GitHub releases unavailable (HTTP ${response.status.value})")
        }
        return json.decodeFromString<List<GitHubReleaseDto>>(response.bodyAsText())
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { release ->
                val asset = release.assets.firstOrNull { it.name == assetName(release.tagName) } ?: return@mapNotNull null
                PackageRelease(
                    packageId = id,
                    version = release.tagName,
                    publishedOn = release.publishedAt?.take(10),
                    artifact = Artifact(
                        fileName = asset.name,
                        url = asset.browserDownloadUrl,
                        sizeBytes = asset.size,
                        checksums = listOfNotNull(asset.digest?.toChecksum()),
                    ),
                )
            }
    }

    private fun String.toChecksum(): Checksum? =
        if (startsWith("sha256:")) runCatching { Checksum.of(HashAlgorithm.SHA256, removePrefix("sha256:")) }.getOrNull() else null

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal class GitHubAssetDto(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val digest: String? = null,
)
