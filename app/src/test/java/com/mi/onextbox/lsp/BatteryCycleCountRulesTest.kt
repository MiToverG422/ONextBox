package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class BatteryCycleCountRulesTest {
    @Test fun parsesNonNegativeWholeCyclesIncludingRealZero() {
        assertEquals(0, BatteryCycleCountRules.parse("0"))
        assertEquals(150, BatteryCycleCountRules.parse(" 150\n"))
        assertEquals(Int.MAX_VALUE, BatteryCycleCountRules.parse(Int.MAX_VALUE.toString()))
    }

    @Test fun failuresAreNotConvertedToZero() {
        listOf(null, "", " ", "-1", "-2", "+1", "1.5", "150 cycles", "2147483648", "unknown")
            .forEach { assertNull(BatteryCycleCountRules.parse(it)) }
    }

    @Test fun broadcastTakesPriorityAndDoesNotReadFallbacks() {
        assertEquals(150, BatteryCycleCountRules.resolve(150, { error("unused") }, { error("unused") }))
        assertEquals(0, BatteryCycleCountRules.resolve(0, { error("unused") }, { error("unused") }))
    }

    @Test fun kernelDataTakesPriorityOverDefaultDiagnosticZero() {
        assertEquals(150, BatteryCycleCountRules.resolve(-1, { "150" }, { error("unused") }))
        assertEquals(0, BatteryCycleCountRules.resolve(null, { "0" }, { error("unused") }))
    }

    @Test fun diagnosticIsUsedOnlyIfBothStandardSourcesAreUnavailable() {
        assertEquals(37, BatteryCycleCountRules.resolve(null, { null }, { "37" }))
        assertNull(BatteryCycleCountRules.resolve(-1, { "-1" }, { "-1" }))
        assertNull(BatteryCycleCountRules.resolve(null, { null }, { null }))
    }
}
