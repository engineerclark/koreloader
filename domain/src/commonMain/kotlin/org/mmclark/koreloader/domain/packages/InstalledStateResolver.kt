package org.mmclark.koreloader.domain.packages

/** Decides what is installed from the files found on the volume. */
object InstalledStateResolver {

    /**
     * @param markerPresent whether the package's marker file exists.
     * @param versionFileContents contents of the package's own version file, if it has one and it exists.
     * @param receipt koreloader's receipt for the package, if any.
     */
    fun resolve(markerPresent: Boolean, versionFileContents: String?, receipt: InstallReceipt?): InstalledState {
        if (!markerPresent) return InstalledState.NotInstalled
        val ownVersion = versionFileContents?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 }
        // The package's own version file wins: it is updated even when something else reinstalls it.
        return InstalledState.Installed(version = ownVersion ?: receipt?.version, receipt = receipt)
    }
}
