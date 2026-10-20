package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object DisplayEnhancementThermalSettings {
    const val SUPER_RESOLUTION = "display_super_resolution_thermal_bypass"
    const val VIDEO_COLOR = "display_video_color_thermal_bypass"
    val keys = setOf(SUPER_RESOLUTION, VIDEO_COLOR)

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
