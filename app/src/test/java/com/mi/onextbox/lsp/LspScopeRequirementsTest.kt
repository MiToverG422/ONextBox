package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LspScopeRequirementsTest {
    @Test
    fun declaredScopeMetadataIsNormalizedAndDeduplicated() {
        assertEquals(
            setOf("system", "com.android.systemui", "com.oplus.athena"),
            LspScopeRequirements.parse("\uFEFFsystem\r\n\n # scopes\n com.android.systemui \ncom.oplus.athena\nsystem"),
        )
    }

    @Test
    fun onlyInstalledUserPackagesAndDeclaredSystemAreRequired() {
        val declared = setOf("system", "com.android.systemui", "com.android.settings", "com.oplus.athena")
        val installed = setOf("com.android.systemui", "com.android.settings", "com.unrelated.app")
        assertEquals(
            setOf("system", "com.android.systemui", "com.android.settings"),
            LspScopeRequirements.installed(declared, installed::contains),
        )
    }

    @Test
    fun systemDoesNotUsePackageManagerOrImplyAndroidScope() {
        val queried = mutableListOf<String>()
        val required = LspScopeRequirements.installed(setOf("system", "android")) {
            queried += it
            false
        }
        assertEquals(listOf("android"), queried)
        assertEquals(setOf("system"), required)
        assertFalse("android" in required)
    }

    @Test
    fun androidNeverSubstitutesForTheDeclaredSystemScope() {
        val parsed = LspScopeRequirements.parse("system\nandroid")
        assertEquals(setOf("system", "android"), parsed)
        assertTrue("system" in LspScopeRequirements.installed(parsed) { it == "android" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyMetadataCannotSilentlyDisableScopeChecks() {
        LspScopeRequirements.parse("\n# comment\n ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMetadataCannotProducePartialAssumedRequirements() {
        LspScopeRequirements.parse("system\ncom.android.systemui\nnot/a/package")
    }

    @Test(expected = IllegalStateException::class)
    fun packageQueryFailuresDoNotSilentlyClassifyPackagesAsUninstalled() {
        LspScopeRequirements.installed(setOf("system", "com.android.systemui")) { error("Query failed") }
    }
}
