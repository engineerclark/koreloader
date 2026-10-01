package org.mmclark.koreloader.presentation.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mmclark.koreloader.application.packages.ObservePackageOverview
import org.mmclark.koreloader.application.packages.OverviewResult
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.port.VolumeRef

data class PackagesUiState(
    /** A background refresh is running (the cached overview, if any, is already shown). */
    val loading: Boolean = true,
    /** The cached overview first, then the current one; kept while refreshing so tiles don't flicker. */
    val overview: OverviewResult? = null,
    /** The overview is the last known state, not confirmed against the Kobo (yet). */
    val fromCache: Boolean = false,
    /** Why the last refresh couldn't confirm the cached overview (e.g. the Kobo is unplugged). */
    val refreshProblem: String? = null,
)

/** The package grid: one tile per package with its install/update status. */
class PackagesViewModel(
    private val volume: VolumeRef,
    private val volumeFactory: KoboVolumeFactory,
    private val observePackageOverview: ObservePackageOverview,
) : ViewModel() {

    private val _state = MutableStateFlow(PackagesUiState())
    val state: StateFlow<PackagesUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        // Start at creation, not when the screen resumes (after the navigation animation): with
        // the cache in memory, the very first frame already shows the tiles.
        refresh()
    }

    /** Shows the cached overview, then refreshes it; also called when returning from a detail screen. */
    fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            // A fresh volume per load: the Kobo may have changed since (another screen installed, or Nickel processed an update).
            observePackageOverview(volume, volumeFactory.open(volume)).collect { update ->
                _state.update {
                    it.copy(overview = update.result, fromCache = update.fromCache, refreshProblem = update.refreshProblem)
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }
}
