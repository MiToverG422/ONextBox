package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object NativeFiveGaSettings {
    const val ENABLED = "mobile_network_native_5ga"
    val restartPackages = listOf("com.android.phone", "com.android.systemui")

    fun enabled(context: Context): Boolean = LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)

    fun getEnabled(): Boolean = HookConfigSnapshot.boolean(ENABLED, false) == true

    fun setEnabled(context: Context, enabled: Boolean) {
        ModernXposedPreferenceSync.setBooleanAndSync(context, ENABLED, enabled)
    }
}
