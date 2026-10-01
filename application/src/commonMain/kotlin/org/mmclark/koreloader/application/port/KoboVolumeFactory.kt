package org.mmclark.koreloader.application.port

/**
 * Opaque reference to a volume the user granted access to (on Android, a SAF tree URI).
 * Only infrastructure interprets [value].
 */
data class VolumeRef(val value: String)

/** Port: opens a [KoboVolume] for a [VolumeRef]. */
fun interface KoboVolumeFactory {
    fun open(ref: VolumeRef): KoboVolume
}
