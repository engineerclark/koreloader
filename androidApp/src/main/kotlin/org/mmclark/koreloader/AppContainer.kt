package org.mmclark.koreloader

import android.content.Context
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.install.DownloadPackage
import org.mmclark.koreloader.application.install.FetchPackageRelease
import org.mmclark.koreloader.application.install.InstallPackage
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.storagecheck.RunStorageCheck
import org.mmclark.koreloader.infrastructure.archive.ZipFileArchiveReader
import org.mmclark.koreloader.infrastructure.hash.FileChecksumCalculator
import org.mmclark.koreloader.infrastructure.http.NetworkAdapters
import org.mmclark.koreloader.infrastructure.storage.SafKoboVolumeFactory
import java.io.File

/** Composition root: wires infrastructure adapters into application use cases. */
class AppContainer(context: Context) {

    private val network = NetworkAdapters(
        userAgent = "koreloader/${BuildConfig.VERSION_NAME}",
        downloadDirectory = File(context.cacheDir, "packages"),
    )

    val volumeFactory: KoboVolumeFactory = SafKoboVolumeFactory(context)
    val detectKoboDevice = DetectKoboDevice()
    val runStorageCheck = RunStorageCheck()

    val fetchPackageRelease = FetchPackageRelease(network.packageCatalog)
    val installPackage = InstallPackage(
        detectKoboDevice = detectKoboDevice,
        downloadPackage = DownloadPackage(network.packageDownloader, FileChecksumCalculator()),
        archiveReader = ZipFileArchiveReader(),
    )
}
