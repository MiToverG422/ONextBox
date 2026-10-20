package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object SettingsHomeCleanupSettings {
    const val ACCOUNT = "settings_home_hide_account"
    const val UPDATE = "settings_home_hide_update_reminder"
    val keys = setOf(ACCOUNT, UPDATE)

    fun enabled(context: Context, key: String): Boolean {
        require(key in keys)
        return LspPreferenceStore.prefs(context).getBoolean(key, false)
    }

    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in keys)
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }

    fun enabledXposed(key: String): Boolean = key in keys && HookConfigSnapshot.boolean(key, false) == true
}
