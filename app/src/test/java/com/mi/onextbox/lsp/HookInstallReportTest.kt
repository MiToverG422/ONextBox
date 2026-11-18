package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class HookInstallReportTest {
    @Test fun emptyHandleSetIsReportedAsMissingRatherThanInstalled() {
        val report = HookInstallReport()
        report.record("Renamed#method", 0)
        assertEquals(0, report.handles)
        assertEquals(0, report.matchedGroups)
        assertEquals("unmatched", report.status)
        assertEquals(setOf("Renamed#method"), report.missingTargets)
    }
    @Test fun overloadedMethodsAndMissingClassesProduceAnAccuratePartialReport() {
        val report = HookInstallReport()
        report.record("Preference#setVisible", 2)
        report.record("Fragment#onResume", 1)
        report.missing("class:OptionalVariant")
        assertEquals(3, report.handles)
        assertEquals(2, report.matchedGroups)
        assertEquals("partial", report.status)
        assertTrue(report.summary().contains("methods=3"))
        assertTrue(report.summary().contains("OptionalVariant"))
    }
}
