package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class CouiAccentPresetTest {
    @Test fun absentOrUnknownPreferenceFollowsSystem() {
        assertEquals(CouiAccentPreset.System, CouiAccentPreset.fromName(null))
        assertEquals(CouiAccentPreset.System, CouiAccentPreset.fromName("invalid"))
        for (preset in CouiAccentPreset.entries) assertEquals(preset, CouiAccentPreset.fromName(preset.name))
    }

    @Test fun colorOsGreenUsesTheAppAppearanceVariant() {
        val overlay = Color(0xFF24B232)
        val setting = 4294967301L
        assertEquals(Color(0xFF00BD13), resolveSystemCouiAccent(setting, overlay, false, Color.Gray).primary)
        assertEquals(overlay, resolveSystemCouiAccent(setting, overlay, true, Color.Gray).primary)
    }

    @Test fun legacyPresetAndFallbacksUseTheAvailableSystemColor() {
        assertEquals(Color(0xFF007AFF), resolveSystemCouiAccent(0x10006L, Color.Gray, true, Color.Black).primary)
        assertEquals(Color(0xFF1A8CFF), resolveSystemCouiAccent(0L, null, true, Color.Gray).primary)
        assertEquals(Color.Gray, resolveSystemCouiAccent(null, null, true, Color.Gray).primary)
        assertEquals(Color.Magenta, resolveSystemCouiAccent(null, Color.Magenta, true, Color.Gray).primary)
    }

    @Test fun themedRolesChangeWithTheAccentWhileSurfacesAndErrorsKeepTheirColors() {
        val base = darkColorScheme()
        val themed = base.withCouiAccent(CouiAccentPreset.Yellow.accent(true), darkTheme = true)
        assertEquals(Color(0xFFE5A100), themed.primary)
        assertEquals(themed.primary, themed.primaryContainer)
        assertEquals(Color.Black, themed.onPrimary)
        assertEquals(base.surface, themed.surface)
        assertEquals(base.error, themed.error)
    }
}
