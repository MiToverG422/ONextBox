package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class PasswordEmergencyVisibilityTest {
    @Test fun hidesOnlyPasswordPagesOutsideAnActiveCallWhenExplicitlyEnabled() {
        assertTrue(PasswordEmergencyVisibility.shouldHide(true, true, false))
        assertFalse(PasswordEmergencyVisibility.shouldHide(false, true, false))
        assertFalse(PasswordEmergencyVisibility.shouldHide(true, false, false))
        assertFalse(PasswordEmergencyVisibility.shouldHide(true, true, true))
        assertFalse(PasswordEmergencyVisibility.shouldHide(true, true, null))
    }

    @Test fun hidingAndRestoringPreservesNativeVisibleStateAndLayoutSpace() {
        val state = PasswordEmergencyVisibility(0)
        assertEquals(4, state.nativeRequest(0, true))
        assertEquals(4, state.reconcile(true, 4))
        assertEquals(0, state.reconcile(false, 4))
        assertNull(state.restore())
    }

    @Test fun aNativeGoneOrInvisibleRequestIsNeverRestoredToVisible() {
        for (visibility in listOf(4, 8)) {
            val state = PasswordEmergencyVisibility(0)
            state.nativeRequest(0, true)
            assertEquals(visibility, state.nativeRequest(visibility, true))
            assertNull(state.reconcile(false, visibility))
            assertNull(state.restore())
            assertEquals(visibility, state.requested)
        }
    }

    @Test fun enteringACallRestoresTheNativeReturnToCallButton() {
        val state = PasswordEmergencyVisibility(0)
        state.nativeRequest(0, true)
        assertEquals(0, state.nativeRequest(0, false))
        assertNull(state.restore())
        assertEquals(4, state.nativeRequest(0, true))
        assertEquals(0, state.restore())
    }

    @Test fun unrelatedNativeVisibilityChangesAreNotOverridden() {
        val state = PasswordEmergencyVisibility(0)
        assertNull(state.reconcile(true, 8))
        assertNull(state.reconcile(true, 4))
        assertNull(state.restore())
        assertEquals(4, state.reconcile(true, 0))
        assertEquals(0, state.restore())
    }
}
