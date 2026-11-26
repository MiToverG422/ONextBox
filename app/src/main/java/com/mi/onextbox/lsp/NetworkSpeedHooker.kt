package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.TextView
import com.mi.onextbox.lsp.LspConfig.NetworkSpeedFeature as Feature
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.util.WeakHashMap

internal object NetworkSpeedHooker {
    private const val TAG = "ONextBox-NetworkSpeed"
    private const val APPEARANCE = "network-speed:appearance"
    internal const val CONTROLLER = "com.oplus.systemui.statusbar.phone.netspeed.OplusNetworkSpeedControllerExImpl"
    internal const val VIEW = "com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView"
    internal const val STATE = "com.oplus.systemui.statusbar.phone.netspeed.NetworkSpeedIconState"
    private val appearances = WeakHashMap<FrameLayout, NetworkSpeedAppearance>()
    private val hosts = WeakHashMap<FrameLayout, Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val appearanceKeys = Feature.entries.filter { it != Feature.CustomRefreshInterval }.mapTo(hashSetOf()) { it.key }

    fun hook(loader: ClassLoader) {
        SystemUiHookActivation.install(
            "network-speed:refresh-interval", setOf(Feature.CustomRefreshInterval.key),
            { LspConfig.isNetworkSpeedFeatureEnabledXposed(Feature.CustomRefreshInterval) },
            legacyTarget = { it.declaringClass.name == CONTROLLER && it.name == "postUpdateNetworkSpeedDelay" &&
                it.parameterTypes.contentEquals(arrayOf(java.lang.Long.TYPE)) },
        ) { installRefreshInterval(loader) }
        SystemUiHookActivation.install(APPEARANCE, appearanceKeys, {
            val settings = NetworkSpeedSettings.snapshot()
            settings.customAppearance || settings.hideLowSpeed
        }) { installAppearance(loader) }
    }

    private fun installRefreshInterval(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass(CONTROLLER, loader)
            val delay = Reflect.findDeclaredMethodExact(type, "postUpdateNetworkSpeedDelay", Long::class.javaPrimitiveType!!)
            ModernHookRegistry.installFast("network-speed:refresh", delay) { chain ->
                val original = chain.getArg(0) as Long
                val settings = NetworkSpeedSettings.snapshot()
                val replacement = NetworkSpeedRules.refreshDelay(original, settings.customRefresh, settings.intervalMs)
                if (replacement == original) chain.proceed() else chain.proceed(arrayOf(replacement))
            }
        }.onFailure { HookLog.w(TAG, "Native refresh interface unavailable; original interval retained", it) }
    }

    private fun installAppearance(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass(VIEW, loader)
            type.getDeclaredField("mSpeedNumber")
            type.getDeclaredField("mSpeedUnit")
            val stateType = Reflect.findClass(STATE, loader)
            val visibility = runCatching { NetworkSpeedVisibility(type, stateType) }
                .onFailure { HookLog.w(TAG, "Low-speed visibility interface unavailable; native visibility retained", it) }.getOrNull()
            fun refresh(host: FrameLayout, settings: NetworkSpeedSettings.State) {
                restore(host)
                runCatching { visibility?.refresh(host, settings) }
                    .onFailure { HookLog.w(TAG, "Network-speed visibility refresh failed", it) }
                if (settings.customAppearance) apply(host, settings)
            }
            val methods = mutableListOf(
                Reflect.findDeclaredMethodExact(type, "applyNetworkState", stateType),
                Reflect.findDeclaredMethodExact(type, "onFinishInflate"),
                Reflect.findDeclaredMethodExact(type, "onAttachedToWindow"),
            )
            listOf("onConfigurationChanged", "onConfigChanged").forEach { name ->
                runCatching { Reflect.findDeclaredMethodExact(type, name, Configuration::class.java) }.getOrNull()?.let(methods::add)
            }
            runCatching { Reflect.findDeclaredMethodExact(type, "onDensityOrFontScaleChanged") }.getOrNull()?.let(methods::add)
            methods += type.declaredMethods.filter { it.name == "setFontTypeface" }
            methods.forEach { method ->
                val networkUpdate = method.name == "applyNetworkState"
                ModernHookRegistry.installFast("network-speed:${method.name}:${method.parameterCount}", method) { chain ->
                    val host = chain.thisObject as? FrameLayout ?: return@installFast chain.proceed()
                    if (host !in hosts) hosts[host] = Unit
                    restore(host)
                    val settings = NetworkSpeedSettings.snapshot()
                    val result = if (networkUpdate && visibility != null) {
                        val incoming = chain.getArg(0)
                        val filtered = visibility.filter(host, incoming, settings)
                        if (filtered === incoming) chain.proceed() else chain.proceed(arrayOf(filtered))
                    } else chain.proceed()
                    if (settings.customAppearance) apply(host, settings)
                    result
                }
            }
            ModernHookRegistry.installCompat("network-speed:detach",
                Reflect.findDeclaredMethodExact(type, "onDetachedFromWindow"), object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        restore(param.thisObject)
                        visibility?.restore(param.thisObject as FrameLayout)
                        hosts.remove(param.thisObject)
                    }
                })
            var lastSettings: NetworkSpeedSettings.State? = null
            var discover = true
            var preferences: SharedPreferences? = null
            val updateHosts = Runnable {
                if (!HotReloadLifecycle.isRetiring) runCatching {
                    val settings = NetworkSpeedSettings.snapshot()
                    if (discover) {
                        discover = false
                        SystemUiWindowViews.find(loader, type::isInstance).filterIsInstance<FrameLayout>()
                            .forEach { hosts[it] = Unit }
                    }
                    if (lastSettings != settings) {
                        lastSettings = settings
                        hosts.keys.toList().forEach { refresh(it, settings) }
                    }
                }.onFailure { HookLog.w(TAG, "Existing speed views will update on their next native callback", it) }
            }
            fun scheduleRefresh() {
                main.removeCallbacks(updateHosts)
                main.post(updateHosts)
            }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key in appearanceKeys || key == NetworkSpeedSettings.FONT_SIZE ||
                    key == NetworkSpeedSettings.LOW_SPEED_THRESHOLD || key == MODERN_PREFERENCES_REVISION_KEY
                ) scheduleRefresh()
            }
            fun observe() {
                if (preferences == null) preferences = ModernHookRuntime.remotePreferences("lsp_features")?.also {
                    it.registerOnSharedPreferenceChangeListener(listener)
                }
                lastSettings = null
                scheduleRefresh()
            }
            HotReloadLifecycle.register(APPEARANCE,
                capture = { hosts.keys.toTypedArray() },
                detach = {
                    preferences?.unregisterOnSharedPreferenceChangeListener(listener)
                    preferences = null
                    main.removeCallbacks(updateHosts)
                    appearances.keys.toList().forEach(::restore)
                    visibility?.restoreAll()
                    hosts.clear()
                },
                restore = { state ->
                    (state as? Array<*>)?.filterIsInstance<FrameLayout>()?.forEach { hosts[it] = Unit }
                },
                resumeAfterRejection = ::observe,
            )
            HotReloadLifecycle.afterCommit(::observe)
            HookLog.i(TAG, "Native network-speed interfaces installed")
        }.onFailure { HookLog.w(TAG, "Native display interface unavailable; original appearance retained", it) }
    }

    private fun restore(receiver: Any?) {
        val view = receiver as? FrameLayout ?: return
        appearances.remove(view)?.restore()
    }

    private fun apply(view: FrameLayout, settings: NetworkSpeedSettings.State) {
        if (HotReloadLifecycle.isRetiring) return
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
