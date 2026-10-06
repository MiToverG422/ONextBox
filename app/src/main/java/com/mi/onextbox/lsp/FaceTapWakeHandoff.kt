package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ColorFilter
import android.graphics.Rect
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Read-only handoff when face success stops the AOD-to-keyguard fingerprint reveal
 * Copies official resource frames, not the native drawable/callback or its HAL window
 * This is independent of the optional face-tap unlock effect and never authorizes entry
 */
internal class FaceTapWakeHandoff {
    data class Capture(
        val source: WeakReference<Any>, val native: WeakReference<AnimationDrawable>,
        val context: Context, val capturedAt: Long,
        val resources: List<Int>, val durations: List<Int>,
        val width: Int, val height: Int, val scaleX: Float, val scaleY: Float,
        val filter: ColorFilter?,
    )

    private val main = Handler(Looper.getMainLooper())
    private val decoder by lazy { Executors.newSingleThreadExecutor() }
    private var pending: Capture? = null
    private var decoding: Capture? = null
    private var layer: ImageView? = null
    private var animation: AnimationDrawable? = null
    private var generation = 0L
    private var restore: (() -> Unit)? = null
    private data class Prepared(val native: WeakReference<AnimationDrawable>,
        val resources: List<Int>, val frames: List<BitmapDrawable>)
    private var prepared: Prepared? = null
    private var preparing = WeakReference<AnimationDrawable>(null)
    private val cleanup = Runnable { cancelPlayback() }
    val running: Boolean get() = layer != null
    val awaiting: Boolean get() = pending != null || decoding != null

    /** Warm small reveal assets outside the transition, never decode borrowed native bitmaps */
    fun prepare(source: Any) {
        runCatching {
            val native = Reflect.getObjectField(source, "fadeInAnimDrawable") as? AnimationDrawable ?: return
            if (prepared?.native?.get() === native || preparing.get() === native || native.numberOfFrames !in 1..90) return
            val decorator = Reflect.getObjectField(native, "mDecorator") ?: return
            val context = Reflect.getObjectField(decorator, "mContext") as? Context ?: return
            val ids = (0 until native.numberOfFrames).map { Reflect.getObjectField(native.getFrame(it), "mResId") as Int }
            if (ids.any { it == 0 }) return
            preparing = WeakReference(native)
            decoder.execute {
                val frames = runCatching { decode(context, ids) }.getOrNull()
                main.post {
                    if (preparing.get() !== native) return@post
                    preparing.clear()
                    if (frames != null) prepared = Prepared(WeakReference(native), ids, frames)
                }
            }
        }
    }

    private fun decode(context: Context, ids: List<Int>): List<BitmapDrawable> {
        var bytes = 0L
        return ids.map { id ->
            val resources = context.resources
            val bitmap = resources.openRawResource(id).use { stream ->
                requireNotNull(BitmapFactory.decodeResourceStream(resources, null, stream, null, BitmapFactory.Options()))
            }
            bytes += bitmap.allocationByteCount.toLong()
            require(bytes <= 24L * 1024 * 1024)
            BitmapDrawable(resources, bitmap).apply { setTargetDensity(bitmap.density) }
        }
    }

    /** Called before native stop on its optical Looper, no native writes or method interception */
    fun capture(source: Any): Capture? = runCatching {
        val native = Reflect.getObjectField(source, "fadeInAnimDrawable") as? AnimationDrawable
            ?: return@runCatching null
        val icon = Reflect.getObjectField(source, "fpIcon") as? ImageView ?: return@runCatching null
        if (!native.isRunning || icon.drawable !== native || !icon.isShown ||
            native.numberOfFrames !in 1..90) return@runCatching null
        val index = Reflect.callMethod(native, "getCurrentIndex") as Int
        if (index !in 0 until native.numberOfFrames) return@runCatching null
        val decorator = Reflect.getObjectField(native, "mDecorator") ?: return@runCatching null
        val context = Reflect.getObjectField(decorator, "mContext") as? Context ?: return@runCatching null
        val options = Reflect.callMethod(native, "getOptions") ?: return@runCatching null
        val scale = Reflect.callMethod(options, "getScaleRate") as Float
        val sx = icon.scaleX * scale
        val sy = icon.scaleY * scale
        if (!sx.isFinite() || !sy.isFinite() || sx <= 0f || sy <= 0f ||
            icon.measuredWidth <= 0 || icon.measuredHeight <= 0) return@runCatching null
        val frameIds = (index until native.numberOfFrames).map {
            Reflect.getObjectField(native.getFrame(it), "mResId") as Int
        }
        val durations = (index until native.numberOfFrames).map(native::getDuration)
        if (frameIds.any { it == 0 } || FaceTapWakeRules.position(durations, 0L) == null) return@runCatching null
        Capture(WeakReference(source), WeakReference(native), context, SystemClock.uptimeMillis(),
            frameIds, durations, icon.measuredWidth, icon.measuredHeight, sx, sy, native.colorFilter)
    }.getOrNull()

    fun offer(capture: Capture) {
        if (!running && decoding == null &&
            FaceTapWakeRules.position(capture.durations, SystemClock.uptimeMillis() - capture.capturedAt) != null) pending = capture
    }

    fun expire() {
        pending?.let {
            if (FaceTapWakeRules.position(it.durations, SystemClock.uptimeMillis() - it.capturedAt) == null) pending = null
        }
    }

    fun show(host: FrameLayout, bounds: Rect, source: Any, eligible: () -> Boolean, onEnd: () -> Unit) {
        expire()
        val capture = pending ?: return
        if (running || decoding != null || capture.source.get() !== source) return
        val native = Reflect.getObjectField(source, "fpIcon") as? ImageView ?: return
        // Wait until stock clears/hides its drawable, don't cover an official reveal still playing
        if (FaceTapUnlockRules.nativeIconVisible(native.isShown, native.alpha, native.drawable != null)) return
        if (!eligible()) return
        pending = null
        decoding = capture
        val epoch = generation
        val capturedBounds = Rect(bounds)
        val cached = prepared?.takeIf { it.native.get() === capture.native.get() &&
            it.resources.takeLast(capture.resources.size) == capture.resources }
            ?.frames?.takeLast(capture.resources.size)
        val display: (Result<List<BitmapDrawable>>) -> Unit = { decoded ->
            runCatching {
                // Generation includes sleep/cancel/new authentication, stale work is never shown
                if (epoch != generation || decoding !== capture) return@runCatching
                decoding = null
                val frames = decoded.getOrNull() ?: return@runCatching
                val position = FaceTapWakeRules.position(capture.durations, SystemClock.uptimeMillis() - capture.capturedAt)
                    ?: return@runCatching
                if (!host.isAttachedToWindow || !eligible() || capture.source.get() !== source ||
                    Reflect.getObjectField(source, "fadeInAnimDrawable") !== capture.native.get()) return@runCatching
                val currentIcon = Reflect.getObjectField(source, "fpIcon") as? ImageView ?: return@runCatching
                if (FaceTapUnlockRules.nativeIconVisible(currentIcon.isShown, currentIcon.alpha, currentIcon.drawable != null)) return@runCatching
                val fresh = AnimationDrawable().apply {
                    isOneShot = true
                    for (index in position.index until frames.size) {
                        val frame = requireNotNull(frames[index].constantState).newDrawable(capture.context.resources).mutate()
                        capture.filter?.let(frame::setColorFilter)
                        addFrame(frame, if (index == position.index) position.remainingMs else capture.durations[index])
                    }
                }
                val location = IntArray(2).also(host::getLocationOnScreen)
                val view = ImageView(host.context).apply {
                    scaleType = ImageView.ScaleType.CENTER
                    scaleX = capture.scaleX
                    scaleY = capture.scaleY
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setImageDrawable(fresh)
                }
                animation = fresh
                layer = view
                restore = onEnd
                host.addView(view, FrameLayout.LayoutParams(capture.width, capture.height,
                    Gravity.TOP or Gravity.LEFT).apply {
                    leftMargin = capturedBounds.centerX() - location[0] - width / 2
                    topMargin = capturedBounds.centerY() - location[1] - height / 2
                })
                // Hide the idle duplicate while the remaining official reveal plays
                onEnd()
                fresh.start()
                main.postDelayed(cleanup, (0 until fresh.numberOfFrames).sumOf { fresh.getDuration(it).toLong() })
                HookLog.d("ONextBox-FaceTap", "Native AOD reveal handed off from remaining frame")
            }.onFailure { cancelPlayback() }
        }
        if (cached != null) display(Result.success(cached))
        else decoder.execute {
            val decoded = runCatching { decode(capture.context, capture.resources) }
            main.post { display(decoded) }
        }
    }

    fun cancelPlayback() {
        main.removeCallbacks(cleanup)
        generation++
        decoding = null
        animation?.stop()
        animation = null
        layer?.let {
            it.setImageDrawable(null)
            (it.parent as? FrameLayout)?.removeView(it)
        }
        layer = null
        restore?.invoke()
        restore = null
    }

    fun clear() {
        pending = null
        cancelPlayback()
    }
}
