package org.mmclark.koreloader.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.domain.device.CompatibilityIssue
import org.mmclark.koreloader.domain.device.DeviceCompatibility

/** Returns a launcher for the SAF folder picker; the chosen tree gets persistent read/write access. */
@Composable
fun rememberKoboVolumePicker(onPicked: (VolumeRef) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistAccess(context, uri)
            onPicked(VolumeRef(uri.toString()))
        }
    }
    return { launcher.launch(null) }
}

/** Reuses a previously granted volume, once, if nothing is selected yet. */
@Composable
fun RestorePersistedVolume(current: VolumeRef?, onFound: (VolumeRef) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (current == null) {
            context.contentResolver.persistedUriPermissions
                .firstOrNull { it.isReadPermission && it.isWritePermission }
                ?.let { onFound(VolumeRef(it.uri.toString())) }
        }
    }
}

private fun persistAccess(context: Context, uri: Uri) {
    val resolver = context.contentResolver
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    resolver.persistedUriPermissions
        .filter { it.uri != uri }
        .forEach { resolver.releasePersistableUriPermission(it.uri, flags) }
    resolver.takePersistableUriPermission(uri, flags)
}

/** Shows what was found on the selected volume. */
@Composable
fun DeviceCard(volume: VolumeRef?, detection: DeviceDetection?) {
    if (volume == null) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (detection) {
                null -> Busy("Reading .kobo/version…")
                is DeviceDetection.VolumeUnavailable ->
                    Text("Can't read the drive: ${detection.message}. Is the Kobo still connected?")
                DeviceDetection.NotAKobo ->
                    Text("No .kobo folder here. Pick the root of the Kobo drive, not a folder inside it.")
                is DeviceDetection.VersionUnreadable ->
                    Text("Found .kobo, but couldn't read .kobo/version (${detection.contents ?: "missing"}).")
                is DeviceDetection.Detected -> {
                    Text(detection.device.model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("Firmware ${detection.device.firmware} · Serial ${detection.device.serial}")
                    CompatibilityText(detection.compatibility)
                }
            }
        }
    }
}

@Composable
private fun CompatibilityText(compatibility: DeviceCompatibility) {
    when (compatibility) {
        DeviceCompatibility.Supported -> Text("✓ Supported", color = MaterialTheme.colorScheme.primary)
        is DeviceCompatibility.SupportedWithWarnings ->
            compatibility.warnings.forEach { Text("⚠ ${it.describe()}") }
        is DeviceCompatibility.Unsupported ->
            compatibility.reasons.forEach { Text("✗ ${it.describe()}", color = MaterialTheme.colorScheme.error) }
    }
}

fun CompatibilityIssue.describe(): String = when (this) {
    is CompatibilityIssue.FirmwareTooNew ->
        "Firmware $firmware isn't supported yet (firmware 5.x needs a different KOReader build)."
    is CompatibilityIssue.FirmwareTooOld -> "Firmware $firmware is too old; $minimum or newer is required."
    is CompatibilityIssue.UnknownModel -> "Unknown model (ID ${model.id}). Installing should still work."
}

@Composable
fun Busy(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
