package org.mmclark.koreloader.application.device

import kotlinx.coroutines.CancellationException
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeException
import org.mmclark.koreloader.domain.device.DeviceCompatibility
import org.mmclark.koreloader.domain.device.DeviceCompatibilityPolicy
import org.mmclark.koreloader.domain.device.KoboDevice
import org.mmclark.koreloader.domain.volume.KoboPath

sealed interface DeviceDetection {
    /** The volume can't be read (e.g. the Kobo was unplugged or access was revoked). */
    data class VolumeUnavailable(val message: String) : DeviceDetection

    /** No `.kobo` folder: this isn't the root of a Kobo volume. */
    data object NotAKobo : DeviceDetection

    /** `.kobo/version` is missing or couldn't be parsed. */
    data class VersionUnreadable(val contents: String?) : DeviceDetection

    data class Detected(val device: KoboDevice, val compatibility: DeviceCompatibility) : DeviceDetection
}

/** Identifies the Kobo behind a volume from `.kobo/version` and checks compatibility. */
class DetectKoboDevice {

    suspend operator fun invoke(volume: KoboVolume): DeviceDetection = try {
        detect(volume)
    } catch (e: CancellationException) {
        throw e
    } catch (e: KoboVolumeException) {
        DeviceDetection.VolumeUnavailable(e.message ?: "Volume not accessible")
    }

    private suspend fun detect(volume: KoboVolume): DeviceDetection {
        if (volume.entryType(KOBO_DIR) != EntryType.DIRECTORY) return DeviceDetection.NotAKobo
        if (volume.entryType(VERSION_FILE) != EntryType.FILE) return DeviceDetection.VersionUnreadable(null)
        val contents = volume.read(VERSION_FILE).decodeToString()
        val device = KoboDevice.fromVersionFile(contents) ?: return DeviceDetection.VersionUnreadable(contents)
        return DeviceDetection.Detected(device, DeviceCompatibilityPolicy.evaluate(device))
    }

    private companion object {
        val KOBO_DIR = KoboPath.of(".kobo")
        val VERSION_FILE = KoboPath.of(".kobo/version")
    }
}
