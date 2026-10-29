package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

internal object BatteryWhitelistSystemHooker {
    private const val TAG = "ONextBox-BatteryRestoreSystem"
    private val automaticMethods = setOf(
        "reconfirmWhiteAppSinceBoot", "reconfirmWhiteList", "whiteListChangedHandle",
    )

    fun hook(loader: ClassLoader) {
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        runCatching {
            val controller = ModernReflect.findClass("com.android.server.DeviceIdleController", loader)
            val add = controller.getDeclaredMethod("addPowerSaveWhitelistAppsInternal", List::class.java)
            val remove = controller.getDeclaredMethod("removePowerSaveWhitelistAppInternal", String::class.java)
            require(add.returnType == Int::class.javaPrimitiveType && remove.returnType == Boolean::class.javaPrimitiveType)
            val automaticDepth = ThreadLocal.withInitial { 0 }
            listOf(add, remove).forEach { method ->
                ModernHookRegistry.installFast(prefix + method.name, method, XposedInterface.Hooker { chain ->
                    if (LspConfig.isBatteryFeatureEnabledXposed(LspConfig.BatteryFeature.BlockWhitelistRestore) &&
                        (automaticDepth.get() > 0 || isAutomaticRestore(Throwable().stackTrace))
                    ) {
                        if (method == add) 0 else false
                    } else chain.proceed()
                })
            }
            // Keep the helper's list bookkeeping; suppress only its changes to the user whitelist.
            ModernReflect.findClassIfExists("com.android.server.OplusDeviceIdleHelper", loader)?.let { helper ->
                helper.declaredMethods.filter { it.name in automaticMethods }.forEach { method ->
                    ModernHookRegistry.installFast(prefix + method.toGenericString(), method, XposedInterface.Hooker { chain ->
                        val depth = automaticDepth.get()
                        automaticDepth.set(depth + 1)
                        try { chain.proceed() } finally {
                            if (depth == 0) automaticDepth.remove() else automaticDepth.set(depth)
                        }
                    })
                }
                helper.declaredMethods.forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
            }
            controller.declaredClasses.filter { it.simpleName.contains("Wrapper") }.forEach { wrapper ->
                wrapper.declaredMethods.filter { it.name == add.name || it.name == remove.name }.forEach {
                    runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                }
            }
            HookLog.i(TAG, "System whitelist reconciliation guard installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "System whitelist reconciliation guard unavailable", it)
        }
    }

    private fun isAutomaticRestore(trace: Array<StackTraceElement>): Boolean = trace.any {
        it.methodName in automaticMethods &&
            (it.className.startsWith("com.android.server.") || it.className.startsWith("com.oplus."))
    }
}
