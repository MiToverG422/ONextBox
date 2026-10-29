package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BrowserPurifyRulesTest {
    @Test fun nativeAndCustomNovelTabsAreRemovedWithoutReorderingOtherTabs() {
        val input = listOf(1, 8, 9, 2, 5, 40)
        assertEquals(listOf(1, 9, 2, 40), BrowserPurifyRules.filterTabs(input, { it }, false, false, true))
        assertEquals(listOf(9, 40), BrowserPurifyRules.filterTabs(input, { it }, true, true, true))
    }

    @Test fun unknownAndUnreadableTabsAreRetained() {
        val input = listOf<Int?>(null, 4, 9, 100)
        assertEquals(input, BrowserPurifyRules.filterTabs(input, { it }, true, true, true))
    }

    @Test fun neverLeaveBrowserWithAnEmptyBottomBar() {
        val input = listOf(2, 5)
        assertSame(input, BrowserPurifyRules.filterTabs(input, { it }, true, true, true))
    }

    @Test fun keepHomeAvailableIfNativeModeDoesNotProvideAnAiDestination() {
        assertEquals(listOf(1, 40), BrowserPurifyRules.filterTabs(listOf(1, 2, 5, 40), { it }, true, true, true))
    }

    @Test fun preserveAvailableEngineSelectionAndRecoverUnsupportedSelection() {
        assertEquals("google", BrowserPurifyRules.currentEngineKey("google"))
        assertEquals("baidu", BrowserPurifyRules.currentEngineKey("baidu"))
        assertEquals("bing_ov", BrowserPurifyRules.currentEngineKey("sogou"))
        assertEquals("bing_ov", BrowserPurifyRules.currentEngineKey(null))
    }
}
