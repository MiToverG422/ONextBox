package com.mi.onextbox.ui.onboarding

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.annotation.RawRes
import java.util.concurrent.atomic.AtomicLong

internal class OnboardingAnimationHaptics(
    context: Context,
    @RawRes private val patternResId: Int,
) {
    private val appContext = context.applicationContext
    private val generation = AtomicLong()
    private val router = GuideHapticRouter.get(appContext)
    private var allowed = false
    private var released = false
    private val playback = AnimationHapticPlayback(::playFrom, ::stopPlayback)
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            playback.setAllowed(allowed && !released && feedbackEnabled())
        }
    }

    init {
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED), false, settingsObserver,
            )
        }
    }

    fun setAllowed(value: Boolean) {
        val next = value && !released
        if (allowed == next) return
        allowed = next
        playback.setAllowed(allowed && feedbackEnabled())
    }

    fun onFrame(positionMillis: () -> Long) {
        if (released || !allowed) return
        playback.onFrame(positionMillis)
    }

    fun reset() = playback.reset()

    fun release() {
        if (released) return
        released = true
        allowed = false
        playback.setAllowed(false)
        stopPlayback()
        runCatching { appContext.contentResolver.unregisterContentObserver(settingsObserver) }
    }

    private fun playFrom(positionMillis: Long) {
        val token = generation.incrementAndGet()
        router.play(this, patternResId, positionMillis) { generation.get() == token && feedbackEnabled() }
    }

    private fun stopPlayback() {
        generation.incrementAndGet()
        router.stop(this)
    }

    private fun feedbackEnabled(): Boolean = runCatching {
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    }.getOrDefault(false)

}

internal class AnimationHapticOutput(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    fun isSupported(): Boolean = vibrator?.hasVibrator() == true

    fun play(waveform: AnimationHapticWaveform) {
        val device = vibrator ?: return
        val effect = VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, -1)
        synchronized(lock) {
            device.vibrate(effect, attributes)
            activeOutput = this
        }
    }

    fun stop() {
        synchronized(lock) {
            // A previous page may release its track after the next page has started.
            if (activeOutput !== this) return
            activeOutput = null
            vibrator?.cancel()
        }
    }

    private companion object {
        val lock = Any()
        val attributes = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
        var activeOutput: AnimationHapticOutput? = null
    }
}
