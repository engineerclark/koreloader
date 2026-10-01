package org.mmclark.koreloader.infrastructure.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.PackageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubReleasesCatalogTest {

    /** Trimmed from the live API (koreader/koreader, 2026-09-30). */
    private val releasesJson = """
        [
          {"tag_name": "ota", "draft": false, "prerelease": true, "published_at": "2026-09-29T03:03:22Z",
           "assets": [{"name": "koreader-kobo-latest-nightly.zsync", "size": 1, "browser_download_url": "https://github.com/x"}]},
          {"tag_name": "v2026.07.1", "draft": false, "prerelease": false, "published_at": "2026-08-01T11:10:30Z",
           "assets": [
             {"name": "koreader-kobo-v2026.07.1.zip", "size": 42370936,
              "browser_download_url": "https://github.com/koreader/koreader/releases/download/v2026.07.1/koreader-kobo-v2026.07.1.zip",
              "digest": "sha256:0f36a62ce73b12516f969e4ad7862cc06920afb03c7bd4db29a0d990bdd84b2f"},
             {"name": "koreader-kobov5-v2026.07.1.zip", "size": 42349662, "browser_download_url": "https://github.com/y", "digest": null}
           ]},
          {"tag_name": "v2024.11", "draft": false, "prerelease": false, "published_at": "2024-11-20T19:52:06Z",
           "assets": [{"name": "koreader-kobo-v2024.11.zip", "size": 40000000,
              "browser_download_url": "https://github.com/koreader/koreader/releases/download/v2024.11/koreader-kobo-v2024.11.zip"}]},
          {"tag_name": "v2014.01", "draft": false, "prerelease": false, "published_at": "2014-01-01T00:00:00Z", "assets": []}
        ]
    """.trimIndent()

    private fun catalog(status: HttpStatusCode = HttpStatusCode.OK, body: String = releasesJson) =
        GitHubReleasesCatalog(HttpClient(MockEngine { respond(body, status) }))

    @Test
    fun keepsStableReleasesWithAKoboAsset() = runTest {
        val releases = catalog().releases(PackageId.KOREADER, "koreader/koreader") { "koreader-kobo-$it.zip" }
        assertEquals(listOf("v2026.07.1", "v2024.11"), releases.map { it.version })
        val latest = releases.first()
        assertEquals("2026-08-01", latest.publishedOn)
        assertEquals("koreader-kobo-v2026.07.1.zip", latest.artifact.fileName)
        assertEquals(HashAlgorithm.SHA256, latest.artifact.checksums.single().algorithm)
        assertTrue(latest.isVerifiable)
        assertFalse(releases[1].isVerifiable)
    }

    @Test
    fun rateLimitIsExplained() = runTest {
        val e = assertFailsWith<IllegalStateException> {
            catalog(HttpStatusCode.Forbidden, "{}").releases(PackageId.KOREADER, "koreader/koreader") { it }
        }
        assertTrue("limit" in e.message!!)
    }
}
