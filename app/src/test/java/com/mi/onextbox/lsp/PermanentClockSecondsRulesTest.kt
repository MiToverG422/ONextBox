package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class PermanentClockSecondsRulesTest {
    @Test fun deadlineIsIgnoredWithoutOverwritingTheNativePreference() {
        listOf(0L, 1L, 300000L, Long.MAX_VALUE).forEach {
            assertEquals(0L, PermanentClockSecondsRules.effectiveDeadline(true, it))
            assertEquals(it, PermanentClockSecondsRules.effectiveDeadline(false, it))
        }
    }

    @Test fun expiredOrPowerSaveSuppressedSelectionStillShowsSeconds() {
        assertTrue(PermanentClockSecondsRules.showSeconds(true, 1, false, false))
        assertTrue(PermanentClockSecondsRules.showSeconds(true, 1, true, false))
        assertTrue(PermanentClockSecondsRules.showSeconds(true, 1, false, true))
    }

    @Test fun turningTheNativeChoiceOffStillTurnsSecondsOff() {
        assertFalse(PermanentClockSecondsRules.showSeconds(true, 0, false, true))
        assertTrue(PermanentClockSecondsRules.showSeconds(true, 0, true, false))
        assertEquals(0, PermanentClockSecondsRules.checkedItem(0))
        assertEquals(1, PermanentClockSecondsRules.checkedItem(1))
        assertEquals(0, PermanentClockSecondsRules.checkedItem(2))
        assertFalse(PermanentClockSecondsRules.showSeconds(true, 2, true, false))
    }

    @Test fun disablingTheFeatureLeavesStockPowerSaveAndExpiryResultsIntact() {
        assertFalse(PermanentClockSecondsRules.showSeconds(false, 1, true, false))
        assertTrue(PermanentClockSecondsRules.showSeconds(false, 0, false, true))
        assertTrue(PermanentClockSecondsRules.hideSeconds(false, true))
        assertFalse(PermanentClockSecondsRules.hideSeconds(false, false))
    }

    @Test fun fluidCloudCannotStripSecondsWhileEnabled() {
        assertFalse(PermanentClockSecondsRules.hideSeconds(true, true))
        assertFalse(PermanentClockSecondsRules.hideSeconds(true, false))
    }

    @Test fun durationSuffixIsRemovedWhileTheNativeLocalizedClockLabelIsKept() {
        assertEquals("时分秒", PermanentClockSecondsRules.optionLabel("时分秒（5分钟）"))
        assertEquals("時分秒", PermanentClockSecondsRules.optionLabel("時分秒（5 分鐘）"))
        assertEquals("Hours, minutes and seconds", PermanentClockSecondsRules.optionLabel("Hours, minutes and seconds (5 minutes)"))
        assertEquals("HH:mm:ss", PermanentClockSecondsRules.optionLabel("HH:mm:ss (5 min) "))
        assertEquals("시분초", PermanentClockSecondsRules.optionLabel("시분초 (5분)"))
        assertEquals("時分秒", PermanentClockSecondsRules.optionLabel("時分秒（5分間）"))
        assertEquals("时分", PermanentClockSecondsRules.optionLabel("时分"))
        assertEquals("时分秒", PermanentClockSecondsRules.optionLabel("时分秒"))
    }
}
