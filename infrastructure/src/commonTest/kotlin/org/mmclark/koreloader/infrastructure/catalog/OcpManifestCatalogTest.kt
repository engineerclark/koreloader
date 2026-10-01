package org.mmclark.koreloader.infrastructure.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.PackageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OcpManifestCatalogTest {

    /** Trimmed copy of the live manifest.json (2026-09-30). */
    private val manifest = """
        {
            "Storage_URL": "https://storage.gra.cloud.ovh.net/v1/AUTH_2ac4bfee353948ec8ea7fd1710574097/kfmon-pub",
            "OCP": [
                {
                    "name": "KFMon", "version": "1.4.6-191-gca31869",
                    "filename": "OCP-KFMon-1.4.6-191-gca31869.zip", "path": "OCP-KFMon-1.4.6-191-gca31869.zip",
                    "mimetype": "application/zip", "date": 1780862126, "revision": -1, "size": 894583,
                    "MD5": "1903711c38b8a0e74ed3d7b96297bd80",
                    "BLAKE2B": "ebdadedd680f08e89105384e0cfbcd21d796a027df9c49c4ebbc869d997d8b2c2dd96c221cb425a17a7981e483a97d008aac382271a0e1298b76b836f38089ec"
                },
                {
                    "name": "KOReader", "version": "v2026.03",
                    "filename": "OCP-KOReader-v2026.03.zip", "path": "OCP-KOReader-v2026.03.zip",
                    "mimetype": "application/zip", "date": 1773777101, "revision": -1, "size": 43626840,
                    "MD5": "266551f49d4a62f2e65edafac1c8fd1d",
                    "BLAKE2B": "75c8e867b88479fff07291fb52faa193699f66f696ce924869f36bf99acbcd9071c93afe3db21a810362d9656455522196f3d585d2162fa6e70c09cd97890eb7"
                }
            ]
        }
    """.trimIndent()

    private fun catalog(status: HttpStatusCode = HttpStatusCode.OK, body: String = manifest) =
        OcpManifestCatalog(HttpClient(MockEngine { respond(body, status) }))

    @Test
    fun mapsKfmonEntry() = runTest {
        val release = catalog().releases(PackageId.KFMON, "KFMon").single()
        assertEquals("1.4.6-191-gca31869", release.version)
        assertEquals("2026-06-07", release.publishedOn)
        assertEquals(
            "https://storage.gra.cloud.ovh.net/v1/AUTH_2ac4bfee353948ec8ea7fd1710574097/kfmon-pub/OCP-KFMon-1.4.6-191-gca31869.zip",
            release.artifact.url,
        )
        assertEquals(894583L, release.artifact.sizeBytes)
        assertEquals(setOf(HashAlgorithm.BLAKE2B_512, HashAlgorithm.MD5), release.artifact.requiredAlgorithms)
        assertTrue(release.isVerifiable)
    }

    @Test
    fun missingPackageIsEmpty() = runTest {
        assertTrue(catalog().releases(PackageId.PLATO, "Plato").isEmpty())
    }

    @Test
    fun httpErrorThrows() = runTest {
        assertFailsWith<IllegalStateException> { catalog(HttpStatusCode.NotFound, "nope").releases(PackageId.KFMON, "KFMon") }
    }
}
