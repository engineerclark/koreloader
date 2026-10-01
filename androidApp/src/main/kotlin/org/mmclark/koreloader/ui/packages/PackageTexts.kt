package org.mmclark.koreloader.ui.packages

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mmclark.koreloader.R
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.install.InstallError
import org.mmclark.koreloader.domain.catalog.ArtifactVerification
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.install.PlanProblem
import org.mmclark.koreloader.domain.packages.PackageStatus
import org.mmclark.koreloader.domain.packages.Packages
import org.mmclark.koreloader.ui.common.describe

@DrawableRes
fun logoFor(id: PackageId): Int = when (id) {
    PackageId.KOREADER -> R.drawable.logo_koreader
    PackageId.PLATO -> R.drawable.logo_plato
    else -> R.drawable.logo_kfmon
}

enum class StatusTone { Neutral, Good, Attention, Problem }

data class StatusLabel(val text: String, val tone: StatusTone)

fun PackageStatus.label(): StatusLabel = when (this) {
    PackageStatus.NotInstalled -> StatusLabel("Not installed", StatusTone.Neutral)
    is PackageStatus.Installed -> when {
        removalPending -> StatusLabel("Removal pending: eject the Kobo", StatusTone.Attention)
        needsReinstall -> StatusLabel("Reinstall needed (firmware updated)", StatusTone.Problem)
        updateAvailable -> StatusLabel("$version · update to $latestVersion", StatusTone.Attention)
        version == null -> StatusLabel("Installed · version unknown", StatusTone.Good)
        else -> StatusLabel("Installed $version", StatusTone.Good)
    }
}

@Composable
fun StatusChip(label: StatusLabel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (label.tone) {
        StatusTone.Neutral -> colors.surfaceVariant to colors.onSurfaceVariant
        StatusTone.Good -> colors.primaryContainer to colors.onPrimaryContainer
        StatusTone.Attention -> colors.tertiaryContainer to colors.onTertiaryContainer
        StatusTone.Problem -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(label.text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

fun InstallError.describe(): String = when (this) {
    is InstallError.DeviceNotReady -> when (detection) {
        is DeviceDetection.VolumeUnavailable -> "The Kobo drive isn't accessible. Is it still connected?"
        DeviceDetection.NotAKobo -> "The selected folder isn't the root of a Kobo drive."
        else -> "The Kobo couldn't be identified."
    }
    is InstallError.DeviceUnsupported -> compatibility.reasons.joinToString(" ") { it.describe() }
    is InstallError.DependencyUnavailable ->
        "${Packages.get(packageId).name} is needed too, but couldn't be found: $message"
    is InstallError.DownloadFailed -> "Download failed: $message"
    is InstallError.VerificationFailed -> when (val v = verification) {
        is ArtifactVerification.SizeMismatch -> "The download has the wrong size (${v.actual} instead of ${v.expected} bytes)."
        is ArtifactVerification.ChecksumMismatch -> "The download is corrupt (${v.expected.algorithm} mismatch)."
        is ArtifactVerification.DigestMissing, ArtifactVerification.Verified -> "The download couldn't be verified."
    } + " Nothing was written to the Kobo."
    is InstallError.InvalidPackage -> when (val p = problem) {
        PlanProblem.EmptyArchive -> "The package is empty."
        is PlanProblem.UnsafeEntry -> "The package contains an unsafe path (${p.entryName})."
        is PlanProblem.Conflict -> "The package contains conflicting files (${p.entryName})."
        is PlanProblem.MissingRequiredFile -> "The package is missing ${p.path}."
    } + " Nothing was written to the Kobo."
    is InstallError.UnreadablePackage -> "The package couldn't be read: $message"
    is InstallError.WriteFailed -> "Writing ${path ?: "to the Kobo"} failed: $message"
    is InstallError.SanityCheckFailed -> "After copying, these files are missing: ${missing.joinToString()}."
}
