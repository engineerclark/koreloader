package org.mmclark.koreloader.infrastructure.hash

import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FileChecksumCalculatorTest {

    @Test
    fun computesAllDigestsInOnePass() = runTest {
        val file = File.createTempFile("checksum", ".bin").apply {
            deleteOnExit()
            writeBytes(ByteArray(1000) { (it % 251).toByte() })
        }
        val digests = FileChecksumCalculator().compute(LocalFile(file.path, file.length()), HashAlgorithm.entries.toSet())
        // Expected values from Python's hashlib.
        assertEquals("a24f1e3ef66950e1327f210e3997ba2c", digests[HashAlgorithm.MD5])
        assertEquals("4e4c294b331f7a2099a379bec34b9f9fc03dc46ab465d998f4d683da53487e6d", digests[HashAlgorithm.SHA256])
        assertEquals(
            "c11e1c0340bd7e5a1b275f1230c962fad215ecb1391486e74e31b960a2f2996381a5fad092da06841d5f26e38f6ecfeaf441acbcd1c2de61aef121e7927175f5",
            digests[HashAlgorithm.BLAKE2B_512],
        )
    }
}
