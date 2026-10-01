package org.mmclark.koreloader.infrastructure.archive

import kotlinx.coroutines.test.runTest
import org.mmclark.koreloader.application.port.ArchiveException
import org.mmclark.koreloader.application.port.LocalFile
import org.mmclark.koreloader.domain.install.ArchiveEntry
import org.mmclark.koreloader.domain.install.InstallPlan
import org.mmclark.koreloader.domain.install.PlanInput
import org.mmclark.koreloader.domain.install.PlanResult
import org.mmclark.koreloader.domain.packages.Packages
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
     * Optional checks against real upstream archives (never committed). Run with e.g.
     * `KORELOADER_OCP_KFMON_ZIP=…/OCP-KFMon-x.zip KORELOADER_KOREADER_ZIP=…/koreader-kobo-vX.zip ./gradlew :infrastructure:jvmTest --rerun-tasks`.
     */
    @Test
    fun realArchivesPlanCleanly() = runTest {
        val kfmon = System.getenv("KORELOADER_OCP_KFMON_ZIP")?.let(::File) ?: return@runTest
        val koreader = System.getenv("KORELOADER_KOREADER_ZIP")?.let(::File) ?: return@runTest
        val reader = ZipFileArchiveReader()
        reader.open(LocalFile(kfmon.path, kfmon.length())) { kfmonArchive ->
            reader.open(LocalFile(koreader.path, koreader.length())) { koreaderArchive ->
                val inputs = listOf(
                    PlanInput.Archive(0, kfmonArchive.entries, Packages.KFMON.layout),
                    PlanInput.Bundled(Packages.KFMON.launcherFiles),
                    PlanInput.Archive(1, koreaderArchive.entries, Packages.KOREADER.layout),
                    PlanInput.Bundled(Packages.KOREADER.launcherFiles),
                )
                val plan = assertIs<PlanResult.Valid>(
                    InstallPlan.compose(inputs, Packages.KFMON.requiredFiles + Packages.KOREADER.requiredFiles),
                ).plan
                assertEquals(listOf(InstallPlan.KOBO_ROOT_TGZ), plan.deferredFiles.map { it.target })
                println("Real KFMon + KOReader: ${plan.allFiles.size} files, ${plan.directories.size} dirs, ${plan.totalBytes} bytes")
            }
        }
    }
}
