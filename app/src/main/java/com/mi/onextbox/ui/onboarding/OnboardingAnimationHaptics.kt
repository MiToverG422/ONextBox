package com.mi.onextbox.ui.onboarding

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RawRes
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal class OnboardingAnimationHaptics(
    context: Context,
    @RawRes private val patternResId: Int,
) {
    private val appContext = context.applicationContext
    private val generation = AtomicLong()
    private val native = NativeAnimationHapticOutput(appContext)
    private var allowed = false
    private var released = false
    private val playback = AnimationHapticPlayback(::playFrom, ::stopPlayback)
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            playback.setAllowed(allowed && !released && feedbackEnabled())
        }
    }

    init {
        worker.execute { runCatching { loadPattern() }.onFailure(::reportFailure) }
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
        val frameTime = SystemClock.elapsedRealtime()
        worker.execute {
            runCatching {
                native.stop()
                val pattern = loadPattern()
                if (generation.get() != token || !feedbackEnabled() || !native.isSupported()) return@execute
                val position = positionMillis + (SystemClock.elapsedRealtime() - frameTime)
                val remaining = pattern.fromPosition(position) ?: return@execute
                if (generation.get() != token) return@execute
                native.play(remaining)
                Log.d(TAG, "play raw=$patternResId positionMs=$position")
                if (generation.get() != token) native.stop()
            }.onFailure(::reportFailure)
        }
    }

    private fun stopPlayback() {
        generation.incrementAndGet()
        worker.execute { runCatching { native.stop() }.onFailure(::reportFailure) }
    }

    private fun loadPattern(): AnimationHapticPattern = patterns.getOrPut(patternResId) {
        appContext.resources.openRawResource(patternResId).bufferedReader().use {
            AnimationHapticPattern(it.readText())
        }
    }

    private fun feedbackEnabled(): Boolean = runCatching {
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    }.getOrDefault(false)

    private fun reportFailure(throwable: Throwable) {
        if (reportedFailures.add(throwable.javaClass.name)) {
            Log.w(TAG, "Animation haptics unavailable", throwable)
        }
    }

    private companion object {
        const val TAG = "ONextBox-GuideHaptic"
        val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "ONextBox-GuideHaptic").apply { isDaemon = true } }
        val patterns = ConcurrentHashMap<Int, AnimationHapticPattern>()
        val reportedFailures = ConcurrentHashMap.newKeySet<String>()
    }
}

internal class NativeAnimationHapticOutput(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private var playing = false

    fun isSupported(): Boolean = vibrator?.hasVibrator() == true && api != null

    fun play(pattern: String) {
        val currentApi = api ?: return
        if (vibrator?.hasVibrator() != true) return
        val effect = currentApi.create.invoke(null, pattern) ?: return
        val player = HiddenApiBypass.newInstance(currentApi.playerType, effect)
        playing = true
        currentApi.apply.invoke(player, pattern, 1, 0, 255, 0)
    }

    fun stop() {
        if (!playing) return
        playing = false
        val effect = api?.stopEffect?.invoke(null, 0, 0, 0) as? VibrationEffect ?: return
        vibrator?.vibrate(effect, VibrationAttributes.Builder().build())
    }

    private class Api(
        val playerType: Class<*>,
        val create: Method,
        val apply: Method,
        val stopEffect: Method,
    )

    private companion object {
        val api: Api? by lazy {
            runCatching {
                val player = Class.forName("android.os.HapticPlayer")
                val effect = Class.forName("android.os.DynamicEffect")
                val richTap = Class.forName("android.os.RichTapVibrationEffect")
                fun method(type: Class<*>, name: String, count: Int): Method =
                    HiddenApiBypass.getDeclaredMethods(type).filterIsInstance<Method>()
                        .single { it.name == name && it.parameterCount == count }.apply { isAccessible = true }
                if (method(player, "isAvailable", 0).invoke(null) != true) return@runCatching null
                Api(
                    player,
                    method(effect, "create", 1),
                    method(player, "applyPatternHeWithString", 5),
                    method(richTap, "createPatternHeParameter", 3),
                )
            }.getOrNull()
        }
    }
}
