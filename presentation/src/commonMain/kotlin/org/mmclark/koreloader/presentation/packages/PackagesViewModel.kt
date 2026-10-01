package org.mmclark.koreloader.presentation.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mmclark.koreloader.application.packages.GetPackageOverview
import org.mmclark.koreloader.application.packages.OverviewResult
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.port.VolumeRef

data class PackagesUiState(
    val loading: Boolean = true,
    /** The last loaded overview; kept while refreshing so tiles don't flicker. */
    val overview: OverviewResult? = null,
)

/** The package grid: one tile per package with its install/update status. */
class PackagesViewModel(
    private val volume: VolumeRef,
    private val volumeFactory: KoboVolumeFactory,
    private val getPackageOverview: GetPackageOverview,
) : ViewModel() {

    private val _state = MutableStateFlow(PackagesUiState())
    val state: StateFlow<PackagesUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** Loads statuses; the screen calls this whenever it is shown (also after returning from a detail screen). */
    fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            // A fresh volume per load: the Kobo may have changed since (another screen installed, or Nickel processed an update).
            val overview = getPackageOverview(volumeFactory.open(volume))
            _state.update { it.copy(loading = false, overview = overview) }
        }
    }
}
