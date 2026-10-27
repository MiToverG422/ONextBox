package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class ScreenTranslationRulesTest {
    @Test fun floatingDetectorRequiresTheNativeBooleanQuerySignature() {
        assertTrue(ScreenTranslationRules.isFloatingDetector("boolean", emptyList(), true))
        assertFalse(ScreenTranslationRules.isFloatingDetector("void", emptyList(), true))
        assertFalse(ScreenTranslationRules.isFloatingDetector("int", emptyList(), true))
        assertFalse(ScreenTranslationRules.isFloatingDetector("boolean", listOf("android.content.Context"), true))
        assertFalse(ScreenTranslationRules.isFloatingDetector("boolean", emptyList(), false))
    }

    @Test fun translationIsADeclaredIndependentScopeWithHotReloadSupport() {
        assertTrue(com.mi.onextbox.lsp.ModuleHotReloadPolicy.supportedPackages.contains(ScreenTranslationSettings.PACKAGE_NAME))
        assertEquals("com.coloros.translate", ScreenTranslationSettings.PACKAGE_NAME)
    }

    @Test fun landscapeOverrideOnlyAppliesToTheTwoNativeEntryChecks() {
        val helper = "com.coloros.translate.screen.utils.Support" to "supportsLandscape"
        val entry = "com.coloros.translate.screen.ScreenTranslationService" to "start"
        val full = "com.coloros.translate.screen.widget.TranslationView" to "full"
        fun frame(method: Pair<String, String>) = StackTraceElement(method.first, method.second, null, -1)
        val bridge = frame("com.mi.onextbox.lsp.ScreenTranslationHooker" to "invoke")
        assertTrue(ScreenTranslationRules.isLandscapeCaller(listOf(bridge, frame(helper), frame(entry)), helper, setOf(entry, full)))
        assertTrue(ScreenTranslationRules.isLandscapeCaller(listOf(bridge, frame(helper), frame(full)), helper, setOf(entry, full)))
        val nestedLayout = "com.coloros.translate.screen.widget.TranslationView" to "layout"
        assertFalse(ScreenTranslationRules.isLandscapeCaller(listOf(bridge, frame(helper), frame(nestedLayout), frame(entry)), helper, setOf(entry, full)))
        assertFalse(ScreenTranslationRules.isLandscapeCaller(listOf(bridge, frame(helper)), helper, setOf(entry, full)))
    }

    @Test fun onlyBrowserVersionsBeyondKnownRulesUseAutomaticExtraction() {
        assertEquals("native.WebView", ScreenTranslationRules.browserClass("native.WebView", 200, listOf(100, 200)))
        assertEquals("native.WebView", ScreenTranslationRules.browserClass("native.WebView", 150, listOf(100, 200)))
        assertEquals("", ScreenTranslationRules.browserClass("native.WebView", 201, listOf(200, 100)))
        assertEquals("native.WebView", ScreenTranslationRules.browserClass("native.WebView", 201, null))
        assertEquals("native.WebView", ScreenTranslationRules.browserClass("native.WebView", 0, listOf(100)))
        assertEquals("native.WebView", ScreenTranslationRules.browserClass("native.WebView", 201, listOf(0, -1)))
        assertEquals("", ScreenTranslationRules.browserClass("", 201, listOf(100)))
        assertNull(ScreenTranslationRules.browserClass(null, 201, listOf(100)))
    }
}
