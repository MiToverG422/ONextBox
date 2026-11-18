package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class DexThreadPolicyTest {
    @Test fun nativeDefaultsAreKeptWhenNoOverrideIsRequested() {
        val policy = DexThreadPolicy()
        repeat(10) { policy.configure(null) { fail("Must retain native defaults") } }
    }

    @Test fun repeatedOverridesDoNotRecreateTheNativeScheduler() {
        val policy = DexThreadPolicy()
        val applied = mutableListOf<Int>()
        listOf(1, 1, null, 1, 2, 2).forEach { policy.configure(it, applied::add) }
        assertEquals(listOf(1, 2), applied)
    }

    @Test fun failedConfigurationIsRetriedRatherThanRecordedAsSuccessful() {
        val policy = DexThreadPolicy()
        var attempts = 0
        assertThrows(IllegalStateException::class.java) { policy.configure(1) { attempts++; error("Native failure") } }
        policy.configure(1) { attempts++ }
        policy.configure(1) { fail("Already configured") }
        assertEquals(2, attempts)
    }
}
