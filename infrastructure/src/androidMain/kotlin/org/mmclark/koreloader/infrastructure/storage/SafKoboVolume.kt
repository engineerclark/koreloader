package org.mmclark.koreloader.infrastructure.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Source
import okio.buffer
import okio.sink
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.KoboVolumeException
import org.mmclark.koreloader.domain.volume.KoboPath
import java.io.FileOutputStream
import java.util.Locale

/**
 * [KoboVolume] backed by a Storage Access Framework document tree (the Kobo volume root the
 * user picked with `ACTION_OPEN_DOCUMENT_TREE`).
 *
 * Uses [DocumentsContract] directly rather than `DocumentFile`, and caches each directory's
 * listing, so that writing thousands of files doesn't re-query the provider per lookup.
 * All operations are serialised: the cache assumes nothing else modifies the volume
 * while it is open.
 */
class SafKoboVolume(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : KoboVolume {

    private data class Entry(val documentId: String, val name: String, val isDirectory: Boolean)

    private val mutex = Mutex()
    private val rootEntry = Entry(DocumentsContract.getTreeDocumentId(treeUri), "", isDirectory = true)

    /** Directory path -> (lower-cased child name -> entry). */
    private val listings = HashMap<KoboPath, MutableMap<String, Entry>>()

    override suspend fun entryType(path: KoboPath): EntryType? = io {
        find(path)?.let { if (it.isDirectory) EntryType.DIRECTORY else EntryType.FILE }
    }

    override suspend fun list(dir: KoboPath): List<String> = io {
        val entry = find(dir) ?: throw KoboVolumeException("No such directory: $dir")
        if (!entry.isDirectory) throw KoboVolumeException("Not a directory: $dir")
        listing(dir, entry).values.map { it.name }
    }

    override suspend fun read(path: KoboPath): ByteArray = io {
        val entry = requireFile(path)
        resolver.openInputStream(uriOf(entry))?.use { it.readBytes() }
            ?: throw KoboVolumeException("Can't open $path for reading")
    }

    override suspend fun createDirectories(dir: KoboPath) {
        io { mkdirs(dir) }
    }

    override suspend fun write(path: KoboPath, source: Source, durable: Boolean) {
        io { writeFile(path, mode = "wt", durable = durable) { out -> out.writeAll(source) } }
    }

    override suspend fun append(path: KoboPath, bytes: ByteArray, durable: Boolean) {
        io { writeFile(path, mode = "wa", durable = durable) { out -> out.write(bytes) } }
    }

    override suspend fun rename(path: KoboPath, newName: String) {
        io {
            val entry = requireFile(path)
            val parent = path.parent ?: throw KoboVolumeException("Can't rename the root")
            val target = parent.resolve(newName)
            // Providers resolve name clashes by renaming to "name (1)", so clear the way first.
            find(target)?.let { existing ->
                if (existing.isDirectory) throw KoboVolumeException("Can't replace directory $target")
                deleteEntry(target, existing)
            }
            val newUri = DocumentsContract.renameDocument(resolver, uriOf(entry), newName)
                ?: throw KoboVolumeException("Rename of $path failed")
            val siblings = listings.getValue(parent)
            siblings.remove(key(entry.name))
            siblings[key(newName)] = Entry(DocumentsContract.getDocumentId(newUri), newName, isDirectory = false)
            requireDisplayName(newUri, newName)
        }
    }

    override suspend fun delete(path: KoboPath): Boolean = io {
        if (path.isRoot) throw KoboVolumeException("Refusing to delete the volume root")
        val entry = find(path) ?: return@io false
        deleteEntry(path, entry)
        true
    }

    // --- internals (call only while holding the mutex, on the IO dispatcher) ---

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: KoboVolumeException) {
                throw e
            } catch (e: Exception) {
                // SecurityException (permission revoked), IOException / IllegalStateException
                // (volume unplugged), FileNotFoundException, ...
                throw KoboVolumeException(e.message ?: e::class.java.simpleName, e)
            }
        }
    }

    private fun find(path: KoboPath): Entry? {
        if (path.isRoot) return rootEntry
        val parentPath = path.parent!!
        val parent = find(parentPath) ?: return null
        if (!parent.isDirectory) return null
        return listing(parentPath, parent)[key(path.name)]
    }

    private fun requireFile(path: KoboPath): Entry {
        val entry = find(path) ?: throw KoboVolumeException("No such file: $path")
        if (entry.isDirectory) throw KoboVolumeException("Is a directory: $path")
        return entry
    }

    private fun listing(path: KoboPath, dir: Entry): MutableMap<String, Entry> = listings.getOrPut(path) {
        val children = HashMap<String, Entry>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dir.documentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                children[key(name)] = Entry(cursor.getString(0), name, cursor.getString(2) == Document.MIME_TYPE_DIR)
            }
        } ?: throw KoboVolumeException("Can't list $path")
        children
    }

    private fun mkdirs(dir: KoboPath): Entry {
        if (dir.isRoot) return rootEntry
        val parentPath = dir.parent!!
        val parent = mkdirs(parentPath)
        val siblings = listing(parentPath, parent)
        siblings[key(dir.name)]?.let { existing ->
            if (!existing.isDirectory) throw KoboVolumeException("Not a directory: $dir")
            return existing
        }
        val created = create(parent, Document.MIME_TYPE_DIR, dir.name, isDirectory = true)
        siblings[key(dir.name)] = created
        listings[dir] = HashMap() // a fresh directory is empty; skip the query
        return created
    }

    private inline fun writeFile(path: KoboPath, mode: String, durable: Boolean, body: (okio.BufferedSink) -> Unit) {
        val parentPath = path.parent ?: throw KoboVolumeException("Can't write to the root")
        val parent = mkdirs(parentPath)
        val siblings = listing(parentPath, parent)
        val entry = siblings[key(path.name)]?.also {
            if (it.isDirectory) throw KoboVolumeException("Is a directory: $path")
        } ?: create(parent, MIME_BINARY, path.name, isDirectory = false).also {
            siblings[key(path.name)] = it
        }
        val pfd = resolver.openFileDescriptor(uriOf(entry), mode)
            ?: throw KoboVolumeException("Can't open $path for writing")
        pfd.use {
            FileOutputStream(pfd.fileDescriptor).use { stream ->
                val sink = stream.sink().buffer()
                body(sink)
                sink.flush()
                if (durable) Os.fsync(pfd.fileDescriptor)
            }
        }
    }

    private fun create(parent: Entry, mimeType: String, name: String, isDirectory: Boolean): Entry {
        val uri = DocumentsContract.createDocument(resolver, uriOf(parent), mimeType, name)
            ?: throw KoboVolumeException("Can't create $name")
        try {
            requireDisplayName(uri, name)
        } catch (e: KoboVolumeException) {
            DocumentsContract.deleteDocument(resolver, uri)
            throw e
        }
        return Entry(DocumentsContract.getDocumentId(uri), name, isDirectory)
    }

    /**
     * Providers may silently alter names, e.g. append an extension or " (1)" on a clash.
     * Installing under the wrong name would be worse than failing, so check.
     */
    private fun requireDisplayName(uri: Uri, expected: String) {
        val actual = resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        if (actual == null || !actual.equals(expected, ignoreCase = true)) {
            throw KoboVolumeException("Provider stored \"$expected\" as \"$actual\"")
        }
    }

    private fun deleteEntry(path: KoboPath, entry: Entry) {
        if (!DocumentsContract.deleteDocument(resolver, uriOf(entry))) {
            throw KoboVolumeException("Delete of $path failed")
        }
        path.parent?.let { listings[it]?.remove(key(path.name)) }
        listings.keys.removeAll { it.startsWith(path) }
    }

    private fun uriOf(entry: Entry): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)

    private fun key(name: String) = name.lowercase(Locale.ROOT)

    private companion object {
        // octet-stream stops providers from appending an extension to the display name.
        const val MIME_BINARY = "application/octet-stream"
    }
}
