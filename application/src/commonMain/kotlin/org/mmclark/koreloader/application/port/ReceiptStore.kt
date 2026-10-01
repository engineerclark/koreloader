package org.mmclark.koreloader.application.port

import org.mmclark.koreloader.domain.packages.InstallReceipt

/** Port: koreloader's install receipts, stored on the Kobo volume itself. */
interface ReceiptStore {
    /** Receipts on [volume]; empty if there are none or they can't be read. */
    suspend fun load(volume: KoboVolume): List<InstallReceipt>

    suspend fun save(volume: KoboVolume, receipts: List<InstallReceipt>)
}
