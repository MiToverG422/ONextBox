package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Preserve the inspected CN launcher touch/animation/dispatch path when its handle is hidden. */
internal object HiddenNavCircleHooker {
    private const val TAG = "ONextBox-HiddenNavCircle"

    fun hookLauncher(loader: ClassLoader): Boolean {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return false
        val handles = mutableListOf<XposedInterface.HookHandle>()
        return runCatching {
            val features = target(loader, "com.android.common.config.FeatureOption")
            val controller = target(loader, "com.android.launcher3.circlesearch.OplusHideNavHandleController")
            val instance = target(loader, controller.name + "\$INSTANCE")
            val consumer = target(loader, "com.android.quickstep.inputconsumers.OplusCuiInputConsumer")
            val companion = target(loader, consumer.name + "\$Companion")
            val listener = target(loader, "com.android.quickstep.inputconsumers.a")
            val service = target(loader, "com.android.quickstep.OplusBaseTouchInteractionService")
            val navigation = HiddenNavCircleGate.bind(target(loader, "com.oplus.quickstep.navigation.a"))
            val hiddenSupport = staticBoolean(features, "isSupportCircleToSearchNavbarHidden")
            val handleSupport = staticBoolean(instance, "isSupportCircleToSearchHandle")
            // Preflight the native path before changing either gate. Do not replace its callbacks.
            controller.getDeclaredMethod("init")
            controller.getDeclaredMethod("startPressAnimation")
            controller.getDeclaredMethod("endPressAnimation", Boolean::class.javaPrimitiveType)
            listener.getDeclaredMethod("onLongPress", android.view.MotionEvent::class.java)
            companion.getDeclaredMethod("e", Int::class.javaPrimitiveType, android.content.Context::class.java)
            handles += ModernHookBridge.hookMethodFast(hiddenSupport) { chain ->
                if (LspConfig.isAssistantNativeCircleEnabledXposed()) true else chain.proceed()
            }
            handles += ModernHookBridge.hookMethodFast(handleSupport) { chain ->
                if (!LspConfig.isAssistantNativeCircleEnabledXposed()) chain.proceed()
                else runCatching { navigation.eligible() }.getOrDefault(false)
            }
            // Remove existing ART inlining only in the inspected launcher gesture path.
            listOf(controller, instance, consumer, companion, listener, service).forEach(::deoptimize)
            HookLog.i(TAG, "Hidden-handle native route enabled; gesture/hidden/Circle observers remain authoritative")
            true
        }.getOrElse { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            HookLog.w(TAG, "Skipped incompatible hidden-handle route; existing launcher route retained", error)
            false
        }
    }

    fun hookSettings(loader: ClassLoader) {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
        val handles = mutableListOf<XposedInterface.HookHandle>()
        runCatching {
            val feature = target(loader, "com.oplus.settings.utils.CustomizeFeatureUtils")
            val fragment = target(loader, "com.oplus.settings.feature.navbar.NavigationBarSettingsFragment")
            val support = staticBoolean(feature, "isSupportCircleToSearchNavbarHidden")
            handles += ModernHookBridge.hookMethodFast(support) { chain ->
                if (LspConfig.isAssistantNativeCircleEnabledXposed()) true else chain.proceed()
            }
            fragment.declaredMethods.filter { it.name.startsWith("needShow") || it.name.startsWith("lambda\$") }
                .forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            // The native Settings dialogs now skip their mutually exclusive reset branches.
            // User changes to circle_to_search_enable_navi are not intercepted.
            HookLog.i(TAG, "Settings supports hidden-handle Circle to Search without resetting the user toggle")
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            HookLog.w(TAG, "Skipped incompatible hidden-handle Settings support", error)
        }
    }

    private fun target(loader: ClassLoader, name: String): Class<*> = Class.forName(name, false, loader)

    private fun staticBoolean(type: Class<*>, name: String): Method = type.getDeclaredMethod(name).also {
        check(it.returnType == Boolean::class.javaPrimitiveType && Modifier.isStatic(it.modifiers)) {
            "Incompatible ${type.name}#$name"
        }
    }

    private fun deoptimize(type: Class<*>) {
        type.declaredMethods.filterNot { Modifier.isNative(it.modifiers) || Modifier.isAbstract(it.modifiers) }
            .forEach { ModernHookRuntime.requireModule().deoptimize(it) }
    }
}
