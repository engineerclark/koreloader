package org.mmclark.koreloader.presentation.credits

import org.mmclark.koreloader.domain.packages.Packages
import kotlin.test.Test
import kotlin.test.assertTrue

class CreditsTest {

    @Test
    fun everyPackageCreditsItsUpstreamProjects() {
        for (definition in Packages.all) {
            val credits = Credits.forPackage(definition.id)
            assertTrue(credits.isNotEmpty(), "no credits for ${definition.name}")
            assertTrue(credits.all { it.url.startsWith("https://") && it.license.isNotBlank() }, "${definition.name}: $credits")
        }
    }
}
