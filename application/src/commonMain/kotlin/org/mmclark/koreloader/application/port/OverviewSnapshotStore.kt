package org.mmclark.koreloader.application.port

import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.packages.InstalledState

/**
 * The raw facts behind a package overview, as last seen for one Kobo volume. Statuses are
 * not stored: they are re-derived from these facts with the current domain rules.
 */
data class OverviewSnapshot(
    val device: KoboDevice,
    val koboUpdatePending: Boolean,
    val installed: Map<PackageId, InstalledState>,
    val releases: Map<PackageId, List<PackageRelease>>,
)

/** Port: persists the last [OverviewSnapshot] per volume (keyed by [VolumeRef.value]). */
interface OverviewSnapshotStore {
    suspend fun load(key: String): OverviewSnapshot?
    suspend fun save(key: String, snapshot: OverviewSnapshot)
}
