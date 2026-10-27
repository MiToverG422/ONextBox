package com.mi.onextbox.lsp

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.util.WeakHashMap

internal object ClockSpacingHooker {
    private const val PREFIX = "clock-spacing:"
    private val hosts = WeakHashMap<TextView, Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var bootstrap: Runnable? = null
    private var restoring = false

    fun hook(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass(ClockPeriodHooker.CLOCK, loader)
            val stat = Reflect.findClass(ClockPeriodHooker.STAT_CLOCK, loader)
            val measure = stat.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val dimensions = View::class.java.getDeclaredMethod("setMeasuredDimension", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
            val end = stat.getDeclaredField("paddingEnd").apply { isAccessible = true }
            val refreshWidth = runCatching { stat.getDeclaredMethod("refreshWidth").apply { isAccessible = true } }.getOrNull()
            val fixedWidth = runCatching { type.getDeclaredMethod("getSecondsFixedWidth") }.getOrNull()
            val minutes = runCatching { stat.getDeclaredField("cachedMinutesLayoutWidth").apply { isAccessible = true } }.getOrNull()
            val legacyWidth = if (refreshWidth == null) stat.getDeclaredField("actualWidth").apply { isAccessible = true } else null
            check(refreshWidth == null || (fixedWidth != null && minutes != null))
            fun enabled() = !restoring && !HotReloadLifecycle.isRetiring && ClockPeriodSettings.compactXposed()
            fun compact(view: TextView, width: Int) = ClockSpacingMeasure.compactWidth(width, view.text, view.paint,
                end.getInt(view), view.paddingLeft + view.paddingRight)
            fun refresh(view: TextView) {
                runCatching { refreshWidth?.invoke(view); view.requestLayout() }
            }
            ModernHookRegistry.installCompat(PREFIX + "measure", measure, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val view = param.thisObject as TextView
                    hosts[view] = Unit
                    if (!enabled()) return
                    val width = compact(view, view.measuredWidth)
                    legacyWidth?.setInt(view, width)
                    dimensions.invoke(view, width, view.measuredHeightAndState)
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
                        minutes.setInt(view, compact(view, minutes.getInt(view)))
                    }
                }
            })
            HotReloadLifecycle.register(PREFIX + "views",
                capture = { hosts.keys.toTypedArray() },
                detach = {
                    bootstrap?.let(main::removeCallbacks)
                    bootstrap = null
                    restoring = true
                    try { hosts.keys.toList().forEach(::refresh) } finally { restoring = false; hosts.clear() }
                },
                restore = { state -> (state as? Array<*>)?.filterIsInstance<TextView>()?.forEach(::refresh) },
            )
            bootstrap = Runnable {
                bootstrap = null
                if (!HotReloadLifecycle.isRetiring) runCatching {
                    val global = Reflect.callStaticMethod(Reflect.findClass("android.view.WindowManagerGlobal", loader), "getInstance")
                    val roots = global?.let { Reflect.getObjectField(it, "mViews") } as? List<*> ?: return@runCatching
                    fun visit(view: View) {
                        if (stat.isInstance(view)) { hosts[view as TextView] = Unit; refresh(view) }
                        else if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                    }
                    roots.filterIsInstance<View>().forEach(::visit)
                }.onFailure { HookLog.w("ONextBox-ClockSpacing", "Clock spacing will refresh on the next layout", it) }
            }.also(main::post)
            HookLog.i("ONextBox-ClockSpacing", "Native clock spacing interfaces installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(PREFIX)
            HookLog.w("ONextBox-ClockSpacing", "Clock spacing interface unavailable; native spacing retained", it)
        }
    }
}
