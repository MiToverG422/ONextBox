package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object Wallpaper3DForceSettings {
    const val ENABLED = "wallpaper_force_3d_enabled"
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(ENABLED, false) == true
    fun save(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, enabled).commit())
        LspConfig.syncReadableState(context)
    }
}
