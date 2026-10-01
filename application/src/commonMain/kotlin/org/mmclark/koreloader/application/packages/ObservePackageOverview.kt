package org.mmclark.koreloader.application.packages

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.OverviewSnapshot
import org.mmclark.koreloader.application.port.OverviewSnapshotStore
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.domain.device.DeviceCompatibilityPolicy

/**
 * One emission of [ObservePackageOverview]. [fromCache] is true while the overview is the
 * last known state rather than what is on the Kobo right now; [refreshProblem] explains why a
 * refresh couldn't replace it (e.g. the Kobo was unplugged).
 */
data class OverviewUpdate(
    val result: OverviewResult,
    val fromCache: Boolean,
    val refreshProblem: String? = null,
)

/**
 * Stale-while-revalidate package overview: emits the cached overview for [VolumeRef] at once
 * (if there is one), then loads the current one and emits it, saving it for next time.
 *
 * Release lists that can't be fetched now (offline) are taken from the cache, so a refresh
 * never makes known versions disappear.
 */
class ObservePackageOverview(
    private val getPackageOverview: GetPackageOverview,
    private val store: OverviewSnapshotStore,
) {
    operator fun invoke(ref: VolumeRef, volume: KoboVolume): Flow<OverviewUpdate> = flow {
        val cached = safely { store.load(ref.value) }
        val cachedOverview = cached?.let(::restore)
        if (cachedOverview != null) emit(OverviewUpdate(cachedOverview, fromCache = true))

        when (val fresh = getPackageOverview(volume)) {
            is OverviewResult.Ready -> {
                val merged = withCachedReleases(fresh, cached)
                safely { store.save(ref.value, snapshotOf(merged)) }
                emit(OverviewUpdate(merged, fromCache = false))
            }
            else -> if (cachedOverview != null) {
                emit(OverviewUpdate(cachedOverview, fromCache = true, refreshProblem = describe(fresh)))
            } else {
                emit(OverviewUpdate(fresh, fromCache = false))
            }
        }
    }

    private fun restore(snapshot: OverviewSnapshot): OverviewResult.Ready = GetPackageOverview.assemble(
        device = snapshot.device,
        compatibility = DeviceCompatibilityPolicy.evaluate(snapshot.device),
        koboUpdatePending = snapshot.koboUpdatePending,
        installed = snapshot.installed,
        releases = snapshot.releases.mapValues { ReleasesLookup.Found(it.value) },
    )

    private fun withCachedReleases(fresh: OverviewResult.Ready, cached: OverviewSnapshot?): OverviewResult.Ready {
        if (cached == null || fresh.packages.all { it.releases is ReleasesLookup.Found }) return fresh
        val releases = fresh.packages.associate { pkg ->
            val id = pkg.definition.id
            id to when (val lookup = pkg.releases) {
                is ReleasesLookup.Found -> lookup
                is ReleasesLookup.Unavailable -> cached.releases[id]?.let { ReleasesLookup.Found(it) } ?: lookup
            }
        }
        return GetPackageOverview.assemble(
            fresh.device, fresh.compatibility, fresh.koboUpdatePending,
            fresh.packages.associate { it.definition.id to it.state }, releases,
        )
    }

    private fun snapshotOf(overview: OverviewResult.Ready) = OverviewSnapshot(
        device = overview.device,
        koboUpdatePending = overview.koboUpdatePending,
        installed = overview.packages.associate { it.definition.id to it.state },
        releases = overview.packages.mapNotNull { pkg ->
            (pkg.releases as? ReleasesLookup.Found)?.let { pkg.definition.id to it.releases }
        }.toMap(),
    )

    private fun describe(result: OverviewResult): String = when (result) {
        is OverviewResult.DeviceProblem -> when (result.detection) {
            is DeviceDetection.VolumeUnavailable, is DeviceDetection.NotAKobo -> "The Kobo isn't connected."
            else -> "The Kobo couldn't be identified."
        }
        is OverviewResult.VolumeUnavailable -> "The Kobo isn't connected."
        is OverviewResult.Ready -> ""
    }

    /** The cache is an optimisation: never let it break loading. */
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
