package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal object ScreenTranslationHooker {
    private const val TAG = "ONextBox-Translation"

    fun hook(loader: ClassLoader, apkPath: String) {
        val key = "$TAG@${System.identityHashCode(loader)}"
        val oldStateKey = "$key:floating-targets"
        val stateKey = "$key:restrictions-v1"
        runCatching {
            val saved = (HotReloadLifecycle.restoredState(stateKey) as? Map<*, *>)?.mapNotNull { (name, value) ->
                (name as? String)?.let { it to ((value as? Array<*>)?.filterIsInstance<Method>()?.toTypedArray() ?: emptyArray()) }
            }?.toMap()
            val targets = if (saved?.containsKey(ScreenTranslationTargets.SCHEMA) == true) saved else {
                ApkDexSession.withSession(apkPath) { open -> ScreenTranslationTargets.resolveAll(open(), loader) }
            }.toMutableMap()
            if (targets["floating"].isNullOrEmpty()) {
                (HotReloadLifecycle.restoredState(oldStateKey) as? Array<*>)?.filterIsInstance<Method>()
                    ?.takeIf { it.isNotEmpty() }?.let { targets["floating"] = it.toTypedArray() }
            }

            fun install(name: String, count: Int = 1, action: (Array<Method>) -> Unit) {
                val methods = targets[name] ?: return
                runCatching {
                    require(methods.size >= count)
                    action(methods)
                    methods.drop(count).forEach { method ->
                        runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                            .onFailure { HookLog.w(TAG, "Translation caller deoptimization failed", it) }
                    }
                    HookLog.i(TAG, "Translation $name restriction hook installed")
                }.onFailure {
                    if (name == "floating") ModernHookRegistry.unhook(key)
                    else ModernHookRegistry.unhookPrefix("$key:$name:")
                    HookLog.w(TAG, "Translation $name hook unavailable; native behavior retained", it)
                }
            }
            fun deny(name: String, setting: String) = install(name) { methods ->
                val hookKey = if (name == "floating") key else "$key:$name:query"
                ModernHookRegistry.installFast(hookKey, methods.first(), XposedInterface.Hooker { chain ->
                    if (ScreenTranslationSettings.active(setting)) false else chain.proceed()
                })
            }
            deny("floating", ScreenTranslationSettings.ALLOW_FLOATING)
            deny("blacklist", ScreenTranslationSettings.BYPASS_APP_BLACKLIST)
            deny("pages", ScreenTranslationSettings.ALLOW_RESTRICTED_PAGES)
            install("landscape", 3) { methods ->
                val helper = methods[0].let { it.declaringClass.name to it.name }
                val entries = methods.slice(1..2).map { it.declaringClass.name to it.name }.toSet()
                ModernHookRegistry.installFast("$key:landscape:support", methods[0], XposedInterface.Hooker { chain ->
                    val native = chain.proceed()
                    if (native == false && ScreenTranslationSettings.active(ScreenTranslationSettings.ALLOW_LANDSCAPE) &&
                        ScreenTranslationRules.isLandscapeCaller(Thread.currentThread().stackTrace.toList(), helper, entries)) true
                    else native
                })
                // These entry frames must remain visible, rather than being inlined into their callers.
                methods.slice(1..2).forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            }
            install("browser", 2) { methods ->
                val versions = ScreenTranslationBrowserVersions(methods[1].declaringClass)
                ModernHookRegistry.installFast("$key:browser:allow", methods[0], XposedInterface.Hooker { chain ->
                    if (ScreenTranslationSettings.active(ScreenTranslationSettings.UNLOCK_BROWSER) &&
                        !(chain.getArg(0) as? String).isNullOrBlank()) true else chain.proceed()
                })
                ModernHookRegistry.installFast("$key:browser:version", methods[1], XposedInterface.Hooker { chain ->
                    val native = chain.proceed() as? String
                    if (!ScreenTranslationSettings.active(ScreenTranslationSettings.UNLOCK_BROWSER)) native else {
                        val pkg = chain.getArg(0) as? String
                        val version = (chain.getArg(1) as? Number)?.toLong() ?: 0
                        ScreenTranslationRules.browserClass(native, version, pkg?.let { versions.read(it, version, native) })
                    }
                })
            }
            HotReloadLifecycle.register(oldStateKey, capture = { targets["floating"] ?: emptyArray<Method>() })
            HotReloadLifecycle.register(stateKey, capture = { targets.toMap() })
        }.onFailure {
            HookLog.w(TAG, "Translation targets unavailable; native behavior retained", it)
        }
    }
}
