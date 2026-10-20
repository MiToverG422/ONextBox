package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class HyperOsPerformanceContractTest {
    private fun source(path: String) = File("src/main/java/com/mi/onextbox/$path").readText()

    @Test fun preferencesAreIndependentAndDefaultOff() {
        assertEquals(3, setOf(HyperOsPerformanceSettings.REFRESH, HyperOsPerformanceSettings.TOUCH,
            HyperOsPerformanceSettings.IDLE).size)
        val settings = source("lsp/HyperOsPerformanceSettings.kt")
        assertTrue(settings.contains("as? Boolean ?: false"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(key, false)"))
        assertTrue(settings.contains("SystemAdaptationVersion.HyperOs3"))
    }

    @Test fun nativeLocksAreNotStartedWhileStagingAndBlockUnsafeReload() {
        val hook = source("lsp/HyperOsPerformanceHooker.kt")
        assertTrue(hook.contains("if (!Force185.supported()) return"))
        assertTrue(hook.contains("HotReloadLifecycle.afterCommit { active.startTouch() }"))
        assertTrue(hook.contains("!refresh && !touch && !idle"))
        val runtime = source("lsp/hyperos/Force185.java")
        assertTrue(runtime.contains("ModernHookRegistry.INSTANCE.installFast"))
        assertFalse(runtime.contains("requireModule().hook"))
        assertTrue(runtime.contains("if (!idleDisabled) return"))
        assertTrue(runtime.contains("if (refreshEnabled && ready && displayToken.equals(args[0])"))
        assertTrue(source("lsp/hyperos/TouchRateLock.java").contains("if (!enabled) return"))
    }

    @Test fun hyperOsSwitchesUseSharedGroupedLayoutForAllStyles() {
        val page = source("ui/screens/HyperOsPerformancePage.kt")
        assertTrue(page.contains("FeatureAnimatedGroup {"))
        for (setting in listOf("REFRESH", "IDLE", "TOUCH")) {
            assertTrue(page.contains("row(HyperOsPerformanceSettings.$setting) { above, below ->"))
        }
        assertEquals(3, Regex("hasDividerAbove = above,").findAll(page).count())
        assertEquals(3, Regex("hasDividerBelow = below,").findAll(page).count())
        assertTrue(page.contains("hasDividerAbove = hasDividerAbove,"))
        assertTrue(page.contains("hasDividerBelow = hasDividerBelow,"))
    }

    @Test fun hyperOsPageHasThreeIndependentSwitchesAndNoColorOsCatalog() {
        val page = source("ui/screens/HyperOsPerformancePage.kt")
        for (setting in listOf("REFRESH", "TOUCH", "IDLE")) {
            assertTrue(page.contains("setting = HyperOsPerformanceSettings.$setting"))
        }
        assertTrue(page.contains("switchEnabled = supported || enabled"))
        assertFalse(page.contains("colorOs17FeatureMainEntries"))
        val features = source("ui/screens/FeaturesScreen.kt").substringAfter("fun FeatureMainRoute(")
        assertTrue(features.indexOf("HyperOsPerformancePage(") < features.indexOf("FeatureSearchBar("))
    }
}
