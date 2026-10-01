package org.mmclark.koreloader.application.install

import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.domain.catalog.ArtifactVerification
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.install.PlanProblem
import org.mmclark.koreloader.domain.volume.KoboPath

/** Progress and outcome of [InstallPackage]. The flow ends with [Succeeded] or [Failed]. */
sealed interface InstallProgress {
    data object CheckingDevice : InstallProgress
    data class Downloading(val bytesDone: Long, val bytesTotal: Long?) : InstallProgress
    data object VerifyingDownload : InstallProgress
    data object PreparingDevice : InstallProgress
    data class Writing(
        val filesDone: Int,
        val filesTotal: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
        val currentFile: KoboPath?,
    ) : InstallProgress
    data object VerifyingInstall : InstallProgress

    data class Succeeded(val device: KoboDevice, val release: PackageRelease) : InstallProgress

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
    data class DownloadFailed(val message: String) : InstallError
    data class VerificationFailed(val verification: ArtifactVerification) : InstallError
    data class InvalidPackage(val problem: PlanProblem) : InstallError
    data class UnreadablePackage(val message: String) : InstallError
    data class WriteFailed(val path: KoboPath?, val message: String) : InstallError
    data class SanityCheckFailed(val missing: List<KoboPath>) : InstallError
}
