package org.mmclark.koreloader.domain.install

import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InstallPlanTest {

    private fun dir(name: String) = ArchiveEntry(name, isDirectory = true, size = 0)
    private fun file(name: String, size: Long = 10) = ArchiveEntry(name, isDirectory = false, size = size)

    /** Shape of OCP-KOReader (KoboRoot.tgz deliberately not last here). */
    private val ocp = listOf(
        dir(".adds/"), dir(".kobo/"),
        file(".kobo/KoboRoot.tgz", 788009),
        file("koreader.png"), file("kfmon.png"),
        dir(".adds/kfmon/"), dir(".adds/kfmon/config/"),
        file(".adds/kfmon/config/koreader.ini"),
        file(".adds/nm/koreader"),
        file(".adds/koreader/koreader.sh"),
    )

    @Test
    fun koboRootIsDeferredAndEverythingElseKeepsArchiveOrder() {
        val plan = assertIs<PlanResult.Valid>(InstallPlan.forOneClickPackage(ocp)).plan
        assertEquals(listOf(InstallPlan.KOBO_ROOT_TGZ), plan.deferredFiles.map { it.target })
        assertEquals(
            listOf("koreader.png", "kfmon.png", ".adds/kfmon/config/koreader.ini", ".adds/nm/koreader", ".adds/koreader/koreader.sh"),
            plan.files.map { it.target.toString() },
        )
        assertEquals(listOf(".adds", ".kobo", ".adds/kfmon", ".adds/kfmon/config"), plan.directories.map { it.toString() })
        assertEquals(listOf(InstallPlan.KOBO_ROOT_TGZ, InstallPlan.KFMON_ICON), plan.requiredFiles)
        assertEquals(788009L + 5 * 10, plan.totalBytes)
        assertEquals(InstallPlan.KOBO_ROOT_TGZ, plan.allFiles.last().target)
    }

    @Test
    fun rejectsUnsafeEntries() {
        for (bad in listOf("../evil", "/etc/passwd", ".adds/../../x", "C:\\x")) {
            val result = assertIs<PlanResult.Invalid>(InstallPlan.forOneClickPackage(ocp + file(bad)))
            assertEquals(PlanProblem.UnsafeEntry(bad), result.problem)
        }
    }

    @Test
    fun rejectsCaseInsensitiveDuplicatesAndFileDirectoryClashes() {
        assertIs<PlanProblem.Conflict>(
            assertIs<PlanResult.Invalid>(InstallPlan.forOneClickPackage(ocp + file("KFMON.png"))).problem,
        )
        assertIs<PlanProblem.Conflict>(
            assertIs<PlanResult.Invalid>(InstallPlan.forOneClickPackage(ocp + file(".adds/kfmon"))).problem,
        )
        assertIs<PlanProblem.Conflict>(
            assertIs<PlanResult.Invalid>(InstallPlan.forOneClickPackage(ocp + file("koreader.png/x"))).problem,
        )
    }

    @Test
    fun requiresKoboRootAndKfmonIcon() {
        val noKoboRoot = ocp.filterNot { it.name == ".kobo/KoboRoot.tgz" }
        assertEquals(
            PlanProblem.MissingRequiredFile(InstallPlan.KOBO_ROOT_TGZ),
            assertIs<PlanResult.Invalid>(InstallPlan.forOneClickPackage(noKoboRoot)).problem,
        )
        val noIcon = ocp.filterNot { it.name == "kfmon.png" }
        assertEquals(
            PlanProblem.MissingRequiredFile(KoboPath.of("kfmon.png")),
            assertIs<PlanResult.Invalid>(InstallPlan.forOneClickPackage(noIcon)).problem,
        )
        assertEquals(
            PlanProblem.EmptyArchive,
            assertIs<PlanResult.Invalid>(InstallPlan.forOneClickPackage(listOf(dir(".adds/")))).problem,
        )
    }
}
