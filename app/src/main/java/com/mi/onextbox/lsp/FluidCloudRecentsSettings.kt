package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object FluidCloudRecentsSettings {
    const val ENABLED = "fluid_cloud_keep_in_recents"

    fun isEnabled(context: Context): Boolean =
        LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, enabled).commit())
        LspConfig.syncReadableState(context)
    }

    fun isEnabledXposed(): Boolean = HookConfigSnapshot.boolean(ENABLED, false) == true
}
