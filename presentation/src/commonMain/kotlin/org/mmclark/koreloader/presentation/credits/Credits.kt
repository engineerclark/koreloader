package org.mmclark.koreloader.presentation.credits

/** An upstream project koreloader is directly based on. */
data class Credit(val project: String, val usedFor: String, val author: String, val url: String)

/** A source of reference data or parsing logic (credited more briefly). */
data class Reference(val label: String, val url: String)

/** Who koreloader is built on. Keep in sync with THIRD_PARTY_ASSETS.md and CLAUDE.md's sources. */
object Credits {

    val projects: List<Credit> = listOf(
        Credit(
            project = "KOReader",
            usedFor = "the KOReader app and logo",
            author = "the KOReader contributors",
            url = "https://github.com/koreader/koreader",
        ),
        Credit(
            project = "Plato",
            usedFor = "the Plato app and logo",
            author = "baskerville",
            url = "https://github.com/baskerville/plato",
        ),
        Credit(
            project = "KFMon & One-Click Packages",
            usedFor = "the launcher, packages, install and uninstall scripts, and launcher configs and icons",
            author = "NiLuJe",
            url = "https://github.com/NiLuJe/kfmon",
        ),
        Credit(
            project = "NickelMenu",
            usedFor = "the Home screen menu entries (installed with KFMon)",
            author = "pgaskin",
            url = "https://github.com/pgaskin/NickelMenu",
        ),
    )

    val references: List<Reference> = listOf(
        Reference("koboutils (Kobo model list)", "https://github.com/pgaskin/koboutils"),
        Reference("calibre (.kobo/version format)", "https://github.com/kovidgoyal/calibre"),
    )
}
