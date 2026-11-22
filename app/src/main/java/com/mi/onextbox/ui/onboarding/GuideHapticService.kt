package com.mi.onextbox.ui.onboarding

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.mi.onextbox.R

internal object GuideHapticProtocol {
    const val Play = 1
    const val Stop = 2
    const val Accepted = 3
    const val Unavailable = 4
    const val Prepare = 5
    const val Ready = 6
    const val Expired = 7
    const val Origin = "origin"
    const val Deadline = "deadline"
}

class GuideHapticService : Service() {
    private lateinit var handler: Handler
    private val tracks = mutableMapOf<Int, RichTapPattern>()
    private val api by lazy { RichTapPlayer() }
    private val vibrator by lazy { getSystemService(VibratorManager::class.java).defaultVibrator }
    private var playing: Int? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(worker.looper) { message ->
            when (message.what) {
                GuideHapticProtocol.Prepare -> prepare(message)
                GuideHapticProtocol.Play -> play(message)
                GuideHapticProtocol.Stop -> if (playing == message.arg1) stopTrack()
            }
            true
        }
    }

    override fun onBind(intent: Intent) = Messenger(handler).binder

    private fun prepare(message: Message) {
        val result = runCatching {
            val id = message.arg2
            require(id == R.raw.c17_welcome_startup_haptic || id == R.raw.c17_guide_next_morph_haptic ||
                id == R.raw.c17_vibration_res_of_complete_page)
            api
            tracks.getOrPut(id) {
                resources.openRawResource(id).bufferedReader().use { RichTapPattern(it.readText()) }
            }
        }.onFailure { Log.w("ONextBox-GuideHaptic", "RichTap unavailable", it) }
        reply(message, if (result.isSuccess) GuideHapticProtocol.Ready else GuideHapticProtocol.Unavailable)
    }

    private fun play(message: Message) {
        val status = runCatching {
            stopTrack()
            val position = SystemClock.elapsedRealtime() - message.data.getLong(GuideHapticProtocol.Origin)
            val remaining = tracks.getValue(message.arg2).packet(position, Process.myPid(), message.arg1)
            if (SystemClock.elapsedRealtime() >= message.data.getLong(GuideHapticProtocol.Deadline)) {
                return@runCatching GuideHapticProtocol.Expired
            }
            if (remaining != null &&
                Settings.System.getInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0) {
                playing = message.arg1
                vibrator.vibrate(api.effect(remaining), attributes)
            }
            GuideHapticProtocol.Accepted
        }.getOrElse {
            stopTrack()
            Log.w("ONextBox-GuideHaptic", "RichTap playback failed", it)
            GuideHapticProtocol.Unavailable
        }
        reply(message, status)
    }

    private fun reply(message: Message, status: Int) {
        runCatching { message.replyTo.send(Message.obtain(null, status, message.arg1, 0)) }
            .onFailure { if (playing == message.arg1) stopTrack() }
    }

    private fun stopTrack() {
        if (playing == null) return
        playing = null
        // The RichTap engine stops a running HE sequence with a zero-parameter command.
        runCatching { vibrator.vibrate(api.stopEffect, attributes) }
            .onFailure { vibrator.cancel() }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        handler.post { stopTrack() }
        super.onDestroy()
    }

    private companion object {
        val worker by lazy { HandlerThread("GuideRichTap").apply { start() } }
        val attributes = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
    }

    private class RichTapPlayer {
        private val type = Class.forName("android.os.RichTapVibrationEffect")
        private val int = Integer.TYPE

        init {
            val support = type.getMethod("checkIfRichTapSupport").invoke(null) as Int
            check((support ushr 8 and 0xff) >= 23) { "HE 2.0 unavailable" }
        }

        private val create = type.getMethod("createPatternHeWithParam", IntArray::class.java, int, int, int, int)
        val stopEffect = type.getMethod("createPatternHeParameter", int, int, int)
            .invoke(null, 0, 0, 0) as VibrationEffect

        fun effect(packet: IntArray) = create.invoke(null, packet, 1, 0, 255, 0) as VibrationEffect
    }
}
