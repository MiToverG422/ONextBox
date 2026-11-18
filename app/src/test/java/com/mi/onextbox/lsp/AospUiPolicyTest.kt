package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class AospUiPolicyTest {
    @Test fun unknownClockIdsNeverReachTheDonor() {
        assertEquals("ANALOG_CLOCK_BIGNUM", AospUiSettings.normalizeClock("unknown"))
        assertEquals("ANALOG_CLOCK_BIGNUM", AospUiSettings.normalizeClock(null))
        assertEquals("ANALOG_CLOCK_BIGNUM", AospUiSettings.normalizeClock("DIGITAL_CLOCK_WEATHER"))
        AospUiSettings.clockIds.forEach { assertEquals(it, AospUiSettings.normalizeClock(it)) }
    }

    @Test fun chargingFeedbackStartsDisabled() {
        val defaults = AospUiSettings.State()
        assertFalse(defaults.chargingEffect)
        assertFalse(defaults.chargingSound)
    }

    @Test fun pausedLockscreenOptionsAreUnavailableToHooks() {
        listOf(AospUiSettings.LOCKSCREEN, AospUiSettings.AOD_LAYOUT, AospUiSettings.FINGERPRINT).forEach { key ->
            assertFalse(key in AospUiSettings.chargingKeys)
            assertFalse(AospUiSettings.enabledXposed(key))
        }
    }
}
