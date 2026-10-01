package org.mmclark.koreloader.presentation.storagecheck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import okio.Source
import org.mmclark.koreloader.application.device.DetectKoboDevice
import org.mmclark.koreloader.application.device.DeviceDetection
import org.mmclark.koreloader.application.port.EntryType
import org.mmclark.koreloader.application.port.KoboVolume
import org.mmclark.koreloader.application.port.VolumeRef
import org.mmclark.koreloader.application.storagecheck.RunStorageCheck
import org.mmclark.koreloader.domain.volume.KoboPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCheckViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun selectingAVolumeDetectsTheDevice() = runTest {
        val vm = StorageCheckViewModel({ TinyVolume() }, DetectKoboDevice(), RunStorageCheck())
        vm.onVolumeSelected(VolumeRef("content://kobo"))
        val state = vm.state.value
        assertEquals(VolumeRef("content://kobo"), state.volume)
        assertFalse(state.detecting)
        assertIs<DeviceDetection.Detected>(state.detection)
        assertTrue(state.canRunCheck)
    }

    @Test
    fun checkCannotRunWithoutAKobo() = runTest {
        val vm = StorageCheckViewModel({ TinyVolume(isKobo = false) }, DetectKoboDevice(), RunStorageCheck())
        vm.onVolumeSelected(VolumeRef("content://other"))
        assertEquals(DeviceDetection.NotAKobo, vm.state.value.detection)
        assertFalse(vm.state.value.canRunCheck)
        vm.onRunCheck()
        assertTrue(vm.state.value.steps.isEmpty())
    }

    /** Minimal volume holding only `.kobo/version`; writes are accepted and discarded. */
    private class TinyVolume(private val isKobo: Boolean = true) : KoboVolume {
        private val version = "N1,4.1,4.38.21908,4.1,4.1,00000000-0000-0000-0000-000000000391"
        override suspend fun entryType(path: KoboPath) = when {
            !isKobo -> null
            path.toString() == ".kobo" -> EntryType.DIRECTORY
            path.toString() == ".kobo/version" -> EntryType.FILE
            else -> null
        }
        override suspend fun list(dir: KoboPath) = emptyList<String>()
        override suspend fun read(path: KoboPath) = version.encodeToByteArray()
        override suspend fun createDirectories(dir: KoboPath) {}
        override suspend fun write(path: KoboPath, source: Source, durable: Boolean) { Buffer().writeAll(source) }
        override suspend fun append(path: KoboPath, bytes: ByteArray, durable: Boolean) {}
        override suspend fun rename(path: KoboPath, newName: String) {}
        override suspend fun delete(path: KoboPath) = false
    }
}
