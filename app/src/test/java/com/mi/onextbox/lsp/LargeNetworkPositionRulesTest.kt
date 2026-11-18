package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class LargeNetworkPositionRulesTest {
    @Test fun movesOnlyNetworkTypeAcrossSignalAndKeepsRoamingInPlace() {
        assertEquals(listOf("type", "signal"),
            LargeNetworkPositionRules.arrange(listOf("signal", "type"), true, 0))
        assertEquals(listOf("roaming", "type", "signal"),
            LargeNetworkPositionRules.arrange(listOf("roaming", "signal", "type"), true, 0))
    }

    @Test fun nativeAndDisabledPathsRetainTheOriginalList() {
        val children = listOf("signal", "type")
        assertSame(children, LargeNetworkPositionRules.arrange(children, true, 1))
        assertSame(children, LargeNetworkPositionRules.arrange(children, false, 0))
        assertSame(children, LargeNetworkPositionRules.arrange(children, true, -4))
    }

    @Test fun unexpectedLayoutsAreNeverReordered() {
        for (count in listOf(0, 1, 4, 8)) {
            val children = (0 until count).toList()
            assertSame(children, LargeNetworkPositionRules.arrange(children, true, 0))
        }
    }

    @Test fun physicalSideRemainsCorrectInRtl() {
        val children = listOf("signal", "type")
        assertSame(children, LargeNetworkPositionRules.arrange(children, true, 0, rtl = true))
        assertEquals(listOf("type", "signal"), LargeNetworkPositionRules.arrange(children, true, 1, rtl = true))
    }

    @Test fun invalidStoredValuesFallBackToNativeRightSide() {
        assertEquals(0, LargeNetworkPositionRules.normalize(0))
        listOf(-1, 1, 2, Int.MAX_VALUE).forEach { assertEquals(1, LargeNetworkPositionRules.normalize(it)) }
    }
}
