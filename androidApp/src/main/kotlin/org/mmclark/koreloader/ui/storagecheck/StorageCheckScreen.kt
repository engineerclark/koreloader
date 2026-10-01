package org.mmclark.koreloader.ui.storagecheck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.storagecheck.StorageCheckStep
import org.mmclark.koreloader.presentation.storagecheck.StorageCheckUiState
import org.mmclark.koreloader.presentation.storagecheck.StorageCheckViewModel
import org.mmclark.koreloader.ui.common.Busy
import org.mmclark.koreloader.ui.common.DeviceCard
import org.mmclark.koreloader.ui.common.RestorePersistedVolume
import org.mmclark.koreloader.ui.common.rememberKoboVolumePicker

/** Diagnostic screen (the SAF spike): exercises every volume operation the installer uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCheckScreen(viewModel: StorageCheckViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickVolume = rememberKoboVolumePicker(viewModel::onVolumeSelected)
    RestorePersistedVolume(state.volume, viewModel::onVolumeSelected)
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage test") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Instructions() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickVolume) {
                        Text(if (state.volume == null) "Choose Kobo drive" else "Choose another drive")
                    }
                    if (state.volume != null) {
                        OutlinedButton(onClick = viewModel::onRefresh, enabled = !state.detecting) { Text("Re-check") }
                    }
                }
            }
            item { DeviceCard(state.volume, state.detection) }
            if (state.detection is DeviceDetection.Detected) {
                item {
                    Button(onClick = viewModel::onRunCheck, enabled = state.canRunCheck) {
                        Text("Run storage test")
                    }
                }
            }
            items(state.steps) { StepRow(it) }
            if (state.checkRunning) item { Busy("Testing…") }
            if (state.checkFinished) item { Summary(state) }
        }
    }
}

@Composable
private fun Instructions() {
    Text(
        "Checks that this phone can write to the Kobo the way the installer needs. " +
            "It writes files into .adds/koreloader-test and deletes them afterwards.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun StepRow(step: StorageCheckStep) {
    Column {
        when (val outcome = step.outcome) {
            is StorageCheckStep.Outcome.Passed -> {
                Text("✓ ${step.name}")
                outcome.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            is StorageCheckStep.Outcome.Failed -> {
                Text("✗ ${step.name}", color = MaterialTheme.colorScheme.error)
                Text(outcome.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Summary(state: StorageCheckUiState) {
    Text(
        if (state.checkPassed) "All checks passed. SAF works with hidden folders on this phone."
        else "A check failed. The test folder was removed (if possible). Please share the error above.",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 24.dp),
    )
}
