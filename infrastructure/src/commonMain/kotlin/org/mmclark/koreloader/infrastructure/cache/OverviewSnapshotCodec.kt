package org.mmclark.koreloader.infrastructure.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mmclark.koreloader.application.port.OverviewSnapshot
import org.mmclark.koreloader.domain.catalog.Artifact
import org.mmclark.koreloader.domain.catalog.Checksum
import org.mmclark.koreloader.domain.catalog.HashAlgorithm
import org.mmclark.koreloader.domain.catalog.PackageId
import org.mmclark.koreloader.domain.catalog.PackageRelease
import org.mmclark.koreloader.domain.device.DeviceModel
import org.mmclark.koreloader.domain.device.FirmwareVersion
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.packages.InstallReceipt
import org.mmclark.koreloader.domain.packages.InstalledState

/**
 * JSON form of the overview cache: volume key -> snapshot. Decoding is lenient: anything that
 * no longer parses or validates is dropped (it's only a cache).
 */
object OverviewSnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshots: Map<String, OverviewSnapshot>): String =
        json.encodeToString(CacheFileDto.serializer(), CacheFileDto(volumes = snapshots.mapValues { it.value.toDto() }))

    fun decode(text: String): Map<String, OverviewSnapshot> {
        val file = runCatching { json.decodeFromString(CacheFileDto.serializer(), text) }.getOrNull() ?: return emptyMap()
        if (file.version != CacheFileDto.VERSION) return emptyMap()
        return file.volumes.mapNotNull { (key, dto) -> runCatching { dto.toDomain() }.getOrNull()?.let { key to it } }.toMap()
    }

    private fun OverviewSnapshot.toDto() = SnapshotDto(
        device = DeviceDto(device.serial, device.firmware.toString(), device.model.id),
        koboUpdatePending = koboUpdatePending,
        installed = installed.entries.associate { (id, state) ->
            id.value to when (state) {
                InstalledState.NotInstalled -> InstalledDto(installed = false)
                is InstalledState.Installed -> InstalledDto(installed = true, version = state.version, receipt = state.receipt?.toDto())
            }
        },
        releases = releases.entries.associate { (id, list) -> id.value to list.map { it.toDto() } },
    )

    private fun InstallReceipt.toDto() = ReceiptDto(packageId.value, version, firmware.toString(), removalPending)

    private fun PackageRelease.toDto() = ReleaseDto(
        version = version,
        publishedOn = publishedOn,
        fileName = artifact.fileName,
        url = artifact.url,
        sizeBytes = artifact.sizeBytes,
        checksums = artifact.checksums.map { ChecksumDto(it.algorithm.name, it.hex) },
    )

    private fun SnapshotDto.toDomain() = OverviewSnapshot(
        device = KoboDevice(device.serial, FirmwareVersion.parseOrNull(device.firmware)!!, DeviceModel.fromId(device.modelId)),
        koboUpdatePending = koboUpdatePending,
        installed = installed.entries.associate { (id, dto) ->
            PackageId(id) to if (!dto.installed) InstalledState.NotInstalled else InstalledState.Installed(dto.version, dto.receipt?.toDomain())
        },
        releases = releases.entries.associate { (id, list) ->
            PackageId(id) to list.mapNotNull { runCatching { it.toDomain(PackageId(id)) }.getOrNull() }
        },
    )

    private fun ReceiptDto.toDomain() =
        InstallReceipt(PackageId(packageId), version, FirmwareVersion.parseOrNull(firmware)!!, removalPending)

    private fun ReleaseDto.toDomain(id: PackageId) = PackageRelease(
        packageId = id,
        version = version,
        publishedOn = publishedOn,
        artifact = Artifact(fileName, url, sizeBytes, checksums.map { Checksum.of(HashAlgorithm.valueOf(it.algorithm), it.hex) }),
    )
}

@Serializable
private class CacheFileDto(val version: Int = VERSION, val volumes: Map<String, SnapshotDto>) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
private class SnapshotDto(
    val device: DeviceDto,
    val koboUpdatePending: Boolean,
    val installed: Map<String, InstalledDto>,
    val releases: Map<String, List<ReleaseDto>>,
)

@Serializable
private class DeviceDto(val serial: String, val firmware: String, val modelId: Int)

@Serializable
private class InstalledDto(val installed: Boolean, val version: String? = null, val receipt: ReceiptDto? = null)

@Serializable
private class ReceiptDto(val packageId: String, val version: String, val firmware: String, val removalPending: Boolean = false)

@Serializable
private class ReleaseDto(
    val version: String,
    val publishedOn: String? = null,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val checksums: List<ChecksumDto>,
)

@Serializable
private class ChecksumDto(val algorithm: String, val hex: String)
