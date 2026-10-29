package com.mi.onextbox.lsp

import android.content.ContentProvider
import android.os.Bundle
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object SystemFileUpdateHooker {
    private const val TAG = "SystemFileUpdate"
    private const val SCHEMA = "schema:opex-discovery-v1"

    fun hook(loader: ClassLoader, apkPath: String) {
        val prefix = "$TAG@${System.identityHashCode(loader)}"
        @Suppress("UNCHECKED_CAST")
        val saved = DexTargetCache.restoredState("$prefix:targets") as? Map<String, Array<Method>>
        val targets = if (saved?.containsKey(SCHEMA) == true) saved else {
            val refreshed = runCatching {
                System.loadLibrary("dexkit")
                DexKitBridge.create(apkPath).use { dex ->
                    val data = dex.findMethod {
                        matcher {
                            usingStrings("can not handle this task: ")
                            returnType = "void"
                            paramTypes("com.oplusos.sau.opex.service.OpexWorkService", "int", "android.os.Bundle")
                        }
                    }.single()
                    val target = data.getMethodInstance(loader)
                    check(Modifier.isStatic(target.modifiers))
                    val callers = (data.callers + data.callers.flatMap { it.callers }).distinctBy { it.descriptor }
                        .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }.toTypedArray()
                    mapOf("tasks" to arrayOf(target), "callers" to callers, SCHEMA to emptyArray())
                }
            }.onFailure { HookLog.w(TAG, "OPEX discovery target unavailable, stock behavior retained", it) }
            if (saved != null) check(refreshed.isSuccess) { "OPEX target cache upgrade failed, restart the scope" }
            saved.orEmpty() + refreshed.getOrDefault(emptyMap())
        }
        HotReloadLifecycle.register("$prefix:targets", capture = { targets })
        fun enabled() = SoftwareUpdateSettings.enabledXposed(SoftwareUpdateFeature.BlockSystemFiles)

        runCatching {
            val target = targets.getValue("tasks").single()
            ModernHookRegistry.installFast("$prefix:tasks", target, XposedInterface.Hooker { chain ->
                val task = chain.args.getOrNull(1) as? Int
                if (task != null && SoftwareUpdateDiscoveryRules.blocksOpexTask(task, enabled())) null else chain.proceed()
            })
            targets["callers"].orEmpty().forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
        }.onFailure { HookLog.w(TAG, "OPEX query guard unavailable", it) }

        runCatching {
            val provider = loader.loadClass("com.oplusos.sau.opex.db.OpexProvider")
            check(ContentProvider::class.java.isAssignableFrom(provider))
            val call = provider.getDeclaredMethod("call", String::class.java, String::class.java, Bundle::class.java)
            check(call.returnType == Bundle::class.java && !Modifier.isStatic(call.modifiers))
            ModernHookRegistry.installFast("$prefix:info", call, XposedInterface.Hooker { chain ->
                val result = chain.proceed() as? Bundle ?: return@Hooker null
                val status = if (result.containsKey("opexStatus")) result.getInt("opexStatus") else null
                if (SoftwareUpdateDiscoveryRules.hidesOpexInfo(chain.args.firstOrNull() as? String, status, enabled())) {
                    Bundle(result).apply {
                        putInt("opexStatus", -1)
                        putString("opexExtraData", "{}")
                    }
                } else result
            })
        }.onFailure { HookLog.w(TAG, "OPEX availability guard unavailable", it) }
    }
}
