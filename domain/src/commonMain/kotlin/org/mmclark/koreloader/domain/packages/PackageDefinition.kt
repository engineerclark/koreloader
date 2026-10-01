package org.mmclark.koreloader.domain.packages

import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.volume.KoboPath

/**
 * Static knowledge about a package: where it lives on the Kobo, how its archive is laid out,
 * what it depends on, and how to remove it. Sources: KFMon's Makefile, `tools/one-click.py`
 * and `scripts/uninstall/kfmon-uninstall.sh`; KOReader's `datastorage.lua`; Plato's
 * `library.rs` and `settings/mod.rs` (all checked 2026-09-30).
 */
data class PackageDefinition(
    val id: PackageId,
    val name: String,
    val summary: String,
    /** File whose presence means the package is on the volume. */
    val marker: KoboPath,
    /** File holding the installed version (KOReader's `git-rev`), if the package has one. */
    val versionFile: KoboPath?,
    /** How the release archive maps onto the volume. */
    val layout: ArchiveLayout,
    /** Small files koreloader ships itself (launcher configs and trigger icons), installed with the package. */
    val launcherFiles: List<BundledFile>,
    /** Packages that must be installed (and working) for this one to be launchable. */
    val dependencies: List<PackageId>,
    /** Files that must exist after installing. */
    val requiredFiles: List<KoboPath>,
    val removal: Removal,
    /** Special messages shown prominently with the package (e.g. when it needn't be installed by hand). */
    val notices: List<String> = emptyList(),
)

/** Where a release archive's entries go: entries under [stripPrefix] are placed under [target]. */
data class ArchiveLayout(
    val target: KoboPath,
    /** Entries outside this prefix are ignored; null takes every entry. */
    val stripPrefix: String? = null,
)

/** A file bundled with koreloader (identified by [asset]) and where it goes on the volume. */
data class BundledFile(val asset: String, val target: KoboPath)

sealed interface Removal {
    /**
     * Delete files over USB. [programDir] is removed except [preservedEntries] (its user
     * settings and history), unless the user opts to remove user data, in which case
     * [userDataOutside] is removed as well.
     */
    data class DeleteFiles(
        val programDir: KoboPath,
        val launcherFiles: List<KoboPath>,
        val preservedEntries: List<String>,
        val userDataOutside: List<KoboPath>,
        /** Directories to remove if they end up empty. */
        val pruneIfEmpty: List<KoboPath> = emptyList(),
    ) : Removal

    /**
     * Run an uninstaller on the Kobo: place [koboRootAsset] as `.kobo/KoboRoot.tgz`, which
     * Nickel processes on the next unplug (then reboots).
     */
    data class KoboRootUninstaller(val koboRootAsset: String, val removedOnDevice: List<KoboPath>) : Removal
}

object Packages {

    val KFMON = PackageDefinition(
        id = PackageId.KFMON,
        name = "KFMon",
        summary = "Launcher that starts KOReader and Plato from their icons in the Kobo library. " +
            "Includes NickelMenu, which adds them to the Home screen menu.",
        marker = KoboPath.of(".adds/kfmon/config/kfmon.ini"),
        versionFile = null,
        layout = ArchiveLayout(target = KoboPath.ROOT),
        launcherFiles = listOf(BundledFile("nm/kfmon", KoboPath.of(".adds/nm/kfmon"))),
        dependencies = emptyList(),
        requiredFiles = listOf(KoboPath.of(".kobo/KoboRoot.tgz"), KoboPath.of("kfmon.png")),
        removal = Removal.KoboRootUninstaller(
            koboRootAsset = "kfmon-uninstaller/KoboRoot.tgz",
            // From kfmon-uninstall.sh: removed by the Kobo itself on the next boot.
            removedOnDevice = listOf(
                ".adds/kfmon/config/kfmon.ini", ".adds/kfmon/config/kfmon-log.ini", ".adds/kfmon/config/koreader.ini",
                ".adds/kfmon/config/plato.ini", ".adds/kfmon/bin/kfmon-printlog.sh", ".adds/kfmon/log/kfmon_dump.log",
                ".adds/nm/kfmon", ".adds/nm/koreader", ".adds/nm/plato", ".adds/nm/doc",
                "icons/plato.png", "koreader.png", "kfmon.png",
            ).map(KoboPath::of),
        ),
        notices = listOf(
            "You don't need to install KFMon separately: it's installed automatically with KOReader and Plato. " +
                "A Kobo firmware update disables KFMon, so reinstall it here after one.",
        ),
    )

    val KOREADER = PackageDefinition(
        id = PackageId.KOREADER,
        name = "KOReader",
        summary = "A document viewer for EPUB, PDF, DjVu, CBZ and more, with extensive reading settings and plugins.",
        marker = KoboPath.of(".adds/koreader/koreader.sh"),
        versionFile = KoboPath.of(".adds/koreader/git-rev"),
        // The release zip holds koreader/… plus a stray top-level koreader.png (an old fmon relic).
        layout = ArchiveLayout(target = KoboPath.of(".adds/koreader"), stripPrefix = "koreader/"),
        launcherFiles = listOf(
            BundledFile("kfmon/koreader.png", KoboPath.of("koreader.png")),
            BundledFile("kfmon/koreader.ini", KoboPath.of(".adds/kfmon/config/koreader.ini")),
            BundledFile("nm/kfmon", KoboPath.of(".adds/nm/kfmon")),
            BundledFile("nm/koreader", KoboPath.of(".adds/nm/koreader")),
        ),
        dependencies = listOf(PackageId.KFMON),
        requiredFiles = listOf(KoboPath.of(".adds/koreader/koreader.sh"), KoboPath.of("koreader.png")),
        removal = Removal.DeleteFiles(
            programDir = KoboPath.of(".adds/koreader"),
            launcherFiles = listOf("koreader.png", ".adds/kfmon/config/koreader.ini", ".adds/nm/koreader").map(KoboPath::of),
            // DataStorage (datastorage.lua): settings, reading history, per-book sidecars, stats, user tweaks.
            preservedEntries = listOf(
                "settings.reader.lua", "settings.reader.lua.old", "history.lua", "history.lua.old",
                "defaults.custom.lua", "defaults.persistent.lua", "defaults.legacy.lua",
                "settings", "docsettings", "hashdocsettings", "history", "statistics",
                "patches", "styletweaks", "screenshots", "clipboard", "icons",
            ),
            userDataOutside = emptyList(),
        ),
    )

    val PLATO = PackageDefinition(
        id = PackageId.PLATO,
        name = "Plato",
        summary = "A minimalist document reader for EPUB, PDF, DjVu, XPS and CBZ, designed for Kobo e-readers.",
        marker = KoboPath.of(".adds/plato/plato.sh"),
        versionFile = null,
        layout = ArchiveLayout(target = KoboPath.of(".adds/plato")),
        launcherFiles = listOf(
            BundledFile("kfmon/plato.png", KoboPath.of("icons/plato.png")),
            BundledFile("kfmon/plato.ini", KoboPath.of(".adds/kfmon/config/plato.ini")),
            BundledFile("nm/kfmon", KoboPath.of(".adds/nm/kfmon")),
            BundledFile("nm/plato", KoboPath.of(".adds/nm/plato")),
        ),
        dependencies = listOf(PackageId.KFMON),
        requiredFiles = listOf(KoboPath.of(".adds/plato/plato.sh"), KoboPath.of("icons/plato.png")),
        removal = Removal.DeleteFiles(
            programDir = KoboPath.of(".adds/plato"),
            launcherFiles = listOf("icons/plato.png", ".adds/kfmon/config/plato.ini", ".adds/nm/plato").map(KoboPath::of),
            preservedEntries = listOf("Settings.toml", "config.sh"),
            // Library metadata Plato keeps in each default library root (library.rs). `.trash` holds books: never touched.
            userDataOutside = listOf("", ".kobo/dropbox/", ".kobo/kepub/").flatMap { root ->
                listOf(".metadata.json", ".fat32-epoch", ".reading-states", ".thumbnail-previews").map { KoboPath.of(root + it) }
            },
            pruneIfEmpty = listOf(KoboPath.of("icons")),
        ),
    )

    val all: List<PackageDefinition> = listOf(KFMON, KOREADER, PLATO)

    fun get(id: PackageId): PackageDefinition = all.first { it.id == id }
}
