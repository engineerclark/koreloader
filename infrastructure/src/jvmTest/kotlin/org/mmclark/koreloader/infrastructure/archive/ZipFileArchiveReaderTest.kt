package org.mmclark.koreloader.infrastructure.archive

import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.port.ArchiveException
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.domain.install.ArchiveEntry
import org.mmclark.koreloader.domain.install.InstallPlan
import org.mmclark.koreloader.domain.install.PlanResult
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ZipFileArchiveReaderTest {

    private fun zipOf(vararg entries: Pair<String, String?>): LocalFile {
        val file = File.createTempFile("archive", ".zip").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                data?.let { zip.write(it.encodeToByteArray()) }
                zip.closeEntry()
            }
        }
        return LocalFile(file.path, file.length())
    }

    @Test
    fun listsEntriesInOrderAndStreamsContents() = runTest {
        val file = zipOf(".adds/" to null, "kfmon.png" to "png", ".kobo/KoboRoot.tgz" to "tarball")
        ZipFileArchiveReader().open(file) { archive ->
            assertEquals(
                listOf(
                    ArchiveEntry(".adds/", isDirectory = true, size = 0),
                    ArchiveEntry("kfmon.png", isDirectory = false, size = 3),
                    ArchiveEntry(".kobo/KoboRoot.tgz", isDirectory = false, size = 7),
                ),
                archive.entries,
            )
            assertEquals("tarball", archive.read(".kobo/KoboRoot.tgz") { okio.Buffer().apply { writeAll(it) }.readUtf8() })
            assertFailsWith<ArchiveException> { archive.read("missing") { } }
        }
    }

    @Test
    fun notAZip() = runTest {
        val file = File.createTempFile("bogus", ".zip").apply { deleteOnExit(); writeText("not a zip") }
        assertFailsWith<ArchiveException> { ZipFileArchiveReader().open(LocalFile(file.path, file.length())) { } }
    }

    /**
     * Optional check against a real one-click package. Run with
     * `KORELOADER_OCP_ZIP=/path/to/OCP-KOReader-vX.zip ./gradlew :infrastructure:jvmTest`.
     */
    @Test
    fun realOneClickPackagePlansCleanly() = runTest {
        val path = System.getenv("KORELOADER_OCP_ZIP") ?: return@runTest
        val file = File(path)
        ZipFileArchiveReader().open(LocalFile(file.path, file.length())) { archive ->
            val plan = assertIs<PlanResult.Valid>(InstallPlan.forOneClickPackage(archive.entries)).plan
            assertEquals(listOf(InstallPlan.KOBO_ROOT_TGZ), plan.deferredFiles.map { it.target })
            println("Real OCP: ${plan.files.size + plan.deferredFiles.size} files, ${plan.directories.size} dirs, ${plan.totalBytes} bytes")
        }
    }
}
