package com.mi.onextbox.ui.onboarding

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class OnboardingAnimationHapticsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun requireNativeHaptics() {
        assumeTrue(NativeAnimationHapticOutput(context).isSupported())
        assumeTrue(Settings.System.getInt(context.contentResolver, "haptic_feedback_enabled", 1) != 0)
    }

    @Test fun nativePlayerAcceptsAllThreeOriginalTracksAndCancelsEachOne() {
        requireNativeHaptics()
        val output = NativeAnimationHapticOutput(context)
        try {
            for (resId in listOf(R.raw.c17_welcome_startup_haptic, R.raw.c17_guide_next_morph_haptic,
                    R.raw.c17_vibration_res_of_complete_page)) {
                val raw = context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
                val remaining = AnimationHapticPattern(raw).fromPosition(0)
                assertNotNull(remaining)
                output.play(remaining!!)
                SystemClock.sleep(1_500)
                output.stop()
            }
        } finally {
            output.stop()
        }
    }

    @Test fun welcomeTrackFollowsRealFramesAndSurvivesPauseResumeAndDisposal() {
        requireNativeHaptics()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        val visible = mutableStateOf(true)
        val frame = AtomicBoolean()
        val complete = AtomicBoolean()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (visible.value) BootregVideoPlayer(
                    videoResId = R.raw.c17_guide_first_startup,
                    hapticResId = R.raw.c17_welcome_startup_haptic,
                    modifier = Modifier.fillMaxSize(),
                    onFirstFrame = { frame.set(true) },
                    onCompleted = { complete.set(true) },
                    onError = { what, extra -> throw AssertionError("Video error $what/$extra") },
                )
            }
        }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { frame.get() }
        SystemClock.sleep(900)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        SystemClock.sleep(250)
        assertFalse(complete.get())
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { complete.get() }
        compose.runOnIdle { visible.value = false }
    }

    @Test fun smileTrackRestartsWithItsVideoLoopAndCompletionUsesTheMattePlayer() {
        requireNativeHaptics()
        val smile = mutableStateOf(true)
        val firstFrames = AtomicInteger()
        val complete = AtomicBoolean()
        compose.setContent {
            if (smile.value) {
                BootregVideoPlayer(
                    videoResId = R.raw.c17_guide_content_animation,
                    hapticResId = R.raw.c17_guide_next_morph_haptic,
                    modifier = Modifier.fillMaxSize(),
                    playCount = 2,
                    onFirstFrame = { firstFrames.incrementAndGet() },
                    onError = { what, extra -> throw AssertionError("Video error $what/$extra") },
                )
            } else {
                BootregMatteVideoSandwich(
                    videoResId = R.raw.c17_anim_of_complete_page,
                    hapticResId = R.raw.c17_vibration_res_of_complete_page,
                    modifier = Modifier.fillMaxSize(),
                    onFirstFrame = { firstFrames.incrementAndGet() },
                    onCompleted = { complete.set(true) },
                    onError = { throw AssertionError("Matte video error", it) },
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
        compose.waitUntil(10_000) { firstFrames.get() == 1 }
        SystemClock.sleep(17_500)
        compose.runOnIdle { smile.value = false }
        compose.waitUntil(10_000) { firstFrames.get() == 2 && complete.get() }
    }
}
