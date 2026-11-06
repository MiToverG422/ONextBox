package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object FluidCloudRecentsSettings {
    const val ENABLED = "fluid_cloud_keep_in_recents"

    fun isEnabled(context: Context): Boolean =
        LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        ModernXposedPreferenceSync.setBooleanAndSync(context, ENABLED, enabled)
    }

    fun isEnabledXposed(): Boolean = HookConfigSnapshot.boolean(ENABLED, false) == true
}
