package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Enables ColorOS's own extreme refresh-rate setting without changing its UI or choices. */
internal object SettingsRefreshRateHooker {
    private const val TAG = "SettingsRefreshRate"
    private const val UTILS_CLASS = "com.oplus.settings.feature.display.ScreenRefreshUtils"
    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val reportedUnlock = AtomicBoolean(false)
    private val nativeExtremeRates = setOf(144, 165, 185)

    fun hook(classLoader: ClassLoader) {
        val loaderId = System.identityHashCode(classLoader)
        if (!hookedLoaders.add(loaderId)) return
        val utils = XposedHelpers.findClassIfExists(UTILS_CLASS, classLoader)
        if (utils == null) {
            hookedLoaders.remove(loaderId)
            HookLog.w(TAG, "ColorOS refresh-rate utility unavailable")
            return
        }
        runCatching {
            val hooks = XposedBridge.hookAllMethods(
                utils,
                "getExtremeRefreshLevelRate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null ||
                            !LspConfig.isSettingsRefreshRateUnlockedXposed()
                        ) return
                        val configuredRate = param.result as? Int ?: return
                        if (configuredRate > 120) return
                        // ColorOS advertises the game's extreme rate separately from the
                        // disabled menu capability. Reuse that OEM value as the gate.
                        val advertisedRate = runCatching {
                            XposedHelpers.callStaticMethod(utils, "getGameExceedRefreshRate") as? Int
                        }.getOrNull() ?: return
                        if (advertisedRate !in nativeExtremeRates) return
                        param.result = advertisedRate
                        if (reportedUnlock.compareAndSet(false, true)) {
                            HookLog.i(TAG, "native ColorOS extreme option enabled: $advertisedRate Hz")
                        }
                    }
                },
            )
            if (hooks.isEmpty()) {
                hookedLoaders.remove(loaderId)
                HookLog.w(TAG, "getExtremeRefreshLevelRate unavailable")
            } else {
                HookLog.i(TAG, "native ColorOS extreme-rate capability hook installed")
            }
        }.onFailure {
            hookedLoaders.remove(loaderId)
            HookLog.w(TAG, "native refresh-rate hook registration failed", it)
        }
    }
}
