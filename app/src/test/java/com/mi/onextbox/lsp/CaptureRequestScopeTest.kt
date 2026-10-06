package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class CaptureRequestScopeTest {
    @Test fun normalRequestReturnsItsResultAndClearsTheScope() {
        val scope = CaptureRequestScope<String>()
        assertEquals(7, scope.withValue("aod") {
            assertEquals("aod", scope.get())
            7
        })
        assertNull(scope.get())
    }

    @Test fun nestedOrdinaryRequestsCannotInheritAnAodException() {
        val scope = CaptureRequestScope<String>()
        scope.withValue("outer") {
            scope.withValue(null) { assertNull(scope.get()) }
            assertEquals("outer", scope.get())
            scope.withValue("inner") { assertEquals("inner", scope.get()) }
            assertEquals("outer", scope.get())
        }
        assertNull(scope.get())
    }

    @Test fun stockExceptionsPropagateWithoutLeavingAnActiveScope() {
        val scope = CaptureRequestScope<String>()
        val expected = IllegalStateException("stock failure")
        val result = runCatching { scope.withValue("aod") { throw expected } }
        assertSame(expected, result.exceptionOrNull())
        assertNull(scope.get())
    }

    @Test fun asynchronousCallbacksDoNotInheritTheException() {
        val scope = CaptureRequestScope<String>()
        val otherThreadValue = AtomicReference<String?>("unread")
        scope.withValue("aod") {
            Thread { otherThreadValue.set(scope.get()) }.apply { start(); join() }
            assertNull(otherThreadValue.get())
            assertEquals("aod", scope.get())
        }
        assertNull(scope.get())
    }
}
