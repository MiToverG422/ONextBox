package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

/** Signature-checked Lens eligibility; never rewrite Google's persisted feature flags. */
internal object GoogleLensAiHooker {
    private const val TAG = "ONextBox-LensAI"

    fun hookGoogleApp(loader: ClassLoader) {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
        val handles = mutableListOf<XposedInterface.HookHandle>()
        runCatching {
            val create = target(loader, "ctwn").getDeclaredMethod("c")
            val talk = target(loader, "dsky").getDeclaredMethod("f")
            listOf(create, talk).forEach { method ->
                check(method.returnType == Boolean::class.javaPrimitiveType && !Modifier.isStatic(method.modifiers)) {
                    "Incompatible Lens eligibility signature: ${method.declaringClass.name}#${method.name}"
                }
            }
            // Preflight all inspected call sites before changing either eligibility gate.
            val callers = listOf(
                "dvwm" to setOf("K", "V"), "dsky" to setOf("c", "e"),
                "dupk" to setOf("af"), "dwcj" to setOf("af"),
                "dwax" to setOf("accept"), "dwbp" to setOf("a"), "dwbs" to setOf("a"),
            ).flatMap { (name, methods) ->
                target(loader, name).declaredMethods.filter {
                    it.name in methods && !Modifier.isAbstract(it.modifiers) && !Modifier.isNative(it.modifiers)
                }.also { found -> check(methods.all { expected -> found.any { it.name == expected } }) }
            }
            listOf(create, talk).forEach { method ->
                handles += ModernHookBridge.hookMethodFast(method) { chain ->
                    if (LspConfig.isAssistantNativeCircleEnabledXposed()) true else chain.proceed()
                }
            }
            callers.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            HookLog.i(TAG, "Lens Talk/Create eligibility enabled after compatibility checks")
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            HookLog.w(TAG, "Skipped incompatible Lens implementation; eligibility hooks removed", error)
        }
    }

    private fun target(loader: ClassLoader, name: String): Class<*> = Class.forName(name, false, loader)
}
