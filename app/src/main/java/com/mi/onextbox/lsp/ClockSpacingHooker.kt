package com.mi.onextbox.lsp

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

internal object ClockSpacingHooker {
    private const val PREFIX = "clock-spacing:"
    private const val VIEWS = PREFIX + "views"
    private val hosts = WeakHashMap<TextView, Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var restoring = false

    fun hook(loader: ClassLoader) = SystemUiHookActivation.install(
        VIEWS, setOf(ClockPeriodSettings.COMPACT), ClockPeriodSettings::compactXposed,
    ) { install(loader) }

    private fun install(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass(ClockPeriodHooker.CLOCK, loader)
            val stat = Reflect.findClass(ClockPeriodHooker.STAT_CLOCK, loader)
            val measure = stat.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val textMeasure = TextView::class.java.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
            val measureText = ModernHookRuntime.requireModule().getInvoker(textMeasure)
                .setType(XposedInterface.Invoker.Type.ORIGIN)
            val end = stat.getDeclaredField("paddingEnd").apply { isAccessible = true }
            val showSeconds = type.getDeclaredField("mShowSeconds").apply { isAccessible = true }
            val refreshWidth = runCatching { stat.getDeclaredMethod("refreshWidth").apply { isAccessible = true } }.getOrNull()
            val fixedWidth = runCatching { type.getDeclaredMethod("getSecondsFixedWidth") }.getOrNull()
            val minutes = runCatching { stat.getDeclaredField("cachedMinutesLayoutWidth").apply { isAccessible = true } }.getOrNull()
            val legacyWidth = if (refreshWidth == null) stat.getDeclaredField("actualWidth").apply { isAccessible = true } else null
            check(refreshWidth == null || (fixedWidth != null && minutes != null))
            fun enabled() = !restoring && !HotReloadLifecycle.isRetiring && ClockPeriodSettings.compactXposed()
            fun compact(view: TextView, width: Int, text: CharSequence = view.text) =
                ClockSpacingMeasure.compactWidth(width, text, view.paint,
                    end.getInt(view), view.compoundPaddingLeft + view.compoundPaddingRight)
            fun refresh(view: TextView) {
                runCatching { refreshWidth?.invoke(view); view.requestLayout() }
            }
            ModernHookRegistry.installCompat(PREFIX + "measure", measure, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || !enabled()) return
                    val view = param.thisObject as TextView
                    if (view !in hosts) hosts[view] = Unit
                    val width = compact(view, view.measuredWidth)
                    if (width != view.measuredWidth) {
                        measureText.invokeSpecial(view,
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), param.args[1])
                    }
                    legacyWidth?.setInt(view, view.measuredWidth)
                    if (!showSeconds.getBoolean(view)) minutes?.setInt(view, view.measuredWidth)
                }
            })
            if (fixedWidth != null) ModernHookRegistry.installCompat(PREFIX + "seconds-area", fixedWidth, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && enabled() && stat.isInstance(param.thisObject)) {
                        param.result = compact(param.thisObject as TextView, param.result as Int)
                    }
                }
            })
            if (refreshWidth != null && minutes != null) ModernHookRegistry.installCompat(PREFIX + "minutes-area", refreshWidth, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && enabled()) {
                        val view = param.thisObject as TextView
                        minutes.setInt(view, compact(view, minutes.getInt(view), ClockSpacingMeasure.minutesText(view.text)))
                    }
                }
            })
            val bootstrap = Runnable {
                if (enabled()) SystemUiWindowViews.find(loader, stat::isInstance).filterIsInstance<TextView>().forEach {
                    hosts[it] = Unit
                    refresh(it)
                }
            }
            fun scheduleRefresh() {
                main.removeCallbacks(bootstrap)
                main.post(bootstrap)
            }
            HotReloadLifecycle.register(VIEWS,
                capture = { hosts.keys.toTypedArray() },
                detach = {
                    main.removeCallbacks(bootstrap)
                    restoring = true
                    try { hosts.keys.toList().forEach(::refresh) } finally { restoring = false; hosts.clear() }
                },
                restore = { state -> (state as? Array<*>)?.filterIsInstance<TextView>()?.forEach(::refresh) },
                resumeAfterRejection = ::scheduleRefresh,
            )
            HotReloadLifecycle.afterCommit(::scheduleRefresh)
            HookLog.i("ONextBox-ClockSpacing", "Native clock spacing interfaces installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(PREFIX)
            HookLog.w("ONextBox-ClockSpacing", "Clock spacing interface unavailable; native spacing retained", it)
        }
    }
}
