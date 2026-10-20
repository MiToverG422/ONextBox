package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class DisplayEnhancementThermalContractTest {
    private fun source(path: String): String = listOf(File("src/main/$path"), File("app/src/main/$path"))
        .first { it.isFile }.readText()
    private fun hook() = source("java/com/mi/onextbox/lsp/DisplayEnhancementThermalHooker.kt")

    @Test fun bothUiAndServiceDecisionsAreCoveredWithoutADeviceWideThermalOverride() {
        val text = hook()
        listOf("isPowerTemperatureControlVideosr", "isPowerTemperatureControlOsie", "notifySFChanged",
            "getPowerTemperatureFromSettings", "handlePowerTemperatureChange", "handleAppEnterX7PMode",
            "shouldSkipActivityEnter", "checkTopActivity").forEach { assertTrue(it, text.contains(it)) }
        listOf("Settings.Secure.put", "Settings.System.put", "setprop", "/sys/", ".transact(",
            "handleThermalThrottlingAction", "getCurrentThermal", "mPowerSaveModeOn", "mWhiteAppEnter")
            .forEach { assertFalse(it, text.contains(it)) }
        assertTrue(text.contains("native.decision.observed(chain.thisObject, genuine)"))
        assertTrue(text.contains("field(owner, \"mLowPowerMode\") == true"))
        assertTrue(text.contains("field(owner, \"mSplitMode\") == true"))
        assertTrue(text.contains("field(owner, \"mNeedClosePWFunctions\") == true"))
        assertTrue(text.contains("!canBypassSuperResolution(owner)"))
    }

    @Test fun newHooksAreInstalledInTheCorrectProcesses() {
        val entry = source("java/com/mi/onextbox/lsp/OosLspModuleEntry.kt")
        assertTrue(entry.contains("DisplayEnhancementThermalHooker.hookSystemServer(loader)"))
        assertTrue(entry.contains("DisplayEnhancementThermalHooker.hookSettings(classLoader)"))
        assertTrue(hook().contains("deoptimize(loader, MEMC)"))
        assertTrue(hook().contains("Iris5VideoDisplayEnhancementController"))
        assertTrue(hook().contains("VideoColorEnhanceHandler"))
        assertTrue(hook().contains("MemcHandler"))
    }

    @Test fun reloadTransfersOnlyNativeOwnersAndPrimitivesAndCancelsAllOwnedCallbacks() {
        val text = hook()
        assertTrue(text.contains("arrayOf(it, applied[it])"))
        assertTrue(text.contains("capture = session::capture, detach = session::detach"))
        assertTrue(text.contains("resumeAfterRejection = session::schedule"))
        assertTrue(text.contains("main.removeCallbacks(refresh)"))
        assertTrue(text.contains("owners.values.forEach { it.removeCallbacksAndMessages(null) }"))
        assertTrue(text.contains("preferences?.unregisterOnSharedPreferenceChangeListener(listener)"))
        assertTrue(text.contains("owners[owner] = Handler(original.looper)"))
        assertTrue(text.contains("if (previous == null && !enabled)"))
        assertFalse(text.contains("postDelayed"))
        assertFalse(text.contains("getInstance("))
    }

    @Test fun independentSwitchesDefaultOffAndUseConfirmedFrameworkSync() {
        val settings = source("java/com/mi/onextbox/lsp/DisplayEnhancementThermalSettings.kt")
        assertTrue(settings.contains("getBoolean(key, false)"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(key, false) == true"))
        assertTrue(settings.contains("ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)"))
        listOf("SettingsFeaturesPages", "FeatureSearchIndex", "EnabledFeatureCatalog", "EnabledFeatureActions").forEach { name ->
            val text = source("java/com/mi/onextbox/ui/screens/$name.kt")
            assertTrue(name, text.contains("display_enhancement_sr_thermal_title"))
            assertTrue(name, text.contains("display_enhancement_color_thermal_title"))
        }
    }

    @Test fun everyLocaleContainsTheGroupBothSwitchesAndTheirHeatWarnings() {
        val locales = listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-vi", "values-ru")
        locales.forEach { locale ->
            val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(root, "$locale/strings.xml")).getElementsByTagName("string")
            val selected = (0 until nodes.length).map { nodes.item(it) }
                .filter { it.attributes.getNamedItem("name").nodeValue.startsWith("display_enhancement_") }
            assertEquals(locale, 5, selected.size)
            selected.forEach { assertTrue(it.textContent.isNotBlank()) }
        }
    }

    @Test fun superResolutionBypassDoesNotBypassHardwareScreenPowerWindowOrEmergencyGuards() {
        for (mask in 0 until 32) {
            val supported = mask and 1 != 0
            val screenOn = mask and 2 != 0
            val lowPower = mask and 4 != 0
            val split = mask and 8 != 0
            val throttled = mask and 16 != 0
            assertEquals("mask=$mask", supported && screenOn && !lowPower && !split && !throttled,
                DisplayEnhancementThermalRules.canBypassSuperResolution(supported, screenOn, lowPower, split, throttled))
        }
    }
}
