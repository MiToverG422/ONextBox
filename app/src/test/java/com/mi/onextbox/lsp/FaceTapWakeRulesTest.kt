package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FaceTapWakeRulesTest {
    @Test fun handoffAdvancesOnlyTheRemainingNativeFrames() {
        val durations = listOf(33, 33, 42)
        assertEquals(FaceTapWakeRules.Position(0, 33), FaceTapWakeRules.position(durations, 0L))
        assertEquals(FaceTapWakeRules.Position(0, 23), FaceTapWakeRules.position(durations, 10L))
        assertEquals(FaceTapWakeRules.Position(1, 33), FaceTapWakeRules.position(durations, 33L))
        assertEquals(FaceTapWakeRules.Position(2, 32), FaceTapWakeRules.position(durations, 76L))
        assertEquals(FaceTapWakeRules.Position(2, 1), FaceTapWakeRules.position(durations, 107L))
    }

    @Test fun expiredRevealNeverRestartsOrLeavesAnAnimationLayer() {
        assertNull(FaceTapWakeRules.position(listOf(33, 33, 42), 108L))
        assertNull(FaceTapWakeRules.position(listOf(33, 33, 42), 1000L))
        assertNull(FaceTapWakeRules.position(listOf(33), -1L))
    }

    @Test fun invalidOrUnboundedNativeFramesFailClosed() {
        assertNull(FaceTapWakeRules.position(emptyList(), 0L))
        assertNull(FaceTapWakeRules.position(listOf(0, 33), 0L))
        assertNull(FaceTapWakeRules.position(listOf(-1), 0L))
        assertNull(FaceTapWakeRules.position(List(91) { 20 }, 0L))
        assertNull(FaceTapWakeRules.position(listOf(2501), 0L))
    }
}
