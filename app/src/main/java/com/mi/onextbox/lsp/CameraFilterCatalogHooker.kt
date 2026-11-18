package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal object CameraFilterCatalogHooker {
    fun target(loader: ClassLoader): Method = loader.loadClass(CameraFilterCatalogRules.SDK)
        .getDeclaredMethod("getAllFilters", Context::class.java).also {
            check(Modifier.isStatic(it.modifiers) && it.returnType == HashMap::class.java)
        }

    fun hook(loader: ClassLoader) {
        if (CameraFilterCatalogRules.idsByKey.keys.none(MediaUnlockSettings::enabledXposed)) return
        val tag = "ONextBox-CameraFilters"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val method = target(loader)
            val reached = AtomicBoolean()
            val inspected = AtomicBoolean()
            ModernHookRegistry.installFast("${prefix}catalog", method, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val map = original as? HashMap<*, *> ?: return@Hooker original
                if (inspected.compareAndSet(false, true)) {
                    val wanted = CameraFilterCatalogRules.idsByKey.filterKeys(MediaUnlockSettings::enabledXposed).values.flatten().toSet()
                    val supplied = map.values.filterIsInstance<List<*>>().flatten().filterIsInstance<Map<*, *>>()
                        .mapNotNull { it[CameraFilterCatalogRules.TYPE] as? String }.toSet()
                    HookLog.i(tag, "Native SDK catalog reached; modes=${map.size}; selected LUTs=${wanted.count { it in supplied }}/${wanted.size}")
                }
                val result = CameraFilterCatalogRules.merge(map, MediaUnlockSettings::enabledXposed)
                if (result !== original && reached.compareAndSet(false, true))
                    HookLog.i(tag, "Native IPU LUT rows imported into rear-photo catalog: " + result.entries
                        .filter { (it.key as? String)?.startsWith("photo-") == true }
                        .joinToString { "${it.key}=${(it.value as? List<*>)?.size}" })
                result
            })
            HotReloadLifecycle.register("${prefix}target", capture = { method })
            HookLog.i(tag, "Native IPU filter catalog hook installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported native filter catalog; stock filters retained", it)
        }
    }
}
