package org.mmclark.koreloader.application.packages

import kotlinx.coroutines.CancellationException
import org.mmclark.koreloader.domain.catalog.PackageCatalog
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease

sealed interface ReleasesLookup {
    /** Releases, newest first (may be empty). */
    data class Found(val releases: List<PackageRelease>) : ReleasesLookup
    data class Unavailable(val message: String) : ReleasesLookup
}

/** Lists the installable releases of a package. */
class FetchReleases(private val catalog: PackageCatalog) {

    suspend operator fun invoke(id: PackageId): ReleasesLookup = try {
        ReleasesLookup.Found(catalog.releases(id))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReleasesLookup.Unavailable(e.message ?: e::class.simpleName ?: "catalog error")
    }
}
