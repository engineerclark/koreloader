package org.mmclark.koreloader.infrastructure.download

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.application.port.PackageDownloadException
import org.mmclark.koreloader.application.port.PackageDownloader
import org.mmclark.koreloader.domain.catalog.Artifact
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads artifacts into [directory] (the app's cache), keeping the [keep] most recent ones
 * (an install can need several: e.g. KFMon and KOReader).
 * A file is written as `<name>.part` and renamed when complete, so a cached file is always
 * a finished download (which the caller still verifies).
 */
class CachingFileDownloader(
    private val client: HttpClient,
    private val directory: File,
    private val keep: Int = 3,
) : PackageDownloader {

    override suspend fun fetch(artifact: Artifact, onProgress: suspend (Long, Long?) -> Unit): LocalFile =
        withContext(Dispatchers.IO) {
            try {
                download(artifact, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PackageDownloadException) {
                throw e
            } catch (e: IOException) {
                throw PackageDownloadException(e.message ?: "network error", e)
            }
        }

    override suspend fun discard(file: LocalFile) {
        withContext(Dispatchers.IO) { File(file.path).delete() }
    }

    private suspend fun download(artifact: Artifact, onProgress: suspend (Long, Long?) -> Unit): LocalFile {
        if (!directory.isDirectory && !directory.mkdirs()) throw PackageDownloadException("Can't create $directory")
        val target = File(directory, artifact.fileName)
        if (target.isFile && target.length() == artifact.sizeBytes) {
            target.setLastModified(System.currentTimeMillis())
            onProgress(target.length(), artifact.sizeBytes)
            return LocalFile(target.path, target.length())
        }
        prune()

        val part = File(directory, artifact.fileName + ".part")
        client.prepareGet(artifact.url).execute { response ->
            if (!response.status.isSuccess()) {
                throw PackageDownloadException("Download failed (HTTP ${response.status.value})")
            }
            val total = response.contentLength() ?: artifact.sizeBytes
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            var done = 0L
            var reported = 0L
            FileOutputStream(part).use { out ->
                while (true) {
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n == -1) break
                    out.write(buffer, 0, n)
                    done += n
                    if (done - reported >= PROGRESS_STEP) {
                        onProgress(done, total)
                        reported = done
                    }
                }
            }
            onProgress(done, total)
        }
        if (!part.renameTo(target)) throw PackageDownloadException("Can't finalise download")
        return LocalFile(target.path, target.length())
    }

    /** Removes partial downloads and all but the newest `keep - 1` files, making room for one more. */
    private fun prune() {
        val files = directory.listFiles()?.toList().orEmpty()
        files.filter { it.name.endsWith(".part") }.forEach { it.delete() }
        files.filter { it.isFile && !it.name.endsWith(".part") }
            .sortedByDescending { it.lastModified() }
            .drop(keep - 1)
            .forEach { it.delete() }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_STEP = 256 * 1024L
    }
}
