package org.mmclark.koreloader

import android.content.Context
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.install.DownloadPackage
import org.mmclark.koreloader.application.install.InstallPackage
import org.mmclark.koreloader.application.packages.FetchReleases
import org.mmclark.koreloader.application.packages.GetPackageOverview
import org.mmclark.koreloader.application.packages.InspectVolume
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.uninstall.UninstallPackage
import org.mmclark.koreloader.infrastructure.archive.ZipFileArchiveReader
import org.mmclark.koreloader.infrastructure.assets.ResourceBundledAssets
import org.mmclark.koreloader.infrastructure.hash.FileChecksumCalculator
import org.mmclark.koreloader.infrastructure.http.NetworkAdapters
import org.mmclark.koreloader.infrastructure.receipts.VolumeReceiptStore
import org.mmclark.koreloader.infrastructure.storage.SafKoboVolumeFactory
import java.io.File

/** Composition root: wires infrastructure adapters into application use cases. */
class AppContainer(context: Context) {

    private val network = NetworkAdapters(
        userAgent = "koreloader/${BuildConfig.VERSION_NAME}",
        downloadDirectory = File(context.cacheDir, "packages"),
    )
    private val bundledAssets = ResourceBundledAssets()
    private val receiptStore = VolumeReceiptStore()

    val volumeFactory: KoboVolumeFactory = SafKoboVolumeFactory(context)
    val detectKoboDevice = DetectKoboDevice()

    private val fetchReleases = FetchReleases(network.packageCatalog)
    private val inspectVolume = InspectVolume(receiptStore)

    val getPackageOverview = GetPackageOverview(detectKoboDevice, inspectVolume, fetchReleases)
    val installPackage = InstallPackage(
        detectKoboDevice = detectKoboDevice,
        inspectVolume = inspectVolume,
        fetchReleases = fetchReleases,
        downloadPackage = DownloadPackage(network.packageDownloader, FileChecksumCalculator()),
        archiveReader = ZipFileArchiveReader(),
        bundledAssets = bundledAssets,
        receiptStore = receiptStore,
    )
    val uninstallPackage = UninstallPackage(detectKoboDevice, bundledAssets, receiptStore)
}
