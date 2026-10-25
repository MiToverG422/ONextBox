package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mi.onextbox.color2026.dynamiccolor.ColorSpec.SpecVersion
import com.mi.onextbox.color2026.dynamiccolor.Variant
import com.mi.onextbox.color2026.hct.Hct
import com.mi.onextbox.color2026.scheme.SchemeCmf
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Coui2026ColorsTest {
    @Test fun newSpecPreservesExistingPreferenceIndices() {
        assertEquals(0, CouiColorSpec.Spec2021.ordinal)
        assertEquals(1, CouiColorSpec.Spec2025.ordinal)
        assertEquals(2, CouiColorSpec.Spec2026.ordinal)
        assertEquals("SPEC_2026", CouiColorSpec.Spec2026.label)
    }

    @Test fun appliedRolesComeFrom2026WithoutSpecFallback() {
        for (seed in listOf(0xFF4285F4.toInt(), 0xFF985A35.toInt(), 0xFF198268.toInt())) {
            for (dark in listOf(false, true)) {
                val scheme = SchemeCmf(Hct.fromInt(seed), dark, 0.0)
                assertEquals(SpecVersion.SPEC_2026, scheme.specVersion)
                assertEquals(Variant.CMF, scheme.variant)
                val colors = coui2026Colors(Color(seed), dark)
                assertEquals(scheme.primary, colors.primary.toArgb())
                assertEquals(scheme.onPrimary, colors.onPrimary.toArgb())
                assertEquals(1f, colors.surface.alpha, 0f)
                assertNotEquals(colors.primary, colors.onPrimary)
            }
        }
    }

    @Test fun selectionDoesNotChangeDefaultsOrSavedPalette() {
        val ui = listOf(File("src/main/java/com/mi/onextbox/ui"), File("app/src/main/java/com/mi/onextbox/ui"))
            .first { it.isDirectory }
        val defaults = File(ui, "common/AppThemeMode.kt").readText()
        assertTrue(defaults.contains("prefs.getInt(PREF_KEY, ThemeColorSpec.Spec2021.ordinal)"))
        assertTrue(defaults.contains("prefs.getInt(PREF_KEY, ThemePaletteStyle.TonalSpot.ordinal)"))
        val settings = File(ui, "settings/SettingsMainPage.kt").readText()
        assertTrue(settings.contains("enabled = appThemeColorSpec != ThemeColorSpec.Spec2026.ordinal"))
        assertTrue(settings.contains("listOf(\"CMF\")"))
        assertTrue(File(ui, "Root.kt").readText().contains("monetColors = monet2026Colors"))
    }
}
