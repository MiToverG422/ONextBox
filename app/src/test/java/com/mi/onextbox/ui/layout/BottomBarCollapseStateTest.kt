package com.mi.onextbox.ui.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class BottomBarCollapseStateTest {
    @Test fun restoresOffByDefaultAndRestrictsChoicesToSupportedBarStyles() {
        assertEquals(BottomBarAutoCollapse.Off, BottomBarAutoCollapse.restore(null))
        for (choice in BottomBarAutoCollapse.entries) {
            assertEquals(choice, BottomBarAutoCollapse.restore(choice.preferenceValue))
        }
        for (mode in FloatingBottomBarMode.entries) {
            val choices = BottomBarAutoCollapse.choicesFor(mode)
            assertEquals(mode.collapsible, BottomBarAutoCollapse.Left in choices)
            assertEquals(mode.collapsible, BottomBarAutoCollapse.Right in choices)
            assertEquals(mode.visible, BottomBarAutoCollapse.Bottom in choices)
            for (choice in BottomBarAutoCollapse.entries) {
                assertTrue(choice.forBar(mode) in choices)
            }
        }
    }

    @Test fun downwardHideAndSideFolderUseTheSameScrollThreshold() {
        for (mode in listOf(BottomBarAutoCollapse.Right, BottomBarAutoCollapse.Bottom, BottomBarAutoCollapse.Off)) {
            val state = BottomBarCollapseState(24f, mode)
            val connection = state.nestedScrollConnection
            connection.onPreScroll(Offset(0f, -24f), NestedScrollSource.UserInput)
            assertEquals(mode == BottomBarAutoCollapse.Right, state.folderCollapsed)
            assertEquals(mode == BottomBarAutoCollapse.Bottom, state.hiddenBelowScreen)
            assertEquals(mode == BottomBarAutoCollapse.Off, state.interactionReady)
            connection.onPreScroll(Offset(0f, 24f), NestedScrollSource.UserInput)
            assertFalse(state.folderCollapsed)
            assertFalse(state.hiddenBelowScreen)
            assertTrue(state.interactionReady)
        }
    }

    @Test fun realScrollHasAThresholdAndNeverConsumesPageMovement() {
        val state = BottomBarCollapseState(24f, BottomBarAutoCollapse.Left)
        fun scroll(y: Float) = state.nestedScrollConnection.onPreScroll(Offset(0f, y), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, scroll(-12f))
        assertFalse(state.collapsed)
        scroll(-12f)
        assertTrue(state.collapsed)
        scroll(12f)
        assertTrue(state.collapsed)
        scroll(12f)
        assertFalse(state.collapsed)
    }

    @Test fun reversingDirectionDiscardsThePreviousScrollDistance() {
        val state = BottomBarCollapseState(24f, BottomBarAutoCollapse.Left)
        listOf(-18f, 10f, -18f).forEach {
            state.nestedScrollConnection.onPreScroll(Offset(0f, it), NestedScrollSource.UserInput)
        }
        assertFalse(state.collapsed)
        state.nestedScrollConnection.onPreScroll(Offset(0f, -6f), NestedScrollSource.UserInput)
        assertTrue(state.collapsed)
    }

    @Test fun ignoresHorizontalAndProgrammaticMovementButStillFoldsAtThePageEdge() {
        val state = BottomBarCollapseState(24f, BottomBarAutoCollapse.Left)
        val connection = state.nestedScrollConnection
        connection.onPreScroll(Offset(100f, -30f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.SideEffect)
        assertFalse(state.collapsed)
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -24f), NestedScrollSource.UserInput))
        assertTrue(state.collapsed)
    }
}
