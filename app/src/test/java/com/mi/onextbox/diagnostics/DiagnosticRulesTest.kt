package com.mi.onextbox.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticRulesTest {
    @Test fun fingerprintIgnoresOrderingAndDiagnosticMetadata() {
        val a = mapOf("a" to true, "b" to hashSetOf("one", "two"), "developer_trace_until" to 1L)
        val b = linkedMapOf("b" to sortedSetOf("two", "one"), "a" to true, "__ready" to true)
        assertEquals(DiagnosticRules.fingerprint(a), DiagnosticRules.fingerprint(b))
        assertNotEquals(DiagnosticRules.fingerprint(a), DiagnosticRules.fingerprint(b + ("a" to false)))
    }
    @Test fun numericTypesRemainDistinctForRoundTripSafety() {
        assertNotEquals(DiagnosticRules.fingerprint(mapOf("x" to 1)), DiagnosticRules.fingerprint(mapOf("x" to 1L)))
    }
    @Test fun temporaryLoggingExpiresAndRejectsClockRollback() {
        assertTrue(DiagnosticRules.temporaryLogging(10_000, 9_000, setOf("Camera"), "Camera"))
        assertFalse(DiagnosticRules.temporaryLogging(10_000, 9_000, setOf("Camera"), "Launcher"))
        assertTrue(DiagnosticRules.temporaryLogging(10_000, 9_000, setOf("*"), "Launcher"))
        assertFalse(DiagnosticRules.temporaryLogging(10_000, 10_000, setOf("*"), "Camera"))
        assertFalse(DiagnosticRules.temporaryLogging(3_000_000, 0, setOf("*"), "Camera"))
    }
    @Test fun exportRedactsKnownPersonalDataButPreservesStackFrames() {
        val source = "token=secret123 phone=13812345678 user=test@example.com " +
            "https://example.com/private?q=secret /storage/emulated/0/private.png 192.168.0.1 aa:bb:cc:dd:ee:ff\n" +
            "at com.mi.onextbox.MainActivity.onCreate(MainActivity.kt:50)"
        val redacted = DiagnosticRules.redact(source)
        listOf("secret123", "13812345678", "test@example.com", "private.png", "192.168.0.1", "aa:bb:cc:dd:ee:ff").forEach {
            assertFalse(redacted, redacted.contains(it))
        }
        assertTrue(redacted.contains("MainActivity.kt:50"))
    }
    @Test fun quotedSecretsAndBearerHeadersAreFiltered() {
        val text = DiagnosticRules.redact("{\"token\": \"private value\"} Bearer abc.def.ghi")
        assertFalse(text.contains("private value"))
        assertFalse(text.contains("abc.def.ghi"))
    }
}
