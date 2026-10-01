package org.mmclark.koreloader.ui.install

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.install.InstallError
import org.mmclark.koreloader.application.install.InstallProgress
import org.mmclark.koreloader.application.install.ReleaseLookup
import org.mmclark.koreloader.domain.catalog.ArtifactVerification
import org.mmclark.koreloader.domain.install.PlanProblem
import org.mmclark.koreloader.presentation.install.InstallViewModel
import org.mmclark.koreloader.ui.common.Busy
import org.mmclark.koreloader.ui.common.DeviceCard
import org.mmclark.koreloader.ui.common.RestorePersistedVolume
import org.mmclark.koreloader.ui.common.describe
import org.mmclark.koreloader.ui.common.formatBytes
import org.mmclark.koreloader.ui.common.rememberKoboVolumePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallScreen(viewModel: InstallViewModel, onOpenStorageTest: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickVolume = rememberKoboVolumePicker(viewModel::onVolumeSelected)
    RestorePersistedVolume(state.volume, viewModel::onVolumeSelected)
    KeepScreenOn(state.installing)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("koreloader") },
                actions = {
                    TextButton(onClick = onOpenStorageTest, enabled = !state.installing) { Text("Storage test") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PackageCard(state.release, onRetry = viewModel::onRetryRelease) }
            item { SectionTitle("Your Kobo") }
            item {
                if (state.volume == null) {
                    Text(
                        "Connect the Kobo with a USB OTG cable, tap \"Connect\" on the Kobo, " +
                            "then choose the root of the Kobo drive (usually labelled KOBOeReader).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickVolume, enabled = !state.installing) {
                        Text(if (state.volume == null) "Choose Kobo drive" else "Choose another drive")
                    }
                    if (state.volume != null) {
                        OutlinedButton(
                            onClick = viewModel::onRefreshDevice,
                            enabled = !state.detecting && !state.installing,
                        ) { Text("Re-check") }
                    }
                }
            }
            item { DeviceCard(state.volume, state.detection) }

            when (val progress = state.progress) {
                null -> {
                    if (state.detection is DeviceDetection.Detected) {
                        item { ChecklistCard() }
                        item {
                            Button(
                                onClick = viewModel::onInstall,
                                enabled = state.canInstall,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Install") }
                        }
                    }
                }
                is InstallProgress.Succeeded -> item { SuccessCard(onDone = viewModel::onDismissResult) }
                is InstallProgress.Failed -> item { FailureCard(progress, onDismiss = viewModel::onDismissResult) }
                else -> item { ProgressCard(progress) }
            }
            item { Text("", modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PackageCard(release: ReleaseLookup?, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("KOReader", style = MaterialTheme.typography.titleLarge)
            Text(
                "NiLuJe's one-click package: KOReader, plus the KFMon and NickelMenu launchers.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (release) {
                null -> Busy("Checking the latest version…")
                is ReleaseLookup.Found -> Text(
                    "Version ${release.release.version} · ${formatBytes(release.release.artifact.sizeBytes)} download",
                    style = MaterialTheme.typography.bodySmall,
                )
                ReleaseLookup.NotFound -> Text("The package list doesn't include KOReader right now.")
                is ReleaseLookup.Unavailable -> {
                    Text("Couldn't load the package list: ${release.message}", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun ChecklistCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Before you install", style = MaterialTheme.typography.titleMedium)
            Bullet("For best results, fully restart the Kobo first, then connect it straight from the Home screen without opening a book.")
            Bullet("Not for KSM users: KSM always takes priority over KFMon.")
            Bullet("If you use fmon, note that only one of fmon and KFMon can be active; the one installed last wins.")
            Bullet("Your books and settings are kept. Hidden folders will be excluded from the Kobo's library scan.")
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ProgressCard(progress: InstallProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Installing… keep the Kobo connected", style = MaterialTheme.typography.titleMedium)
            when (progress) {
                InstallProgress.CheckingDevice -> Busy("Checking the Kobo…")
                is InstallProgress.Downloading -> {
                    val total = progress.bytesTotal
                    Text(
                        if (total != null) "Downloading ${formatBytes(progress.bytesDone)} of ${formatBytes(total)}"
                        else "Downloading ${formatBytes(progress.bytesDone)}",
                    )
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = { (progress.bytesDone.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                InstallProgress.VerifyingDownload -> Busy("Verifying the download…")
                InstallProgress.PreparingDevice -> Busy("Preparing the Kobo…")
                is InstallProgress.Writing -> {
                    Text("Copying file ${minOf(progress.filesDone + 1, progress.filesTotal)} of ${progress.filesTotal}")
                    LinearProgressIndicator(
                        progress = {
                            if (progress.bytesTotal > 0) (progress.bytesDone.toFloat() / progress.bytesTotal).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    progress.currentFile?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
                }
                InstallProgress.VerifyingInstall -> Busy("Checking the installed files…")
                is InstallProgress.Succeeded, is InstallProgress.Failed -> Unit
            }
        }
    }
}

@Composable
private fun SuccessCard(onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Files copied. Now eject the Kobo", style = MaterialTheme.typography.titleMedium)
            Text("1.  Eject the Kobo from Android: tap the USB drive notification and choose Eject (or go to Settings › Storage).")
            Text("2.  Unplug the cable.")
            Text("3.  The Kobo will look like it's importing a book, then restart to install KFMon and NickelMenu.")
            Text("4.  After the restart, open KOReader from its icon in your Library, or from the new menu on the Home screen.")
            Text(
                "If the Kobo doesn't restart, the install didn't work. Reconnect it and install again.",
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun FailureCard(failed: InstallProgress.Failed, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Install failed", style = MaterialTheme.typography.titleMedium)
            Text(failed.error.describe())
            if (failed.leftovers.isNotEmpty()) {
                Text(
                    "The Kobo won't install anything when you unplug it. These package files are on the Kobo now; " +
                        "they're harmless, and installing again will overwrite them:",
                )
                failed.leftovers.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onDismiss) { Text("Back") }
        }
    }
}

private fun InstallError.describe(): String = when (this) {
    is InstallError.DeviceNotReady -> when (detection) {
        is DeviceDetection.VolumeUnavailable -> "The Kobo drive isn't accessible. Is it still connected?"
        DeviceDetection.NotAKobo -> "The selected folder isn't the root of a Kobo drive."
        else -> "The Kobo couldn't be identified."
    }
    is InstallError.DeviceUnsupported -> compatibility.reasons.joinToString(" ") { it.describe() }
    is InstallError.DownloadFailed -> "Download failed: $message"
    is InstallError.VerificationFailed -> when (val v = verification) {
        is ArtifactVerification.SizeMismatch -> "The download has the wrong size (${v.actual} instead of ${v.expected} bytes)."
        is ArtifactVerification.ChecksumMismatch -> "The download is corrupt (${v.expected.algorithm} mismatch)."
        is ArtifactVerification.DigestMissing -> "The download couldn't be verified."
        ArtifactVerification.Verified -> "The download couldn't be verified."
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

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
