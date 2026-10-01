package org.mmclark.koreloader.infrastructure.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Source
import okio.source
import org.mmclark.koreloader.application.port.Archive
import org.mmclark.koreloader.application.port.ArchiveException
import org.mmclark.koreloader.application.port.ArchiveReader
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.domain.install.ArchiveEntry
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** [ArchiveReader] for local zip files, streaming each entry without loading the archive. */
class ZipFileArchiveReader : ArchiveReader {

    override suspend fun <T> open(file: LocalFile, block: suspend (Archive) -> T): T {
        val zip = withContext(Dispatchers.IO) {
            try {
                ZipFile(File(file.path))
            } catch (e: IOException) {
                throw ArchiveException("Not a readable zip archive: ${e.message}", e)
            }
        }
        return try {
            block(ZipArchive(zip))
        } finally {
            withContext(Dispatchers.IO) { zip.close() }
        }
    }

    private class ZipArchive(private val zip: ZipFile) : Archive {

        override val entries: List<ArchiveEntry> = zip.entries().asSequence()
            .map { ArchiveEntry(it.name, it.isDirectory, it.size) }
            .toList()

        override suspend fun <T> read(entryName: String, block: suspend (Source) -> T): T {
            val entry = zip.getEntry(entryName) ?: throw ArchiveException("No entry $entryName")
            val stream = try {
                withContext(Dispatchers.IO) { zip.getInputStream(entry) }
            } catch (e: IOException) {
                throw ArchiveException("Can't read $entryName: ${e.message}", e)
            }
            return stream.use { input ->
                try {
                    block(input.source())
                } catch (e: ZipException) {
                    // Raised while the consumer reads (e.g. corrupt deflate data).
                    throw ArchiveException("Corrupt entry $entryName: ${e.message}", e)
                }
            }
        }
    }
}
