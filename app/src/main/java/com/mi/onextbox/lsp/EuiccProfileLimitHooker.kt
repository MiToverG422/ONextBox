package com.mi.onextbox.lsp

import android.content.Context
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicBoolean

internal object EuiccProfileLimitHooker {
    private const val TAG = "ONextBox-Euicc"
    private const val STATE = "euicc:add-entry:targets-v1"
    private val logged = AtomicBoolean(false)

    fun hookPhone(loader: ClassLoader, apkPath: String) {
        runCatching {
            val saved = DexTargetCache.restoredState(STATE) as? Array<*>
            val targets = saved?.let { EuiccProfileLimitTargets.AddEntry(it) }
                ?: EuiccProfileLimitTargets.addEntry(loader, apkPath)
            ModernHookRegistry.installFast("euicc:add-entry:${targets.click.toGenericString()}", targets.click,
                XposedInterface.Hooker { chain ->
                    if (!LspConfig.isEsimProfileLimitBypassEnabledXposed() ||
                        Looper.myLooper() != Looper.getMainLooper()) return@Hooker chain.proceed()
                    val controller = runCatching {
                        val preference = chain.args.firstOrNull() ?: return@runCatching null
                        if (targets.preferenceKey.invoke(preference) != "add_esim_profile") return@runCatching null
                        val fragment = chain.thisObject ?: return@runCatching null
                        val context = targets.context.invoke(fragment) as? Context ?: return@runCatching null
                        if (!context.packageManager.getApplicationInfo("com.oplus.euicc", 0).enabled) {
                            return@runCatching null
                        }
                        targets.controller.get(fragment)
                    }.onFailure { HookLog.w(TAG, "Add-eSIM count context unavailable", it) }.getOrNull()
                        ?: return@Hooker chain.proceed()
                    if (logged.compareAndSet(false, true)) {
                        HookLog.i(TAG, "Bypassing the add-eSIM profile-count check")
                    }
                    EuiccProfileCountOverride.withUnrestrictedCount(controller, targets.profileCount) { chain.proceed() }
                })
            runCatching { ModernHookRuntime.requireModule().deoptimize(targets.click) }
                .onFailure { HookLog.w(TAG, "Add-eSIM caller deoptimization failed", it) }
            HotReloadLifecycle.register(STATE, capture = { targets.values })
            HookLog.i(TAG, "Installed eSIM settings add-entry count hook")
        }.onFailure { HookLog.w(TAG, "Add-eSIM count interface unavailable; native behavior retained", it) }
    }
}
