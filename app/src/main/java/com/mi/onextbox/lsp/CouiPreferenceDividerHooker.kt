package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object CouiPreferenceDividerHooker {
    fun hook(loader: ClassLoader, apkPath: String, prefix: String) {
        val stateKey = prefix + "preference-targets"
        val restored = (DexTargetCache.restoredState(stateKey) as? Array<*>)?.filterIsInstance<Method>()
        if (restored == null && (!CouiDividerSettings.enabledXposed() || apkPath.isBlank())) return
        runCatching {
            val methods = restored ?: ApkDexSession.withSession(apkPath) { open ->
                open().findMethod {
                    matcher {
                        name = "drawDivider"
                        returnType = "boolean"
                        paramCount = 0
                    }
                }.filter { CouiPreferenceDividerRules.isOwner(it.className) }
                    .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
            }
            val targets = methods.filter {
                CouiPreferenceDividerRules.matches(it.declaringClass.name, it.name, it.returnType.name,
                    it.parameterCount, Modifier.isStatic(it.modifiers), Modifier.isAbstract(it.modifiers))
            }.distinct()
            HotReloadLifecycle.register(stateKey, capture = { targets.toTypedArray() })
            targets.forEach { method ->
                runCatching {
                    val contextGetter = method.declaringClass.getMethod("getContext")
                    if (!Context::class.java.isAssignableFrom(contextGetter.returnType)) return@runCatching
                    ModernHookRegistry.installFast(prefix + method.toGenericString(), method,
                        XposedInterface.Hooker { chain ->
                            val context = if (CouiDividerSettings.enabledXposed()) {
                                runCatching { contextGetter.invoke(chain.thisObject) as? Context }.getOrNull()
                            } else null
                            if (context != null && CouiDividerHooker.isSystemContext(context)) false else chain.proceed()
                        })
                    method.declaringClass.declaredMethods.filter { !Modifier.isAbstract(it.modifiers) }.forEach {
                        runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    }
                }.onFailure { HookLog.w("ONextBox-CouiDividers", "Preference divider unavailable: ${method.declaringClass.name}", it) }
            }
        }.onFailure { HookLog.w("ONextBox-CouiDividers", "Preference divider discovery unavailable", it) }
    }
}

internal object CouiPreferenceDividerRules {
    private val namespaces = listOf(
        "com.coui.appcompat.preference.", "com.android.settings.",
        "com.oplus.settings.", "com.oplus.settingslib.",
    )

    fun isOwner(name: String): Boolean = namespaces.any(name::startsWith)

    fun matches(owner: String, name: String, returnType: String, parameters: Int,
        isStatic: Boolean, isAbstract: Boolean): Boolean =
        isOwner(owner) && name == "drawDivider" && returnType == "boolean" && parameters == 0 &&
            !isStatic && !isAbstract
}
