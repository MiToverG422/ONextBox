package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Material3ExpressiveColorsTest {
    @Test fun selectedLegacySpecsUseTheirExactEngineAndDoNotAffectOtherStyles() {
        for (dark in listOf(false, true)) {
            val seed = Color(0xFF2196F3)
            for ((selected, engine) in listOf(
                MaterialColorSpec.SPEC_2021 to ColorSpec.SpecVersion.SPEC_2021,
                MaterialColorSpec.SPEC_2025 to ColorSpec.SpecVersion.SPEC_2025,
            )) {
                val expected = dynamicColorScheme(seedColor = seed, isDark = dark,
                    style = PaletteStyle.TonalSpot, specVersion = engine)
                assertEquals(expected.toString(), material3ExpressiveColorScheme(seed, dark, spec = selected).toString())
            }
            val otherStyle = material3ExpressiveColorScheme(seed, dark, use2026 = false)
            for (selected in MaterialColorSpec.entries) {
                assertEquals(otherStyle.toString(), material3ExpressiveColorScheme(seed, dark, use2026 = false, spec = selected).toString())
            }
        }
        assertEquals(MaterialColorSpec.SPEC_2026, MaterialColorSpec.fromYear(-1))
        for (spec in MaterialColorSpec.entries) assertEquals(spec, MaterialColorSpec.fromYear(spec.year))
    }

    @Test fun newPaletteDoesNotSilentlyFallBackToTonalSpot2025() {
        for (dark in listOf(false, true)) {
            val seed = Color(0xFF2196F3)
            val old = dynamicColorScheme(seedColor = seed, isDark = dark,
                style = PaletteStyle.TonalSpot, specVersion = ColorSpec.SpecVersion.SPEC_2025)
            val updated = material3ExpressiveColorScheme(seed, dark)
            assertEquals(old.toString(), material3ExpressiveColorScheme(seed, dark, use2026 = false).toString())
            assertNotEquals(listOf(old.primary, old.surface, old.tertiary),
                listOf(updated.primary, updated.surface, updated.tertiary))
        }
    }

    @Test fun mainTextRolesRemainReadableForLightDarkAndExtremeSeeds() {
        val seeds = listOf(0xFF2196F3, 0xFFF44336, 0xFFFFEB3B, 0xFF4FAF50, 0xFF000000, 0xFFFFFFFF)
        for (dark in listOf(false, true)) for (seed in seeds) {
            val colors = material3ExpressiveColorScheme(Color(seed), dark)
            val pairs = listOf(
                colors.onSurface to colors.surface,
                colors.onPrimary to colors.primary,
                colors.onSecondary to colors.secondary,
                colors.onTertiary to colors.tertiary,
                colors.onError to colors.error,
            )
            for ((foreground, background) in pairs) {
                val a = foreground.luminance()
                val b = background.luminance()
                val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                assertTrue("seed=$seed dark=$dark contrast=$ratio", ratio >= 4.5f)
            }
        }
    }
}
