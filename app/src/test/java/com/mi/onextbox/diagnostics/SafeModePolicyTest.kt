package com.mi.onextbox.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SafeModePolicyTest {
    @Test fun threeCrashesWithinWindowLatchUntilReboot() {
        var state = SafeModePolicy.State("boot-a")
        state = SafeModePolicy.crash(state, 1000)
        state = SafeModePolicy.crash(state, 2000)
        assertFalse(state.blocked)
        state = SafeModePolicy.crash(state, 3000)
        assertTrue(state.blocked)
        assertTrue(SafeModePolicy.crash(state, 900000).blocked)
        assertTrue(SafeModePolicy.forBoot(state, "boot-a").blocked)
        assertFalse(SafeModePolicy.forBoot(state, "boot-b").blocked)
    }

    @Test fun isolatedCrashesDoNotAccumulateForever() {
        var state = SafeModePolicy.State("boot")
        listOf(1L, 130000L, 260000L).forEach { state = SafeModePolicy.crash(state, it) }
        assertFalse(state.blocked)
        assertEquals(listOf(260000L), state.crashes)
    }

    @Test fun onlyMainHighRiskProcessesAreProtected() {
        assertEquals("system", SafeModePolicy.scope("system_server", true))
        assertEquals("com.android.systemui", SafeModePolicy.scope("com.android.systemui", false))
        assertEquals("com.android.launcher", SafeModePolicy.scope("com.android.launcher", false))
        assertNull(SafeModePolicy.scope("com.android.settings", false))
        assertNull(SafeModePolicy.scope("com.android.systemui:screenshot", false))
    }

    @Test fun scopeIsolation() {
        val original = SafeModePolicy.State("boot")
        var system = original
        repeat(3) { system = SafeModePolicy.crash(system, it.toLong()) }
        assertTrue(system.blocked)
        assertFalse(original.blocked)
        assertTrue(original.crashes.isEmpty())
    }
}
