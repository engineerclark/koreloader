package org.mmclark.koreloader.application.fake

import okio.Buffer
import okio.Source
import org.mmclark.koreloader.application.port.Archive
import org.mmclark.koreloader.application.port.ArchiveException
import org.mmclark.koreloader.application.port.ArchiveReader
import org.mmclark.koreloader.application.port.BundledAssets
import org.mmclark.koreloader.application.port.ChecksumCalculator
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.application.port.PackageDownloadException
import org.mmclark.koreloader.application.port.PackageDownloader
import org.mmclark.koreloader.application.port.ReceiptStore
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.PackageCatalog
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.FirmwareVersion
import org.mmclark.koreloader.domain.install.ArchiveEntry
import org.mmclark.koreloader.domain.packages.InstallReceipt

const val GOOD_BLAKE = "ab"

fun release(id: PackageId, version: String, verifiable: Boolean = true) = PackageRelease(
    packageId = id,
    version = version,
    publishedOn = null,
    artifact = Artifact(
        fileName = "$id-$version.zip",
        url = "https://example.org/$id-$version.zip",
        sizeBytes = 1000,
        checksums = if (verifiable) listOf(Checksum.of(HashAlgorithm.BLAKE2B_512, GOOD_BLAKE.repeat(64))) else emptyList(),
    ),
)

val kfmonRelease = release(PackageId.KFMON, "1.4.6")
val koreaderRelease = release(PackageId.KOREADER, "v2026.07.1")

class FakeCatalog(private val releases: Map<PackageId, List<PackageRelease>> = mapOf(PackageId.KFMON to listOf(kfmonRelease))) : PackageCatalog {
    var failWith: String? = null
    override suspend fun releases(id: PackageId): List<PackageRelease> {
        failWith?.let { error(it) }
        return releases[id].orEmpty()
    }
}

/** Hands out "cached files" named after the artifact. */
class FakeDownloader(var failWith: String? = null) : PackageDownloader {
    val fetched = mutableListOf<String>()
    var discards = 0

    override suspend fun fetch(artifact: Artifact, onProgress: suspend (Long, Long?) -> Unit): LocalFile {
        failWith?.let { throw PackageDownloadException(it) }
        fetched += artifact.fileName
        onProgress(artifact.sizeBytes, artifact.sizeBytes)
        return LocalFile("/cache/${artifact.fileName}", artifact.sizeBytes)
    }

    override suspend fun discard(file: LocalFile) {
        discards++
    }
}

/** Returns BLAKE2b digests in sequence (last one repeats). */
class FakeChecksums(private vararg val blakes: String = arrayOf(GOOD_BLAKE)) : ChecksumCalculator {
    private var calls = 0
    override suspend fun compute(file: LocalFile, algorithms: Set<HashAlgorithm>): Map<HashAlgorithm, String> {
        if (algorithms.isEmpty()) return emptyMap()
        val blake = blakes[minOf(calls++, blakes.lastIndex)]
        return mapOf(HashAlgorithm.BLAKE2B_512 to blake.repeat(64))
    }
}

/** Archives held in memory, keyed by the cached file name: entry name -> contents (null for directories). */
class FakeArchiveReader(private val archives: Map<String, List<Pair<String, String?>>>) : ArchiveReader {
    var failOnRead: String? = null

    override suspend fun <T> open(file: LocalFile, block: suspend (Archive) -> T): T {
        val entries = archives[file.path.substringAfterLast('/')] ?: throw ArchiveException("no archive ${file.path}")
        return block(object : Archive {
            override val entries: List<ArchiveEntry> = entries.map { (name, data) ->
                ArchiveEntry(name, isDirectory = data == null, size = data?.length?.toLong() ?: 0)
            }

            override suspend fun <T> read(entryName: String, block: suspend (Source) -> T): T {
                if (entryName == failOnRead) throw ArchiveException("corrupt entry $entryName")
                val data = entries.first { it.first == entryName }.second ?: throw ArchiveException("$entryName is a directory")
                return block(Buffer().writeUtf8(data))
            }
        })
    }
}

class FakeBundledAssets : BundledAssets {
    override fun size(asset: String): Long = contents(asset).length.toLong()
    override suspend fun <T> open(asset: String, block: suspend (Source) -> T): T = block(Buffer().writeUtf8(contents(asset)))
    private fun contents(asset: String) = "bundled:$asset"
}

class InMemoryReceiptStore(var receipts: List<InstallReceipt> = emptyList()) : ReceiptStore {
    override suspend fun load(volume: KoboVolume) = receipts
    override suspend fun save(volume: KoboVolume, receipts: List<InstallReceipt>) {
        this.receipts = receipts
    }
}

val ocpKfmonEntries: List<Pair<String, String?>> = listOf(
    ".adds/" to null,
    ".kobo/" to null,
    ".kobo/KoboRoot.tgz" to "KFMON-KOBOROOT",
    "kfmon.png" to "png-kfmon",
    ".adds/kfmon/" to null,
    ".adds/kfmon/config/kfmon.ini" to "[kfmon]",
)

val koreaderZipEntries: List<Pair<String, String?>> = listOf(
    "koreader.png" to "fmon relic",
    "koreader/" to null,
    "koreader/koreader.sh" to "#!/bin/sh",
    "koreader/git-rev" to "v2026.07.1",
)

fun archivesFor(vararg releases: PackageRelease, entries: Map<PackageId, List<Pair<String, String?>>> = mapOf(
    PackageId.KFMON to ocpKfmonEntries,
    PackageId.KOREADER to koreaderZipEntries,
)) = FakeArchiveReader(releases.associate { it.artifact.fileName to entries.getValue(it.packageId) })

val FW = FirmwareVersion.of(4, 38, 21908)

fun koboVolume(firmware: String = "4.38.21908", conf: String? = "[General]\nfoo=bar\n") = InMemoryKoboVolume().apply {
    putFile(".kobo/version", "N1,4.1,$firmware,4.1,4.1,00000000-0000-0000-0000-000000000391")
    if (conf != null) putFile(".kobo/Kobo/Kobo eReader.conf", conf)
}

/** A volume where KFMon was installed by koreloader on the current firmware. */
fun koboVolumeWithKfmon(receipts: InMemoryReceiptStore, firmware: FirmwareVersion = FW) = koboVolume().apply {
    putFile(".adds/kfmon/config/kfmon.ini", "[kfmon]")
    putFile("kfmon.png", "png-kfmon")
    receipts.receipts = listOf(InstallReceipt(PackageId.KFMON, "1.4.5", firmware))
}
