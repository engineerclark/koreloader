package org.mmclark.koreloader.application.port

import okio.Source
import org.mmclark.koreloader.domain.volume.KoboPath

/**
 * Port: read/write access to a Kobo's USB mass-storage volume.
 *
 * Paths are relative to the volume root. Implementations treat names case-insensitively
 * (the volume is FAT32) and throw [KoboVolumeException] on I/O failure.
 */
interface KoboVolume {

    /** Type of the entry at [path], or `null` if nothing exists there. */
    suspend fun entryType(path: KoboPath): EntryType?

    /** Names of the direct children of directory [dir]. */
    suspend fun list(dir: KoboPath): List<String>

    /** Reads a whole (small) file. */
    suspend fun read(path: KoboPath): ByteArray

    /** Creates [dir] and any missing parents. */
    suspend fun createDirectories(dir: KoboPath)

    /**
     * Writes [source] to [path], replacing any existing file and creating missing parents.
     * With [durable], the data is flushed to the device (fsync) before returning.
     */
    suspend fun write(path: KoboPath, source: Source, durable: Boolean = false)

    /** Appends [bytes] to [path], creating the file if needed. */
    suspend fun append(path: KoboPath, bytes: ByteArray, durable: Boolean = false)

    /** Renames [path] within its directory to [newName], replacing an existing file of that name. */
    suspend fun rename(path: KoboPath, newName: String)

    /** Deletes a file, or a directory and everything in it. Returns `false` if nothing was there. */
    suspend fun delete(path: KoboPath): Boolean
}

enum class EntryType { FILE, DIRECTORY }

suspend fun KoboVolume.exists(path: KoboPath): Boolean = entryType(path) != null

class KoboVolumeException(message: String, cause: Throwable? = null) : Exception(message, cause)
