package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect

internal object SettingsProcessManager {
    const val KEY = "settings_show_process_manager"
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(KEY, false)
    fun set(context: Context, enabled: Boolean) = ModernXposedPreferenceSync.setBooleanAndSync(context, KEY, enabled)
    fun availability(original: Int, enabled: Boolean, applicationPage: Boolean): Int =
        if (!enabled) original else if (applicationPage) 0 else 3

    fun hook(loader: ClassLoader) {
        val targets = listOf(
            "com.oplus.settings.feature.appmanager.controller.RunningApplicationsNewPreferenceController" to true,
            "com.oplus.settings.feature.spfunction.RunningApplicationsPreferenceController" to false,
        )
        // Resolve the native page and both placement controllers before changing visibility.
        runCatching {
            ModernReflect.findClass("com.oplus.settings.feature.process.RunningApplicationActivity", loader)
            val methods = targets.map { (name, appPage) ->
                ModernReflect.findClass(name, loader).getDeclaredMethod("getAvailabilityStatus") to appPage
            }
            methods.forEach { (method, appPage) ->
                check(method.returnType == Int::class.javaPrimitiveType)
                ModernHookRegistry.installCompat("ONextBox-ProcessManager:$appPage", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || HookConfigSnapshot.boolean(KEY, false) != true) return
                        val original = param.result as? Int ?: return
                        param.result = availability(original, true, appPage)
                    }
                })
            }
        }.onFailure { HookLog.w("ONextBox-ProcessManager", "Native process manager entry unavailable", it) }
    }
}
