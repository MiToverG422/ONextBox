package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class DisplayEnhancementThermalOverrideTest {
    private class NativeState(@JvmField var blocked: Boolean)
    private fun override() = DisplayEnhancementThermalOverride(NativeState::class.java.getDeclaredField("blocked"))

    @Test fun disabledFeatureRetainsTheNativeDecisionAndState() {
        val native = NativeState(true)
        val policy = override()
        assertTrue(policy.decide(native, false) { native.blocked })
        assertTrue(native.blocked)
    }

    @Test fun enabledFeatureOnlyMasksTheDecisionAndRestoresItsGenuineState() {
        val native = NativeState(true)
        val policy = override()
        assertFalse(policy.decide(native, true) { native.blocked })
        assertTrue(native.blocked)
        assertTrue(policy.decide(native, false) { native.blocked })
    }

    @Test fun exceptionsCannotLeaveTheNativeFlagCleared() {
        val native = NativeState(true)
        val policy = override()
        val failure = IllegalStateException("OEM decision failed")
        assertSame(failure, runCatching { policy.decide(native, true) { throw failure } }.exceptionOrNull())
        assertTrue(native.blocked)
        assertTrue(policy.observed(native, true))
    }

    @Test fun freshThermalReadUpdatesRestorationRatherThanRestoringAStaleCachedValue() {
        val native = NativeState(false)
        val policy = override()
        policy.decide(native, true) {
            native.blocked = policy.observed(native, true)
            assertFalse(native.blocked)
        }
        assertTrue(native.blocked)
        policy.decide(native, true) { native.blocked = policy.observed(native, false) }
        assertFalse(native.blocked)
    }

    @Test fun nestedNativeDecisionsShareTheScopeAndRestoreOnlyOnce() {
        val native = NativeState(true)
        val policy = override()
        policy.decide(native, true) {
            policy.decide(native, true) { assertFalse(native.blocked) }
            assertFalse(native.blocked)
        }
        assertTrue(native.blocked)
    }

    @Test fun anotherOwnerAndUnrelatedThreadsRetainTheirRealThermalRead() {
        val native = NativeState(true)
        val other = NativeState(true)
        val policy = override()
        policy.decide(native, true) {
            assertTrue(policy.observed(other, true))
            var otherThread: Boolean? = null
            val worker = Thread { otherThread = policy.observed(native, true) }
            worker.start()
            worker.join(2000)
            assertFalse(worker.isAlive)
            assertEquals(true, otherThread)
        }
        assertTrue(native.blocked)
    }

    @Test fun nestedDifferentOwnersRestoreBothFramesIncludingUpdatedState() {
        val native = NativeState(false)
        val other = NativeState(true)
        val policy = override()
        policy.decide(native, true) {
            policy.decide(other, true) { assertFalse(other.blocked) }
            assertTrue(other.blocked)
            assertFalse(policy.observed(native, true))
        }
        assertTrue(native.blocked)
    }

    @Test fun manyEnableDisableCyclesNeverEraseTheGenuineFlag() {
        val native = NativeState(true)
        val policy = override()
        repeat(100) {
            assertFalse(policy.decide(native, true) { native.blocked })
            assertTrue(policy.decide(native, false) { native.blocked })
        }
    }

    @Test fun pausedNestedHookCannotLeaveAStaleFlagWhenTheNativeTemperatureChanges() {
        val native = NativeState(false)
        val policy = override()
        var genuine = true
        policy.decide(native, true, genuineAfter = { policy.observed(native, genuine) }) {
            native.blocked = genuine // Simulates a nested Hook falling through during retirement.
        }
        assertTrue(native.blocked)
        genuine = false
        policy.decide(native, true, genuineAfter = { policy.observed(native, genuine) }) { native.blocked = genuine }
        assertFalse(native.blocked)
    }

    @Test fun failedAdditionalReadStillRestoresTheLastSuccessfullyObservedNativeState() {
        val native = NativeState(true)
        val policy = override()
        policy.decide(native, true, genuineAfter = { error("OEM setting unavailable") }) { assertFalse(native.blocked) }
        assertTrue(native.blocked)
        assertTrue(policy.observed(native, true))
    }
}
