package org.mmclark.koreloader.application.port

import okio.Source

/** Port: small files shipped inside koreloader (launcher configs, trigger icons, uninstallers). */
interface BundledAssets {
    /** Size in bytes, or -1 if unknown. */
    fun size(asset: String): Long

    /** Streams [asset]; the [Source] is valid only inside [block]. */
    suspend fun <T> open(asset: String, block: suspend (Source) -> T): T
}
