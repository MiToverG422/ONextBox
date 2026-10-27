package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsAppearanceRulesTest {
    @Test fun accountSizingAndTitleLockDefaultToNativeBehavior() {
        val defaults = SettingsAppearanceSettings.State()
        assertFalse(defaults.accountSize)
        assertFalse(defaults.titleLocked)
        assertEquals(100, defaults.accountPercent)
        assertEquals(4, SettingsAppearanceSettings.keys.size)
    }

    @Test fun accountScaleHasSafeBoundsAndKeepsLayoutFlags() {
        assertEquals(60, SettingsAppearanceSettings.normalize(-1))
        assertEquals(150, SettingsAppearanceSettings.normalize(500))
        assertEquals(100, SettingsAppearanceSettings.normalize(100))
        assertEquals(48, SettingsAppearanceRules.scaled(64, 75))
        assertEquals(96, SettingsAppearanceRules.scaled(64, 150))
        assertEquals(-2, SettingsAppearanceRules.scaled(-2, 75))
    }

    @Test fun repeatedChangesAlwaysScaleTheNativeValue() {
        val value = SettingsOwnedDimension(100)
        assertEquals(75, value.apply(100, 75))
        assertEquals(150, value.apply(75, 150))
        assertEquals(150, value.apply(150, 150))
        assertEquals(100, value.restore(150))
    }

    @Test fun genuineNativeChangesWinOverTheOldSnapshot() {
        val value = SettingsOwnedDimension(100)
        assertEquals(75, value.apply(100, 75))
        assertEquals(120, value.apply(80, 150))
        assertEquals(80, value.restore(120))
        assertEquals(70, value.restore(70))
    }

    @Test fun restorationDoesNotOverwriteANativeWriteMadeAfterOurOverride() {
        val value = SettingsOwnedDimension(64)
        assertEquals(48, value.apply(64, 75))
        assertEquals(72, value.restore(72))
        assertEquals(-2, SettingsOwnedDimension(-2).apply(-2, 60))
    }

    @Test fun fixedCollapseUsesTheNativeModeAndPreservesSpecialExpandedPages() {
        assertEquals(0, SettingsAppearanceRules.titleMode(0, false))
        assertEquals(1, SettingsAppearanceRules.titleMode(0, true))
        assertEquals(1, SettingsAppearanceRules.titleMode(1, false))
        assertEquals(1, SettingsAppearanceRules.titleMode(1, true))
        assertEquals(2, SettingsAppearanceRules.titleMode(2, true))
    }
}
