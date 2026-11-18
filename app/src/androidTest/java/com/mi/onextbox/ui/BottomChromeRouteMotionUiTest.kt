package com.mi.onextbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BottomChromeRouteMotionUiTest {
    @get:Rule val compose = createComposeRule()
    private val visible = mutableStateOf(true)
    private lateinit var progress: State<Float>

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            progress = rememberBottomChromeRouteVisibility(visible.value)
            Box(Modifier.size(1.dp))
        }
        advance(32)
    }

    @Test fun enteringAndLeavingADetailUseTheSamePhysicalTiming() {
        start()
        fun samples(show: Boolean): List<Float> {
            compose.runOnIdle { visible.value = show }
            advance(32)
            return listOf(64L, 64L, 64L, 64L, 128L).map { step ->
                advance(step)
                if (show) progress.value else 1f - progress.value
            }
        }
        val down = samples(false)
        advance(500)
        assertEquals(0f, progress.value, 0f)
        val up = samples(true)
        down.zip(up).forEach { (exit, enter) -> assertEquals(exit, enter, .005f) }
        assertEquals(1f, progress.value, 0f)
    }

    @Test fun returningBeforeTheHideFinishesKeepsTheCurrentPosition() {
        start()
        compose.runOnIdle { visible.value = false }
        advance(144)
        val intermediate = progress.value
        assertTrue(intermediate in .01f.. .99f)
        compose.runOnIdle { visible.value = true }
        assertEquals(intermediate, progress.value, .001f)
        advance(500)
        assertEquals(1f, progress.value, 0f)
    }
}
