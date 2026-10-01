package org.mmclark.koreloader.infrastructure.storage

import android.content.Context
import android.net.Uri
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeFactory
import org.mmclark.koreloader.application.port.VolumeRef

/** Opens [SafKoboVolume]s for SAF tree URIs (a [VolumeRef] holds the URI string). */
class SafKoboVolumeFactory(context: Context) : KoboVolumeFactory {

    private val resolver = context.applicationContext.contentResolver

    override fun open(ref: VolumeRef): KoboVolume = SafKoboVolume(resolver, Uri.parse(ref.value))
}
