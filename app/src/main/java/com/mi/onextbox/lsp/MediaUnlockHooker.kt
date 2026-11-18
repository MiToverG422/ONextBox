package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object MediaUnlockHooker {
    fun camera(loader: ClassLoader, path: String) = hook(MediaUnlockCatalog.App.Camera, loader, path)
    fun gallery(loader: ClassLoader, path: String) = hook(MediaUnlockCatalog.App.Gallery, loader, path)

    private fun hook(app: MediaUnlockCatalog.App, loader: ClassLoader, path: String) {
        val tag = "ONextBox-${app.name}Unlock"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        val stateKey = "${prefix}targets"
        if (!MediaUnlockSettings.active(app) && DexTargetCache.restoredState(stateKey) == null) return
        runCatching {
            // Store only framework/host objects across module generations.
            val saved = DexTargetCache.restoredState(stateKey) as? Map<*, *>
            val targets = saved?.takeIf { it.containsKey(MediaUnlockTargets.SCHEMA) }?.mapNotNull { (key, value) ->
                (key as? String)?.let { it to (value as? Array<*>)?.filterIsInstance<Method>().orEmpty().toTypedArray() }
            }?.toMap() ?: HostDexMetadata.methods(loader, path, "media-unlock-${app.name.lowercase()}", MediaUnlockTargets.SCHEMA) {
                if (app == MediaUnlockCatalog.App.Camera) MediaUnlockTargets.camera(it, loader)
                else MediaUnlockTargets.gallery(it, loader)
            }
            val reached = ConcurrentHashMap.newKeySet<String>()
            fun result(key: String?): Boolean? {
                val desired = MediaUnlockSettings.overrideValue(app, key) ?: return null
                if (key != null && reached.add(key)) HookLog.i(tag, "Native eligibility reached: $key=$desired")
                return desired
            }
            if (app == MediaUnlockCatalog.App.Camera) {
                targets.getValue("readers").forEachIndexed { index, method ->
                    check(MediaUnlockTargets.cameraReader(method))
                    ModernHookRegistry.installFast("${prefix}reader:$index", method, XposedInterface.Hooker { chain ->
                        result(chain.getArg(0) as? String) ?: chain.proceed()
                    })
                }
            } else {
                val dispatcher = targets.getValue("dispatcher").single()
                val getter = targets.getValue("key").single().apply { isAccessible = true }
                check(GalleryAiRetouchRules.configMethod(dispatcher))
                ModernHookRegistry.installFast("${prefix}dispatcher", dispatcher, XposedInterface.Hooker { chain ->
                    val key = runCatching { getter.invoke(chain.getArg(0)) as? String }.getOrNull()
                    result(key) ?: chain.proceed()
                })
                targets.getValue("stringReaders").forEachIndexed { index, method ->
                    check(MediaUnlockTargets.galleryKeyReader(method))
                    val keyIndex = method.parameterTypes.indexOf(String::class.java)
                    ModernHookRegistry.installFast("${prefix}string:$index", method, XposedInterface.Hooker { chain ->
                        result(chain.getArg(keyIndex) as? String) ?: chain.proceed()
                    })
                }
                targets["functionReaders"].orEmpty().forEachIndexed { index, method ->
                    ModernHookRegistry.installFast("${prefix}function:$index", method, XposedInterface.Hooker { chain ->
                        val key = chain.getArg(0) as? String
                        if (key == "pref_ai_filter_enable") result(key) ?: chain.proceed() else chain.proceed()
                    })
                }
            }
            val callers = MediaUnlockTargets.callersFor(targets, app, MediaUnlockSettings::enabledXposed)
            callers.forEach { method ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { HookLog.w(tag, "Eligibility caller deoptimization unavailable: ${method.name}", it) }
            }
            HotReloadLifecycle.register(stateKey, capture = { targets })
            HookLog.i(tag, "Independent media gates installed; active callers=${callers.size}")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported media configuration; stock behavior retained", it)
        }
    }
}
