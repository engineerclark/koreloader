package org.mmclark.koreloader.application.storagecheck

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import okio.Buffer
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.exists
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.time.TimeSource

/**
 * Exercises every volume operation the installer depends on, inside a throwaway hidden
 * folder (`.adds/koreloader-test`), and removes it afterwards.
 *
 * This is the SAF spike: it proves that hidden folders, overwrite-with-truncate, append,
 * rename-replacing (the `KoboRoot.tgz` pattern) and recursive delete work on a real phone,
 * and measures small-file throughput (KOReader is thousands of small files).
 */
class RunStorageCheck(private val timeSource: TimeSource = TimeSource.Monotonic) {

    operator fun invoke(volume: KoboVolume): Flow<StorageCheckStep> = flow {
        try {
            runChecks(volume)
        } finally {
            cleanUp(volume)
        }
    }

    private suspend fun FlowCollector<StorageCheckStep>.runChecks(volume: KoboVolume) {
        step("Create hidden folder $TEST_DIR") {
            volume.createDirectories(TEST_DIR)
            ensure(volume.exists(TEST_DIR)) { "folder not found after creating it" }
            null
        } || return

        step("Write a file in the hidden folder") {
            volume.write(HELLO, "Hello, Kobo!\nThis is a longer first line.\n".source())
            null
        } || return

        step("Read the file back") {
            val text = volume.read(HELLO).decodeToString()
            ensure(text == "Hello, Kobo!\nThis is a longer first line.\n") { "read back \"$text\"" }
            null
        } || return

        step("Overwrite with shorter content (truncates, no duplicate)") {
            volume.write(HELLO, "Hello again\n".source())
            val text = volume.read(HELLO).decodeToString()
            ensure(text == "Hello again\n") { "read back \"$text\" (not truncated?)" }
            val names = volume.list(TEST_DIR)
            ensure(names == listOf(HELLO.name)) { "folder contains $names" }
            null
        } || return

        step("Append to the file (Kobo eReader.conf pattern)") {
            volume.append(HELLO, "appended\n".encodeToByteArray(), durable = true)
            val text = volume.read(HELLO).decodeToString()
            ensure(text == "Hello again\nappended\n") { "read back \"$text\"" }
            null
        } || return

        step("Write a hidden file") {
            volume.write(HIDDEN_FILE, "hidden\n".source())
            ensure(HIDDEN_FILE.name in volume.list(TEST_DIR)) { "${HIDDEN_FILE.name} not listed" }
            null
        } || return

        step("Write temp file, rename over existing (KoboRoot.tgz pattern)") {
            volume.write(PAYLOAD, "old".source())
            volume.write(PAYLOAD_PART, "new payload".source(), durable = true)
            volume.rename(PAYLOAD_PART, PAYLOAD.name)
            val text = volume.read(PAYLOAD).decodeToString()
            ensure(text == "new payload") { "read back \"$text\"" }
            val names = volume.list(TEST_DIR).sorted()
            val expected = listOf(HELLO.name, HIDDEN_FILE.name, PAYLOAD.name).sorted()
            ensure(names == expected) { "folder contains $names, expected $expected" }
            null
        } || return

        step("Throughput: $MANY_FILES small files in nested folders") {
            val chunk = ByteArray(SMALL_FILE_SIZE) { (it % 251).toByte() }
            val mark = timeSource.markNow()
            for (i in 0 until MANY_FILES) {
                volume.write(MANY_DIR.resolve("d${i % 10}/f$i.bin"), Buffer().write(chunk))
            }
            val elapsed = mark.elapsedNow()
            val perFile = elapsed / MANY_FILES
            "$elapsed total, $perFile per file (≈ ${perFile * 3000} for 3000 files)"
        } || return

        step("Delete the test folder recursively") {
            ensure(volume.delete(TEST_DIR)) { "delete returned false" }
            ensure(!volume.exists(TEST_DIR)) { "folder still exists" }
            null
        }
    }

    /** Runs one step, emits its outcome, and returns whether it passed. */
    private suspend inline fun FlowCollector<StorageCheckStep>.step(
        name: String,
        block: () -> String?,
    ): Boolean {
        val outcome = try {
            StorageCheckStep.Outcome.Passed(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StorageCheckStep.Outcome.Failed(e.message ?: e::class.simpleName ?: "error")
        }
        emit(StorageCheckStep(name, outcome))
        return outcome is StorageCheckStep.Outcome.Passed
    }

    private suspend fun cleanUp(volume: KoboVolume) {
        try {
            volume.delete(TEST_DIR)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort: the user can delete .adds/koreloader-test by hand.
        }
    }

    private fun ensure(condition: Boolean, message: () -> String) {
        if (!condition) throw IllegalStateException(message())
    }

    private fun String.source() = Buffer().writeUtf8(this)

    companion object {
        val TEST_DIR = KoboPath.of(".adds/koreloader-test")
        private val HELLO = TEST_DIR.resolve("hello.txt")
        private val HIDDEN_FILE = TEST_DIR.resolve(".hidden-file")
        private val PAYLOAD = TEST_DIR.resolve("payload.tgz")
        private val PAYLOAD_PART = TEST_DIR.resolve("payload.tgz.part")
        private val MANY_DIR = TEST_DIR.resolve("many")
        private const val MANY_FILES = 100
        private const val SMALL_FILE_SIZE = 4 * 1024
    }
}

data class StorageCheckStep(val name: String, val outcome: Outcome) {
    sealed interface Outcome {
        data class Passed(val detail: String?) : Outcome
        data class Failed(val message: String) : Outcome
    }
}
