package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SystemRootDetectionRulesTest {
    @Test fun enabledQueryReturnsFalseWithoutRunningTheOriginal() {
        assertEquals(false, SystemRootDetectionRules.query(true) { error("Must not query Root") })
    }

    @Test fun disabledQueryPreservesBothResultsAndCallsOriginalOnce() {
        for (value in listOf(false, true)) {
            var calls = 0
            assertEquals(value, SystemRootDetectionRules.query(false) { calls++; value })
            assertEquals(1, calls)
        }
    }

    @Test fun enabledCheckReportsNotRootedExactlyOnceWithoutProbes() {
        val results = mutableListOf<Boolean>()
        assertNull(SystemRootDetectionRules.check(true, results::add) { error("Must not run probes") })
        assertEquals(listOf(false), results)
    }

    @Test fun forcedRefreshUpdatesPreviouslyRootedCachedStatus() {
        var cachedRoot = true
        SystemRootDetectionRules.check(true, { cachedRoot = it }) { error("Must not run probes") }
        assertFalse(cachedRoot)
    }

    @Test fun disabledCheckRunsOriginalAndDoesNotSendAReplacementCallback() {
        var calls = 0
        val originalResult = Any()
        val result = SystemRootDetectionRules.check(false, { error("Must not replace callback") }) {
            calls++
            originalResult
        }
        assertSame(originalResult, result)
        assertEquals(1, calls)
    }

    @Test fun callbackFailureDoesNotRerunTheRootProbe() {
        var probes = 0
        val result = runCatching {
            SystemRootDetectionRules.check(true, { error("Callback disconnected") }) { probes++; null }
        }
        assertTrue(result.isFailure)
        assertEquals(0, probes)
    }

    @Test fun queryContractsRejectWrongArgumentsReturnTypeOrStaticMethods() {
        val string = String::class.java
        val boolean = Boolean::class.javaPrimitiveType!!
        val query = Fixture::class.java.getDeclaredMethod("isRoot", string)
        assertTrue(SystemRootDetectionRules.isInstanceMethod(query, boolean, string))
        assertFalse(SystemRootDetectionRules.isInstanceMethod(query, boolean))
        assertFalse(SystemRootDetectionRules.isInstanceMethod(query, Void.TYPE, string))
        assertFalse(SystemRootDetectionRules.isInstanceMethod(query, boolean, Integer.TYPE))
        val staticQuery = Fixture::class.java.getDeclaredMethod("staticRoot", string)
        assertFalse(SystemRootDetectionRules.isInstanceMethod(staticQuery, boolean, string))
    }

    @Test fun detectorContractRequiresAnInstanceVoidMethodWithCallback() {
        val check = Fixture::class.java.getDeclaredMethod("check", Callback::class.java)
        assertTrue(SystemRootDetectionRules.isInstanceMethod(check, Void.TYPE, Callback::class.java))
        assertFalse(SystemRootDetectionRules.isInstanceMethod(check, Boolean::class.javaPrimitiveType!!, Callback::class.java))
        assertFalse(SystemRootDetectionRules.isInstanceMethod(check, Void.TYPE, Any::class.java))
    }

    @Test fun abstractInterfaceMethodsAreNotInstalledAsConcreteHooks() {
        val notify = Callback::class.java.getDeclaredMethod("notifyRootStatus", Boolean::class.javaPrimitiveType!!)
        assertFalse(SystemRootDetectionRules.isInstanceMethod(notify, Void.TYPE, Boolean::class.javaPrimitiveType!!))
    }

    @Test fun targetsStayInsideTheOplusSystemService() {
        listOf(
            SystemRootDetectionRules.SERVICE, SystemRootDetectionRules.ROOT_SERVICE,
            SystemRootDetectionRules.DETECTOR, SystemRootDetectionRules.CALLBACK,
        ).forEach { assertTrue(it.startsWith("com.android.server.oplus.heimdall.")) }
    }

    private interface Callback { fun notifyRootStatus(rooted: Boolean) }

    @Suppress("UNUSED_PARAMETER")
    private class Fixture {
        fun isRoot(caller: String): Boolean = true
        fun check(callback: Callback) = callback.notifyRootStatus(true)
        companion object {
            @JvmStatic fun staticRoot(caller: String): Boolean = true
        }
    }
}
