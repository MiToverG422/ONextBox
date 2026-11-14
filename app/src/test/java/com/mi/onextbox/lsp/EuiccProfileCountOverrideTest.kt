package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class EuiccProfileCountOverrideTest {
    private class Controller(@JvmField var count: Int)
    private val field = Controller::class.java.getDeclaredField("count").apply { isAccessible = true }

    @Test fun bothRegionalLimitsPermitAddAndRestoreTheRealCount() {
        for (limit in listOf(2, 10)) {
            val owner = Controller(limit)
            val allowed = EuiccProfileCountOverride.withUnrestrictedCount(owner, field) { owner.count < limit }
            assertTrue(allowed)
            assertEquals(limit, owner.count)
        }
    }

    @Test fun exceptionRestoresCountAndPropagatesWithoutReplayingTheCallback() {
        val owner = Controller(2)
        val error = IllegalStateException("native failure")
        var calls = 0
        val caught = runCatching {
            EuiccProfileCountOverride.withUnrestrictedCount(owner, field) { calls++; throw error }
        }.exceptionOrNull()
        assertSame(error, caught)
        assertEquals(1, calls)
        assertEquals(2, owner.count)
    }

    @Test fun nativeRefreshDuringTheCallbackIsNotOverwritten() {
        val owner = Controller(2)
        EuiccProfileCountOverride.withUnrestrictedCount(owner, field) { owner.count = 3 }
        assertEquals(3, owner.count)
    }

    @Test fun nestedCallbackRestoresOnlyAtTheOuterBoundary() {
        val owner = Controller(10)
        EuiccProfileCountOverride.withUnrestrictedCount(owner, field) {
            EuiccProfileCountOverride.withUnrestrictedCount(owner, field) { assertEquals(0, owner.count) }
            assertEquals(0, owner.count)
        }
        assertEquals(10, owner.count)
    }

    @Test fun emptyOrUnknownNativeCountsAreRetained() {
        for (count in listOf(0, -1)) {
            val owner = Controller(count)
            assertEquals("native", EuiccProfileCountOverride.withUnrestrictedCount(owner, field) {
                assertEquals(count, owner.count); "native"
            })
            assertEquals(count, owner.count)
        }
    }
}
