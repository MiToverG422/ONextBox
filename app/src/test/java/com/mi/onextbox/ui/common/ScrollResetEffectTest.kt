package com.mi.onextbox.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollResetEffectTest {
    @Test fun returningFromDetailDoesNotReplayInitialGeneration() {
        assertFalse(shouldResetScroll(0, 0))
    }

    @Test fun returningFromDetailDoesNotReplayConsumedTabSwitch() {
        assertFalse(shouldResetScroll(7, 7))
    }

    @Test fun explicitNewRequestStillResets() {
        assertTrue(shouldResetScroll(7, 8))
    }

    @Test fun pageRestoredAfterATabSwitchConsumesTheNewRequest() {
        assertTrue(shouldResetScroll(2, 4))
    }

    @Test fun absentRequestNeverResets() {
        assertFalse(shouldResetScroll(null, null))
        assertFalse(shouldResetScroll(7, null))
    }

    @Test fun firstExplicitRequestAfterNoRequestResets() {
        assertTrue(shouldResetScroll(null, 0))
    }
}
