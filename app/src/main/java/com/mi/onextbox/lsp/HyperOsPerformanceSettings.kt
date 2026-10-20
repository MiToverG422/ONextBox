package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object HyperOsPerformanceSettings {
    const val REFRESH = "hyperos3_global_185hz"
    const val TOUCH = "hyperos3_high_touch_rate"
    const val IDLE = "hyperos3_disable_idle_refresh"
    val packages = listOf("system", "com.xiaomi.touchservice", "com.miui.securitycenter")

    fun enabled(context: Context, key: String): Boolean =
        LspPreferenceStore.prefs(context).all[key] as? Boolean ?: false

    fun set(context: Context, key: String, enabled: Boolean) {
        require(key == REFRESH || key == TOUCH || key == IDLE)
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }

    fun enabledXposed(key: String): Boolean =
        SystemAdaptationSettings.selectedVersionXposed() == SystemAdaptationVersion.HyperOs3 &&
            HookConfigSnapshot.boolean(key, false) == true
}
