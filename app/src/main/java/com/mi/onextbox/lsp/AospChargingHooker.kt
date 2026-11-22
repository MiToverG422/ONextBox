package com.mi.onextbox.lsp

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object AospChargingHooker {
    private const val RIPPLE_STATE = "aosp-charging-ripple"

    fun hook(loader: ClassLoader) {
        if (AospUiSettings.enabledXposed(AospUiSettings.CHARGING_EFFECT) || HotReloadLifecycle.restoredState(RIPPLE_STATE) != null) runCatching { ripple(loader) }
            .onFailure { HookLog.w("ONextBox-AospCharging", "Native charging ripple unavailable", it) }
        if (AospUiSettings.enabledXposed(AospUiSettings.CHARGING_SOUND)) runCatching { sound(loader) }
            .onFailure { HookLog.w("ONextBox-AospCharging", "Native charging sound unavailable", it) }
    }

    private fun ripple(loader: ClassLoader) {
        val type = HostClassLookup.load("com.android.systemui.charging.WiredChargingRippleController", false, loader)
        val callbacks = type.getMethod("registerCallbacks")
        val originals = Collections.synchronizedMap(IdentityHashMap<Any, Boolean>())
        val registered = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()))
        val ready = AtomicBoolean(false)
        val pending = AtomicInteger()
        ModernHookBridge.hookAllMethods(type, "registerCallbacks", object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                if (!registered.add(owner)) param.result = null
            }
        })
        ModernHookBridge.hookAllMethods(type, "startRipple", object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                if (!AospUiSettings.enabledXposed(AospUiSettings.CHARGING_EFFECT) && originals[owner] != true) param.result = null
                else HookLog.i("ONextBox-AospCharging", "Native charging ripple requested")
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                val failure = param.throwable ?: return
                if (param.thisObject !in originals) return
                ready.set(false)
                HookLog.w("ONextBox-AospCharging", "Ripple failed; retaining OEM animation", failure)
                param.result = null
            }
        })
        ModernHookBridge.hookAllConstructors(type, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                if (!AospUiSettings.enabledXposed(AospUiSettings.CHARGING_EFFECT)) return
                originals[owner] = ModernReflect.getObjectField(owner, "rippleEnabled") == true
                ModernReflect.setObjectField(owner, "rippleEnabled", true)
                pending.incrementAndGet()
                val posted = Handler(Looper.getMainLooper()).post {
                    try {
                        callbacks.invoke(owner)
                        ready.set(true)
                        HookLog.i("ONextBox-AospCharging", "Native charging ripple callbacks ready")
                    } catch (error: Exception) {
                        registered.remove(owner)
                        ModernReflect.setObjectField(owner, "rippleEnabled", originals.remove(owner) == true)
                        HookLog.w("ONextBox-AospCharging", "Ripple callback registration failed", error)
                    } finally {
                        pending.decrementAndGet()
                    }
                }
                if (!posted) {
                    pending.decrementAndGet()
                    ModernReflect.setObjectField(owner, "rippleEnabled", originals.remove(owner) == true)
                }
            }
        })
        runCatching {
            val stock = HostClassLookup.load("com.oplus.systemui.keyguard.charginganim.ChargingAnimationImpl", false, loader)
            stock.declaredMethods.filter { it.name.contains("createChargeAnim") }.forEach { method ->
                ModernHookBridge.hookMethod(method, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (ready.get() && AospUiSettings.enabledXposed(AospUiSettings.CHARGING_EFFECT)) param.result = null
                    }
                })
            }
        }.onFailure { HookLog.w("ONextBox-AospCharging", "OEM animation adapter unavailable", it) }
        // registerCallbacks installs two SystemUI-owned listeners. Keep them attached across generations.
        HotReloadLifecycle.register(RIPPLE_STATE,
            canPrepare = { pending.get() == 0 }, canReload = { pending.get() == 0 },
            capture = { arrayOf(registered.toTypedArray(), synchronized(originals) { IdentityHashMap(originals) }, ready.get()) },
            detach = { registered.clear(); originals.clear(); ready.set(false) },
            restore = { saved ->
                if (saved is Array<*>) {
                    (saved[0] as Array<*>).filterNotNullTo(registered)
                    (saved[1] as Map<*, *>).forEach { (owner, original) ->
                        if (owner != null && original is Boolean) originals[owner] = original
                    }
                    ready.set(saved[2] == true)
                }
            })
    }

    private fun sound(loader: ClassLoader) {
        val type = HostClassLookup.load("com.oplus.systemui.statusbar.notification.power.OplusPowerUISoundUtil", false, loader)
        ModernHookBridge.hookAllMethods(type, "playPowerUISound", object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!AospChargingRules.replaceSound(AospUiSettings.enabledXposed(AospUiSettings.CHARGING_SOUND), param.args[0] as? Int)) return
                val owner = param.thisObject ?: return
                val context = ModernReflect.getObjectField(owner, "context") as Context
                val module = context.createPackageContext("com.mi.onextbox", 0)
                module.resources.openRawResourceFd(R.raw.aosp_charging_started).use { check(it.length > 0) }
                val player = ModernReflect.getObjectField(owner, "notificationPlayer") ?: return
                val uri = Uri.parse("android.resource://com.mi.onextbox/${R.raw.aosp_charging_started}")
                val attributes = AudioAttributes.Builder().setLegacyStreamType(android.media.AudioManager.STREAM_SYSTEM).build()
                ModernReflect.callMethod(player, "play", context, uri, false, attributes, 1f)
                HookLog.i("ONextBox-AospCharging", "Native charging sound queued")
                param.result = null
            }
        })
    }
}

internal object AospChargingRules {
    fun replaceSound(enabled: Boolean, flag: Int?) = enabled && flag == 3
}
