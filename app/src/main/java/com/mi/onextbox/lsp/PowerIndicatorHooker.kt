package com.mi.onextbox.lsp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.graphics.Typeface
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.io.File
import java.util.IdentityHashMap

/** An independent view beside the clock; does not replace the native network-speed slot. */
internal object PowerIndicatorHooker {
    private const val TAG = "ONextBox-PowerIndicator"
    private val owners = IdentityHashMap<ViewGroup, Owner>()
    fun hook(loader: ClassLoader) {
        runCatching {
            val type = ModernReflect.findClass("com.android.systemui.statusbar.phone.PhoneStatusBarView", loader)
            for (methodName in listOf("onAttachedToWindow", "onDetachedFromWindow")) {
                ModernHookRegistry.installCompat("$TAG:$methodName", type.getDeclaredMethod(methodName), object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val root = param.thisObject as? ViewGroup ?: return
                        if (methodName == "onDetachedFromWindow") owners.remove(root)?.close()
                        else if (!owners.containsKey(root)) runCatching { attach(root) }
                            .onFailure { HookLog.w(TAG, "Status-bar placement unavailable", it) }
                    }
                })
            }
            HotReloadLifecycle.register(TAG,
                capture = { owners.keys.toTypedArray() },
                detach = ::detachOwners,
                restore = { state ->
                    (state as? Array<*>)?.filterIsInstance<ViewGroup>()?.forEach { root ->
                        if (root.isAttachedToWindow && !owners.containsKey(root)) attach(root)
                    }
                },
                resumeAfterRejection = { owners.values.toList().forEach { it.resume() } },
            )
        }.onFailure { HookLog.w(TAG, "Status-bar hook unavailable", it) }
    }

    private fun detachOwners() {
        val retired = owners.values.toList()
        owners.clear()
        var failure: Throwable? = null
        retired.forEach { owner ->
            runCatching { owner.close() }.onFailure { error ->
                if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
    private fun attach(root: ViewGroup) {
        fun id(name: String) = root.resources.getIdentifier(name, "id", "com.android.systemui")
        val clock = root.findViewById<TextView>(id("clock")) ?: return
        val container = root.findViewById<View>(id("status_bar_start_side_except_heads_up")) as? LinearLayout ?: return
        val clockFrame = root.findViewById<View>(id("clock_for_fake")) ?: return
        if (clockFrame.parent !== container) return
        val text = TextView(root.context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            includeFontPadding = false
            visibility = View.GONE
            setPadding((4 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt(), 0)
        }
        container.addView(text, container.indexOfChild(clockFrame) + 1,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val owner = Owner(root, clock, text)
        try { owner.start(); owners[root] = owner }
        catch (error: Exception) { owner.close(); throw error }
    }
    private class Owner(val root: ViewGroup, val clock: TextView, val text: TextView) {
        val context = root.context
        val main = Handler(Looper.getMainLooper())
        val thread = HandlerThread("ONextBox-Power")
        lateinit var worker: Handler
        @Volatile var closed = false
        @Volatile var generation = 0
        var registered = false
        var displayedLines = 1
        var fontSizeSp = 10
        val measuringPaint = Paint()
        var lastClockTypeface: Typeface? = null
        var lastFontVariation: String? = null
        var lastFakeBold = false
        var lastFollowFontWeight: Boolean? = null
        val power = context.getSystemService(PowerManager::class.java)
        val layoutListener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (!closed && !HotReloadLifecycle.isRetiring && bottom - top != oldBottom - oldTop) updateTextSize()
        }
        val observer = ViewTreeObserver.OnPreDrawListener {
            if (closed || HotReloadLifecycle.isRetiring) return@OnPreDrawListener true
            if (text.currentTextColor != clock.currentTextColor) text.setTextColor(clock.currentTextColor)
            updateFontWeight()
            text.alpha = clock.alpha
            true
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (closed || HotReloadLifecycle.isRetiring) return
                generation++
                main.removeCallbacks(tick)
                worker.removeCallbacksAndMessages(null)
                text.visibility = View.GONE
                if (power.isInteractive) main.post(tick)
            }
        }
        val tick: Runnable = object : Runnable {
            override fun run() {
                if (closed || HotReloadLifecycle.isRetiring || !root.isAttachedToWindow || !power.isInteractive) return
                val settings = PowerIndicatorSettings.snapshot()
                if (!settings.enabled || !root.isShown || clock.visibility != View.VISIBLE) {
                    text.visibility = View.GONE
                    main.postDelayed(this, 1000)
                    return
                }
                val token = generation
                worker.post {
                    HotReloadLifecycle.withHook(fallback = {}, action = sample@{
                        if (closed || token != generation) return@sample
                        val result = runCatching { read(settings) }.getOrNull()
                        main.post publish@{
                            if (closed || HotReloadLifecycle.isRetiring || token != generation ||
                                !power.isInteractive) return@publish
                            if (PowerIndicatorSettings.snapshot() != settings) {
                                text.visibility = View.GONE
                                main.post(tick)
                                return@publish
                            }
                            if (result != null) applyTextStyle(settings.fontSize, if ('\n' in result) 2 else 1)
                            if (text.text.toString() != result) text.text = result.orEmpty()
                            text.visibility = if (result == null) View.GONE else View.VISIBLE
                            main.postDelayed(tick, settings.interval.toLong())
                        }
                    })
                }
            }
        }
        private fun updateFontWeight() {
            val follow = HookConfigSnapshot.boolean(PowerIndicatorSettings.FOLLOW_FONT_WEIGHT, true) != false
            val face = clock.typeface
            val variation = if (follow) clock.fontVariationSettings else null
            val fakeBold = follow && clock.paint.isFakeBoldText
            if (lastFollowFontWeight == follow && lastClockTypeface === face &&
                lastFontVariation == variation && lastFakeBold == fakeBold) return
            text.typeface = if (follow) face else Typeface.create(face, 400, false)
            text.fontVariationSettings = variation
            text.paint.isFakeBoldText = fakeBold
            lastFollowFontWeight = follow
            lastClockTypeface = face
            lastFontVariation = variation
            lastFakeBold = fakeBold
            updateTextSize()
            text.requestLayout()
            text.invalidate()
        }
        fun read(s: PowerIndicatorSettings.State): String? {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (status !in listOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_DISCHARGING,
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_STATUS_FULL)) return null
            val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (plugged && s.hideCharging) return null
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            val current = if (s.source == 0) context.getSystemService(BatteryManager::class.java)
                .getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                else File("/sys/class/power_supply/battery/current_now").readText().trim().toLong()
            val voltage = if (s.source == 0) battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).toLong()
                else File("/sys/class/power_supply/battery/voltage_now").readText().trim().toLong() / 1000
            val watts = PowerIndicatorRules.watts(current, voltage, s.source, s.scale) ?: return null
            val temperature = if (s.showTemperature) {
                battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            } else null
            return PowerIndicatorRules.format(watts, s.decimals, charging, s.hideDischargeSign,
                temperature, s.temperatureUnit)
        }
        private fun applyTextStyle(fontSize: Int, lines: Int) {
            fontSizeSp = fontSize
            if (displayedLines != lines) {
                displayedLines = lines
                text.setSingleLine(lines == 1)
                text.maxLines = lines
                text.setLineSpacing(0f, 1f)
                text.gravity = if (lines == 2) Gravity.CENTER else Gravity.CENTER_VERTICAL
            }
            updateTextSize()
        }
        private fun updateTextSize() {
            val requested = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                fontSizeSp.toFloat(), text.resources.displayMetrics)
            measuringPaint.set(text.paint)
            measuringPaint.textSize = requested
            val metrics = measuringPaint.fontMetrics
            val lineHeight = kotlin.math.ceil(metrics.descent - metrics.ascent + metrics.leading.coerceAtLeast(0f))
            val height = (text.height.takeIf { it > 0 } ?: clock.height) -
                text.compoundPaddingTop - text.compoundPaddingBottom
            val size = PowerIndicatorRules.fittedTextSizePx(requested, lineHeight, displayedLines, height)
            if (kotlin.math.abs(text.textSize - size) > 0.1f) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        fun start() {
            thread.start()
            worker = Handler(thread.looper)
            root.viewTreeObserver.addOnPreDrawListener(observer)
            text.addOnLayoutChangeListener(layoutListener)
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            }, Context.RECEIVER_NOT_EXPORTED)
            registered = true
            main.post(tick)
        }
        fun resume() {
            if (closed) return
            generation++
            main.removeCallbacksAndMessages(null)
            if (::worker.isInitialized) worker.removeCallbacksAndMessages(null)
            if (root.isAttachedToWindow && power.isInteractive) main.post(tick)
        }
        fun close() {
            if (closed) return
            closed = true
            generation++
            main.removeCallbacksAndMessages(null)
            if (::worker.isInitialized) worker.removeCallbacksAndMessages(null)
            thread.quitSafely()
            if (registered) runCatching { context.unregisterReceiver(receiver) }
            registered = false
            if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(observer)
            text.removeOnLayoutChangeListener(layoutListener)
            (text.parent as? ViewGroup)?.removeView(text)
        }
    }
}
