package org.mmclark.koreloader.domain.install

import org.mmclark.koreloader.domain.packages.ArchiveLayout
import org.mmclark.koreloader.domain.packages.BundledFile
import org.mmclark.koreloader.domain.packages.Packages
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InstallPlanTest {

    private fun dir(name: String) = ArchiveEntry(name, isDirectory = true, size = 0)
    private fun file(name: String, size: Long = 10) = ArchiveEntry(name, isDirectory = false, size = size)
    private fun paths(files: List<InstallPlan.PlannedFile>) = files.map { it.target.toString() }

    /** Shape of OCP-KFMon (KoboRoot.tgz deliberately not last here). */
    private val kfmonOcp = PlanInput.Archive(
        archive = 0,
        entries = listOf(
            dir(".adds/"), dir(".kobo/"), file(".kobo/KoboRoot.tgz", 788009), file("kfmon.png"),
            dir(".adds/kfmon/"), dir(".adds/kfmon/config/"), file(".adds/kfmon/config/kfmon.ini"),
        ),
        layout = Packages.KFMON.layout,
    )

    /** Shape of koreader-kobo-vX.zip. */
    private val koreaderZip = PlanInput.Archive(
        archive = 1,
        entries = listOf(file("koreader.png"), dir("koreader/"), file("koreader/koreader.sh"), dir("koreader/plugins/"), file("koreader/plugins/x.lua")),
        layout = Packages.KOREADER.layout,
    )

    private val koreaderLaunchers = PlanInput.Bundled(Packages.KOREADER.launcherFiles + Packages.KFMON.launcherFiles)

    @Test
    fun kfmonPlusKoreaderWithKoboRootLast() {
        val required = Packages.KFMON.requiredFiles + Packages.KOREADER.requiredFiles
        val plan = assertIs<PlanResult.Valid>(InstallPlan.compose(listOf(kfmonOcp, koreaderZip, koreaderLaunchers), required)).plan

        assertEquals(listOf(".kobo/KoboRoot.tgz"), paths(plan.deferredFiles))
        assertEquals(
            listOf(
                "kfmon.png", ".adds/kfmon/config/kfmon.ini",
                ".adds/koreader/koreader.sh", ".adds/koreader/plugins/x.lua",
                "koreader.png", ".adds/kfmon/config/koreader.ini", ".adds/nm/kfmon", ".adds/nm/koreader",
            ),
            paths(plan.files),
        )
        // The zip's stray top-level koreader.png is ignored; ours (the KFMon trigger icon) is used.
        assertEquals(FileSource.FromBundle("kfmon/koreader.png"), plan.files.single { it.target == KoboPath.of("koreader.png") }.source)
        assertTrue(KoboPath.of(".adds/koreader/plugins") in plan.directories)
        assertTrue(KoboPath.of(".adds/nm") in plan.directories)
        assertTrue(plan.triggersKoboUpdate)
    }

    @Test
    fun koreaderAloneDoesNotTriggerAnUpdate() {
        val plan = assertIs<PlanResult.Valid>(
            InstallPlan.compose(listOf(koreaderZip, PlanInput.Bundled(Packages.KOREADER.launcherFiles)), Packages.KOREADER.requiredFiles),
        ).plan
        assertFalse(plan.triggersKoboUpdate)
        assertTrue(plan.deferredFiles.isEmpty())
    }

    @Test
    fun pathsMapIntoTheTargetWithoutStripping() {
        val plato = PlanInput.Archive(2, listOf(file("plato.sh"), dir("css/"), file("css/epub.css")), Packages.PLATO.layout)
        val plan = assertIs<PlanResult.Valid>(InstallPlan.compose(listOf(plato), emptyList())).plan
        assertEquals(listOf(".adds/plato/plato.sh", ".adds/plato/css/epub.css"), paths(plan.files))
    }

    @Test
    fun rejectsUnsafeEntries() {
        for (bad in listOf("../evil", "/etc/passwd", ".adds/../../x", "C:\\x")) {
            val input = kfmonOcp.copy(entries = kfmonOcp.entries + file(bad))
            assertEquals(PlanProblem.UnsafeEntry(bad), assertIs<PlanResult.Invalid>(InstallPlan.compose(listOf(input), emptyList())).problem)
        }
    }

    @Test
    fun rejectsConflicts() {
        fun problem(vararg inputs: PlanInput) = assertIs<PlanResult.Invalid>(InstallPlan.compose(inputs.toList(), emptyList())).problem
        // Case-insensitive duplicate within an archive.
        assertIs<PlanProblem.Conflict>(problem(kfmonOcp.copy(entries = kfmonOcp.entries + file("KFMON.png"))))
        // File where a directory is needed, and vice versa.
        assertIs<PlanProblem.Conflict>(problem(kfmonOcp.copy(entries = kfmonOcp.entries + file(".adds/kfmon"))))
        assertIs<PlanProblem.Conflict>(problem(kfmonOcp.copy(entries = kfmonOcp.entries + file("kfmon.png/x"))))
        // Two different sources for the same target.
        assertIs<PlanProblem.Conflict>(problem(kfmonOcp, PlanInput.Bundled(listOf(BundledFile("other", KoboPath.of("kfmon.png"))))))
    }

    @Test
    fun requiredFilesAndEmptyArchives() {
        assertEquals(
            PlanProblem.MissingRequiredFile(KoboPath.of("kfmon.png")),
            assertIs<PlanResult.Invalid>(
                InstallPlan.compose(listOf(kfmonOcp.copy(entries = kfmonOcp.entries.filterNot { it.name == "kfmon.png" })), Packages.KFMON.requiredFiles),
            ).problem,
        )
        assertEquals(
            PlanProblem.EmptyArchive,
            assertIs<PlanResult.Invalid>(InstallPlan.compose(listOf(PlanInput.Archive(0, listOf(dir("a/")), ArchiveLayout(KoboPath.ROOT))), emptyList())).problem,
        )
    }
}
