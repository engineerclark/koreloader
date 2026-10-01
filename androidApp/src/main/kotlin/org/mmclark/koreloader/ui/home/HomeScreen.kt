package org.mmclark.koreloader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mmclark.koreloader.presentation.home.HomeViewModel
import org.mmclark.koreloader.ui.common.DeviceCard
import org.mmclark.koreloader.ui.common.RestorePersistedVolume
import org.mmclark.koreloader.ui.common.rememberKoboVolumePicker

/** Main screen: choose the Kobo drive, show the device, then go to package management. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onManagePackages: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickVolume = rememberKoboVolumePicker(viewModel::onVolumeSelected)
    RestorePersistedVolume(state.volume, viewModel::onVolumeSelected)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("koreloader") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.volume == null) {
                Text("Connect your Kobo", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Plug the Kobo into this phone with a USB OTG cable and tap \"Connect\" on the Kobo. " +
                        "Then choose the root of the Kobo drive (usually labelled KOBOeReader).",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = pickVolume, modifier = Modifier.fillMaxWidth()) { Text("Choose Kobo drive") }
            } else {
                DeviceCard(state.volume, state.detection, prominent = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pickVolume) { Text("Choose another drive") }
                    OutlinedButton(onClick = viewModel::onRefresh, enabled = !state.detecting) { Text("Re-check") }
                }
                if (state.firmwareUnsupported) {
                    Text(
                        "You can still view packages, but installing needs a supported firmware.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = onManagePackages,
                    enabled = state.canManagePackages,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Manage Kobo packages") }
            }
            AuthorLine(Modifier.padding(top = 16.dp))
            CreditsCard()
        }
    }
}
