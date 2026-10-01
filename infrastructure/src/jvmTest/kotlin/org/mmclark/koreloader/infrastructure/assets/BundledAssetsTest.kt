package org.mmclark.koreloader.infrastructure.assets

import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.mmclark.koreloader.domain.packages.BundledFile
import org.mmclark.koreloader.domain.packages.Packages
import org.mmclark.koreloader.domain.packages.Removal
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class BundledAssetsTest {

    /** Provenance: infrastructure/BUNDLED_ASSETS.md. */
    private val pinned = mapOf(
        "kfmon-uninstaller/KoboRoot.tgz" to "8110c12f894504cd1583d98cd1bab959f2e114c138d3f0ea051dc7403dcd4387",
        "kfmon/koreader.ini" to "7dc8dbf871812c61bed22ddac0e453fb11da17ced861339a7e9635ea97bd9c3f",
        "kfmon/koreader.png" to "abbb3b2545019d8dda34456d2c21294f35d6dc9a398cf08607a5161aa521d104",
        "kfmon/plato.ini" to "d74924383cb8fad0e372f2880b8a8d55beeb852b12746bbc2247a965fc037044",
        "kfmon/plato.png" to "dc2eecd1d51d37293ab70b1690095fdeba4b1748baf84830bbb92ac7d1a91c32",
        "nm/kfmon" to "6fb7440e0f7e93e9a2fc8fb29e7a537138a98870d675ddfbb1cf0d41c6d54c5e",
        "nm/koreader" to "a463572d71f4a60f76b30ce4117f0ac5be1208cea5199727f54a00fdfabdc04a",
        "nm/plato" to "22b5c61bae3c2f57a85e4b3aeb677ff128f4220d1b34729fdeb9ec0ffb5b4dd8",
    )

    @Test
    fun bundledAssetsMatchPinnedHashes() = runTest {
        val assets = ResourceBundledAssets()
        for ((asset, sha256) in pinned) {
            val bytes = assets.open(asset) { Buffer().apply { writeAll(it) }.readByteArray() }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(sha256, actual, asset)
            assertEquals(bytes.size.toLong(), assets.size(asset))
        }
    }

    @Test
    fun everyAssetReferencedByAPackageIsBundledAndPinned() {
        val referenced = Packages.all.flatMap { it.launcherFiles.map(BundledFile::asset) } +
            Packages.all.mapNotNull { (it.removal as? Removal.KoboRootUninstaller)?.koboRootAsset }
        assertEquals(pinned.keys, referenced.toSet())
    }
}
