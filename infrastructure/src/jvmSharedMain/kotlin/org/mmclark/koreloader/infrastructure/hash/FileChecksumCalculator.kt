package org.mmclark.koreloader.infrastructure.hash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.mmclark.koreloader.application.port.ChecksumCalculator
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import java.io.File
import java.security.MessageDigest

/** Computes all requested digests of a local file in a single read. */
class FileChecksumCalculator : ChecksumCalculator {

    override suspend fun compute(file: LocalFile, algorithms: Set<HashAlgorithm>): Map<HashAlgorithm, String> =
        withContext(Dispatchers.IO) {
            val hashers = algorithms.associateWith { hasherFor(it) }
            val buffer = ByteArray(64 * 1024)
            File(file.path).inputStream().use { input ->
                while (true) {
                    ensureActive()
                    val n = input.read(buffer)
                    if (n == -1) break
                    hashers.values.forEach { it.update(buffer, n) }
                }
            }
            hashers.mapValues { it.value.digestHex() }
        }

    private interface Hasher {
        fun update(bytes: ByteArray, length: Int)
        fun digestHex(): String
    }

    private fun hasherFor(algorithm: HashAlgorithm): Hasher = when (algorithm) {
        HashAlgorithm.BLAKE2B_512 -> object : Hasher {
            val blake = Blake2b()
            override fun update(bytes: ByteArray, length: Int) = blake.update(bytes, 0, length)
            override fun digestHex() = blake.digest().toHex()
        }
        HashAlgorithm.MD5, HashAlgorithm.SHA256 -> object : Hasher {
            val digest = MessageDigest.getInstance(if (algorithm == HashAlgorithm.MD5) "MD5" else "SHA-256")
            override fun update(bytes: ByteArray, length: Int) = digest.update(bytes, 0, length)
            override fun digestHex() = digest.digest().toHex()
        }
    }
}
