package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouiPreferenceDividerRulesTest {
    @Test fun acceptsPreferenceImplementationsAcrossNamespaces() {
        listOf("com.coui.appcompat.preference.COUIPreference", "com.android.settings.wifi.WifiPreference",
            "com.oplus.settings.bluetooth.DevicePreference", "com.oplus.settingslib.a").forEach {
            assertTrue(CouiPreferenceDividerRules.matches(it, "drawDivider", "boolean", 0, false, false))
        }
    }

    @Test fun rejectsOtherNamespacesAndDrawingOverloads() {
        assertFalse(CouiPreferenceDividerRules.isOwner("com.oplus.settingslibextra.Preference"))
        assertFalse(CouiPreferenceDividerRules.isOwner("com.example.Preference"))
        val owner = "com.coui.appcompat.preference.COUIPreference"
        assertFalse(CouiPreferenceDividerRules.matches(owner, "drawDivider", "void", 1, false, false))
        assertFalse(CouiPreferenceDividerRules.matches(owner, "drawDivider", "boolean", 0, true, false))
        assertFalse(CouiPreferenceDividerRules.matches(owner, "drawDivider", "boolean", 0, false, true))
    }
}
