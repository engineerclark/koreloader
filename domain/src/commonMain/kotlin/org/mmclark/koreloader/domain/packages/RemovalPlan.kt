package org.mmclark.koreloader.domain.packages

import org.mmclark.koreloader.domain.volume.KoboPath

/** Paths to delete over USB to remove a package (for [Removal.DeleteFiles]). */
data class RemovalPlan(val deletions: List<KoboPath>, val preserved: List<KoboPath>, val pruneIfEmpty: List<KoboPath>) {

    companion object {
        /**
         * @param programDirEntries names of the entries currently in the package's program directory.
         * @param removeUserData whether to also delete settings, history and other user data.
         */
        fun forPackage(removal: Removal.DeleteFiles, programDirEntries: List<String>, removeUserData: Boolean): RemovalPlan {
            if (removeUserData) {
                return RemovalPlan(
                    deletions = listOf(removal.programDir) + removal.launcherFiles + removal.userDataOutside,
                    preserved = emptyList(),
                    pruneIfEmpty = removal.pruneIfEmpty,
                )
            }
            val keep = removal.preservedEntries.map { it.lowercase() }.toSet()
            val (kept, deleted) = programDirEntries.partition { it.lowercase() in keep }
            return RemovalPlan(
                deletions = deleted.map { removal.programDir.resolve(it) } + removal.launcherFiles,
                preserved = kept.map { removal.programDir.resolve(it) },
                pruneIfEmpty = removal.pruneIfEmpty,
            )
        }
    }
}
