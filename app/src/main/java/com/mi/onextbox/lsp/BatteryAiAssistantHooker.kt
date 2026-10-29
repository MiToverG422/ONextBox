package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.reflect.Method

internal object BatteryAiAssistantHooker {
    private const val TAG = "BatteryAiAssistant"
    fun hook(loader: ClassLoader, apkPath: String) {
        runCatching {
            val stateKey = "$TAG:eligibility-v1"
            val saved = DexTargetCache.restoredState(stateKey) as? Method
            val method = saved ?: ApkDexSession.withSession(apkPath) { open ->
                val debugOverride = open().findMethod { matcher {
                    usingStrings("oplus_battery_agent_debug_force_entry")
                    returnType = "boolean"; paramTypes(Context::class.java)
                } }.single()
                val eligibility = debugOverride.callers.single { data ->
                    data.className == debugOverride.className && data.returnTypeName == "boolean" &&
                        data.paramTypeNames == listOf("android.content.Context")
                }
                eligibility.callers.filter { it.isMethod }.forEach { caller ->
                    runCatching { ModernHookRuntime.requireModule().deoptimize(caller.getMethodInstance(loader)) }
                }
                eligibility.getMethodInstance(loader)
            }
            HotReloadLifecycle.register(stateKey, capture = { method })
            ModernHookRegistry.installFast("$TAG:$method", method) { chain ->
                if (SystemExtrasSettings.active(SystemExtrasSettings.AI_BATTERY) &&
                    chain.args.firstOrNull() != null && android.os.Process.myUid() / 100000 == 0) true
                else chain.proceed()
            }
            HookLog.i(TAG, "Native AI battery assistant eligibility hooked")
        }.onFailure { HookLog.w(TAG, "AI battery assistant interface unavailable", it) }
    }
}
