package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockPeriodRulesTest {
    @Test fun removesNativePeriodAndSpanMarkersWithoutChangingTimeOrSeconds() {
        assertEquals("h:mm:ss", ClockPeriodRules.timePattern("\uef00a\uef01h:mm:ss"))
        assertEquals("hh:mm", ClockPeriodRules.timePattern("hh:mm\uef00\u202fa\uef01"))
        assertEquals("HH:mm:ss", ClockPeriodRules.timePattern("HH:mm:ss"))
    }

    @Test fun quotedLiteralsAndEscapedQuotesArePreserved() {
        assertEquals("h 'a b B' mm 'o''clock'", ClockPeriodRules.timePattern("a h 'a b B' mm 'o''clock'"))
        assertEquals("h 'a' mm", ClockPeriodRules.timePattern("B h 'a' mm"))
    }

    @Test fun exposesSystemDayPeriodPatterns() {
        assertEquals("a", ClockPeriodRules.periodPattern(0))
        assertEquals("BBBB", ClockPeriodRules.periodPattern(1))
        assertEquals(1, ClockPeriodRules.normalizeFormat(2))
        assertEquals(0, ClockPeriodRules.normalizeFormat(0))
        assertEquals("BBBB", ClockPeriodRules.periodPattern(2))
    }
}
