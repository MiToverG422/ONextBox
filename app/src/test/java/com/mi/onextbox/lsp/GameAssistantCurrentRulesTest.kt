package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class GameAssistantCurrentRulesTest {
    @Test fun bookmarkLimitBypassPreservesLoggingAndPostInsertionPositions() {
        listOf(0, 7, 8, 20, 200).forEach { size ->
            assertEquals(size, GameAssistantCurrentRules.bookmarkSize(true, 1, size))
            assertEquals(size.coerceAtMost(7), GameAssistantCurrentRules.bookmarkSize(true, 2, size))
            assertEquals(size + 1, GameAssistantCurrentRules.bookmarkSize(true, 3, size + 1))
            assertEquals(size + 1, GameAssistantCurrentRules.bookmarkSize(true, 4, size + 1))
        }
    }

    @Test fun disablingTheOptionRestoresEveryNativeListSizeRead() {
        for (read in 0..6) for (size in listOf(0, 8, 20, 200))
            assertEquals(size, GameAssistantCurrentRules.bookmarkSize(false, read, size))
    }
}
