package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.diagnostics.RuntimeDiagnostics
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect

internal object HideNotificationClearSettings {
    const val KEY = "systemui_hide_notification_clear"
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(KEY, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(KEY, false) == true
    fun set(context: Context, value: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEY, value).commit())
        LspConfig.syncReadableState(context)
    }
}

internal object HideNotificationClearHooker {
    private const val CONTROLLER = "com.oplus.systemui.notification.clearall.ClearAllController"
    private const val BUTTON = "com.oplus.systemui.notification.clearall.OplusClearAllButton"

    fun hook(loader: ClassLoader) = SystemUiHookActivation.install(
        "notification-clear:hidden", setOf(HideNotificationClearSettings.KEY), HideNotificationClearSettings::enabledXposed,
        legacyTarget = { target -> target.declaringClass.name == CONTROLLER && target.name == "setVisible" &&
            target.parameterCount == 3 && target.parameterTypes[0].name == BUTTON },
    ) {
        RuntimeDiagnostics.measure("HideNotificationClearHooker.hook") { install(loader) }
    }

    private fun install(loader: ClassLoader) {
        runCatching {
            val type = ModernReflect.findClass(CONTROLLER, loader)
            val button = ModernReflect.findClass(BUTTON, loader)
            val target = type.getDeclaredMethod("setVisible", button, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            require(target.returnType == Void.TYPE)
            ModernHookRegistry.installCompat("ONextBox-HideNotificationClear:visibility", target, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (HideNotificationClearSettings.enabledXposed()) {
                        // Keep native target visibility, accessibility and animation cleanup in sync.
                        // Do not block notification dismissal or modify the notification collection.
                        param.args[1] = false
                        param.args[2] = false
                    }
                }
            })
            type.declaredMethods.forEach { method ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { HookLog.w("ONextBox-HideNotificationClear", "Deoptimization unavailable: ${method.name}", it) }
            }
        }.onFailure { HookLog.w("ONextBox-HideNotificationClear", "Native visibility hook unavailable; stock button retained", it) }
    }
}
