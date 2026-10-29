package com.mi.onextbox.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticTransactionTest {
    @Test fun failedPublicationRestoresBothCopies() {
        var local = "old"
        var remote = "old"
        val failure = IllegalStateException("connection lost")
        val result = runCatching { diagnosticTransaction(
            { local = "new" }, { remote = "new"; throw failure },
            { local = "old" }, { remote = local },
        ) }
        assertSame(failure, result.exceptionOrNull())
        assertEquals("old", local)
        assertEquals("old", remote)
    }
    @Test fun rollbackErrorsAreReportedWithoutHidingOriginalFailure() {
        val failure = IllegalStateException("write failed")
        val result = runCatching { diagnosticTransaction(
            { throw failure }, { fail("Must not publish") },
            { error("rollback failed") }, { error("remote rollback failed") },
        ) }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(2, failure.suppressed.size)
    }
    @Test fun successfulWriteDoesNotRollback() {
        var remote = false
        diagnosticTransaction({}, { remote = true }, { fail("Unexpected rollback") }, { fail("Unexpected republish") })
        assertTrue(remote)
    }
}
