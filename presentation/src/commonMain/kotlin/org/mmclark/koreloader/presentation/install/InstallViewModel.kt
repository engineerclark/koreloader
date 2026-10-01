package org.mmclark.koreloader.presentation.install

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
import org.mmclark.koreloader.application.install.FetchPackageRelease
import org.mmclark.koreloader.application.install.InstallPackage
import org.mmclark.koreloader.application.install.InstallProgress
import org.mmclark.koreloader.application.install.ReleaseLookup
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.device.DeviceCompatibility

data class InstallUiState(
    val packageId: PackageId,
    val release: ReleaseLookup? = null,
    val volume: VolumeRef? = null,
    val detecting: Boolean = false,
    val detection: DeviceDetection? = null,
    /** null until an install is started; stays at the final Succeeded/Failed value afterwards. */
    val progress: InstallProgress? = null,
) {
    val installing: Boolean get() = progress != null && !progress.isFinished

    val deviceReady: Boolean
        get() = (detection as? DeviceDetection.Detected)?.compatibility.let {
            it != null && it !is DeviceCompatibility.Unsupported
        }

    val canInstall: Boolean
        get() = release is ReleaseLookup.Found && deviceReady && !detecting && !installing
}

/** Milestone 1 screen: install one hardcoded package (the KOReader one-click package). */
class InstallViewModel(
    private val packageId: PackageId,
    private val volumeFactory: KoboVolumeFactory,
    private val fetchPackageRelease: FetchPackageRelease,
    private val detectKoboDevice: DetectKoboDevice,
    private val installPackage: InstallPackage,
) : ViewModel() {

    private val _state = MutableStateFlow(InstallUiState(packageId))
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    private var volume: KoboVolume? = null
    private var detectJob: Job? = null
    private var installJob: Job? = null

    init {
        loadRelease()
    }

    fun onRetryRelease() = loadRelease()

    fun onVolumeSelected(ref: VolumeRef) {
        if (_state.value.installing) return
        volume = volumeFactory.open(ref)
        _state.update { it.copy(volume = ref, detection = null, progress = null) }
        detect()
    }

    fun onRefreshDevice() {
        if (volume != null && !_state.value.installing) detect()
    }

    fun onInstall() {
        val current = _state.value
        val release = (current.release as? ReleaseLookup.Found)?.release ?: return
        val target = volume ?: return
        if (!current.canInstall) return
        installJob = viewModelScope.launch {
            installPackage(target, release).collect { progress ->
                _state.update { it.copy(progress = progress) }
            }
        }
    }

    /** Leaves the success/failure result and returns to the ready state. */
    fun onDismissResult() {
        if (_state.value.installing) return
        _state.update { it.copy(progress = null) }
        detect()
    }

    private fun loadRelease() {
        _state.update { it.copy(release = null) }
        viewModelScope.launch {
            val lookup = fetchPackageRelease(packageId)
            _state.update { it.copy(release = lookup) }
        }
    }

    private fun detect() {
        val target = volume ?: return
        detectJob?.cancel()
        _state.update { it.copy(detecting = true) }
        detectJob = viewModelScope.launch {
            val detection = detectKoboDevice(target)
            _state.update { it.copy(detecting = false, detection = detection) }
        }
    }
}
