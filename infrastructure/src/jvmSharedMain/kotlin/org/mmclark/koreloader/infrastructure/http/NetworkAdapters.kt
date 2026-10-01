package org.mmclark.koreloader.infrastructure.http

import org.mmclark.koreloader.application.port.PackageDownloader
import org.mmclark.koreloader.domain.catalog.PackageCatalog
import org.mmclark.koreloader.infrastructure.catalog.GitHubReleasesCatalog
import org.mmclark.koreloader.infrastructure.catalog.OcpManifestCatalog
import org.mmclark.koreloader.infrastructure.catalog.UpstreamPackageCatalog
import org.mmclark.koreloader.infrastructure.download.CachingFileDownloader
import java.io.File

/**
 * The network-backed adapters, sharing one HTTP client. Only port types are exposed, so the
 * composition root doesn't depend on the HTTP library.
 */
class NetworkAdapters(userAgent: String, downloadDirectory: File) {
    private val client = createHttpClient(userAgent)

    val packageCatalog: PackageCatalog = UpstreamPackageCatalog(OcpManifestCatalog(client), GitHubReleasesCatalog(client))
    val packageDownloader: PackageDownloader = CachingFileDownloader(client, downloadDirectory)
}
