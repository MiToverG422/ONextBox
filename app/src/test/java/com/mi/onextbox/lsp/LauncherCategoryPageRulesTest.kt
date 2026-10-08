package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.LauncherCategoryPageRules.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCategoryPageRulesTest {
    @Test fun `enabled standard-mode category page receives the native application binding`() {
        assertTrue(LauncherCategoryPageRules.shouldBindApps(true, true, false))
        assertFalse(LauncherCategoryPageRules.shouldBindApps(false, true, false))
    }

    @Test fun `category loader compatibility does not disable native bindings or enable other modes`() {
        for (enabled in listOf(false, true)) {
            for (standard in listOf(false, true)) {
                assertTrue(LauncherCategoryPageRules.shouldBindApps(enabled, standard, true))
            }
            assertFalse(LauncherCategoryPageRules.shouldBindApps(enabled, false, false))
        }
    }

    @Test fun `completed entry and cancelled exit both retain the category page`() {
        assertTrue(LauncherCategoryPageRules.showCategories(Direction.Enter, true))
        assertTrue(LauncherCategoryPageRules.showCategories(Direction.Exit, false))
        assertFalse(LauncherCategoryPageRules.showCategories(Direction.Enter, false))
        assertFalse(LauncherCategoryPageRules.showCategories(Direction.Exit, true))
    }

    @Test fun `new desktop press finishes the closing transition without swallowing down`() {
        assertTrue(LauncherCategoryPageRules.handOffDesktopTouch(Direction.Exit, true))
        assertTrue(LauncherCategoryPageRules.handOffDesktopTouch(Direction.Enter, false))
        assertFalse(LauncherCategoryPageRules.handOffDesktopTouch(Direction.Enter, true))
        assertFalse(LauncherCategoryPageRules.handOffDesktopTouch(Direction.Exit, false))
    }

    @Test fun `folder dragging and long pressing cannot turn into page swipes`() {
        assertTrue(LauncherCategoryPageRules.canClaimSwipe(false, 120, 500))
        assertFalse(LauncherCategoryPageRules.canClaimSwipe(true, 120, 500))
        assertFalse(LauncherCategoryPageRules.canClaimSwipe(false, 500, 500))
        assertFalse(LauncherCategoryPageRules.canClaimSwipe(false, 1200, 500))
        assertTrue(LauncherCategoryPageRules.canClaimSwipe(false, -1, 500))
        assertFalse(LauncherCategoryPageRules.canClaimSwipe(false, 1, 0))
    }

    @Test fun `independent folder order retains new categories and ignores removed or duplicate saved aliases`() {
        assertEquals(listOf("games", "tools", "social", "new"), LauncherCategoryPageRules.categoryOrder(
            listOf("social", "tools", "games", "new"), listOf("games", "missing", "games", "tools")))
        assertEquals(listOf("social", "tools"), LauncherCategoryPageRules.categoryOrder(listOf("social", "tools"), emptyList()))
        assertEquals(emptyList<String>(), LauncherCategoryPageRules.categoryOrder(emptyList(), listOf("missing")))
    }

    @Test fun `only an app launched from an independent category page retains its return destination`() {
        assertTrue(LauncherCategoryPageRules.retainAppReturn(true, true, true, false))
        assertFalse(LauncherCategoryPageRules.retainAppReturn(false, true, true, false))
        assertFalse(LauncherCategoryPageRules.retainAppReturn(true, false, true, false))
        assertFalse(LauncherCategoryPageRules.retainAppReturn(true, true, false, false))
        assertFalse(LauncherCategoryPageRules.retainAppReturn(true, true, true, true))
    }

    @Test fun `app return keeps only its original independent folder open`() {
        assertTrue(LauncherCategoryPageRules.retainExpandedFolder(true, true))
        assertFalse(LauncherCategoryPageRules.retainExpandedFolder(true, false))
        assertFalse(LauncherCategoryPageRules.retainExpandedFolder(false, true))
        assertFalse(LauncherCategoryPageRules.retainExpandedFolder(false, false))
    }

    @Test fun `proxy touch during an app return cannot clear the category return destination`() {
        assertTrue(LauncherCategoryPageRules.isPanelTouch(true, true, false, false))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(false, true, false, false))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, false, false, false))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, true, true, false))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, true, false, true))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, true, true, true))
    }

    @Test fun `unavailable native animation or lifecycle queries retain the app return destination`() {
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, null, false, false))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, true, null, false))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, true, false, null))
        assertFalse(LauncherCategoryPageRules.isPanelTouch(true, null, null, null))
    }

    @Test fun `search and content use real screen insets instead of the old drawer header`() {
        assertEquals(LauncherCategoryPageRules.Layout(72, 192, 48),
            LauncherCategoryPageRules.layout(48, 0, 108, 24, 0, 3f))
        assertEquals(LauncherCategoryPageRules.Layout(24, 144, 48),
            LauncherCategoryPageRules.layout(48, 48, 108, 24, 0, 3f))
    }

    @Test fun `keyboard does not move the search row or category content origin`() {
        val hidden = LauncherCategoryPageRules.layout(48, 0, 108, 24, 0, 3f)
        val shown = LauncherCategoryPageRules.layout(48, 0, 108, 24, 800, 3f)
        assertEquals(hidden.searchTop, shown.searchTop)
        assertEquals(hidden.contentTop, shown.contentTop)
        assertEquals(824, shown.bottomInset)
    }

    @Test fun `keyboard resizes search results but leaves category and palette viewports stable`() {
        assertEquals(0, LauncherCategoryPageRules.listImeInset(false, 1264))
        assertEquals(1264, LauncherCategoryPageRules.listImeInset(true, 1264))
        assertEquals(0, LauncherCategoryPageRules.listImeInset(true, -1))
        assertEquals(0, LauncherCategoryPageRules.listImeInset(false, 0))
    }

    @Test fun `top fade follows status inset and relocated search row`() {
        assertEquals(LauncherCategoryPageRules.Fade(160, 416),
            LauncherCategoryPageRules.topFade(160, 416, 0, 3168))
        assertEquals(LauncherCategoryPageRules.Fade(112, 368),
            LauncherCategoryPageRules.topFade(160, 416, 48, 3168))
        assertEquals(LauncherCategoryPageRules.Fade(0, 0),
            LauncherCategoryPageRules.topFade(160, 416, 656, 3168))
    }

    @Test fun `fade bounds remain valid before measuring and for short viewports`() {
        assertEquals(LauncherCategoryPageRules.Fade(0, 0),
            LauncherCategoryPageRules.topFade(160, 416, 0, 0))
        assertEquals(LauncherCategoryPageRules.Fade(160, 200),
            LauncherCategoryPageRules.topFade(160, 416, 0, 200))
        assertEquals(LauncherCategoryPageRules.Fade(200, 200),
            LauncherCategoryPageRules.topFade(300, 416, 0, 200))
        assertEquals(LauncherCategoryPageRules.Fade(0, 0),
            LauncherCategoryPageRules.topFade(-1, -1, -1, -1))
    }

    @Test fun `entry waits for the last page edge instead of snapping from an earlier page`() {
        assertTrue(LauncherCategoryPageRules.isAtEntryEdge(2880, 2880, 24))
        assertTrue(LauncherCategoryPageRules.isAtEntryEdge(2860, 2880, 24))
        assertFalse(LauncherCategoryPageRules.isAtEntryEdge(1440, 2880, 24))
        assertFalse(LauncherCategoryPageRules.isAtEntryEdge(2800, 2880, 24))
    }

    @Test fun `edge comparison handles RTL negative scroll and avoids integer overflow`() {
        assertTrue(LauncherCategoryPageRules.isAtEntryEdge(-20, 0, 24))
        assertTrue(LauncherCategoryPageRules.isAtEntryEdge(0, 0, -1))
        assertFalse(LauncherCategoryPageRules.isAtEntryEdge(1, 0, -1))
        assertFalse(LauncherCategoryPageRules.isAtEntryEdge(Int.MIN_VALUE, Int.MAX_VALUE, 24))
    }

    @Test fun `list does not reserve the old bottom search height`() {
        assertEquals(32, LauncherCategoryPageRules.layout(48, 0, 108, 24, 0, 1f).bottomInset)
        assertEquals(32, LauncherCategoryPageRules.layout(48, 0, 216, 24, 0, 1f).bottomInset)
    }

    @Test fun `invalid insets and density keep layout bounds nonnegative`() {
        assertEquals(LauncherCategoryPageRules.Layout(8, 12, 8),
            LauncherCategoryPageRules.layout(-10, 40, -1, -10, -20, Float.NaN))
        assertEquals(LauncherCategoryPageRules.Layout(8, 52, 8),
            LauncherCategoryPageRules.layout(0, 0, 40, 0, 0, 0f))
    }

    @Test fun `search row outer edges include the folder background inset`() {
        assertEquals(1376, LauncherCategoryPageRules.searchRowWidth(1440, 64))
        assertEquals(968, LauncherCategoryPageRules.searchRowWidth(1000, 32))
        assertEquals(1440, LauncherCategoryPageRules.searchRowWidth(1440, 0))
    }

    @Test fun `unmeasured row and invalid folder gaps never produce negative widths`() {
        assertEquals(0, LauncherCategoryPageRules.searchRowWidth(0, 64))
        assertEquals(0, LauncherCategoryPageRules.searchRowWidth(-10, 64))
        assertEquals(0, LauncherCategoryPageRules.searchRowWidth(100, Int.MAX_VALUE))
        assertEquals(100, LauncherCategoryPageRules.searchRowWidth(100, -10))
    }

    @Test fun `standard and drawer modes are eligible without changing the mode`() {
        assertTrue(LauncherCategoryPageRules.supportsMode(true, false))
        assertTrue(LauncherCategoryPageRules.supportsMode(false, true))
        assertFalse(LauncherCategoryPageRules.supportsMode(false, false))
    }

    @Test fun `full screen list preserves initial content position with padding instead of clipping`() {
        assertEquals(416, LauncherCategoryPageRules.contentPadding(416, 0))
        assertEquals(368, LauncherCategoryPageRules.contentPadding(416, 48))
        assertEquals(0, LauncherCategoryPageRules.contentPadding(416, 500))
        assertEquals(0, LauncherCategoryPageRules.contentPadding(-10, -20))
    }

    @Test fun `color title and first app row have separate top anchored space`() {
        assertEquals(LauncherCategoryPageRules.ColorLayout(448, 656),
            LauncherCategoryPageRules.colorLayout(416, 160, 4f))
        assertEquals(LauncherCategoryPageRules.ColorLayout(8, 20),
            LauncherCategoryPageRules.colorLayout(-10, -20, Float.NaN))
    }

    @Test fun `color layout does not depend on result count or old footer height`() {
        val layout = LauncherCategoryPageRules.colorLayout(192, 120, 3f)
        assertEquals(216, layout.titleTop)
        assertEquals(372, layout.listTop)
        assertTrue(layout.listTop > layout.titleTop + 120)
    }

    @Test fun `entry is available only on the rightmost desktop page`() {
        assertFalse(LauncherCategoryPageRules.isRightmostPage(0, 3, 1, false))
        assertFalse(LauncherCategoryPageRules.isRightmostPage(1, 3, 1, false))
        assertTrue(LauncherCategoryPageRules.isRightmostPage(2, 3, 1, false))
        assertTrue(LauncherCategoryPageRules.isRightmostPage(0, 1, 1, false))
    }

    @Test fun `tablet and unfolded dual panels use the last visible page group`() {
        assertFalse(LauncherCategoryPageRules.isRightmostPage(2, 6, 2, false))
        assertTrue(LauncherCategoryPageRules.isRightmostPage(4, 6, 2, false))
        assertTrue(LauncherCategoryPageRules.isRightmostPage(2, 3, 2, false))
        assertTrue(LauncherCategoryPageRules.isRightmostPage(0, 1, 2, false))
    }

    @Test fun `RTL uses the physically rightmost desktop page`() {
        assertTrue(LauncherCategoryPageRules.isRightmostPage(0, 3, 1, true))
        assertFalse(LauncherCategoryPageRules.isRightmostPage(2, 3, 1, true))
    }

    @Test fun `unbound and invalid page indices never start the entry`() {
        assertFalse(LauncherCategoryPageRules.isRightmostPage(0, 0, 1, false))
        assertFalse(LauncherCategoryPageRules.isRightmostPage(-1, 3, 1, false))
        assertFalse(LauncherCategoryPageRules.isRightmostPage(3, 3, 1, false))
        assertFalse(LauncherCategoryPageRules.isRightmostPage(0, 3, 0, false))
    }

    @Test fun `leftward entry and rightward exit require a horizontal drag`() {
        assertTrue(LauncherCategoryPageRules.canStart(-40f, 5f, Direction.Enter, 10f))
        assertTrue(LauncherCategoryPageRules.canStart(40f, 5f, Direction.Exit, 10f))
        assertFalse(LauncherCategoryPageRules.canStart(40f, 5f, Direction.Enter, 10f))
        assertFalse(LauncherCategoryPageRules.canStart(-40f, 5f, Direction.Exit, 10f))
        assertFalse(LauncherCategoryPageRules.canStart(-10f, 0f, Direction.Enter, 10f))
        assertFalse(LauncherCategoryPageRules.canStart(-40f, 80f, Direction.Enter, 10f))
    }

    @Test fun `drag fraction follows the finger and allows reversal`() {
        assertEquals(0.3f, LauncherCategoryPageRules.progress(-300f, 1000f, Direction.Enter), 0.0001f)
        assertEquals(0.1f, LauncherCategoryPageRules.progress(-100f, 1000f, Direction.Enter), 0.0001f)
        assertEquals(0.3f, LauncherCategoryPageRules.progress(300f, 1000f, Direction.Exit), 0.0001f)
        assertEquals(0f, LauncherCategoryPageRules.progress(100f, 1000f, Direction.Enter), 0f)
        assertEquals(1f, LauncherCategoryPageRules.progress(-1200f, 1000f, Direction.Enter), 0f)
    }

    @Test fun `slow release uses the native forty percent page threshold`() {
        assertFalse(LauncherCategoryPageRules.shouldComplete(-300f, 1000f, 0f, Direction.Enter, 600f))
        assertFalse(LauncherCategoryPageRules.shouldComplete(-400f, 1000f, 0f, Direction.Enter, 600f))
        assertTrue(LauncherCategoryPageRules.shouldComplete(-401f, 1000f, 0f, Direction.Enter, 600f))
        assertTrue(LauncherCategoryPageRules.shouldComplete(500f, 1000f, 0f, Direction.Exit, 600f))
    }

    @Test fun `a deliberate fling completes a short drag in either direction`() {
        assertTrue(LauncherCategoryPageRules.shouldComplete(-120f, 1000f, -1200f, Direction.Enter, 600f))
        assertTrue(LauncherCategoryPageRules.shouldComplete(120f, 1000f, 1200f, Direction.Exit, 600f))
        assertFalse(LauncherCategoryPageRules.shouldComplete(-10f, 1000f, -1200f, Direction.Enter, 600f))
    }

    @Test fun `reversing a fling returns to the original page`() {
        assertFalse(LauncherCategoryPageRules.shouldComplete(-600f, 1000f, 1200f, Direction.Enter, 600f))
        assertFalse(LauncherCategoryPageRules.shouldComplete(600f, 1000f, -1200f, Direction.Exit, 600f))
    }

    @Test fun `desktop and category panel stay adjacent throughout the transition`() {
        for (direction in Direction.entries) {
            for (progress in listOf(0f, 0.1f, 0.5f, 0.9f, 1f)) {
                val offsets = LauncherCategoryPageRules.offsets(progress, 1080f, direction)
                assertEquals(1080f, offsets.apps - offsets.workspace, 0.0001f)
            }
        }
        assertEquals(LauncherCategoryPageRules.Offsets(0f, 1080f), LauncherCategoryPageRules.offsets(0f, 1080f, Direction.Enter))
        assertEquals(LauncherCategoryPageRules.Offsets(-1080f, 0f), LauncherCategoryPageRules.offsets(1f, 1080f, Direction.Enter))
        assertEquals(LauncherCategoryPageRules.Offsets(0f, 1080f), LauncherCategoryPageRules.offsets(1f, 1080f, Direction.Exit))
    }

    @Test fun `invalid geometry and non finite input never complete a gesture`() {
        assertFalse(LauncherCategoryPageRules.canStart(Float.NaN, 0f, Direction.Enter, 10f))
        assertFalse(LauncherCategoryPageRules.canStart(-50f, Float.POSITIVE_INFINITY, Direction.Enter, 10f))
        assertEquals(0f, LauncherCategoryPageRules.progress(-100f, 0f, Direction.Enter), 0f)
        assertEquals(0f, LauncherCategoryPageRules.progress(Float.NaN, 1000f, Direction.Enter), 0f)
        assertFalse(LauncherCategoryPageRules.shouldComplete(-500f, 0f, 0f, Direction.Enter, 600f))
        assertFalse(LauncherCategoryPageRules.shouldComplete(-500f, 1000f, Float.NaN, Direction.Enter, 600f))
    }

    @Test fun `native settling duration remains finite and bounded`() {
        assertEquals(120L, LauncherCategoryPageRules.settleDuration(0))
        assertEquals(360L, LauncherCategoryPageRules.settleDuration(360))
        assertEquals(600L, LauncherCategoryPageRules.settleDuration(Int.MAX_VALUE))
    }

    @Test fun `a second quick swipe can enter while the last desktop page is still settling`() {
        assertEquals(2, LauncherCategoryPageRules.entryPage(1, 2, 3, 1, false))
        assertEquals(2, LauncherCategoryPageRules.entryPage(2, 2, 3, 1, false))
        assertEquals(2, LauncherCategoryPageRules.entryPage(2, -1, 3, 1, false))
        assertEquals(-1, LauncherCategoryPageRules.entryPage(0, 1, 3, 1, false))
    }

    @Test fun `leaving the last page never captures the normal paging gesture`() {
        assertEquals(-1, LauncherCategoryPageRules.entryPage(2, 1, 3, 1, false))
        assertEquals(-1, LauncherCategoryPageRules.entryPage(0, 1, 3, 1, true))
    }

    @Test fun `fast paging respects tablet panel groups and RTL destinations`() {
        assertEquals(4, LauncherCategoryPageRules.entryPage(2, 4, 6, 2, false))
        assertEquals(0, LauncherCategoryPageRules.entryPage(1, 0, 3, 1, true))
        assertEquals(-1, LauncherCategoryPageRules.entryPage(0, 0, 0, 1, false))
    }

    @Test fun `grabbing an unfinished transition continues from its visible fraction`() {
        val enter = LauncherCategoryPageRules.continuedDelta(-100f, 0.2f, 1000f, Direction.Enter)
        val exit = LauncherCategoryPageRules.continuedDelta(100f, 0.2f, 1000f, Direction.Exit)
        assertEquals(0.3f, LauncherCategoryPageRules.progress(enter, 1000f, Direction.Enter), 0.0001f)
        assertEquals(0.3f, LauncherCategoryPageRules.progress(exit, 1000f, Direction.Exit), 0.0001f)
        val reverse = LauncherCategoryPageRules.continuedDelta(100f, 0.2f, 1000f, Direction.Enter)
        assertEquals(0.1f, LauncherCategoryPageRules.progress(reverse, 1000f, Direction.Enter), 0.0001f)
    }

    @Test fun `continued gestures keep velocity based completion and cancellation`() {
        val enter = LauncherCategoryPageRules.continuedDelta(-80f, 0.2f, 1000f, Direction.Enter)
        assertTrue(LauncherCategoryPageRules.shouldComplete(enter, 1000f, -1200f, Direction.Enter, 600f))
        assertFalse(LauncherCategoryPageRules.shouldComplete(enter, 1000f, 1200f, Direction.Enter, 600f))
        assertEquals(0f, LauncherCategoryPageRules.continuedDelta(-50f, Float.NaN, 1000f, Direction.Enter), 0f)
        assertEquals(0f, LauncherCategoryPageRules.continuedDelta(-50f, 0.2f, 0f, Direction.Enter), 0f)
    }

    @Test fun `deferred app list notifications cannot bounce between the two stores`() {
        val first = Any()
        val second = Any()
        val source = arrayOf(first, second)
        assertTrue(LauncherCategoryPageRules.sameAppSnapshot(source, source.copyOf(), 3, 3))
        assertTrue(LauncherCategoryPageRules.sameAppSnapshot(emptyArray<Any>(), emptyArray<Any>(), 0, 0))
        assertFalse(LauncherCategoryPageRules.sameAppSnapshot(source, arrayOf(second, first), 3, 3))
        assertFalse(LauncherCategoryPageRules.sameAppSnapshot(source, arrayOf(first), 3, 3))
        assertFalse(LauncherCategoryPageRules.sameAppSnapshot(source, source.copyOf(), 3, 1))
        assertFalse(LauncherCategoryPageRules.sameAppSnapshot(source, arrayOf(first, Any()), 3, 3))
    }
}
