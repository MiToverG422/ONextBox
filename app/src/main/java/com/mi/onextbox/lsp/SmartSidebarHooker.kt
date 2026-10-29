package com.mi.onextbox.lsp

import android.content.pm.LauncherActivityInfo
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

internal object SmartSidebarHooker {
    private const val TAG = "ONextBox-Sidebar"

    fun hook(loader: ClassLoader, apkPath: String) {
        val prefix = "$TAG@${System.identityHashCode(loader)}"
        val stateKey = "$prefix:targets-v1"
        runCatching {
            val saved = DexTargetCache.restoredState(stateKey) as? Map<*, *>
            val groups = saved?.takeIf { it.containsKey(SmartSidebarTargets.SCHEMA) }?.mapNotNull { (name, value) ->
                (name as? String)?.let { it to ((value as? Array<*>)?.filterIsInstance<Method>()?.toTypedArray() ?: emptyArray()) }
            }?.toMap() ?: ApkDexSession.withSession(apkPath) { open -> SmartSidebarTargets.resolve(open(), loader) }
            fun install(name: String, count: Int, action: (Array<Method>) -> Unit) {
                val methods = groups[name] ?: return
                runCatching {
                    require(methods.size >= count)
                    action(methods)
                    methods.drop(count).forEach {
                        runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                            .onFailure { error -> HookLog.w(TAG, "Sidebar caller deoptimization failed", error) }
                    }
                    HookLog.i(TAG, "Sidebar $name restriction hooks installed")
                }.onFailure {
                    ModernHookRegistry.unhookPrefix("$prefix:$name:")
                    HookLog.w(TAG, "Sidebar $name hooks unavailable; native behavior retained", it)
                }
            }
            install("apps", 3) { methods ->
                val loaded = methods[2].declaringClass.getDeclaredField("hasLoadedAllApp").apply { isAccessible = true }
                require(loaded.type == Boolean::class.javaPrimitiveType && Modifier.isStatic(loaded.modifiers))
                val lastEnabled = AtomicReference<Boolean?>(null)
                ModernHookRegistry.installFast("$prefix:apps:available", methods[0], XposedInterface.Hooker { chain ->
                    val native = chain.proceed()
                    if (native == false && SmartSidebarSettings.active(SmartSidebarSettings.ALLOW_APPS) &&
                        runCatching { !(chain.getArg(0) as? LauncherActivityInfo)?.label.isNullOrBlank() }.getOrDefault(false)) true
                    else native
                })
                ModernHookRegistry.installFast("$prefix:apps:hidden", methods[1], XposedInterface.Hooker { chain ->
                    if (SmartSidebarSettings.active(SmartSidebarSettings.ALLOW_APPS)) false else chain.proceed()
                })
                ModernHookRegistry.installFast("$prefix:apps:reload", methods[2], XposedInterface.Hooker { chain ->
                    val enabled = SmartSidebarSettings.active(SmartSidebarSettings.ALLOW_APPS)
                    if (lastEnabled.getAndSet(enabled) != enabled) loaded.setBoolean(null, false)
                    chain.proceed()
                })
                HotReloadLifecycle.register("$prefix:app-cache", detach = { loaded.setBoolean(null, false) })
            }
            install("display", 2) { methods ->
                methods.take(2).forEachIndexed { index, method ->
                    ModernHookRegistry.installFast("$prefix:display:$index", method, XposedInterface.Hooker { chain ->
                        if (SmartSidebarSettings.active(SmartSidebarSettings.ALLOW_IN_APPS)) false else chain.proceed()
                    })
                }
            }
            HotReloadLifecycle.register(stateKey, capture = { groups })
        }.onFailure { HookLog.w(TAG, "Sidebar interfaces unavailable; native behavior retained", it) }
    }
}
