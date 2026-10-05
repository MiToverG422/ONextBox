package com.mi.onextbox.lsp.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernMethodHookTest {
    @Test
    fun settingResultBeforeCallSkipsOriginal() {
        val method = Fixture::class.java.getDeclaredMethod("echo", String::class.java)
        val param = ModernMethodHook.MethodHookParam(method, Fixture(), arrayOf("input"))

        param.result = "replacement"

        assertTrue(param.returnEarly)
        assertEquals("replacement", param.resultOrThrow())
    }

    @Test
    fun beforeFailureResetRestoresNormalCallState() {
        val method = Fixture::class.java.getDeclaredMethod("echo", String::class.java)
        val param = ModernMethodHook.MethodHookParam(method, Fixture(), arrayOf("input"))
        param.result = null

        param.resetAfterBeforeFailure()

        assertFalse(param.returnEarly)
        assertNull(param.result)
        assertNull(param.throwable)
    }

    @Test
    fun afterFailureCanRestoreOriginalOutcome() {
        val method = Fixture::class.java.getDeclaredMethod("echo", String::class.java)
        val param = ModernMethodHook.MethodHookParam(method, Fixture(), arrayOf("input"))
        val originalError = IllegalStateException("original")
        param.beginAfter(null, originalError)
        val outcome = param.snapshotOutcome()
        param.result = "overridden"

        param.restoreOutcome(outcome)

        assertSame(originalError, param.throwable)
    }

    private class Fixture {
        @Suppress("unused")
        fun echo(value: String): String = value
    }
}
