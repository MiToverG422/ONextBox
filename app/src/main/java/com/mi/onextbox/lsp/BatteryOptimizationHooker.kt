package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mi.onextbox.lsp.LspConfig.BatteryFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object BatteryOptimizationHooker {
    private const val TAG = "ONextBox-Battery"

    fun hookBattery(loader: ClassLoader, apkPath: String) {
        // The plugin can initialize during Application.onCreate, so resolve before that point.
        // Scanning is unnecessary while disabled; loading a plugin already resident is not reversible.
        if (!LspConfig.isBatteryFeatureEnabledXposed(BatteryFeature.RemoveRestrictPlugin)) return
        val key = "$TAG:App@${System.identityHashCode(loader)}:"
        runCatching {
            System.loadLibrary("dexkit")
            val targets = DexKitBridge.create(apkPath).use { bridge ->
                val supporters = bridge.findClass {
                    matcher {
                        addFieldForType(Context::class.java)
                        addFieldForType(String::class.java)
                        addMethod { paramTypes(Int::class.java, Bundle::class.java) }
                        addMethod { paramTypes(Int::class.java, Intent::class.java) }
                        usingStrings("loadRestrictPlugin", "loadConfigPlugin", "onPluginConnected")
                    }
                }
                val data = if (supporters.isNotEmpty()) {
                    require(supporters.size == 1) { "Ambiguous PluginSupporter" }
                    supporters.findMethod { matcher { usingStrings("loadRestrictPlugin") } }
                } else {
                    // C17 embeds the restrict plugin. Its loader no longer exposes the config callbacks.
                    bridge.findMethod {
                        matcher { usingStrings("loadRestrictPlugin", "loadRestrictPlugin onPluginConnected") }
                    }
                }
                data.map { methodData ->
                    val clazz = ModernReflect.findClass(methodData.className, loader)
                    val method = clazz.getDeclaredMethod(methodData.methodName)
                    require(BatteryOptimizationRules.isRestrictLoader(
                        method.returnType.name, method.parameterTypes.map { it.name },
                        Modifier.isStatic(method.modifiers),
                    )) { "Unsupported restrict loader signature" }
                    if (supporters.isEmpty()) {
                        require(clazz.declaredFields.any { it.type == Context::class.java } &&
                            clazz.declaredFields.any { it.type.name == BatteryOptimizationRules.RESTRICT_APPLICATION }) {
                            "Not the built-in restrict plugin supporter"
                        }
                    }
                    method
                }.distinct()
            }
            require(targets.isNotEmpty() && targets.map { it.declaringClass }.distinct().size == 1) {
                "Restrict plugin loader missing or ambiguous"
            }
            targets.forEach { target ->
                ModernHookRegistry.installFast("$key${target.toGenericString()}", target, XposedInterface.Hooker { chain ->
                    if (LspConfig.isBatteryFeatureEnabledXposed(BatteryFeature.RemoveRestrictPlugin)) {
                        HookLog.d(TAG, "Restrict plugin initialization blocked")
                        null
                    } else chain.proceed()
                })
                // The small loader can be inlined into the public initialization entry.
                target.declaringClass.declaredMethods.filter { it != target }.forEach {
                    runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                }
            }
            HookLog.i(TAG, "Restrict plugin hooks installed: ${targets.joinToString { it.toGenericString() }}")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Restrict plugin hooks unavailable; stock behavior retained", it)
        }
    }

    fun hookSystemServer(loader: ClassLoader) {
        val key = "$TAG:System@${System.identityHashCode(loader)}:whitelist"
        runCatching {
            val helper = ModernReflect.findClass("com.android.server.OplusDeviceIdleHelper", loader)
            val target = listOf("getNewWhiteList", "getNewWhiteListLocked").firstNotNullOfOrNull { name ->
                runCatching { helper.getDeclaredMethod(name, ArrayList::class.java) }.getOrNull()
            } ?: error("Device idle whitelist builder missing")
            require(target.returnType == Void.TYPE)
            val defaults = helper.getDeclaredField("mDefaultWhitelist").apply { isAccessible = true }
            val customize = helper.getDeclaredMethod("getCustomizeWhiteList", ArrayList::class.java).accessible()
            val felica = helper.getDeclaredMethod("addNfcJapanFelica", ArrayList::class.java).accessible()
            ModernHookRegistry.installFast(key, target, XposedInterface.Hooker { chain ->
                if (!LspConfig.isBatteryFeatureEnabledXposed(BatteryFeature.RestoreDefaultWhitelist)) {
                    return@Hooker chain.proceed()
                }
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                @Suppress("UNCHECKED_CAST")
                val output = chain.getArg(0) as? ArrayList<String> ?: return@Hooker chain.proceed()
                val replacement = runCatching {
                    val defaultValues = defaults.get(if (Modifier.isStatic(defaults.modifiers)) null else owner) as? List<*>
                        ?: error("Default whitelist unavailable")
                    require(defaultValues.isNotEmpty() && defaultValues.all { it is String })
                    BatteryOptimizationRules.defaultWhitelist(
                        defaultValues.map { it as String },
                        addCustomize = { customize.invoke(owner, it) },
                        addFelica = { felica.invoke(owner, it) },
                    )
                }.getOrElse {
                    HookLog.w(TAG, "Default whitelist restore failed; stock list retained", it)
                    return@Hooker chain.proceed()
                }
                output.clear()
                output.addAll(replacement)
                HookLog.i(TAG, "Default battery optimization whitelist restored (${replacement.size} entries)")
                null
            })
            helper.declaredMethods.filter { it.name == "updateWhiteList" }.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    .onFailure { error -> HookLog.w(TAG, "Whitelist caller deoptimization failed", error) }
            }
            HookLog.i(TAG, "Default battery optimization whitelist hook installed")
        }.onFailure {
            ModernHookRegistry.unhook(key)
            HookLog.w(TAG, "Default whitelist hook unavailable; stock behavior retained", it)
        }
    }

    private fun Method.accessible(): Method = apply { isAccessible = true }
}
