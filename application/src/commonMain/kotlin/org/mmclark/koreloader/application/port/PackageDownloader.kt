package org.mmclark.koreloader.application.port

import org.mmclark.koreloader.domain.catalog.Artifact

/** A file in the app's local cache. Only infrastructure interprets [path]. */
data class LocalFile(val path: String, val sizeBytes: Long)

/** Port: downloads artifacts into local storage (not onto the Kobo). */
interface PackageDownloader {

    /**
     * Returns a local copy of [artifact], reusing a cached one when present. Reports progress
     * as (bytes so far, total bytes or null if unknown).
     *
     * @throws PackageDownloadException on network or storage errors.
     */
    suspend fun fetch(artifact: Artifact, onProgress: suspend (Long, Long?) -> Unit): LocalFile

    /** Deletes a cached file, e.g. after it failed verification. */
    suspend fun discard(file: LocalFile)
}

class PackageDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
