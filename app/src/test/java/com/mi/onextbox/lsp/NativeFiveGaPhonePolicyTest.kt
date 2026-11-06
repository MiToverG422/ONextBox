package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFiveGaPhonePolicyTest {
    @Test fun onlyVerifiedFinalMainlandProfilesAreEligible() {
        for (profile in listOf("1001_CMCC", "1001S1DT_CMCC", "1002_CU", "1003_CT", "1004_CBN")) {
            assertTrue(profile, NativeFiveGaPhonePolicy.supportedProfile(profile))
        }
        for (profile in listOf("", "1174_CTMO", "1003", "CT", "46003", "1003_CT ", "1001_MVNO")) {
            assertFalse(profile, NativeFiveGaPhonePolicy.supportedProfile(profile))
        }
    }

    @Test fun unappliedMissingEmptyAndMalformedConfigurationsStayNative() {
        assertFalse(NativeFiveGaPhonePolicy.configReady(false, arrayOf("2,200000")))
        assertFalse(NativeFiveGaPhonePolicy.configReady(true, null))
        assertFalse(NativeFiveGaPhonePolicy.configReady(true, emptyArray()))
        for (invalid in listOf("", "3", "0,180000", "3,0", "-1,180000", "3,no", "3,2147483648")) {
            assertFalse(invalid, NativeFiveGaPhonePolicy.configReady(true, arrayOf(invalid)))
        }
        assertFalse(NativeFiveGaPhonePolicy.configReady(true, arrayOf("2,200000", "")))
    }

    @Test fun validCustomNativeThresholdsAreAcceptedWithoutReplacingThem() {
        assertTrue(NativeFiveGaPhonePolicy.configReady(true, arrayOf("2,200000", "3,130000")))
        assertTrue(NativeFiveGaPhonePolicy.configReady(true, arrayOf(" 4 , 250000 ")))
    }

    @Test fun savedPreferenceAloneCannotEnableAnIncompleteOrNativeOwnedSession() {
        assertFalse(NativeFiveGaPhonePolicy.mayRestore(true, false, false, true))
        assertFalse(NativeFiveGaPhonePolicy.mayRestore(true, true, true, true))
        assertFalse(NativeFiveGaPhonePolicy.mayRestore(false, false, true, true))
        assertFalse(NativeFiveGaPhonePolicy.mayRestore(true, false, true, false))
        assertTrue(NativeFiveGaPhonePolicy.mayRestore(true, false, true, true))
    }

    @Test fun genericAdvancedNrDoesNotCertifyNativeFiveGa() {
        assertFalse(NativeFiveGaPhonePolicy.certified(true, true, false, true, 20, 5))
        assertFalse(NativeFiveGaPhonePolicy.certified(true, false, true, true, 20, 5))
        assertFalse(NativeFiveGaPhonePolicy.certified(true, true, true, false, 20, 5))
        assertFalse(NativeFiveGaPhonePolicy.certified(false, true, true, true, 20, 5))
        assertFalse(NativeFiveGaPhonePolicy.certified(true, true, true, true, 13, 5))
        assertFalse(NativeFiveGaPhonePolicy.certified(true, true, true, true, 20, 0))
        assertTrue(NativeFiveGaPhonePolicy.certified(true, true, true, true, 20, 5))
    }
}
