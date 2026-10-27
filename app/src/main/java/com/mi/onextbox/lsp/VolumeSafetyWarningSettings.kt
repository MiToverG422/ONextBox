package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object VolumeSafetyWarningSettings {
    const val KEY = "systemui_block_hearing_safety_warning"

    fun enabled(context: Context): Boolean =
        LspPreferenceStore.prefs(context).getBoolean(KEY, false)

    fun enabledXposed(): Boolean = HookConfigSnapshot.boolean(KEY, false) == true

    fun set(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEY, enabled).commit())
        LspConfig.syncReadableState(context)
    }
}
