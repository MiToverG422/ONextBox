package com.mi.onextbox.lsp

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.util.WeakHashMap

internal object NetworkSpeedHooker {
    private const val TAG = "ONextBox-NetworkSpeed"
    internal const val CONTROLLER = "com.oplus.systemui.statusbar.phone.netspeed.OplusNetworkSpeedControllerExImpl"
    internal const val VIEW = "com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView"
    internal const val STATE = "com.oplus.systemui.statusbar.phone.netspeed.NetworkSpeedIconState"
    private val appearances = WeakHashMap<FrameLayout, NetworkSpeedAppearance>()
    private val hosts = WeakHashMap<FrameLayout, Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var bootstrap: Runnable? = null

    fun hook(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass(CONTROLLER, loader)
            val delay = Reflect.findDeclaredMethodExact(type, "postUpdateNetworkSpeedDelay", Long::class.javaPrimitiveType!!)
            ModernHookRegistry.installCompat("network-speed:refresh", delay, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val original = param.args[0] as? Long ?: return
                    val settings = NetworkSpeedSettings.snapshot()
                    param.args[0] = NetworkSpeedRules.refreshDelay(original, settings.customRefresh, settings.intervalMs)
                }
            })
        }.onFailure { HookLog.w(TAG, "Native refresh interface unavailable; original interval retained", it) }
        runCatching {
            val type = Reflect.findClass(VIEW, loader)
            type.getDeclaredField("mSpeedNumber")
            type.getDeclaredField("mSpeedUnit")
            val methods = mutableListOf(
                Reflect.findDeclaredMethodExact(type, "applyNetworkState", Reflect.findClass(STATE, loader)),
                Reflect.findDeclaredMethodExact(type, "onFinishInflate"),
                Reflect.findDeclaredMethodExact(type, "onAttachedToWindow"),
            )
            listOf("onConfigurationChanged", "onConfigChanged").forEach { name ->
                runCatching { Reflect.findDeclaredMethodExact(type, name, Configuration::class.java) }.getOrNull()?.let(methods::add)
            }
            runCatching { Reflect.findDeclaredMethodExact(type, "onDensityOrFontScaleChanged") }.getOrNull()?.let(methods::add)
            methods += type.declaredMethods.filter { it.name == "setFontTypeface" }
            methods.forEach { method ->
                ModernHookRegistry.installCompat("network-speed:${method.name}:${method.parameterCount}", method, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) { restore(param.thisObject) }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) apply(param.thisObject)
                    }
                })
            }
            ModernHookRegistry.installCompat("network-speed:detach",
                Reflect.findDeclaredMethodExact(type, "onDetachedFromWindow"), object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        restore(param.thisObject)
                        hosts.remove(param.thisObject)
                    }
                })
            HotReloadLifecycle.register("network-speed:appearance",
                capture = { hosts.keys.toTypedArray() },
                detach = {
                    bootstrap?.let(main::removeCallbacks)
                    bootstrap = null
                    appearances.keys.toList().forEach(::restore)
                    hosts.clear()
                },
                restore = { state -> (state as? Array<*>)?.forEach(::apply) },
            )
            bootstrap = Runnable {
                bootstrap = null
                if (!HotReloadLifecycle.isRetiring) runCatching {
                    val global = Reflect.callStaticMethod(Reflect.findClass("android.view.WindowManagerGlobal", loader), "getInstance")
                    val roots = global?.let { Reflect.getObjectField(it, "mViews") } as? List<*> ?: return@runCatching
                    fun visit(view: View) {
                        if (type.isInstance(view)) apply(view)
                        else if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
                    }
                    roots.filterIsInstance<View>().forEach(::visit)
                }.onFailure { HookLog.w(TAG, "Existing speed views will update on their next native callback", it) }
            }.also(main::post)
            HookLog.i(TAG, "Native network-speed interfaces installed")
        }.onFailure { HookLog.w(TAG, "Native display interface unavailable; original appearance retained", it) }
    }

    private fun restore(receiver: Any?) {
        val view = receiver as? FrameLayout ?: return
        appearances.remove(view)?.restore()
    }

    private fun apply(receiver: Any?) {
        if (HotReloadLifecycle.isRetiring) return
        val view = receiver as? FrameLayout ?: return
        restore(view)
        hosts[view] = Unit
        val settings = NetworkSpeedSettings.snapshot()
        if (!settings.hidePerSecond && !settings.hideUnits && !settings.singleLine &&
            !settings.followFontWeight && !settings.customFontSize) return
        runCatching {
            val number = Reflect.getObjectField(view, "mSpeedNumber") as? TextView ?: return@runCatching
            val unit = Reflect.getObjectField(view, "mSpeedUnit") as? TextView ?: return@runCatching
            if (view.layoutParams == null || number.layoutParams !is FrameLayout.LayoutParams) return@runCatching
            val style = NetworkSpeedAppearance(view, number, unit)
            appearances[view] = style
            style.apply(settings.hidePerSecond, settings.hideUnits, settings.singleLine, settings.followFontWeight,
                settings.fontSizeDp.takeIf { settings.customFontSize })
        }.onFailure {
            restore(view)
            HookLog.w(TAG, "Network-speed display update failed; original appearance restored", it)
        }
    }
}
