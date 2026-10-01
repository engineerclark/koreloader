package org.mmclark.koreloader.application.install

import kotlinx.coroutines.CancellationException
import org.mmclark.koreloader.domain.catalog.PackageCatalog
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease

sealed interface ReleaseLookup {
    data class Found(val release: PackageRelease) : ReleaseLookup
    data object NotFound : ReleaseLookup
    data class Unavailable(val message: String) : ReleaseLookup
}

/** Looks up the latest release of a package in the catalog. */
class FetchPackageRelease(private val catalog: PackageCatalog) {

    suspend operator fun invoke(id: PackageId): ReleaseLookup = try {
        catalog.latestRelease(id)?.let { ReleaseLookup.Found(it) } ?: ReleaseLookup.NotFound
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReleaseLookup.Unavailable(e.message ?: e::class.simpleName ?: "catalog error")
    }
}
