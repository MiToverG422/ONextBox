package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class PowerIndicatorRulesTest {
    @Test fun fontWeightChoicePreservesOtherSettingsAndDefaultsToFollowingClock() {
        val defaults = PowerIndicatorSettings.State()
        assertTrue(defaults.followFontWeight)
        val custom = defaults.copy(fontSize = 14, showTemperature = true, followFontWeight = false)
        assertEquals(custom, PowerIndicatorSettings.normalize(custom))
        assertTrue(PowerIndicatorSettings.normalize(custom.copy(followFontWeight = true)).followFontWeight)
    }

    @Test fun fontWeightIsPersistedAndMirroredWithVariableFontAndFakeBold() {
        fun source(name: String) = java.io.File("src/main/java/com/mi/onextbox/lsp/$name.kt").readText()
        val settings = source("PowerIndicatorSettings")
        assertTrue(settings.contains("p.getBoolean(FOLLOW_FONT_WEIGHT, true)"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(FOLLOW_FONT_WEIGHT, true)"))
        assertTrue(settings.contains("putBoolean(FOLLOW_FONT_WEIGHT, s.followFontWeight)"))
        val hook = source("PowerIndicatorHooker")
        assertTrue(hook.contains("clock.fontVariationSettings"))
        assertTrue(hook.contains("clock.paint.isFakeBoldText"))
        assertTrue(hook.contains("Typeface.create(face, 400, false)"))
        assertTrue(hook.contains("lastClockTypeface === face"))
    }

    @Test fun unitsAndCellCorrectionAreExplicit() {
        assertEquals(4.0, PowerIndicatorRules.watts(-1_000_000, 4000, 0, 0)!!, 0.0001)
        assertEquals(4.0, PowerIndicatorRules.watts(-1000, 4000, 1, 0)!!, 0.0001)
        assertEquals(8.0, PowerIndicatorRules.watts(1_000_000, 4000, 2, 1)!!, 0.0001)
        assertEquals(2.0, PowerIndicatorRules.watts(1_000_000, 4000, 0, 2)!!, 0.0001)
        assertEquals(0.004, PowerIndicatorRules.watts(1000, 4000, 0, 0)!!, 0.0001)
    }
    @Test fun invalidAndUnsupportedReadingsAreNotDisplayed() {
        assertNull(PowerIndicatorRules.watts(Long.MIN_VALUE, 4000, 0, 0))
        assertNull(PowerIndicatorRules.watts(Int.MIN_VALUE.toLong(), 4000, 0, 0))
        assertNull(PowerIndicatorRules.watts(Long.MAX_VALUE, 4000, 0, 0))
        assertNull(PowerIndicatorRules.watts(1_000_000, 0, 0, 0))
        assertNull(PowerIndicatorRules.watts(100_000_000, 4000, 0, 0))
        assertEquals(0.0, PowerIndicatorRules.watts(0, 4000, 0, 0)!!, 0.0)
    }
    @Test fun settingsHaveSafeDefaultsAndBounds() {
        val defaults = PowerIndicatorSettings.State()
        assertFalse(defaults.enabled)
        assertTrue(defaults.hideCharging)
        assertFalse(defaults.hideDischargeSign)
        assertFalse(defaults.showTemperature)
        assertEquals(0, defaults.temperatureUnit)
        assertEquals(0, defaults.scale)
        val s = PowerIndicatorSettings.normalize(defaults.copy(interval = -1, decimals = 10, scale = -1, source = 99, fontSize = 99,
            temperatureUnit = 99))
        assertEquals(500, s.interval)
        assertEquals(2, s.decimals)
        assertEquals(0, s.scale)
        assertEquals(2, s.source)
        assertEquals(16, s.fontSize)
        assertEquals(1, s.temperatureUnit)
        assertEquals(0, PowerIndicatorSettings.normalize(defaults.copy(temperatureUnit = -1)).temperatureUnit)
    }
    @Test fun chargingAndPrecisionAreUnambiguous() {
        assertEquals("+4.00 W", PowerIndicatorRules.format(4.0, 2, true))
        assertEquals("-4 W", PowerIndicatorRules.format(4.0, 0, false))
        assertEquals("-4.3 W", PowerIndicatorRules.format(4.26, 1, false))
    }
    @Test fun dischargeSignCanBeHiddenWithoutHidingTheChargeSign() {
        assertEquals("4.3 W", PowerIndicatorRules.format(4.26, 1, false, hideDischargeSign = true))
        assertEquals("+4.3 W", PowerIndicatorRules.format(4.26, 1, true, hideDischargeSign = true))
    }
    @Test fun optionalTemperatureUsesTwoLinesAndTheSelectedUnit() {
        assertEquals("-4.3 W\n37.0°C", PowerIndicatorRules.format(4.26, 1, false, temperatureTenthsC = 370))
        assertEquals("-4.3 W\n98.6°F", PowerIndicatorRules.format(4.26, 1, false,
            temperatureTenthsC = 370, temperatureUnit = 1))
        assertEquals("+4 W\n32.0°F", PowerIndicatorRules.format(4.0, 0, true,
            temperatureTenthsC = 0, temperatureUnit = 1))
        assertEquals("4.00 W\n-5.0°C", PowerIndicatorRules.format(4.0, 2, false,
            hideDischargeSign = true, temperatureTenthsC = -50))
    }
    @Test fun absentOrInvalidTemperatureKeepsThePowerLine() {
        for (temperature in listOf(null, Int.MIN_VALUE, Int.MAX_VALUE, -501, 1001)) {
            assertEquals("-4.0 W", PowerIndicatorRules.format(4.0, 1, false, temperatureTenthsC = temperature))
        }
    }
    @Test fun doubleLinesFitTheNativeHeightAndSingleLineSizeIsUnchanged() {
        assertEquals(20f, PowerIndicatorRules.fittedTextSizePx(20f, 24f, 1, 24), 0f)
        assertEquals(20f, PowerIndicatorRules.fittedTextSizePx(20f, 24f, 2, 60), 0f)
        val fitted = PowerIndicatorRules.fittedTextSizePx(20f, 24f, 2, 24)
        assertTrue(fitted > 0f)
        assertTrue(fitted < 20f)
        assertTrue(24f * 2 * fitted / 20f <= 24f)
        assertEquals(20f, PowerIndicatorRules.fittedTextSizePx(20f, 24f, 2, 0), 0f)
    }
}
