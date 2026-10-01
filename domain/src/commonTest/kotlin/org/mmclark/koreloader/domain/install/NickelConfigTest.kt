package org.mmclark.koreloader.domain.install

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NickelConfigTest {

    @Test
    fun exclusionLineMatchesKfmonInstallScriptByteForByte() {
        // From NiLuJe/kfmon tools/install.sh (quoted heredoc, so backslashes are literal).
        assertEquals("ExcludeSyncFolders=(\\\\.(?!kobo|adobe).+|([^.][^/]*/)+\\\\..+)", NickelConfig.EXCLUDE_SYNC_FOLDERS_LINE)
        assertEquals("\n[FeatureSettings]\n${NickelConfig.EXCLUDE_SYNC_FOLDERS_LINE}\n", NickelConfig.HIDDEN_FOLDER_EXCLUSION)
    }

    @Test
    fun appendOnlyWhenMissing() {
        assertTrue(NickelConfig.needsHiddenFolderExclusion(null))
        assertTrue(NickelConfig.needsHiddenFolderExclusion("[General]\nfoo=bar\n"))
        assertFalse(NickelConfig.needsHiddenFolderExclusion("[General]\nfoo=bar\n" + NickelConfig.HIDDEN_FOLDER_EXCLUSION))
    }
}
