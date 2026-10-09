package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FluidCloudPanelDrawGateTest {
    @Test fun leavesGlobalPolicyUntouchedOutsideTargetDraw() {
        val gate = FluidCloudPanelDrawGate()
        assertFalse(gate.consumeCustomBackgroundBypass())
        gate.duringDraw(false) { assertFalse(gate.consumeCustomBackgroundBypass()) }
        assertFalse(gate.consumeCustomBackgroundBypass())
    }

    @Test fun onlyDispatchCheckChangesNotLaterAnimationChecks() {
        val gate = FluidCloudPanelDrawGate()
        gate.duringDraw(true) {
            assertTrue(gate.consumeCustomBackgroundBypass())
            repeat(3) { assertFalse(gate.consumeCustomBackgroundBypass()) }
        }
        assertFalse(gate.consumeCustomBackgroundBypass())
    }

    @Test fun nestedOrdinaryCardCannotConsumeOuterCustomDispatch() {
        val gate = FluidCloudPanelDrawGate()
        gate.duringDraw(true) {
            gate.duringDraw(false) { assertFalse(gate.consumeCustomBackgroundBypass()) }
            assertTrue(gate.consumeCustomBackgroundBypass())
        }
    }

    @Test fun nestedCustomDrawHasItsOwnDispatch() {
        val gate = FluidCloudPanelDrawGate()
        gate.duringDraw(true) {
            gate.duringDraw(true) {
                assertTrue(gate.consumeCustomBackgroundBypass())
                assertFalse(gate.consumeCustomBackgroundBypass())
            }
            assertTrue(gate.consumeCustomBackgroundBypass())
        }
    }

    @Test fun exceptionCannotLeakBypassToOtherNativeDraws() {
        val gate = FluidCloudPanelDrawGate()
        runCatching { gate.duringDraw(true) { error("interrupted draw") } }
        assertFalse(gate.consumeCustomBackgroundBypass())
    }

    @Test fun drawScopeDoesNotAffectAnotherThread() {
        val gate = FluidCloudPanelDrawGate()
        gate.duringDraw(true) {
            var otherThreadBypassed = true
            Thread { otherThreadBypassed = gate.consumeCustomBackgroundBypass() }.apply { start(); join() }
            assertFalse(otherThreadBypassed)
            assertTrue(gate.consumeCustomBackgroundBypass())
        }
    }
}
