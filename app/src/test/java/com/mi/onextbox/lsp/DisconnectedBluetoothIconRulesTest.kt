package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectedBluetoothIconRulesTest {
    @Test fun enabledFeatureHidesOnlyDisconnectedDevices() {
        assertTrue(DisconnectedBluetoothIconRules.shouldHide(true, false))
        assertFalse(DisconnectedBluetoothIconRules.shouldHide(true, true))
    }

    @Test fun disablingTheFeatureKeepsAllStockStates() {
        listOf(true, false, null).forEach {
            assertFalse(DisconnectedBluetoothIconRules.shouldHide(false, it))
        }
    }

    @Test fun anUnknownConnectionMustNotHideTheIcon() {
        assertFalse(DisconnectedBluetoothIconRules.shouldHide(true, null))
    }

    @Test fun connectingAndDisconnectingDoesNotLatchThePreviousState() {
        assertTrue(DisconnectedBluetoothIconRules.shouldHide(true, false))
        assertFalse(DisconnectedBluetoothIconRules.shouldHide(true, true))
        assertTrue(DisconnectedBluetoothIconRules.shouldHide(true, false))
        assertFalse(DisconnectedBluetoothIconRules.shouldHide(false, false))
    }
}
