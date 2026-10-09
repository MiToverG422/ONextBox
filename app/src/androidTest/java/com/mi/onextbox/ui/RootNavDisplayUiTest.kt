package com.mi.onextbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RootNavDisplayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enteringPageReversesContinuouslyWithoutAShortenedFinish() {
        val stack = showPages()
        val width = pageWidth("main")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { stack.add("child") }
        compose.mainClock.advanceTimeBy(144)
        val before = bounds("child").left.value
        assertTrue(before > 0f && before < width)

        compose.runOnIdle { stack.removeLast() }
        compose.mainClock.advanceTimeByFrame()
        val after = bounds("child").left.value
        assertTrue("Reversing must retain the visible page position", abs(after - before) < width * 0.2f)
        compose.mainClock.advanceTimeBy(128)
        assertTrue("A partial return must not finish in a few frames", bounds("child").left.value < width * 0.99f)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0f, bounds("main").left.value, 0.5f)
        compose.onNodeWithTag("child").assertDoesNotExist()
    }

    @Test fun openingAnotherPageRetainsTheMovingParentPosition() {
        val stack = showPages()
        val width = pageWidth("main")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { stack.add("child") }
        compose.mainClock.advanceTimeBy(144)
        val before = bounds("child").left.value

        compose.runOnIdle { stack.add("nested") }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(abs(bounds("child").left.value - before) < width * 0.2f)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0f, bounds("nested").left.value, 0.5f)
        compose.runOnIdle { stack.removeLast() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0f, bounds("child").left.value, 0.5f)
        compose.onNodeWithTag("nested").assertDoesNotExist()
    }

    @Test fun pageCanReopenWhileItsReturnIsStillRunning() {
        val stack = showPages()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { stack.add("child") }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { stack.removeLast() }
        compose.mainClock.advanceTimeBy(144)
        val before = bounds("child").left.value
        compose.runOnIdle { stack.add("child") }
        compose.mainClock.advanceTimeByFrame()
        val width = pageWidth("child")
        assertTrue(abs(bounds("child").left.value - before) < width * 0.2f)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0f, bounds("child").left.value, 0.5f)
    }

    private fun showPages() = mutableStateListOf("main").also { stack ->
        compose.setContent {
            RootNavDisplay(
                entries = stack.map { page ->
                    NavEntry(page) {
                        Box(Modifier.fillMaxSize().background(Color.White).testTag(page))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                transitionEffects = NavDisplayTransitionEffects.None,
                onBack = { if (stack.size > 1) stack.removeLast() },
            )
        }
        compose.waitForIdle()
    }

    private fun bounds(page: String) = compose.onNodeWithTag(page).getUnclippedBoundsInRoot()

    private fun pageWidth(page: String) = bounds(page).let { it.right.value - it.left.value }
}
