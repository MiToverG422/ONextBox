package com.mi.onextbox.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log

/** All page handoffs, service callbacks and fallback vibrations are ordered on one looper. */
internal class GuideHapticRouter private constructor(private val context: Context) {
    private val handler = Handler(HandlerThread("ONextBox-GuideHaptic").apply { start() }.looper)
    private val replies: Messenger = Messenger(Handler(handler.looper) { message ->
        val track = active
        if (track != null && track.sequence == message.arg1 && connection != null) {
            when (message.what) {
                GuideHapticProtocol.Ready -> connection?.remote?.let { send(it, track, GuideHapticProtocol.Play) }
                GuideHapticProtocol.Accepted -> {
                    handler.removeCallbacks(timeout)
                    Log.d(TAG, "RichTap playing raw=${track.raw}")
                }
                else -> fallback(disableOnThisBuild = message.what == GuideHapticProtocol.Unavailable)
            }
        }
        true
    })
    private val output = AnimationHapticOutput(context)
    private val waves = mutableMapOf<Int, AnimationHapticPattern>()
    private val prefs = context.getSharedPreferences("guide_haptic_compat", Context.MODE_PRIVATE)
    private var disabled = prefs.getString("unsupported_build", null) == Build.FINGERPRINT
    private var active: Track? = null
    private var sequence = 0
    private var connection: Connection? = null
    private val timeout = Runnable { fallback(disableOnThisBuild = false) }
    private val unbindIfIdle = Runnable { if (active == null) disconnect() }

    private data class Track(
        val owner: Any, val raw: Int, val origin: Long, val sequence: Int, val current: () -> Boolean,
        val deadline: Long,
    )

    fun play(owner: Any, raw: Int, position: Long, current: () -> Boolean) {
        val now = SystemClock.elapsedRealtime()
        handler.post {
            if (!current() || !output.isSupported()) return@post
            handler.removeCallbacks(unbindIfIdle)
            stopActive()
            val track = Track(owner, raw, now - position, ++sequence, current, now + 1500)
            active = track
            if (disabled) {
                playWave(track)
                return@post
            }
            handler.postDelayed(timeout, (track.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            val bound = connection
            if (bound == null) {
                val next = Connection()
                connection = next
                val intent = Intent(context, GuideHapticService::class.java)
                val ok = runCatching {
                    context.bindService(intent, Context.BIND_AUTO_CREATE, { handler.post(it) }, next)
                }.getOrDefault(false)
                if (!ok) fallback(disableOnThisBuild = false)
            } else {
                bound.remote?.let { send(it, track, GuideHapticProtocol.Prepare) }
            }
        }
    }

    fun stop(owner: Any) {
        handler.post {
            if (active?.owner !== owner) return@post
            stopActive()
            handler.postDelayed(unbindIfIdle, 350)
        }
    }

    private fun send(remote: Messenger, track: Track, command: Int) {
        if (!track.current()) return
        val message = Message.obtain(null, command, track.sequence, track.raw)
        message.data.putLong(GuideHapticProtocol.Origin, track.origin)
        message.data.putLong(GuideHapticProtocol.Deadline, track.deadline)
        message.replyTo = replies
        runCatching { remote.send(message) }.onFailure { fallback(disableOnThisBuild = true) }
    }

    private fun stopActive() {
        handler.removeCallbacks(timeout)
        active?.let { sendStop(it.sequence) }
        active = null
        output.stop()
    }

    private fun sendStop(sequence: Int) {
        runCatching { connection?.remote?.send(Message.obtain(null, GuideHapticProtocol.Stop, sequence, 0)) }
    }

    private fun fallback(disableOnThisBuild: Boolean) {
        handler.removeCallbacks(timeout)
        if (disableOnThisBuild) {
            disabled = true
            prefs.edit().putString("unsupported_build", Build.FINGERPRINT).apply()
        }
        active?.let { sendStop(it.sequence) }
        disconnect()
        active?.let(::playWave)
    }

    private fun playWave(track: Track) {
        if (!track.current()) return
        runCatching {
            val pattern = waves.getOrPut(track.raw) {
                context.resources.openRawResource(track.raw).bufferedReader().use { AnimationHapticPattern(it.readText()) }
            }
            val position = SystemClock.elapsedRealtime() - track.origin
            val remaining = pattern.fromPosition(position) ?: return
            if (!track.current()) return
            output.play(remaining)
            Log.d(TAG, "Waveform playing raw=${track.raw} positionMs=$position")
        }.onFailure { Log.w(TAG, "Cannot play guide waveform", it) }
    }

    private fun disconnect() {
        val bound = connection ?: return
        connection = null
        runCatching { context.unbindService(bound) }
    }

    private inner class Connection : ServiceConnection {
        var remote: Messenger? = null
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (connection !== this) return
            val messenger = Messenger(binder)
            remote = messenger
            active?.let { send(messenger, it, GuideHapticProtocol.Prepare) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (connection === this) fallback(disableOnThisBuild = true)
        }

        override fun onBindingDied(name: ComponentName) {
            if (connection === this) fallback(disableOnThisBuild = false)
        }

        override fun onNullBinding(name: ComponentName) {
            if (connection === this) fallback(disableOnThisBuild = true)
        }
    }

    companion object {
        private const val TAG = "ONextBox-GuideHaptic"
        private var instance: GuideHapticRouter? = null
        @Synchronized fun get(context: Context): GuideHapticRouter =
            instance ?: GuideHapticRouter(context.applicationContext).also { instance = it }
    }
}
