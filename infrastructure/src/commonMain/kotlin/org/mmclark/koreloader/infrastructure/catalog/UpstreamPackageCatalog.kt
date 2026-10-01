package org.mmclark.koreloader.infrastructure.catalog

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mmclark.koreloader.domain.catalog.PackageCatalog
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Where each package comes from: KFMon from NiLuJe's one-click manifest (it has no GitHub
 * releases), KOReader and Plato from their GitHub releases. Results are cached for [ttl] to
 * stay well inside GitHub's anonymous rate limit (60 requests per hour).
 */
class UpstreamPackageCatalog(
    private val ocp: OcpManifestCatalog,
    private val gitHub: GitHubReleasesCatalog,
    private val ttl: Duration = 15.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : PackageCatalog {

    private val mutex = Mutex()
    private val cache = HashMap<PackageId, Pair<TimeMark, List<PackageRelease>>>()

    override suspend fun releases(id: PackageId): List<PackageRelease> {
        mutex.withLock {
            cache[id]?.let { (fetchedAt, releases) -> if (fetchedAt.elapsedNow() < ttl) return releases }
        }
        val releases = fetch(id)
        mutex.withLock { cache[id] = timeSource.markNow() to releases }
        return releases
    }

    private suspend fun fetch(id: PackageId): List<PackageRelease> = when (id) {
        PackageId.KFMON -> ocp.releases(id, manifestName = "KFMon")
        PackageId.KOREADER -> gitHub.releases(id, "koreader/koreader") { tag -> "koreader-kobo-$tag.zip" }
        PackageId.PLATO -> gitHub.releases(id, "baskerville/plato") { tag -> "plato-$tag.zip" }
        else -> emptyList()
    }
}
