package org.mmclark.koreloader.application.packages

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.packages.InstalledState
import org.mmclark.koreloader.domain.packages.PackageDefinition
import org.mmclark.koreloader.domain.packages.PackageStatus
import org.mmclark.koreloader.domain.packages.PackageStatusPolicy
import org.mmclark.koreloader.domain.packages.Packages

data class PackageOverview(
    val definition: PackageDefinition,
    val releases: ReleasesLookup,
    val state: InstalledState,
    val status: PackageStatus,
    /** Dependencies that installing this package will (re)install first. */
    val dependenciesToInstall: List<PackageDefinition>,
)

sealed interface OverviewResult {
    data class Ready(
        val device: KoboDevice,
        val compatibility: DeviceCompatibility,
        val koboUpdatePending: Boolean,
        val packages: List<PackageOverview>,
    ) : OverviewResult {
        fun get(id: PackageId): PackageOverview = packages.first { it.definition.id == id }
    }

    data class DeviceProblem(val detection: DeviceDetection) : OverviewResult
    data class VolumeUnavailable(val message: String) : OverviewResult
}

/** Everything the package screens show: device, what's installed, available releases and statuses. */
class GetPackageOverview(
    private val detectKoboDevice: DetectKoboDevice,
    private val inspectVolume: InspectVolume,
    private val fetchReleases: FetchReleases,
) {
    suspend operator fun invoke(volume: KoboVolume): OverviewResult = coroutineScope {
        val releases = Packages.all.associate { it.id to async { fetchReleases(it.id) } }
        val detection = detectKoboDevice(volume)
        if (detection !is DeviceDetection.Detected) return@coroutineScope OverviewResult.DeviceProblem(detection)
        val device = detection.device
        val inventory = when (val result = inspectVolume(volume)) {
            is InventoryResult.Ready -> result.inventory
            is InventoryResult.Unavailable -> return@coroutineScope OverviewResult.VolumeUnavailable(result.message)
        }
        releases.values.awaitAll()

        val statuses = Packages.all.associate { definition ->
            val state = inventory.installed[definition.id] ?: InstalledState.NotInstalled
            val lookup = releases.getValue(definition.id).await()
            val list = (lookup as? ReleasesLookup.Found)?.releases.orEmpty()
            definition.id to Triple(lookup, state, PackageStatusPolicy.status(state, list, device.firmware))
        }
        val overviews = Packages.all.map { definition ->
            val (lookup, state, status) = statuses.getValue(definition.id)
            PackageOverview(
                definition = definition,
                releases = lookup,
                state = state,
                status = status,
                dependenciesToInstall = definition.dependencies
                    .filter { PackageStatusPolicy.dependencyNeedsInstall(statuses.getValue(it).third) }
                    .map(Packages::get),
            )
        }
        OverviewResult.Ready(device, detection.compatibility, inventory.koboUpdatePending, overviews)
    }
}
