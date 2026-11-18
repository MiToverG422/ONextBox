package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class AospChargingRulesTest {
    @Test fun onlyConnectedChargingUsesTheSelectedSound() {
        assertTrue(AospChargingRules.replaceSound(true, 3))
        listOf(null, -1, 0, 1, 2, 4, 100).forEach { assertFalse(AospChargingRules.replaceSound(true, it)) }
        assertFalse(AospChargingRules.replaceSound(false, 3))
    }
}
