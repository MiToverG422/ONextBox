package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SettingsFixedRefreshRateContractTest {
    private fun source(path: String): String = listOf(File("src/main/$path"), File("app/src/main/$path"))
        .first { it.isFile }.readText()
    private fun hook() = source("java/com/mi/onextbox/lsp/SettingsFixedRefreshRateHooker.kt")

    @Test fun fixedModeUsesNativePhysicalPicksAndBothRangeBoundsWithoutWritingPanelHardware() {
        val text = hook()
        for (name in listOf("getPerfectRefreshRate", "getPreferredMinRefreshRate", "getPreferredMaxRefreshRate",
                "applyVrrScene", "setAdfrDirectorState", "adjustFrtcModeIfNeed")) assertTrue(name, text.contains(name))
        assertTrue(text.contains("fixedRate(current).takeIf { it > 0f } ?: original"))
        for (write in listOf("Settings.System.put", "Settings.Secure.put", "setprop", "/sys/", ".transact(")) {
            assertFalse(write, text.contains(write))
        }
    }

    @Test fun sharedCoreIsScopedToTheOwnedPrimaryDisplayWithNoOverrideOfExternalDisplays() {
        val text = hook()
        assertTrue(text.contains("fields.displayId.getInt(policy) != Display.DEFAULT_DISPLAY"))
        assertTrue(text.contains("pickingPrimaryDisplay.get() != true"))
        assertTrue(text.contains("pickingPrimaryDisplay.remove()"))
        assertTrue(text.contains("chain.thisObject !== current.policy"))
        assertTrue(text.contains("it.physicalWidth == size.x && it.physicalHeight == size.y"))
        assertTrue(text.contains("SettingsFixedRefreshRateRules.isSupported(rate, rates.toFloatArray())"))
    }

    @Test fun nativePowerThermalAodFingerprintDcAndPwmGuardsRemainInPlace() {
        val text = hook()
        assertTrue(text.contains("Settings.Global.getInt(resolver, \"low_power\", 0) != 0"))
        assertTrue(text.contains("fields.displayState.getInt(info) != Display.STATE_ON"))
        assertTrue(text.contains("intArrayOf(50, 60, 67, 70, 80, 110, 120)"))
        assertTrue(text.contains("intArrayOf(121, 130, 140, 150)"))
        assertTrue(text.contains("mLimitMaxRefrashRateId"))
        assertTrue(text.contains("mDisplayPwmStatus"))
        assertTrue(text.contains("mIpePencilState"))
        assertTrue(text.contains("cap = minOf(cap, rate)"))
    }

    @Test fun policyOwnershipSharesTheExistingHotReloadHandoverAndReplaysAfterHooksResume() {
        val text = source("java/com/mi/onextbox/lsp/SettingsGlobalExtremeRefreshRateHooker.kt")
        assertTrue(text.contains("capture = { policy }"))
        assertTrue(text.contains("restore = { savedPolicy -> bindPolicy(savedPolicy) }"))
        assertTrue(text.contains("SettingsFixedRefreshRateHooker.clear()"))
        assertTrue(text.contains("val fixedChanged = SettingsFixedRefreshRateHooker.updatePolicy(oemPolicy, oemContext)"))
        assertTrue(text.contains("next == target && !fixedChanged"))
        assertTrue(text.contains("Settings.Global.getUriFor(SettingsFixedRefreshRateRules.TOGGLE_KEY)"))
        assertTrue(text.contains("Settings.Global.getUriFor(\"low_power\")"))
        assertTrue(text.contains("runCatching { refreshTarget(force = true) }"))
        assertFalse(hook().contains("registerContentObserver"))
    }

    @Test fun toggleDefaultsOffAndParticipatesInNormalSyncResetSearchAndEnabledPages() {
        val config = source("java/com/mi/onextbox/lsp/LspConfig.kt")
        assertTrue(config.contains("DEFAULT_SETTINGS_DISABLE_ADAPTIVE_REFRESH_RATE = false"))
        assertTrue(config.contains("setSettingsDisableAdaptiveRefreshRateEnabled(context, DEFAULT_SETTINGS_DISABLE_ADAPTIVE_REFRESH_RATE)"))
        assertTrue(config.contains("value = if (settingsDisableAdaptiveRefreshRate) \"1\" else \"0\""))
        for (page in listOf("SettingsFeaturesPages", "EnabledFeatureActions", "EnabledFeatureCatalog", "FeatureSearchIndex")) {
            assertTrue(page, source("java/com/mi/onextbox/ui/screens/$page.kt")
                .contains("feature_settings_disable_adaptive_refresh_rate_title"))
        }
        val strings = source("res/values-zh-rCN/strings.xml")
        assertTrue(strings.contains("亮屏时保持系统设置中选定的刷新率，开启可能增加耗电</string>"))
    }
}
