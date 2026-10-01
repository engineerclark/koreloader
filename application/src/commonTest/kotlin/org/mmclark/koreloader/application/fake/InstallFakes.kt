package org.mmclark.koreloader.application.fake

import okio.Buffer
import okio.Source
import org.mmclark.koreloader.application.port.Archive
import org.mmclark.koreloader.application.port.ArchiveException
import org.mmclark.koreloader.application.port.ArchiveReader
import org.mmclark.koreloader.application.port.ChecksumCalculator
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.application.port.PackageDownloadException
import org.mmclark.koreloader.application.port.PackageDownloader
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.InstallTarget
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.install.ArchiveEntry

const val GOOD_BLAKE = "ab"

val testArtifact = Artifact(
    fileName = "OCP-KOReader-v1.zip",
    url = "https://example.org/OCP-KOReader-v1.zip",
    sizeBytes = 1000,
    checksums = listOf(Checksum.of(HashAlgorithm.BLAKE2B_512, GOOD_BLAKE.repeat(64))),
)

val testRelease = PackageRelease(PackageId.KOREADER, "v1", testArtifact, InstallTarget.VOLUME_ROOT)

/** Hands out cached files; each fetch returns the next digest from [digests] (via [FakeChecksums]). */
class FakeDownloader(var failWith: String? = null) : PackageDownloader {
    var fetches = 0
    var discards = 0

    override suspend fun fetch(artifact: Artifact, onProgress: suspend (Long, Long?) -> Unit): LocalFile {
        failWith?.let { throw PackageDownloadException(it) }
        fetches++
        onProgress(artifact.sizeBytes / 2, artifact.sizeBytes)
        onProgress(artifact.sizeBytes, artifact.sizeBytes)
        return LocalFile("/cache/${artifact.fileName}#$fetches", artifact.sizeBytes)
    }

    override suspend fun discard(file: LocalFile) {
        discards++
    }
}

/** Returns BLAKE2b digests in sequence (last one repeats). */
class FakeChecksums(private vararg val blakes: String = arrayOf(GOOD_BLAKE)) : ChecksumCalculator {
    private var calls = 0
    override suspend fun compute(file: LocalFile, algorithms: Set<HashAlgorithm>): Map<HashAlgorithm, String> {
        val blake = blakes[minOf(calls++, blakes.lastIndex)]
        return mapOf(HashAlgorithm.BLAKE2B_512 to blake.repeat(64))
    }
}

/** An archive held in memory: entry name -> contents (null for directories). */
class FakeArchiveReader(private val entries: List<Pair<String, String?>>) : ArchiveReader {
    var failOnRead: String? = null

    override suspend fun <T> open(file: LocalFile, block: suspend (Archive) -> T): T = block(object : Archive {
        override val entries: List<ArchiveEntry> = this@FakeArchiveReader.entries.map { (name, data) ->
            ArchiveEntry(name, isDirectory = data == null, size = data?.length?.toLong() ?: 0)
        }

        override suspend fun <T> read(entryName: String, block: suspend (Source) -> T): T {
            if (entryName == failOnRead) throw ArchiveException("corrupt entry $entryName")
            val data = this@FakeArchiveReader.entries.first { it.first == entryName }.second
                ?: throw ArchiveException("$entryName is a directory")
            return block(Buffer().writeUtf8(data))
        }
    })
}

fun ocpArchive(vararg extra: Pair<String, String?>) = FakeArchiveReader(
    listOf(
        ".adds/" to null,
        ".kobo/" to null,
        "koreader.png" to "png-ko",
        "kfmon.png" to "png-kfmon",
        ".adds/kfmon/" to null,
        ".adds/kfmon/config/koreader.ini" to "[watch]",
        ".adds/nm/koreader" to "menu_item:main:KOReader",
        ".adds/koreader/koreader.sh" to "#!/bin/sh",
        ".kobo/KoboRoot.tgz" to "NEW-KOBOROOT",
    ) + extra,
)

fun koboVolume(firmware: String = "4.38.21908", conf: String? = "[General]\nfoo=bar\n") = InMemoryKoboVolume().apply {
    putFile(".kobo/version", "N1,4.1,$firmware,4.1,4.1,00000000-0000-0000-0000-000000000391")
    if (conf != null) putFile(".kobo/Kobo/Kobo eReader.conf", conf)
}
