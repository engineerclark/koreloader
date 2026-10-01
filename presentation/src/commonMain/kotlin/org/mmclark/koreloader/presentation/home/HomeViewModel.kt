package org.mmclark.koreloader.presentation.home

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
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.domain.device.DeviceCompatibility

data class HomeUiState(
    val volume: VolumeRef? = null,
    val detecting: Boolean = false,
    val detection: DeviceDetection? = null,
) {
    /** Packages can be managed on any recognised Kobo; unsupported firmware still allows viewing and deleting. */
    val canManagePackages: Boolean get() = detection is DeviceDetection.Detected && !detecting

    val firmwareUnsupported: Boolean
        get() = (detection as? DeviceDetection.Detected)?.compatibility is DeviceCompatibility.Unsupported
}

/** Main screen: choose the Kobo drive and show the detected device. */
class HomeViewModel(
    private val volumeFactory: KoboVolumeFactory,
    private val detectKoboDevice: DetectKoboDevice,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onVolumeSelected(ref: VolumeRef) {
        _state.value = HomeUiState(volume = ref)
        detect()
    }

    fun onRefresh() = detect()

    private fun detect() {
        val ref = _state.value.volume ?: return
        val volume = volumeFactory.open(ref)
        job?.cancel()
        _state.update { it.copy(detecting = true) }
        job = viewModelScope.launch {
            val detection = detectKoboDevice(volume)
            _state.update { it.copy(detecting = false, detection = detection) }
        }
    }
}
