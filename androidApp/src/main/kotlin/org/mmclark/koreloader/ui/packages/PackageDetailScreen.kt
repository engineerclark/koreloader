package org.mmclark.koreloader.ui.packages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mmclark.koreloader.application.install.InstallProgress
import org.mmclark.koreloader.application.packages.OverviewResult
import org.mmclark.koreloader.application.packages.ReleasesLookup
import org.mmclark.koreloader.application.uninstall.RemovalPreview
import org.mmclark.koreloader.application.uninstall.UninstallProgress
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.packages.PackageStatus
import org.mmclark.koreloader.domain.packages.Packages
import org.mmclark.koreloader.presentation.packages.DetailDialog
import org.mmclark.koreloader.presentation.packages.Operation
import org.mmclark.koreloader.presentation.packages.PackageDetailUiState
import org.mmclark.koreloader.presentation.packages.PackageDetailViewModel
import org.mmclark.koreloader.ui.common.Busy
import org.mmclark.koreloader.ui.common.describe
import org.mmclark.koreloader.ui.common.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageDetailScreen(viewModel: PackageDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val definition = state.definition
    KeepScreenOn(state.busy)
    // Don't leave mid-install: the operation would keep running without its progress screen.
    BackHandler(enabled = state.busy) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(definition.name) },
                navigationIcon = { TextButton(onClick = onBack, enabled = !state.busy) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(state)
            Notices(state.definition.notices)
            if (state.loading && state.overview == null) Busy("Checking the Kobo and available versions…")

            when (val overview = state.overview) {
                is OverviewResult.DeviceProblem, is OverviewResult.VolumeUnavailable ->
                    Text("Can't read the Kobo. Is it still connected?", color = MaterialTheme.colorScheme.error)
                is OverviewResult.Ready -> {
                    if (overview.koboUpdatePending && state.operation == null) PendingUpdateNote()
                    val compatibility = overview.compatibility
                    if (compatibility is DeviceCompatibility.Unsupported) {
                        compatibility.reasons.forEach { Text("✗ ${it.describe()}", color = MaterialTheme.colorScheme.error) }
                    }
                    StatusCard(state)
                    when (val operation = state.operation) {
                        null -> Actions(state, viewModel)
                        is Operation.Install -> InstallCard(operation, onDone = viewModel::onDismissResult)
                        is Operation.Delete -> DeleteCard(operation.progress, definition.id, onDone = viewModel::onDismissResult)
                    }
                }
                null -> Unit
            }
            Text("", modifier = Modifier.padding(bottom = 16.dp))
        }
    }

    when (val dialog = state.dialog) {
        is DetailDialog.ConfirmUnverified -> UnverifiedDialog(dialog.release, viewModel::onConfirmUnverified, viewModel::onDismissDialog)
        is DetailDialog.ConfirmDelete -> DeleteDialog(
            name = definition.name,
            dialog = dialog,
            onRemoveUserDataChanged = viewModel::onRemoveUserDataChanged,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissDialog,
        )
        null -> Unit
    }
}

@Composable
private fun Header(state: PackageDetailUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(logoFor(state.definition.id)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp),
        )
        Text(state.definition.summary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Notices(notices: List<String>) {
    notices.forEach { notice ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Text("ⓘ  $notice", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusCard(state: PackageDetailUiState) {
    val status = state.status ?: return
    val latest = state.releases.firstOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(status.label())
            when (val releases = state.packageOverview?.releases) {
                is ReleasesLookup.Found -> Text(
                    "Latest available: " + (latest?.let { "${it.version}${it.publishedOn?.let { d -> " ($d)" } ?: ""}" } ?: "none found"),
                )
                is ReleasesLookup.Unavailable -> Text("Couldn't load versions: ${releases.message}", color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
            if (status is PackageStatus.Installed && status.needsReinstall) {
                Text("The Kobo's firmware changed since KFMon was installed, which disables it. Reinstall it to launch KOReader and Plato again.")
            }
            if (state.definition.id == PackageId.KFMON) {
                Text(
                    "Only the current KFMon build is published, so older versions can't be installed.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Actions(state: PackageDetailUiState, viewModel: PackageDetailViewModel) {
    val releases = state.releases
    val selected = state.selectedRelease
    val status = state.status
    val installedVersion = (status as? PackageStatus.Installed)?.version

    if (releases.size > 1) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.let { releaseLabel(it, releases, installedVersion) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Version") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                releases.forEach { release ->
                    DropdownMenuItem(
                        text = { Text(releaseLabel(release, releases, installedVersion)) },
                        onClick = {
                            viewModel.onSelectVersion(release.version)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    if (selected != null) {
        Text(
            "Download: ${formatBytes(selected.artifact.sizeBytes)}" +
                if (selected.isVerifiable) " · checksum published" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!selected.isVerifiable) {
            Text(
                "⚠ No checksum was published for ${selected.version}: only its size can be checked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    state.packageOverview?.dependenciesToInstall?.takeIf { it.isNotEmpty() }?.let { dependencies ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(
                "${dependencies.joinToString { it.name }} will be installed too: ${state.definition.name} needs it to launch. " +
                    "Your Kobo will restart after you unplug it.",
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    Button(onClick = viewModel::onInstall, enabled = state.canInstall, modifier = Modifier.fillMaxWidth()) {
        Text(installLabel(selected, releases, installedVersion))
    }
    if (status is PackageStatus.Installed) {
        OutlinedButton(
            onClick = viewModel::onDelete,
            enabled = state.canDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Delete ${state.definition.name}") }
    }
}

private fun releaseLabel(release: PackageRelease, releases: List<PackageRelease>, installed: String?): String = buildList {
    add(release.version)
    release.publishedOn?.let(::add)
    if (release == releases.firstOrNull()) add("latest")
    if (release.version == installed) add("installed")
    if (!release.isVerifiable) add("no checksum")
}.joinToString(" · ")

private fun installLabel(selected: PackageRelease?, releases: List<PackageRelease>, installed: String?): String {
    if (selected == null) return "Install"
    val selectedIndex = releases.indexOf(selected)
    val installedIndex = installed?.let { v -> releases.indexOfFirst { it.version == v } } ?: -1
    return when {
        installed == null -> "Install ${selected.version}"
        selected.version == installed -> "Reinstall ${selected.version}"
        installedIndex >= 0 && selectedIndex < installedIndex -> "Update to ${selected.version}"
        installedIndex >= 0 && selectedIndex > installedIndex -> "Install older version ${selected.version}"
        else -> "Install ${selected.version}"
    }
}

@Composable
private fun InstallCard(operation: Operation.Install, onDone: () -> Unit) {
    when (val progress = operation.progress) {
        is InstallProgress.Succeeded -> ResultCard(
            title = "Installed ${progress.installed.joinToString { "${Packages.get(it.packageId).name} ${it.version}" }}",
            koboUpdateRequired = progress.koboUpdateRequired,
            launchable = progress.installed.map { Packages.get(it.packageId) }.filter { it.id != PackageId.KFMON }.joinToString(" or ") { it.name },
            onDone = onDone,
        )
        is InstallProgress.Failed -> FailureCard(
            message = progress.error.describe(),
            leftovers = progress.leftovers.map { it.toString() },
            onDone = onDone,
        )
        else -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Installing… keep the Kobo connected", style = MaterialTheme.typography.titleMedium)
                InstallProgressBody(progress)
            }
        }
    }
}

@Composable
private fun InstallProgressBody(progress: InstallProgress) {
    when (progress) {
        InstallProgress.CheckingDevice -> Busy("Checking the Kobo…")
        is InstallProgress.Downloading -> {
            val name = Packages.get(progress.packageId).name
            val total = progress.bytesTotal
            val part = if (progress.parts > 1) " (${progress.part} of ${progress.parts})" else ""
            Text(
                if (total != null) "Downloading $name$part: ${formatBytes(progress.bytesDone)} of ${formatBytes(total)}"
                else "Downloading $name$part: ${formatBytes(progress.bytesDone)}",
            )
            if (total != null && total > 0) {
                LinearProgressIndicator(progress = { (progress.bytesDone.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is InstallProgress.VerifyingDownload -> Busy("Verifying ${Packages.get(progress.packageId).name}…")
        InstallProgress.PreparingDevice -> Busy("Preparing the Kobo…")
        is InstallProgress.Writing -> {
            Text("Copying file ${minOf(progress.filesDone + 1, progress.filesTotal)} of ${progress.filesTotal}")
            LinearProgressIndicator(
                progress = { if (progress.bytesTotal > 0) (progress.bytesDone.toFloat() / progress.bytesTotal).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            progress.currentFile?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
        }
        InstallProgress.VerifyingInstall -> Busy("Checking the installed files…")
        is InstallProgress.Succeeded, is InstallProgress.Failed -> Unit
    }
}

@Composable
private fun DeleteCard(progress: UninstallProgress, id: PackageId, onDone: () -> Unit) {
    when (progress) {
        is UninstallProgress.Succeeded -> ResultCard(
            title = if (progress.koboUpdateRequired) "${Packages.get(id).name} will be removed by the Kobo" else "${Packages.get(id).name} deleted",
            koboUpdateRequired = progress.koboUpdateRequired,
            launchable = "",
            onDone = onDone,
        )
        is UninstallProgress.Failed -> FailureCard(progress.message, emptyList(), onDone)
        is UninstallProgress.Removing -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Deleting…", style = MaterialTheme.typography.titleMedium)
                if (progress.total > 0) {
                    LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
                }
                progress.current?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, koboUpdateRequired: Boolean, launchable: String, onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("1.  Eject the Kobo from Android: tap the USB drive notification and choose Eject (or go to Settings › Storage).")
            Text("2.  Unplug the cable.")
            if (koboUpdateRequired) {
                Text("3.  The Kobo will look like it's importing a book, then restart to finish.")
                if (launchable.isNotEmpty()) Text("4.  After the restart, open $launchable from its icon in your Library, or from the menu on the Home screen.")
                Text("If the Kobo doesn't restart, it didn't work. Reconnect it and try again.", fontWeight = FontWeight.Bold)
            } else if (launchable.isNotEmpty()) {
                Text("3.  Open $launchable from its icon in your Library (it may take a moment to appear), or from the menu on the Home screen.")
            }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun FailureCard(message: String, leftovers: List<String>, onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("That didn't work", style = MaterialTheme.typography.titleMedium)
            Text(message)
            if (leftovers.isNotEmpty()) {
                Text(
                    "The Kobo won't install anything new when you unplug it. These package files are on the Kobo now; " +
                        "trying again will overwrite them:",
                )
                leftovers.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onDone) { Text("Back") }
        }
    }
}

@Composable
private fun UnverifiedDialog(release: PackageRelease, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install without a checksum?") },
        text = {
            Text(
                "No checksum was published for ${release.version}. koreloader can check the file's size, " +
                    "but can't prove the download is intact and genuine. It comes from the project's GitHub releases over HTTPS.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Install anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(
    name: String,
    dialog: DetailDialog.ConfirmDelete,
    onRemoveUserDataChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $name?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val preview = dialog.preview) {
                    null -> Busy("Checking what's installed…")
                    is RemovalPreview.OnDevice -> {
                        Text(
                            "Your Kobo removes KFMon itself after you eject and unplug it (it restarts). " +
                                "This also removes the KOReader and Plato launchers: their library icons and Home menu entries.",
                        )
                        Text("KOReader and Plato stay installed, but can't be started until KFMon is reinstalled.")
                        Text(
                            "NickelMenu stays installed. With its settings folder gone, its menu shows an error entry until KFMon is reinstalled.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (preview.removedOnDevice.isNotEmpty()) {
                            Text("Files the Kobo will delete:", fontWeight = FontWeight.Bold)
                            preview.removedOnDevice.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    is RemovalPreview.Files -> {
                        Text("Removes the $name program files and its launcher entries.")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = dialog.removeUserData,
                                role = Role.Checkbox,
                                onValueChange = onRemoveUserDataChanged,
                            ),
                        ) {
                            Checkbox(checked = dialog.removeUserData, onCheckedChange = null)
                            Text("Also delete settings and reading history", modifier = Modifier.padding(start = 8.dp))
                        }
                        if (dialog.removeUserData) {
                            Text(
                                "⚠ These will be permanently deleted:",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                            if (preview.userData.isEmpty()) {
                                Text("(no settings or history found)", style = MaterialTheme.typography.bodySmall)
                            }
                            preview.userData.forEach {
                                Text("•  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (name == Packages.KOREADER.name) {
                                Text(
                                    "Reading progress KOReader stores next to your books (.sdr folders) is not touched.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else if (preview.userData.isNotEmpty()) {
                            Text(
                                "Kept, so a reinstall picks them up: ${preview.userData.joinToString { it.name }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.preview != null) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
