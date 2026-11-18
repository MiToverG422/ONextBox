package com.mi.onextbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplayTransitionEffects
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RootNavVisibilityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibilityObserverReturnsToIdleAfterBothPushAndPop() {
        val detail = mutableStateOf(false)
        val reported = CopyOnWriteArrayList<Boolean>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val entries = remember(detail.value) {
                buildList {
                    add(NavEntry("main") { Box(Modifier.fillMaxSize()) })
                    if (detail.value) add(NavEntry("detail") { Box(Modifier.fillMaxSize()) })
                }
            }
            RootNavDisplay(
                entries = entries,
                modifier = Modifier.fillMaxSize(),
                transitionEffects = NavDisplayTransitionEffects(enableCornerClip = false),
                onTransitionRunningChanged = { if (reported.lastOrNull() != it) reported.add(it) },
                onBack = { detail.value = false },
            )
        }
        compose.runOnIdle { assertEquals(false, reported.last()); detail.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle {
            assertTrue(reported.contains(true))
            assertEquals(false, reported.last())
            detail.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle {
            assertTrue(reported.count { it } >= 2)
            assertEquals(false, reported.last())
        }
    }
}
