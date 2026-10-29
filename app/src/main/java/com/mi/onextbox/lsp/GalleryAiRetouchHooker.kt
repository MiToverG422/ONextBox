package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object GalleryAiRetouchHooker {
    private const val TAG = "ONextBox-GalleryAI"

    fun hook(loader: ClassLoader, apkPath: String) {
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        val stateKey = "${prefix}targets"
        if (!GalleryAiRetouchSettings.enabledXposed() && DexTargetCache.restoredState(stateKey) == null) return
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val saved = DexTargetCache.restoredState(stateKey) as? Map<String, Array<Method>>
            val targets = saved ?: run {
                System.loadLibrary("dexkit")
                GalleryAiRetouchDex.open(apkPath).use { GalleryAiRetouchTargets(it).resolve(loader) }
            }
            val support = targets.getValue("support").single()
            val config = targets.getValue("config").single()
            val getter = targets.getValue("key").single()
            val reached = AtomicBoolean()
            fun record() {
                if (reached.compareAndSet(false, true)) HookLog.i(TAG, "Native AI Retouch eligibility override reached")
            }
            ModernHookRegistry.installFast("${prefix}support", support, XposedInterface.Hooker { chain ->
                if (GalleryAiRetouchSettings.enabledXposed()) { record(); true } else chain.proceed()
            })
            ModernHookRegistry.installFast("${prefix}config", config, XposedInterface.Hooker { chain ->
                val enabled = GalleryAiRetouchSettings.enabledXposed()
                val key = if (enabled) runCatching { getter.invoke(chain.getArg(0)) as? String }.getOrNull() else null
                if (GalleryAiRetouchRules.shouldOverride(enabled, key)) { record(); true } else chain.proceed()
            })
            targets.getValue("callers").forEach { method ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { HookLog.w(TAG, "AI Retouch caller deoptimization unavailable: ${method.name}", it) }
            }
            HotReloadLifecycle.register(stateKey, capture = { targets })
            HookLog.i(TAG, "Native AI Retouch gates installed: ${support.declaringClass.name}#${support.name}, ${config.name}")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Incompatible gallery; AI Retouch hooks removed and stock behavior retained", it)
        }
    }
}
