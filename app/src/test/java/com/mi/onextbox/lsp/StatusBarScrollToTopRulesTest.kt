package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarScrollToTopRulesTest {
    @Test fun disablingTheBypassPreservesTheNativeAllowlist() {
        assertFalse(StatusBarScrollToTopRules.isAllowedPackage(false, "com.example.app", false))
        assertTrue(StatusBarScrollToTopRules.isAllowedPackage(false, "com.example.app", true))
    }

    @Test fun enablingTheBypassAllowsAnUnlistedApp() {
        assertTrue(StatusBarScrollToTopRules.isAllowedPackage(true, "com.example.app", false))
        assertTrue(StatusBarScrollToTopRules.isAllowedPackage(true, "com.example.app", true))
    }

    @Test fun anEmptyPackageDoesNotGainEligibility() {
        listOf(null, "", " ").forEach {
            assertFalse(StatusBarScrollToTopRules.isAllowedPackage(true, it, false))
        }
    }

    @Test fun theTwoSwitchesUseIndependentKeysWithinAndroidPropertyLimits() {
        val features = LspConfig.StatusBarInteractionFeature.entries
        assertEquals(2, features.size)
        assertEquals(features.size, features.map { it.key }.distinct().size)
        assertTrue(features.all { it.persistPropertyKey.length < 92 })
    }
}
