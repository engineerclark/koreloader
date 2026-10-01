package org.mmclark.koreloader.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ArtifactTest {

    private val md5 = Checksum.of(HashAlgorithm.MD5, "1903711C38B8A0E74ED3D7B96297BD80")
    private val blake = Checksum.of(HashAlgorithm.BLAKE2B_512, "ab".repeat(64))
    private val artifact = Artifact("OCP-KFMon.zip", "https://example.org/OCP-KFMon.zip", 894583, listOf(md5, blake))

    @Test
    fun checksumIsNormalisedAndValidated() {
        assertEquals("1903711c38b8a0e74ed3d7b96297bd80", md5.hex)
        assertFailsWith<IllegalArgumentException> { Checksum.of(HashAlgorithm.MD5, "abc") }
        assertFailsWith<IllegalArgumentException> { Checksum.of(HashAlgorithm.SHA256, "z".repeat(64)) }
    }

    @Test
    fun artifactRequiresAStrongChecksumHttpsAndASafeName() {
        assertFailsWith<IllegalArgumentException> { artifact.copy(checksums = listOf(md5)) }
        assertFailsWith<IllegalArgumentException> { artifact.copy(url = "http://example.org/x.zip") }
        assertFailsWith<IllegalArgumentException> { artifact.copy(fileName = "../x.zip") }
        assertFailsWith<IllegalArgumentException> { artifact.copy(sizeBytes = 0) }
    }

    @Test
    fun verification() {
        val good = mapOf(HashAlgorithm.MD5 to md5.hex.uppercase(), HashAlgorithm.BLAKE2B_512 to blake.hex)
        assertEquals(ArtifactVerification.Verified, artifact.verify(894583, good))
        assertIs<ArtifactVerification.SizeMismatch>(artifact.verify(1, good))
        assertIs<ArtifactVerification.ChecksumMismatch>(artifact.verify(894583, good + (HashAlgorithm.BLAKE2B_512 to "cd".repeat(64))))
        assertEquals(
            ArtifactVerification.DigestMissing(HashAlgorithm.BLAKE2B_512),
            artifact.verify(894583, mapOf(HashAlgorithm.MD5 to md5.hex)),
        )
        assertEquals(setOf(HashAlgorithm.MD5, HashAlgorithm.BLAKE2B_512), artifact.requiredAlgorithms)
    }
}
