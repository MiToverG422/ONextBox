package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object CouiDividerSettings {
    const val ENABLED = "system_apps_hide_coui_dividers"

    val recommendedPackages = setOf("com.android.settings")

    fun enabled(context: Context): Boolean = LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)
    fun enabledXposed(): Boolean = HookConfigSnapshot.boolean(ENABLED, false) == true
    fun setEnabled(context: Context, enabled: Boolean) =
        ModernXposedPreferenceSync.setBooleanAndSync(context, ENABLED, enabled)
}
