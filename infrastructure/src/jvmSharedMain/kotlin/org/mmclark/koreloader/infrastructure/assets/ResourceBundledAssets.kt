package org.mmclark.koreloader.infrastructure.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Source
import okio.source
import org.mmclark.koreloader.application.port.BundledAssets
import java.io.InputStream

/** [BundledAssets] served from Java resources (see `infrastructure/BUNDLED_ASSETS.md`). */
class ResourceBundledAssets : BundledAssets {

    private val sizes = HashMap<String, Long>()

    override fun size(asset: String): Long = synchronized(sizes) {
        sizes.getOrPut(asset) { stream(asset).use { it.readBytes().size.toLong() } } // small files
    }

    override suspend fun <T> open(asset: String, block: suspend (Source) -> T): T {
        val stream = withContext(Dispatchers.IO) { stream(asset) }
        return stream.use { block(it.source()) }
    }

    private fun stream(asset: String): InputStream {
        require(asset.isNotEmpty() && ".." !in asset && !asset.startsWith("/")) { "Bad asset name: $asset" }
        return ResourceBundledAssets::class.java.getResourceAsStream("$ROOT/$asset")
            ?: throw IllegalStateException("Missing bundled asset $asset")
    }

    private companion object {
        const val ROOT = "/org/mmclark/koreloader/assets"
    }
}
