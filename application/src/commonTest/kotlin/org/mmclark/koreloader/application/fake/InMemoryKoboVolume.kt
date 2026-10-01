package org.mmclark.koreloader.application.fake

import okio.Buffer
import okio.Source
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeException
import org.mmclark.koreloader.domain.volume.KoboPath

/** Case-insensitive in-memory volume, like a FAT32 drive. */
class InMemoryKoboVolume : KoboVolume {

    private val directories = mutableMapOf<String, KoboPath>("" to KoboPath.ROOT)
    private val files = mutableMapOf<String, Pair<KoboPath, ByteArray>>()

    var failOn: ((operation: String, path: KoboPath) -> Boolean) = { _, _ -> false }

    /** Every mutating operation, in order, e.g. "write .adds/x", "rename .kobo/a -> b". */
    val log = mutableListOf<String>()

    fun putFile(path: String, contents: String) {
        val p = KoboPath.of(path)
        p.parent?.let { mkdirs(it) }
        files[key(p)] = p to contents.encodeToByteArray()
    }

    fun fileText(path: String): String? = files[key(KoboPath.of(path))]?.second?.decodeToString()

    val allPaths: Set<String> get() = (directories.values + files.values.map { it.first }).map { it.toString() }.toSet()

    override suspend fun entryType(path: KoboPath): EntryType? = when (key(path)) {
        in directories -> EntryType.DIRECTORY
        in files -> EntryType.FILE
        else -> null
    }

    override suspend fun list(dir: KoboPath): List<String> {
        maybeFail("list", dir)
        if (key(dir) !in directories) throw KoboVolumeException("No such directory: $dir")
        return (directories.values + files.values.map { it.first })
            .filter { it.parent?.let(::key) == key(dir) }
            .map { it.name }
    }

    override suspend fun read(path: KoboPath): ByteArray {
        maybeFail("read", path)
        return files[key(path)]?.second ?: throw KoboVolumeException("No such file: $path")
    }

    override suspend fun createDirectories(dir: KoboPath) {
        maybeFail("createDirectories", dir)
        mkdirs(dir)
    }

    override suspend fun write(path: KoboPath, source: Source, durable: Boolean) {
        maybeFail("write", path)
        path.parent?.let { mkdirs(it) }
        val buffer = Buffer().also { it.writeAll(source) }
        files[key(path)] = path to buffer.readByteArray()
        log += "write $path"
    }

    override suspend fun append(path: KoboPath, bytes: ByteArray, durable: Boolean) {
        maybeFail("append", path)
        path.parent?.let { mkdirs(it) }
        val existing = files[key(path)]?.second ?: ByteArray(0)
        files[key(path)] = path to existing + bytes
        log += "append $path"
    }

    override suspend fun rename(path: KoboPath, newName: String) {
        maybeFail("rename", path)
        val (_, data) = files.remove(key(path)) ?: throw KoboVolumeException("No such file: $path")
        val target = (path.parent ?: KoboPath.ROOT).resolve(newName)
        files[key(target)] = target to data
        log += "rename $path -> $newName"
    }

    override suspend fun delete(path: KoboPath): Boolean {
        maybeFail("delete", path)
        if (files.remove(key(path)) != null) return true.also { log += "delete $path" }
        if (key(path) !in directories || path.isRoot) return false
        directories.keys.removeAll { it == key(path) || it.startsWith(key(path) + "/") }
        files.keys.removeAll { it.startsWith(key(path) + "/") }
        return true
    }

    private fun mkdirs(dir: KoboPath) {
        var current: KoboPath? = dir
        while (current != null && !current.isRoot) {
            if (key(current) in files) throw KoboVolumeException("Not a directory: $current")
            directories.getOrPut(key(current)) { current }
            current = current.parent
        }
    }

    private fun maybeFail(operation: String, path: KoboPath) {
        if (failOn(operation, path)) throw KoboVolumeException("Injected failure: $operation $path")
    }

    private fun key(path: KoboPath) = path.toString().lowercase()
}
