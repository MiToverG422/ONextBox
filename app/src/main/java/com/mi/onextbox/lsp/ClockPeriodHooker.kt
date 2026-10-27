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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.WeakHashMap

internal object ClockPeriodHooker {
    internal const val CLOCK = "com.android.systemui.statusbar.policy.Clock"
    internal const val STAT_CLOCK = "com.oplus.systemui.statusbar.widget.StatClock"
    internal const val QS_CLOCK = "com.oplus.systemui.qs.widget.OplusQSClock"
    private const val PREFIX = "clock-period:"
    private val hosts = WeakHashMap<TextView, ClockPeriodFormatter>()
    private val main = Handler(Looper.getMainLooper())
    private var bootstrap: Runnable? = null
    private var restoring = false

    fun hook(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass(CLOCK, loader)
            val stat = Reflect.findClass(STAT_CLOCK, loader)
            val shade = runCatching { Reflect.findClass(QS_CLOCK, loader) }.getOrNull()
            fun target(view: Any?) = stat.isInstance(view) || shade?.isInstance(view) == true
            fun active(view: Any?): Boolean {
                val settings = ClockPeriodSettings.snapshot()
                return settings.enabled && (stat.isInstance(view) || (settings.shade && shade?.isInstance(view) == true))
            }
            val smallTime = type.getDeclaredMethod("getSmallTime").apply { isAccessible = true }
            val calendar = type.getDeclaredField("mCalendar").apply { isAccessible = true }
            val format = type.getDeclaredField("mClockFormat").apply { isAccessible = true }
            val update = type.getDeclaredMethod("updateClock").apply { isAccessible = true }
            fun refresh(view: TextView) { runCatching { update.invoke(view) } }
            ModernHookRegistry.installCompat(PREFIX + "format", smallTime, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || restoring || HotReloadLifecycle.isRetiring || !target(param.thisObject)) return
                    val view = param.thisObject as TextView
                    val formatter = hosts.getOrPut(view) { ClockPeriodFormatter() }
                    val settings = ClockPeriodSettings.snapshot()
                    if (!active(view)) return
                    runCatching {
                        val current = calendar.get(view) as? Calendar ?: return@runCatching
                        val native = format.get(view) as? SimpleDateFormat ?: return@runCatching
                        val locale = view.resources.configuration.locales[0] ?: Locale.getDefault()
                        param.result = formatter.format(current, locale, native.toPattern(), settings.format, settings.small)
                    }.onFailure { HookLog.w("ONextBox-ClockPeriod", "Native clock retained after formatting failure", it) }
                }
            })
            ModernHookRegistry.installCompat(PREFIX + "accessibility", update, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && !restoring && !HotReloadLifecycle.isRetiring && active(param.thisObject)) {
                        (param.thisObject as TextView).let { it.contentDescription = it.text.toString() }
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
                        if (target(view)) refresh(view as TextView)
                        else if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                    }
                    roots.filterIsInstance<View>().forEach(::visit)
                }.onFailure { HookLog.w("ONextBox-ClockPeriod", "Clock will refresh on its next native tick", it) }
            }.also(main::post)
            HookLog.i("ONextBox-ClockPeriod", "Native clock day-period formatting installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(PREFIX)
            HookLog.w("ONextBox-ClockPeriod", "Clock format interface unavailable; native clock retained", it)
        }
    }
}
