package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMessagesOtpActionTest {
    @Test fun acceptsModuleActionWithoutMessageIds() {
        assertTrue(GoogleMessagesOtpAction.ownsAction(GoogleMessagesOtpAction.ACTION, "onextbox-otp", false))
    }

    @Test fun consumesOldModuleButtonsWithoutNativeIds() {
        assertTrue(GoogleMessagesOtpAction.ownsAction("com.google.android.apps.messaging.copy_otp", "onextbox-otp", false))
    }

    @Test fun leavesNativeCopyAndOtherBroadcastsUntouched() {
        assertFalse(GoogleMessagesOtpAction.ownsAction("com.google.android.apps.messaging.copy_otp", null, false))
        assertFalse(GoogleMessagesOtpAction.ownsAction("com.google.android.apps.messaging.copy_otp", "onextbox-otp", true))
        assertFalse(GoogleMessagesOtpAction.ownsAction("other.action", "onextbox-otp", false))
        assertFalse(GoogleMessagesOtpAction.ownsAction(null, null, false))
    }

    @Test fun acceptsFourToEightAsciiDigitsIncludingLeadingZeroes() {
        for (code in listOf("0123", "246810", "01234567")) {
            assertTrue(GoogleMessagesOtpAction.isValidCode(code))
        }
    }

    @Test fun rejectsMissingShortLongAndNonNumericPayloads() {
        for (code in listOf(null, "", "123", "123456789", "12a4", "１２３４", "1234 ", "1234\n")) {
            assertFalse(GoogleMessagesOtpAction.isValidCode(code))
        }
    }
}
