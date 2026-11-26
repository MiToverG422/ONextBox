package com.mi.onextbox.lsp

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.util.WeakHashMap

internal object ClockTabularDigitsHooker {
    private const val PREFIX = "clock-tabular-digits:"
    private const val VIEWS = PREFIX + "views"
    private data class Appearance(val original: String?, val applied: String)
    private val hosts = WeakHashMap<TextView, Unit>()
    private val appearances = WeakHashMap<TextView, Appearance>()
    private val main = Handler(Looper.getMainLooper())
    private var restoring = false
    private val digitFeature = Regex("""^(?:['"](?:pnum|tnum)['"]|pnum|tnum)(?:\s+(?:on|off|\d+))?$""")

    private fun tabularFeatures(original: String?): String =
        (original.orEmpty().split(',').map(String::trim)
            .filter { it.isNotEmpty() && !digitFeature.matches(it) } + listOf("'pnum' 0", "'tnum' 1"))
            .joinToString(", ")

    fun hook(loader: ClassLoader) = SystemUiHookActivation.install(
        VIEWS, setOf(ClockPeriodSettings.TABULAR_DIGITS), ClockPeriodSettings::tabularDigitsXposed,
    ) { install(loader) }

    private fun install(loader: ClassLoader) {
        runCatching {
            val clock = Reflect.findClass(ClockPeriodHooker.CLOCK, loader)
            val stat = Reflect.findClass(ClockPeriodHooker.STAT_CLOCK, loader)
            val update = clock.getDeclaredMethod("updateClock")
            val measure = stat.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val refreshWidth = runCatching { stat.getDeclaredMethod("refreshWidth").apply { isAccessible = true } }.getOrNull()
            val suffixWidth = runCatching { clock.getDeclaredField("mCachedSecondsSuffixMaxWidth").apply { isAccessible = true } }.getOrNull()
            fun refresh(view: TextView) {
                suffixWidth?.setFloat(view, -1f)
                refreshWidth?.invoke(view)
                view.requestLayout()
                view.invalidate()
            }
            fun restore(view: TextView) {
                val appearance = appearances.remove(view) ?: return
                if (view.fontFeatureSettings == appearance.applied) {
                    view.fontFeatureSettings = appearance.original
                    refresh(view)
                }
            }
            fun apply(view: TextView, refreshLayout: Boolean = true) {
                if (restoring || HotReloadLifecycle.isRetiring) return
                if (!ClockPeriodSettings.tabularDigitsXposed()) {
                    restore(view)
                    return
                }
                if (view !in hosts) hosts[view] = Unit
                val current = view.fontFeatureSettings
                appearances[view]?.let { if (it.applied == current) return }
                val next = tabularFeatures(current)
                appearances[view] = Appearance(current, next)
                if (current == next) return
                view.fontFeatureSettings = next
                suffixWidth?.setFloat(view, -1f)
                if (refreshLayout) refresh(view)
            }
            for ((name, method) in listOf("update" to update, "measure" to measure)) {
                ModernHookRegistry.installCompat(PREFIX + name, method, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (stat.isInstance(param.thisObject)) apply(param.thisObject as TextView)
                    }
                })
            }
            if (refreshWidth != null) ModernHookRegistry.installCompat(PREFIX + "width", refreshWidth, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    apply(param.thisObject as TextView, refreshLayout = false)
                }
            })
            val bootstrap = Runnable {
                if (!HotReloadLifecycle.isRetiring && ClockPeriodSettings.tabularDigitsXposed()) runCatching {
                    SystemUiWindowViews.find(loader, stat::isInstance).filterIsInstance<TextView>().forEach { apply(it) }
                }.onFailure { HookLog.w("ONextBox-ClockDigits", "Clock digits will refresh on the next native update", it) }
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
                    try { appearances.keys.toList().forEach(::restore) }
                    finally { restoring = false; appearances.clear(); hosts.clear() }
                },
                restore = { state -> (state as? Array<*>)?.filterIsInstance<TextView>()?.forEach { apply(it) } },
                resumeAfterRejection = ::scheduleRefresh,
            )
            HotReloadLifecycle.afterCommit(::scheduleRefresh)
            HookLog.i("ONextBox-ClockDigits", "Native tabular clock digits installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(PREFIX)
            HookLog.w("ONextBox-ClockDigits", "Clock digit interface unavailable; native typography retained", it)
        }
    }
}
