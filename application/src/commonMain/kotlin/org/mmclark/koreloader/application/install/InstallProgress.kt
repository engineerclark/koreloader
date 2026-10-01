package org.mmclark.koreloader.application.install

import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.domain.catalog.ArtifactVerification
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.install.PlanProblem
import org.mmclark.koreloader.domain.volume.KoboPath

/** Progress and outcome of [InstallPackage]. The flow ends with [Succeeded] or [Failed]. */
sealed interface InstallProgress {
    data object CheckingDevice : InstallProgress

    /** Downloading part [part] (1-based) of [parts]: one per package being installed. */
    data class Downloading(
        val packageId: PackageId,
        val part: Int,
        val parts: Int,
        val bytesDone: Long,
        val bytesTotal: Long?,
    ) : InstallProgress

    data class VerifyingDownload(val packageId: PackageId) : InstallProgress
    data object PreparingDevice : InstallProgress
    data class Writing(
        val filesDone: Int,
        val filesTotal: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
        val currentFile: KoboPath?,
    ) : InstallProgress
    data object VerifyingInstall : InstallProgress

    /**
     * [installed] lists every release written, dependencies first. When [koboUpdateRequired]
     * (this install, or an earlier one in the same USB session, left a `KoboRoot.tgz`), the
     * Kobo must be ejected and unplugged so Nickel processes it and reboots.
     */
    data class Succeeded(
        val device: KoboDevice,
        val installed: List<PackageRelease>,
        val koboUpdateRequired: Boolean,
    ) : InstallProgress

    /**
     * [leftovers] are package files present on the volume after the failure. They are left in
     * place (as KFMon's own installer does), because they may belong to an earlier, working install.
     */
    data class Failed(val error: InstallError, val leftovers: List<KoboPath> = emptyList()) : InstallProgress

    val isFinished: Boolean get() = this is Succeeded || this is Failed
}

sealed interface InstallError {
    data class DeviceNotReady(val detection: DeviceDetection) : InstallError
    data class DeviceUnsupported(val compatibility: DeviceCompatibility.Unsupported) : InstallError
    data class DependencyUnavailable(val packageId: PackageId, val message: String) : InstallError
    data class DownloadFailed(val message: String) : InstallError
    data class VerificationFailed(val verification: ArtifactVerification) : InstallError
    data class InvalidPackage(val problem: PlanProblem) : InstallError
    data class UnreadablePackage(val message: String) : InstallError
    data class WriteFailed(val path: KoboPath?, val message: String) : InstallError
    data class SanityCheckFailed(val missing: List<KoboPath>) : InstallError
}
