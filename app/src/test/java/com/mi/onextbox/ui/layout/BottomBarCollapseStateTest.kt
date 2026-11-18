package com.mi.onextbox.ui.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class BottomBarCollapseStateTest {
    @Test fun realScrollHasAThresholdAndNeverConsumesPageMovement() {
        val state = BottomBarCollapseState(24f)
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
        val state = BottomBarCollapseState(24f)
        listOf(-18f, 10f, -18f).forEach {
            state.nestedScrollConnection.onPreScroll(Offset(0f, it), NestedScrollSource.UserInput)
        }
        assertFalse(state.collapsed)
        state.nestedScrollConnection.onPreScroll(Offset(0f, -6f), NestedScrollSource.UserInput)
        assertTrue(state.collapsed)
    }

    @Test fun ignoresHorizontalAndProgrammaticMovementButStillFoldsAtThePageEdge() {
        val state = BottomBarCollapseState(24f)
        val connection = state.nestedScrollConnection
        connection.onPreScroll(Offset(100f, -30f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.SideEffect)
        assertFalse(state.collapsed)
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -24f), NestedScrollSource.UserInput))
        assertTrue(state.collapsed)
    }
}
