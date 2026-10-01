package org.mmclark.koreloader.infrastructure.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mmclark.koreloader.application.port.OverviewSnapshot
import org.mmclark.koreloader.application.port.OverviewSnapshotStore
import java.io.File

/**
 * [OverviewSnapshotStore] kept in memory (so screens get it instantly) and in [file] (so it
 * survives app restarts). Writes go to a temp file then rename, so a crash never leaves a
 * half-written cache.
 */
class FileOverviewSnapshotStore(private val file: File) : OverviewSnapshotStore {

    private val mutex = Mutex()
    private var snapshots: MutableMap<String, OverviewSnapshot>? = null

    override suspend fun load(key: String): OverviewSnapshot? = mutex.withLock { loaded()[key] }

    override suspend fun save(key: String, snapshot: OverviewSnapshot) {
        mutex.withLock {
            val all = loaded()
            all[key] = snapshot
            val text = OverviewSnapshotCodec.encode(all)
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val temp = File(file.path + ".tmp")
                temp.writeText(text)
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            }
        }
    }

    private suspend fun loaded(): MutableMap<String, OverviewSnapshot> = snapshots ?: withContext(Dispatchers.IO) {
        val text = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
        text?.let(OverviewSnapshotCodec::decode).orEmpty().toMutableMap()
    }.also { snapshots = it }
}
