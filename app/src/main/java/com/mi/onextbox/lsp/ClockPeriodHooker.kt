package com.mi.onextbox.lsp

import android.os.Handler
import android.os.Looper
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
    private const val VIEWS = PREFIX + "views"
    private val hosts = WeakHashMap<TextView, ClockPeriodFormatter>()
    private val main = Handler(Looper.getMainLooper())
    private var restoring = false

    fun hook(loader: ClassLoader) = SystemUiHookActivation.install(
        VIEWS, setOf(ClockPeriodSettings.ENABLED), ClockPeriodSettings::enabledXposed,
    ) { install(loader) }

    private fun install(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass(CLOCK, loader)
            val stat = Reflect.findClass(STAT_CLOCK, loader)
            val shade = runCatching { Reflect.findClass(QS_CLOCK, loader) }.getOrNull()
            fun target(view: Any?) = stat.isInstance(view) || shade?.isInstance(view) == true
            fun active(view: Any?) = ClockPeriodSettings.enabledXposed() &&
                (stat.isInstance(view) || (shade?.isInstance(view) == true && ClockPeriodSettings.shadeXposed()))
            val smallTime = type.getDeclaredMethod("getSmallTime").apply { isAccessible = true }
            val calendar = type.getDeclaredField("mCalendar").apply { isAccessible = true }
            val format = type.getDeclaredField("mClockFormat").apply { isAccessible = true }
            val update = type.getDeclaredMethod("updateClock").apply { isAccessible = true }
            fun refresh(view: TextView) { runCatching { update.invoke(view) } }
            ModernHookRegistry.installCompat(PREFIX + "format", smallTime, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || restoring || HotReloadLifecycle.isRetiring || !active(param.thisObject)) return
                    val view = param.thisObject as TextView
                    val formatter = hosts.getOrPut(view) { ClockPeriodFormatter() }
                    val settings = ClockPeriodSettings.snapshot()
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
            val bootstrap = Runnable {
                if (!HotReloadLifecycle.isRetiring && ClockPeriodSettings.enabledXposed()) {
                    SystemUiWindowViews.find(loader, ::target).filterIsInstance<TextView>().forEach(::refresh)
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
            HookLog.i("ONextBox-ClockPeriod", "Native clock day-period formatting installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(PREFIX)
            HookLog.w("ONextBox-ClockPeriod", "Clock format interface unavailable; native clock retained", it)
        }
    }
}
