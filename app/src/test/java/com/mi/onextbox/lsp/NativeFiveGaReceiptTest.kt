package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFiveGaReceiptTest {
    @Test fun rejectsReorderedAndRepeatedCallbacks() {
        val receipt = NativeFiveGaReceipt()
        assertEquals(NativeFiveGaReceipt.Result.ACCEPT, receipt.receive("phone-a", 2))
        assertEquals(NativeFiveGaReceipt.Result.IGNORE, receipt.receive("phone-a", 1))
        assertEquals(NativeFiveGaReceipt.Result.IGNORE, receipt.receive("phone-a", 2))
        assertEquals(NativeFiveGaReceipt.Result.ACCEPT, receipt.receive("phone-a", 3))
    }

    @Test fun retiredConnectionCannotResurrectQueuedQualification() {
        val receipt = NativeFiveGaReceipt()
        assertEquals(NativeFiveGaReceipt.Result.ACCEPT, receipt.receive("phone-a", 0))
        assertEquals(NativeFiveGaReceipt.Result.INVALID, receipt.receive(null, -1))
        assertEquals(NativeFiveGaReceipt.Result.IGNORE, receipt.receive("phone-a", 1))
        assertEquals(NativeFiveGaReceipt.Result.IGNORE, receipt.receive("phone-b", 0))
    }

    @Test fun newEndpointRequiresNewReceipt() {
        val old = NativeFiveGaReceipt()
        assertEquals(NativeFiveGaReceipt.Result.ACCEPT, old.receive("phone-a", 12))
        assertEquals(NativeFiveGaReceipt.Result.INVALID, old.receive("phone-b", 13))
        assertEquals(NativeFiveGaReceipt.Result.ACCEPT, NativeFiveGaReceipt().receive("phone-b", 0))
    }

    @Test fun rejectsMalformedSessions() {
        assertEquals(NativeFiveGaReceipt.Result.INVALID, NativeFiveGaReceipt().receive("", 0))
        assertEquals(NativeFiveGaReceipt.Result.INVALID, NativeFiveGaReceipt().receive("phone", -1))
    }

    @Test fun publisherAndReadersHaveSeparateBoundaries() {
        assertTrue(NativeFiveGaAccess.publisher(1001))
        assertFalse(NativeFiveGaAccess.publisher(1000))
        assertFalse(NativeFiveGaAccess.publisher(0))
        assertTrue(NativeFiveGaAccess.reader(1000, 12345))
        assertTrue(NativeFiveGaAccess.reader(12345, 12345))
        assertFalse(NativeFiveGaAccess.reader(12346, 12345))
        assertFalse(NativeFiveGaAccess.reader(1001, 12345))
        assertFalse(NativeFiveGaAccess.reader(0, 0))
        assertTrue(NativeFiveGaAccess.reader(10309, 12345, 10309))
        assertFalse(NativeFiveGaAccess.reader(10310, 12345, 10309))
        assertFalse(NativeFiveGaAccess.reader(10309, 12345, -1))
    }
}
