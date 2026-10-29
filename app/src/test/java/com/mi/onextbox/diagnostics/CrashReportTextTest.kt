package com.mi.onextbox.diagnostics

import org.junit.Assert.*
import org.junit.Test

class CrashReportTextTest {
    @Test fun preservesCauseAndSuppressedExceptions() {
        val error = IllegalStateException("outer", IllegalArgumentException("original cause"))
        error.addSuppressed(UnsupportedOperationException("cleanup failure"))
        val text = CrashReportText.stack(error)
        assertTrue(text.contains("IllegalStateException: outer"))
        assertTrue(text.contains("Caused by: java.lang.IllegalArgumentException: original cause"))
        assertTrue(text.contains("Suppressed: java.lang.UnsupportedOperationException: cleanup failure"))
        assertTrue(text.contains("CrashReportTextTest"))
    }

    @Test fun circularCausesDoNotPreventReportGeneration() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)
        assertTrue(CrashReportText.stack(first).contains("CIRCULAR REFERENCE"))
    }

    @Test fun fingerprintIsStableAndDistinguishesReports() {
        assertEquals(CrashReportText.fingerprint("堆栈"), CrashReportText.fingerprint("堆栈"))
        assertNotEquals(CrashReportText.fingerprint("first"), CrashReportText.fingerprint("second"))
        assertTrue(CrashReportText.fingerprint("stack").matches(Regex("[0-9a-f]{16}")))
    }
}
