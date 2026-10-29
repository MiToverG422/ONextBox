package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object KeyboardMaterialHooker {
    private const val TAG = "ONextBox-Keyboard"

    fun hook(loader: ClassLoader, apkPath: String) {
        val stateKey = "$TAG:targets@${System.identityHashCode(loader)}"
        val saved = DexTargetCache.restoredState(stateKey) as? Array<*>
        if (!LspConfig.isKeyboardMaterialUnlockedXposed() && saved == null) return
        val hookKey = "$TAG@${System.identityHashCode(loader)}:material-allowlist"
        runCatching {
            val targets = saved ?: resolve(loader, apkPath)
            val field = targets[0] as Field
            val allowlist = field.apply { isAccessible = true }.get(null) as ArrayList<*>
            check(KeyboardMaterialRules.isMaterialAllowlist(allowlist)) { "Material allowlist changed" }
            targets.drop(1).forEach { ModernHookRuntime.requireModule().deoptimize(it as Method) }
            ModernHookRegistry.installFast(
                hookKey, ArrayList::class.java.getDeclaredMethod("contains", Any::class.java),
                XposedInterface.Hooker { chain ->
                    if (chain.thisObject === allowlist && KeyboardMaterialRules.shouldAllow(
                            LspConfig.isKeyboardMaterialUnlockedXposed(), true, chain.getArg(0),
                        )) true else chain.proceed()
                },
            )
            HotReloadLifecycle.register(stateKey, capture = { targets })
            HookLog.i(TAG, "Native material allowlist hook installed")
        }.onFailure {
            ModernHookRegistry.unhook(hookKey)
            HookLog.w(TAG, "Native material hook unavailable, stock behavior retained", it)
        }
    }

    private fun resolve(loader: ClassLoader, apkPath: String): Array<Any> {
        System.loadLibrary("dexkit")
        return DexKitBridge.create(apkPath).use { dex ->
            val apply = dex.findMethod {
                matcher {
                    usingStrings("applyBlurEnable pkg=")
                    returnType = "void"
                    paramTypes("android.view.inputmethod.EditorInfo", "boolean")
                }
            }.single()
            val field = apply.usingFields.map { it.field }.distinctBy { it.descriptor }.single { data ->
                data.typeName == "java.util.ArrayList" && Modifier.isStatic(data.modifiers) &&
                    Modifier.isFinal(data.modifiers) && data.writers.any {
                        it.isStaticInitializer && it.usingStrings.containsAll(KeyboardMaterialRules.stockPackages)
                    }
            }
            val readers = field.readers.filter { it.isMethod }
            check(readers.any { it.descriptor == apply.descriptor })
            val methods = (readers + readers.flatMap { it.callers } +
                readers.flatMap { it.callers }.flatMap { it.callers })
                .filter { it.isMethod }.distinctBy { it.descriptor }.map { it.getMethodInstance(loader) }
            arrayOf(field.getFieldInstance(loader), *methods.toTypedArray())
        }
    }
}
