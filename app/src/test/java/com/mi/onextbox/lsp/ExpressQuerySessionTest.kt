package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressQuerySessionTest {
    private val number = "123456789012"

    @Test fun defaultSessionDoesNotSubmitOrSuppressTheKeyboard() {
        val session = ExpressQuerySession()
        assertFalse(session.inExpress)
        assertNull(session.pending(0))
        assertFalse(session.imeSuppressed(0))
    }

    @Test fun markedQueryHasABoundedSubmissionWindow() {
        val session = ExpressQuerySession()
        session.receive(ExpressCardRules.destination(number), 1_000)
        assertTrue(session.inExpress)
        assertEquals(number, session.pending(1_000)?.number)
        assertTrue(session.imeSuppressed(2_999))
        assertFalse(session.imeSuppressed(3_000))
        assertNull(session.pending(7_000))
    }

    @Test fun manuallyOpenedExpressPageDoesNotAutoSubmit() {
        val session = ExpressQuerySession()
        session.receive("hap://app/${ExpressCardRules.EXPRESS}/pages/expressSearch?expressNo=$number", 1_000)
        assertTrue(session.inExpress)
        assertNull(session.pending(1_000))
        assertFalse(session.imeSuppressed(1_000))
    }

    @Test fun openingAnotherQuickAppClearsThePendingQuery() {
        val session = ExpressQuerySession()
        session.receive(ExpressCardRules.destination(number), 1_000)
        session.receive("hap://app/another.app/pages/home", 1_001)
        assertFalse(session.inExpress)
        assertNull(session.pending(1_001))
        assertFalse(session.imeSuppressed(1_001))
    }

    @Test fun completedQueryCannotSubmitTwice() {
        val session = ExpressQuerySession()
        session.receive(ExpressCardRules.destination(number), 1_000)
        val query = requireNotNull(session.pending(1_000))
        session.complete(query, 1_100)
        assertNull(session.pending(1_100))
        assertFalse(session.matches(query, 1_100))
        assertTrue(session.imeSuppressed(1_119))
        assertFalse(session.imeSuppressed(1_120))
    }

    @Test fun delayedOldQueryCannotCompleteANewerQueryWithTheSameNumber() {
        val session = ExpressQuerySession()
        session.receive(ExpressCardRules.destination(number), 1_000)
        val old = requireNotNull(session.pending(1_000))
        session.receive(ExpressCardRules.destination(number), 1_001)
        val current = requireNotNull(session.pending(1_001))
        assertFalse(session.matches(old, 1_001))
        session.complete(old, 1_001)
        assertEquals(current, session.pending(1_001))
    }

    @Test fun keyboardSuppressionCannotExtendPastTheDeadline() {
        val session = ExpressQuerySession()
        session.receive(ExpressCardRules.destination(number), 1_000)
        val query = requireNotNull(session.pending(1_000))
        session.suppressIme(query, 6_999)
        assertTrue(session.imeSuppressed(6_999))
        assertFalse(session.imeSuppressed(7_000))
        session.suppressIme(query, 7_001)
        assertFalse(session.imeSuppressed(7_001))
    }
}
