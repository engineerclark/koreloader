package org.mmclark.koreloader.application.port

import okio.Source
import org.mmclark.koreloader.domain.install.ArchiveEntry

/** Port: random access to the entries of a local archive (zip). */
interface ArchiveReader {
    /**
     * Opens [file], runs [block], and closes the archive.
     *
     * @throws ArchiveException if the file isn't a readable archive.
     */
    suspend fun <T> open(file: LocalFile, block: suspend (Archive) -> T): T
}

interface Archive {
    /** All entries, in archive order. */
    val entries: List<ArchiveEntry>

    /** Streams the contents of the entry named [entryName]; the [Source] is valid only inside [block]. */
    suspend fun <T> read(entryName: String, block: suspend (Source) -> T): T
}

class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)
