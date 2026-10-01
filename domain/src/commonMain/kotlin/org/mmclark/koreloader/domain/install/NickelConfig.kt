package org.mmclark.koreloader.domain.install

import org.mmclark.koreloader.domain.volume.KoboPath

/**
 * Rules for Nickel's settings file. Since firmware 4.17, Nickel indexes content in hidden
 * folders unless `ExcludeSyncFolders` says otherwise, which would turn KOReader's files into
 * "books". This mirrors KFMon's `tools/install.sh`: append (never rewrite) a
 * `[FeatureSettings]` section; QSettings merges the duplicate section on its next save.
 */
object NickelConfig {

    val PATH: KoboPath = KoboPath.of(".kobo/Kobo/Kobo eReader.conf")

    /** Written verbatim: the doubled backslashes are QSettings escaping. */
    const val EXCLUDE_SYNC_FOLDERS_LINE: String = """ExcludeSyncFolders=(\\.(?!kobo|adobe).+|([^.][^/]*/)+\\..+)"""

    /** Text to append to the config, starting with a newline in case the file doesn't end with one. */
    const val HIDDEN_FOLDER_EXCLUSION: String = "\n[FeatureSettings]\n$EXCLUDE_SYNC_FOLDERS_LINE\n"

    /** Whether [contents] (null if the file doesn't exist) still needs [HIDDEN_FOLDER_EXCLUSION]. */
    fun needsHiddenFolderExclusion(contents: String?): Boolean =
        contents == null || !contents.contains(EXCLUDE_SYNC_FOLDERS_LINE)
}
