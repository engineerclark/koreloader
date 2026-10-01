package org.mmclark.koreloader.domain.packages

import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.FirmwareVersion

/**
 * Written by koreloader to the volume after each install (`.adds/koreloader/receipts.json`),
 * because not every package records its own version, and KFMon's real install (on the
 * Kobo's system partition) isn't visible over USB.
 */
data class InstallReceipt(
    val packageId: PackageId,
    val version: String,
    /** Kobo firmware at install time: a later firmware update disables KFMon. */
    val firmware: FirmwareVersion,
    /** An uninstaller was placed; the Kobo removes the package when it is next unplugged. */
    val removalPending: Boolean = false,
)

/** What is found on the volume for one package. */
sealed interface InstalledState {
    data object NotInstalled : InstalledState

    /** [version] is null when neither a version file nor a koreloader receipt says which. */
    data class Installed(val version: String?, val receipt: InstallReceipt?) : InstalledState
}

sealed interface PackageStatus {
    data object NotInstalled : PackageStatus

    data class Installed(
        /** null: installed by other means, version unknown. */
        val version: String?,
        val latestVersion: String?,
        val updateAvailable: Boolean,
        /** KFMon installed on an older firmware: the firmware update disabled it. */
        val needsReinstall: Boolean,
        /** Removal is scheduled on the Kobo (KFMon uninstaller placed, not yet run). */
        val removalPending: Boolean = false,
    ) : PackageStatus
}

object PackageStatusPolicy {

    /**
     * Derives the status shown to the user. An update is only claimed when the installed
     * version is a known, older release; unknown versions (e.g. nightlies) never show one.
     */
    fun status(
        state: InstalledState,
        releases: List<PackageRelease>,
        currentFirmware: FirmwareVersion,
    ): PackageStatus = when (state) {
        InstalledState.NotInstalled -> PackageStatus.NotInstalled
        is InstalledState.Installed -> {
            val latest = releases.firstOrNull()?.version
            val installedIndex = state.version?.let { v -> releases.indexOfFirst { it.version == v } } ?: -1
            PackageStatus.Installed(
                version = state.version,
                latestVersion = latest,
                updateAvailable = installedIndex > 0,
                needsReinstall = state.receipt != null &&
                    state.receipt.packageId == PackageId.KFMON &&
                    state.receipt.firmware != currentFirmware,
                removalPending = state.receipt?.removalPending == true,
            )
        }
    }

    /** Whether installing a package must also (re)install [dependency] first. */
    fun dependencyNeedsInstall(dependency: PackageStatus): Boolean = when (dependency) {
        PackageStatus.NotInstalled -> true
        is PackageStatus.Installed -> dependency.needsReinstall || dependency.removalPending
    }
}
