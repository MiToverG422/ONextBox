package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.LspConfig.BatteryFeature
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal object BatteryWhitelistRestoreHooker {
    private const val TAG = "ONextBox-BatteryRestore"

    fun hook(loader: ClassLoader, resolve: () -> Array<Method>) {
        val key = "$TAG@${System.identityHashCode(loader)}"
        val stateKey = "$key:targets"
        runCatching {
            val saved = (HotReloadLifecycle.restoredState(stateKey) as? Array<*>)?.filterIsInstance<Method>()
            val targets = saved?.takeIf { it.isNotEmpty() } ?: resolve().toList()
            val restore = targets.first()
            ModernHookRegistry.installFast(key, restore, XposedInterface.Hooker { chain ->
                if (LspConfig.isBatteryFeatureEnabledXposed(BatteryFeature.BlockWhitelistRestore)) null
                else chain.proceed()
            })
            targets.drop(1).forEach { caller ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(caller) }
                    .onFailure { HookLog.w(TAG, "Restore caller deoptimization failed", it) }
            }
            HotReloadLifecycle.register(stateKey, capture = { targets.toTypedArray() })
            HookLog.i(TAG, "Per-app default optimization restore hook installed")
        }.onFailure {
            ModernHookRegistry.unhook(key)
            HookLog.w(TAG, "Default optimization restore target unavailable; native behavior retained", it)
        }
    }
}
