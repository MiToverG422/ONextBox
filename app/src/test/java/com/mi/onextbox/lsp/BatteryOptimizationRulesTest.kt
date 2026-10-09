package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class BatteryOptimizationRulesTest {
    @Test fun restrictLoaderMustBeAnInstanceVoidMethodWithNoArguments() {
        assertTrue(BatteryOptimizationRules.isRestrictLoader("void", emptyList(), false))
        assertFalse(BatteryOptimizationRules.isRestrictLoader("boolean", emptyList(), false))
        assertFalse(BatteryOptimizationRules.isRestrictLoader("void", listOf("android.content.Context"), false))
        assertFalse(BatteryOptimizationRules.isRestrictLoader("void", emptyList(), true))
    }

    @Test fun defaultsAreTakenFromTheDeviceAndBothOemExtensionsArePreserved() {
        val defaults = listOf("system.push", "system.clock")
        val calls = mutableListOf<String>()
        val result = BatteryOptimizationRules.defaultWhitelist(defaults,
            { calls += "customize"; it += "vendor.required" },
            { calls += "felica"; it += "nfc.required" })
        assertEquals(listOf("system.push", "system.clock", "vendor.required", "nfc.required"), result)
        assertEquals(listOf("customize", "felica"), calls)
        assertEquals(listOf("system.push", "system.clock"), defaults)
    }

    @Test fun remoteWhitelistAndUpgradeGuideAreNotInjectedIntoDefaults() {
        val result = BatteryOptimizationRules.defaultWhitelist(listOf("system.push"), {}, {})
        assertEquals(listOf("system.push"), result)
        assertFalse(result.contains("com.oplus.upgradeguide"))
    }

    @Test fun extensionFailureDoesNotMutateDefaults() {
        val defaults = arrayListOf("system.push")
        val result = runCatching {
            BatteryOptimizationRules.defaultWhitelist(defaults,
                { it += "partial"; error("OEM failure") }, {})
        }
        assertTrue(result.isFailure)
        assertEquals(listOf("system.push"), defaults)
    }

    @Test fun batteryFeatureKeysAreIndependentAndFitSystemPropertyLimits() {
        val features = LspConfig.BatteryFeature.entries
        assertEquals(3, features.size)
        assertEquals(3, features.map { it.key }.distinct().size)
        assertTrue(features.all { it.persistPropertyKey.length < 92 })
        assertEquals("com.oplus.battery", BatteryOptimizationRules.PACKAGE_NAME)
    }
}
