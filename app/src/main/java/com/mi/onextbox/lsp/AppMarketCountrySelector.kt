package com.mi.onextbox.lsp

import android.app.Activity
import android.os.Bundle
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

internal object AppMarketCountrySelector {
    private const val SDK = "com.heytap.mspsdk.regionv3"
    private const val TAG = "ONextBox-AppMarket"

    fun hook(loader: ClassLoader) {
        runCatching {
            val sdk = loader.loadClass("$SDK.impl.g")
            val recommendType = loader.loadClass("$SDK.b")
            val checkerCallback = loader.loadClass("$SDK.impl.g\$a")
            val runtime = loader.loadClass("$SDK.switcher.a")
            val resultListener = loader.loadClass("$SDK.a")
            val entry = sdk.declaredMethods.single { method ->
                Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(Activity::class.java, Bundle::class.java,
                        String::class.java, LinkedHashMap::class.java, String::class.java,
                        String::class.java, resultListener))
            }
            val clear = runtime.declaredMethods.single { method ->
                !Modifier.isStatic(method.modifiers) && method.parameterCount == 0 && method.returnType == Void.TYPE
            }
            val readList = recommendType.declaredMethods.single { method ->
                method.parameterCount == 0 && method.returnType == List::class.java
            }
            val onLocalCandidates = checkerCallback.declaredMethods.single { method ->
                method.parameterTypes.contentEquals(arrayOf(recommendType)) && method.returnType == Void.TYPE
            }
            val supported = AtomicReference(emptySet<String>())
            ModernHookBridge.hookMethod(entry, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val groups = param.args[3] as? Map<*, *>
                    supported.set(groups?.values.orEmpty().filterIsInstance<Map<*, *>>()
                        .flatMap { group -> group.keys.filterIsInstance<String>() }
                        .filter { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }.toSet())
                }
            })
            ModernHookBridge.hookMethod(clear, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    supported.set(emptySet())
                }
            })
            ModernHookBridge.hookMethod(onLocalCandidates, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!LspConfig.isAppMarketRegionRestrictionBypassEnabledXposed()) return
                    val catalogue = supported.get()
                    if (catalogue.isEmpty()) return
                    val candidates = readList.invoke(param.args[0]) as? ArrayList<*> ?: return
                    @Suppress("UNCHECKED_CAST")
                    (candidates as ArrayList<String>).addAll(catalogue - candidates.toSet())
                }
            })
            HookLog.i(TAG, "Installed ColorOS 16 country selector catalogue support")
        }.onFailure { HookLog.w(TAG, "ColorOS 16 country selector unavailable", it) }
    }
}
