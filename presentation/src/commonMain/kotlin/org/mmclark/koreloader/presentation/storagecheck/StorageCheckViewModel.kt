package org.mmclark.koreloader.presentation.storagecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.application.storagecheck.RunStorageCheck
import org.mmclark.koreloader.application.storagecheck.StorageCheckStep

data class StorageCheckUiState(
    val volume: VolumeRef? = null,
    val detecting: Boolean = false,
    val detection: DeviceDetection? = null,
    val checkRunning: Boolean = false,
    val steps: List<StorageCheckStep> = emptyList(),
) {
    val canRunCheck: Boolean get() = detection is DeviceDetection.Detected && !checkRunning && !detecting
    val checkFinished: Boolean get() = steps.isNotEmpty() && !checkRunning
    val checkPassed: Boolean get() = checkFinished && steps.all { it.outcome is StorageCheckStep.Outcome.Passed }
}

/** Spike screen: pick the Kobo volume, identify the device, and run the storage check. */
class StorageCheckViewModel(
    private val volumeFactory: KoboVolumeFactory,
    private val detectKoboDevice: DetectKoboDevice,
    private val runStorageCheck: RunStorageCheck,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageCheckUiState())
    val state: StateFlow<StorageCheckUiState> = _state.asStateFlow()

    private var volume: KoboVolume? = null
    private var job: Job? = null

    fun onVolumeSelected(ref: VolumeRef) {
        job?.cancel()
        val opened = volumeFactory.open(ref)
        volume = opened
        _state.value = StorageCheckUiState(volume = ref, detecting = true)
        job = viewModelScope.launch {
            val detection = detectKoboDevice(opened)
            _state.update { it.copy(detecting = false, detection = detection) }
        }
    }

    fun onRefresh() {
        _state.value.volume?.let(::onVolumeSelected)
    }

    fun onRunCheck() {
        val opened = volume ?: return
        if (!_state.value.canRunCheck) return
        _state.update { it.copy(checkRunning = true, steps = emptyList()) }
        job = viewModelScope.launch {
            try {
                runStorageCheck(opened).collect { step ->
                    _state.update { it.copy(steps = it.steps + step) }
                }
            } finally {
                _state.update { it.copy(checkRunning = false) }
            }
        }
    }
}
