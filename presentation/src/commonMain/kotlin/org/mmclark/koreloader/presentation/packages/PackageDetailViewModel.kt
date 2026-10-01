package org.mmclark.koreloader.presentation.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mmclark.koreloader.application.install.InstallPackage
import org.mmclark.koreloader.application.install.InstallProgress
import org.mmclark.koreloader.application.packages.ObservePackageOverview
import org.mmclark.koreloader.application.packages.OverviewResult
import org.mmclark.koreloader.application.packages.PackageOverview
import org.mmclark.koreloader.application.packages.ReleasesLookup
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.application.uninstall.RemovalPreview
import org.mmclark.koreloader.application.uninstall.UninstallPackage
import org.mmclark.koreloader.application.uninstall.UninstallProgress
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.packages.PackageDefinition
import org.mmclark.koreloader.domain.packages.PackageStatus
import org.mmclark.koreloader.domain.packages.Packages

/** A running or finished install/delete. */
sealed interface Operation {
    data class Install(val release: PackageRelease, val progress: InstallProgress) : Operation
    data class Delete(val progress: UninstallProgress) : Operation

    val isFinished: Boolean
        get() = when (this) {
            is Install -> progress.isFinished
            is Delete -> progress.isFinished
        }
}

sealed interface DetailDialog {
    /** Installing a release without a published checksum needs explicit consent. */
    data class ConfirmUnverified(val release: PackageRelease) : DetailDialog

    /** Delete confirmation; [removeUserData] is the opt-in (off by default) to also delete settings and history. */
    data class ConfirmDelete(val preview: RemovalPreview?, val removeUserData: Boolean = false) : DetailDialog
}

data class PackageDetailUiState(
    val definition: PackageDefinition,
    /** A background refresh is running (the cached overview, if any, is already shown). */
    val loading: Boolean = true,
    /** The overview is the last known state, not confirmed against the Kobo (yet). */
    val fromCache: Boolean = false,
    /** Why the last refresh couldn't confirm the cached overview (e.g. the Kobo is unplugged). */
    val refreshProblem: String? = null,
    val overview: OverviewResult? = null,
    /** Version chosen in the dropdown; null means the latest. */
    val selectedVersion: String? = null,
    val operation: Operation? = null,
    val dialog: DetailDialog? = null,
) {
    val packageOverview: PackageOverview? get() = (overview as? OverviewResult.Ready)?.get(definition.id)

    val releases: List<PackageRelease>
        get() = (packageOverview?.releases as? ReleasesLookup.Found)?.releases.orEmpty()

    val selectedRelease: PackageRelease?
        get() = releases.firstOrNull { it.version == selectedVersion } ?: releases.firstOrNull()

    val status: PackageStatus? get() = packageOverview?.status

    val busy: Boolean get() = operation?.isFinished == false

    private val firmwareSupported: Boolean
        get() = (overview as? OverviewResult.Ready)?.compatibility.let { it != null && it !is DeviceCompatibility.Unsupported }

    /** Installing and deleting wait for the refresh, so they never act on stale (cached) state. */
    private val confirmed: Boolean get() = !loading && !fromCache

    val canInstall: Boolean get() = confirmed && !busy && selectedRelease != null && firmwareSupported && operation == null
    val canDelete: Boolean
        get() = confirmed && !busy && operation == null && (status as? PackageStatus.Installed)?.removalPending == false
}

/** Detail screen for one package: versions, install/reinstall/update, and delete. */
class PackageDetailViewModel(
    packageId: PackageId,
    private val volumeRef: VolumeRef,
    private val volumeFactory: KoboVolumeFactory,
    private val observePackageOverview: ObservePackageOverview,
    private val installPackage: InstallPackage,
    private val uninstallPackage: UninstallPackage,
) : ViewModel() {

    /** Opens the volume afresh for each operation, so no file listing outlives it. */
    private val volume get() = volumeFactory.open(volumeRef)
    private val _state = MutableStateFlow(PackageDetailUiState(Packages.get(packageId)))
    val state: StateFlow<PackageDetailUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.busy) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            observePackageOverview(volumeRef, volume).collect { update ->
                _state.update {
                    it.copy(overview = update.result, fromCache = update.fromCache, refreshProblem = update.refreshProblem)
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun onSelectVersion(version: String) {
        _state.update { it.copy(selectedVersion = version) }
    }

    fun onInstall() {
        val state = _state.value
        val release = state.selectedRelease ?: return
        if (!state.canInstall) return
        if (!release.isVerifiable) {
            _state.update { it.copy(dialog = DetailDialog.ConfirmUnverified(release)) }
        } else {
            startInstall(release)
        }
    }

    fun onConfirmUnverified() {
        val dialog = _state.value.dialog as? DetailDialog.ConfirmUnverified ?: return
        _state.update { it.copy(dialog = null) }
        startInstall(dialog.release)
    }

    fun onDelete() {
        if (!_state.value.canDelete) return
        _state.update { it.copy(dialog = DetailDialog.ConfirmDelete(preview = null)) }
        viewModelScope.launch {
            val preview = runCatching { uninstallPackage.preview(volume, _state.value.definition.id) }.getOrNull()
            _state.update { current ->
                val dialog = current.dialog as? DetailDialog.ConfirmDelete ?: return@update current
                current.copy(dialog = dialog.copy(preview = preview))
            }
        }
    }

    fun onRemoveUserDataChanged(remove: Boolean) {
        _state.update { current ->
            val dialog = current.dialog as? DetailDialog.ConfirmDelete ?: return@update current
            current.copy(dialog = dialog.copy(removeUserData = remove))
        }
    }

    fun onConfirmDelete() {
        val dialog = _state.value.dialog as? DetailDialog.ConfirmDelete ?: return
        _state.update { it.copy(dialog = null, operation = Operation.Delete(UninstallProgress.Removing(0, 0, null))) }
        job = viewModelScope.launch {
            uninstallPackage(volume, _state.value.definition.id, dialog.removeUserData).collect { progress ->
                _state.update { it.copy(operation = Operation.Delete(progress)) }
            }
            refresh() // the result stays on screen; the status above it catches up
        }
    }

    fun onDismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    /** Leaves a finished install/delete result and reloads the status. */
    fun onDismissResult() {
        if (_state.value.busy) return
        _state.update { it.copy(operation = null, selectedVersion = null) }
        refresh()
    }

    private fun startInstall(release: PackageRelease) {
        _state.update { it.copy(operation = Operation.Install(release, InstallProgress.CheckingDevice)) }
        job = viewModelScope.launch {
            installPackage(volume, release).collect { progress ->
                _state.update { it.copy(operation = Operation.Install(release, progress)) }
            }
            refresh() // the result stays on screen; the status above it catches up
        }
    }
}
