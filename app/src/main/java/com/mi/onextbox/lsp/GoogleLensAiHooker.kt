package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

/** Version-specific Lens eligibility; never rewrite Google's persisted feature flags. */
internal object GoogleLensAiHooker {
    private const val TAG = "ONextBox-LensAI"
    private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val VERIFIED_VERSION = 301818946L // 17.65.17.ve.arm64

    fun hookGoogleApp(loader: ClassLoader) {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
        val handles = mutableListOf<XposedInterface.HookHandle>()
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val thread = activityThread.getMethod("currentActivityThread").invoke(null)
            val context = activityThread.getMethod("getSystemContext").invoke(thread) as Context
            val version = context.packageManager.getPackageInfo(GOOGLE_PACKAGE, 0).longVersionCode
            if (version != VERIFIED_VERSION) {
                HookLog.i(TAG, "Skipped unverified Google version: $version")
                return
            }
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
            HookLog.i(TAG, "Lens Talk/Create eligibility enabled for Google 17.65.17")
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            HookLog.w(TAG, "Skipped incompatible Lens implementation; eligibility hooks removed", error)
        }
    }

    private fun target(loader: ClassLoader, name: String): Class<*> = Class.forName(name, false, loader)
}
