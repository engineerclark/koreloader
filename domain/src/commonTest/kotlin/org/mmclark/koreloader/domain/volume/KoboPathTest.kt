package org.mmclark.koreloader.domain.volume

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoboPathTest {

    @Test
    fun parsesHiddenFolders() {
        val path = KoboPath.of(".adds/koreader/koreader.sh")
        assertEquals(listOf(".adds", "koreader", "koreader.sh"), path.segments)
        assertEquals("koreader.sh", path.name)
        assertEquals(KoboPath.of(".adds/koreader"), path.parent)
    }

    @Test
    fun normalisesBackslashesAndTrailingSlash() {
        assertEquals(KoboPath.of(".kobo/KoboRoot.tgz"), KoboPath.of(".kobo\\KoboRoot.tgz"))
        assertEquals(KoboPath.of(".adds/kfmon"), KoboPath.of(".adds/kfmon/"))
    }

    @Test
    fun rejectsUnsafePaths() {
        for (raw in listOf("", "/etc/passwd", "../x", ".adds/../../x", "a//b", "./a", "C:/x", "C:\\x", "a/\u0000b")) {
            assertFailsWith<IllegalArgumentException>(raw) { KoboPath.of(raw) }
            assertNull(KoboPath.parseOrNull(raw))
        }
    }

    @Test
    fun resolveAndRoot() {
        assertTrue(KoboPath.ROOT.isRoot)
        assertNull(KoboPath.ROOT.parent)
        assertEquals(KoboPath.of(".kobo/version"), KoboPath.ROOT.resolve(".kobo").resolve("version"))
        assertEquals(KoboPath.ROOT, KoboPath.of("kfmon.png").parent)
        assertTrue(KoboPath.of(".adds/kfmon/config").startsWith(KoboPath.of(".adds")))
        assertFailsWith<IllegalArgumentException> { KoboPath.ROOT.resolve("../x") }
    }
}
