package com.mi.onextbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mi.onextbox.ui.screens.FeatureLaunchOrigin
import com.mi.onextbox.ui.screens.FeaturePageMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureWindowSurfaceUiTest {
    @get:Rule val compose = createComposeRule()
    private val session = FeatureLaunchSession(FeaturePageMode.SystemUiStatusBar,
        FeatureLaunchOrigin(100f, 520f, 54f, 54f))

    @Test fun splitPassesComposeExactlyOneDetailTree() {
        val owners = AtomicInteger()
        compose.setContent {
            COUITheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    for (surface in listOf(FeatureWindowSurface.Icon, FeatureWindowSurface.Task)) {
                        C17FeatureWindowLayer(session, FeatureLaunchDirection.Opening, motion = null,
                            viewportBounds = Rect(0f, 0f, 360f, 640f), surface = surface) {
                            DisposableEffect(Unit) {
                                owners.incrementAndGet()
                                onDispose { owners.decrementAndGet() }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(1, owners.get()) }
    }

    @Test fun taskOnlyPassCannotStartOrCompleteTheCloseSpring() {
        val completed = AtomicInteger()
        val animation = FeatureLaunchAnimation(session, FeatureLaunchDirection.Closing,
            FeatureLaunchMotion(1f, alphaDirection = FeatureLaunchDirection.Closing, useReturnOrigin = true))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            COUITheme {
                C17ClosingFeatureWindowLayer(animation, Rect(0f, 0f, 360f, 640f), 1f, null,
                    onFinished = { completed.incrementAndGet() }, surface = FeatureWindowSurface.Task) { }
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle {
            assertEquals(1f, animation.motion.geometry.value, 0f)
            assertEquals(0, completed.get())
        }
    }

    @Test fun pairedClosePassesFinishOnce() {
        val completed = AtomicInteger()
        val animation = FeatureLaunchAnimation(session, FeatureLaunchDirection.Closing,
            FeatureLaunchMotion(1f, alphaDirection = FeatureLaunchDirection.Closing, useReturnOrigin = true))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            COUITheme {
                for (surface in listOf(FeatureWindowSurface.Icon, FeatureWindowSurface.Task)) {
                    C17ClosingFeatureWindowLayer(animation, Rect(0f, 0f, 360f, 640f), 1f, null,
                        onFinished = { completed.incrementAndGet() }, surface = surface) { }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle {
            assertEquals(0f, animation.motion.geometry.value, 0.001f)
            assertEquals(1, completed.get())
        }
    }

    @Test fun coveredFooterReceivesTouchesWhileTheExposedIconStillLaunches() {
        val footerClicks = AtomicInteger()
        val launches = AtomicInteger()
        val presses = AtomicInteger()
        compose.setContent {
            val scale = LocalDensity.current.density
            val origin = FeatureLaunchOrigin(100f * scale, 520f * scale, 100f * scale, 100f * scale)
            Box(Modifier.size(360.dp, 640.dp).testTag("host")) {
                Box(Modifier.offset(y = 540.dp).size(360.dp, 100.dp)
                    .background(Color.Gray).clickable { footerClicks.incrementAndGet() }.testTag("footer"))
                FeatureLaunchQueueHitLayer(
                    origins = mapOf(FeaturePageMode.Aod to origin),
                    viewportBounds = Rect(0f, 0f, 360f * scale, 640f * scale),
                    gridPosition = null, scrollState = rememberScrollState(),
                    onPressStart = { presses.incrementAndGet().toLong() }, onPressEnd = { _, _ -> },
                    onOpen = { _, _, _ -> launches.incrementAndGet() },
                    excludedBounds = Rect(0f, 540f * scale, 360f * scale, 640f * scale),
                )
            }
        }
        compose.onNodeWithTag("footer").performTouchInput { click(center.copy(y = height * 0.2f)) }
        compose.runOnIdle {
            assertEquals(1, footerClicks.get())
            assertEquals(0, launches.get())
            assertEquals(0, presses.get())
        }
        compose.onNodeWithTag("host").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 150f / 360f, height * 530f / 640f))
        }
        compose.runOnIdle {
            assertEquals(1, footerClicks.get())
            assertEquals(1, launches.get())
            assertEquals(1, presses.get())
        }
    }

    @Test fun closingTaskRetainsItsContentButCannotChangeSettings() {
        val direction = mutableStateOf(FeatureLaunchDirection.Closing)
        val owners = AtomicInteger()
        val writes = AtomicInteger()
        compose.setContent {
            COUITheme {
                C17FeatureWindowLayer(session, direction.value, motion = null,
                    viewportBounds = Rect(0f, 0f, 360f, 640f), surface = FeatureWindowSurface.Task) {
                    DisposableEffect(Unit) {
                        owners.incrementAndGet()
                        onDispose { owners.decrementAndGet() }
                    }
                    Box(Modifier.size(100.dp).background(Color.Gray)
                        .clickable { writes.incrementAndGet() }.testTag("setting"))
                }
            }
        }
        compose.onNodeWithTag("setting").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, owners.get())
            assertEquals(0, writes.get())
            direction.value = FeatureLaunchDirection.Opening
        }
        compose.onNodeWithTag("setting").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, owners.get())
            assertEquals(1, writes.get())
        }
    }
}
