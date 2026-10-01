package org.mmclark.koreloader.infrastructure.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.port.PackageDownloadException
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CachingFileDownloaderTest {

    private val payload = ByteArray(700_000) { (it % 251).toByte() }
    private val artifact = Artifact(
        "OCP-KOReader-v1.zip", "https://example.org/OCP-KOReader-v1.zip", payload.size.toLong(),
        listOf(Checksum.of(HashAlgorithm.SHA256, "0".repeat(64))),
    )
    private val directory: File = Files.createTempDirectory("downloads").toFile().apply { deleteOnExit() }

    @Test
    fun downloadsReportsProgressAndReusesTheCache() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond(payload, HttpStatusCode.OK) })
        val downloader = CachingFileDownloader(client, directory)
        val progress = mutableListOf<Long>()

        val file = downloader.fetch(artifact) { done, _ -> progress += done }
        assertContentEquals(payload, File(file.path).readBytes())
        assertEquals(payload.size.toLong(), progress.last())
        assertFalse(File(directory, "OCP-KOReader-v1.zip.part").exists())

        downloader.fetch(artifact) { _, _ -> }
        assertEquals(1, requests)

        downloader.discard(file)
        downloader.fetch(artifact) { _, _ -> }
        assertEquals(2, requests)
    }

    @Test
    fun httpErrorIsADownloadFailure() = runTest {
        val downloader = CachingFileDownloader(HttpClient(MockEngine { respond("gone", HttpStatusCode.NotFound) }), directory)
        assertFailsWith<PackageDownloadException> { downloader.fetch(artifact) { _, _ -> } }
    }
}
