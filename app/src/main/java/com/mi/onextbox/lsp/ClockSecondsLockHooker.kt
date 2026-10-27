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

internal object ClockSecondsLockHooker {
    private const val PREFIX = "clock-seconds-lock:"
    private val nativeChoices = WeakHashMap<TextView, Boolean>()
    private val main = Handler(Looper.getMainLooper())
    private var bootstrap: Runnable? = null
    private var restoring = false

    fun hook(loader: ClassLoader) {
        runCatching {
            val clock = Reflect.findClass(ClockPeriodHooker.CLOCK, loader)
            val apply = clock.getDeclaredMethod("applyShowSecondsFromController", Boolean::class.javaPrimitiveType)
            val showing = clock.getDeclaredField("mShowSeconds").apply { isAccessible = true }
            fun refresh(view: TextView, native: Boolean) {
                runCatching { apply.invoke(view, native) }
            }
            ModernHookRegistry.installCompat(PREFIX + "apply", apply, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as TextView
                    nativeChoices[view] = param.args[0] as Boolean
                    if (!restoring && !HotReloadLifecycle.isRetiring && ClockPeriodSettings.secondsLockedXposed()) {
                        param.args[0] = true
                    }
                }
            })
            HotReloadLifecycle.register(PREFIX + "views",
                capture = { nativeChoices.toMap() },
                detach = {
                    bootstrap?.let(main::removeCallbacks)
                    bootstrap = null
                    restoring = true
                    try { nativeChoices.toMap().forEach { (view, native) -> refresh(view, native) } }
                    finally { restoring = false; nativeChoices.clear() }
                },
                restore = { state ->
                    (state as? Map<*, *>)?.forEach { (view, native) ->
                        if (view is TextView && native is Boolean) refresh(view, native)
                    }
                },
            )
            bootstrap = Runnable {
                bootstrap = null
                if (!HotReloadLifecycle.isRetiring) runCatching {
                    val global = Reflect.callStaticMethod(Reflect.findClass("android.view.WindowManagerGlobal", loader), "getInstance")
                    val roots = global?.let { Reflect.getObjectField(it, "mViews") } as? List<*> ?: return@runCatching
                    fun visit(view: View) {
                        if (clock.isInstance(view)) {
                            val text = view as TextView
                            refresh(text, nativeChoices[text] ?: showing.getBoolean(text))
                        } else if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                    }
                    roots.filterIsInstance<View>().forEach(::visit)
                }.onFailure { HookLog.w("ONextBox-ClockSeconds", "Locked seconds will refresh on the next native update", it) }
            }.also(main::post)
        }.onFailure {
            ModernHookRegistry.unhookPrefix(PREFIX)
            HookLog.w("ONextBox-ClockSeconds", "Seconds lock unavailable; native choice retained", it)
        }
    }
}
