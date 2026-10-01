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
 * Downloads artifacts into [directory] (the app's cache), keeping only the most recent one.
 * A file is written as `<name>.part` and renamed when complete, so a cached file is always
 * a finished download (which the caller still verifies).
 */
class CachingFileDownloader(
    private val client: HttpClient,
    private val directory: File,
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
            onProgress(target.length(), artifact.sizeBytes)
            return LocalFile(target.path, target.length())
        }
        // Only one package is cached at a time; they're tens of MB each.
        directory.listFiles()?.forEach { it.delete() }

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

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_STEP = 256 * 1024L
    }
}
