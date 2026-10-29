package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object SmartSidebarSettings {
    const val PACKAGE_NAME = "com.coloros.smartsidebar"
    const val ALLOW_APPS = "smart_sidebar_allow_apps"
    const val ALLOW_IN_APPS = "smart_sidebar_allow_in_apps"
    val keys = linkedSetOf(ALLOW_APPS, ALLOW_IN_APPS)

    fun enabled(context: Context, key: String) = LspPreferenceStore.prefs(context).getBoolean(key, false)
    fun state(context: Context): Map<String, Boolean> {
        val values = LspPreferenceStore.prefs(context).all
        return keys.associateWith { values[it] == true }
    }
    fun active(key: String) = HookConfigSnapshot.boolean(key, false) == true
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in keys)
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }
}
