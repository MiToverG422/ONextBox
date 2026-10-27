package com.mi.onextbox.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BottomBarStartupUiTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private val ready = mutableStateOf(false)
    private val animate = mutableStateOf(true)
    private lateinit var owner: Owner
    private lateinit var progress: () -> Float

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            owner = remember { Owner() }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                progress = rememberBottomBarStartupProgress(ready.value, animate.value)
                Box(Modifier.size(1.dp))
            }
        }
        advance(32)
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun assertComplete() = compose.runOnIdle { assertEquals(1f, progress(), 0f) }

    @Test fun entryWithoutASplashIsVisibleFromItsFirstFrame() {
        start()
        assertComplete()
        advance(1_000)
        assertComplete()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            animate.value = false
            ready.value = true
        }
        advance(32)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        advance(32)
        assertComplete()
    }

    @Test fun aRealSplashExitStillRevealsOnceAndDoesNotReplay() {
        start()
        compose.runOnIdle { ready.value = true }
        advance(112)
        compose.runOnIdle { assertTrue(progress() > 0f && progress() < 1f) }
        advance(500)
        assertComplete()
        compose.runOnIdle { ready.value = false }
        advance(32)
        compose.runOnIdle { ready.value = true }
        advance(32)
        assertComplete()
    }

    @Test fun backgroundingDuringTheRevealFinishesItWithoutASecondAnimation() {
        start()
        compose.runOnIdle { ready.value = true }
        advance(112)
        compose.runOnIdle {
            assertTrue(progress() < 1f)
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        advance(32)
        assertComplete()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        advance(32)
        assertComplete()
    }

    @Test fun restoredOrDisabledStartupDoesNotWaitForSplashCallbacks() {
        animate.value = false
        start()
        assertComplete()
        compose.runOnIdle { ready.value = true }
        advance(112)
        assertComplete()
    }
}
