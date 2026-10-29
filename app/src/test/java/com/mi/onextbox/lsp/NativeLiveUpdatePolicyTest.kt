package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class NativeLiveUpdatePolicyTest {
    @Test fun enabledModeRemovesOnlyOemPresentationFlags() {
        for (flags in 0..31) assertEquals(0, NativeLiveUpdatePolicy.cloudFilter(true, flags))
    }
    @Test fun disabledModePreservesEveryOriginalOemDecision() {
        for (flags in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 7, 31, Int.MAX_VALUE))
            assertEquals(flags, NativeLiveUpdatePolicy.cloudFilter(false, flags))
    }
    @Test fun modeTransitionsRestoreLatestNativeVisibilityWithoutReservingSpace() {
        for (visibility in listOf(0, 4, 8)) repeat(50) {
            assertEquals(8, NativeLiveUpdatePolicy.cloudVisibility(true, visibility))
            assertEquals(visibility, NativeLiveUpdatePolicy.cloudVisibility(false, visibility))
        }
    }
    @Test fun onlySystemUiHooksAreRegisteredAndNoPermissionOrNotificationIsFabricated() {
        val source = File("src/main/java/com/mi/onextbox/lsp/NativeLiveUpdateHooker.kt").readText()
        for (forbidden in listOf("Notification.Builder", "FLAG_PROMOTED_ONGOING", "hasPromotableCharacteristics",
                "POST_PROMOTED_NOTIFICATIONS", "setUidMode", "NotificationManagerService", "Canvas", "dispatchDraw"))
            assertFalse(forbidden, source.contains(forbidden))
        for (required in listOf("reInitLiveAlert", "dispatchEventsAndRebuildList", "setHiddenIconAreas", "hiddenIconList",
                "unregisterOnSharedPreferenceChangeListener", "unhookPrefix", "HotReloadLifecycle.register", "forceStock = true",
                "getBoolean(KEY, false)", "createDumpManager", "getDumpables", "adjustVisibility.invoke(null, view, model)",
                "if (!enabled && !modeApplied) return", "if (!applyingViews) views[view] = original")) assertTrue(required, source.contains(required))
        val entry = File("src/main/java/com/mi/onextbox/lsp/OosLspModuleEntry.kt").readText()
        assertEquals(1, Regex("""NativeLiveUpdateHooker\.hook\(""").findAll(entry).count())
    }
    @Test fun pageSearchInventoryAndQuickSwitchShareOneDefinition() {
        fun source(name: String) = File("src/main/java/com/mi/onextbox/ui/screens/$name.kt").readText()
        assertTrue(source("SystemUiFeaturesPage").contains("NativeLiveUpdateSettingsRow()"))
        assertTrue(source("SharedFeatureDefinitions").contains("val nativeLiveUpdate = Toggle"))
        for (name in listOf("FeatureSearchIndex", "EnabledFeatureCatalog", "EnabledFeatureActions"))
            assertTrue(name, source(name).contains("SharedFeatureDefinitions.nativeLiveUpdate."))
        assertTrue(source("NativeLiveUpdateSettingsRow").contains("interactionEnabled = !saving"))
    }
    @Test fun chipBoundsUseNativeLaidOutViewsAndNeverForceVisibility() {
        val source = File("src/main/java/com/mi/onextbox/lsp/NativeLiveUpdateHooker.kt").readText()
        for (required in listOf("getStartSideContainerBounds", "startSideContainerView", "getClockBounds", "clockView",
                "getBoundsOnScreen", "!view.isAttachedToWindow || view.width <= 0 || view.height <= 0",
                "if (!active() || param.throwable != null) return", "viewBounds.invoke(null, view) as Rect"))
            assertTrue(required, source.contains(required))
        for (forbidden in listOf("setMinimumWidth", "minimumWidth =", "setShowStatusBarChip", "areChipsAllowed = true",
                "View::class.java.getDeclaredMethod(\"onLayout\"", "getDeclaredMethod(\"dispatchDraw\""))
            assertFalse(forbidden, source.contains(forbidden))
    }
    @Test fun onlyTheFluidBundleFilterIsBypassedWithNativeDefaultRestoration() {
        val source = File("src/main/java/com/mi/onextbox/lsp/NativeLiveUpdateHooker.kt").readText()
        for (required in listOf("native-chip-emission", "stateFlowType.isInstance(sink)", "chipRelay.accept(sink, bundle, enabled)",
                "if (enabled) param.result = hostUnit", "chipRelay.reconcile(enabled)", "chipRelay.capture()",
                "chipRelay.restore(saved[\"chipRelay\"]", "chipRelay.clear()"))
            assertTrue(required, source.contains(required))
        assertFalse(source.contains("getDeclaredMethod(\"isFluidDevice\""))
    }
    @Test fun allLocalesExplainMutualExclusion() {
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val strings = File("src/main/res/$locale/strings.xml").readText()
            for (key in listOf("native_live_update_title", "native_live_update_summary"))
                assertEquals("$locale $key", 1, Regex("name=\"$key\"").findAll(strings).count())
        }
        val chinese = File("src/main/res/values-zh-rCN/strings.xml").readText()
        assertTrue(chinese.contains("开启后流体云将不再显示，两者无法同时使用"))
    }
}
