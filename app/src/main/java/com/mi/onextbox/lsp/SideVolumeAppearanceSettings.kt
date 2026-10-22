package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object SideVolumeAppearanceSettings {
    const val KEEP_WIDE = "systemui_side_volume_keep_wide"
    val keys = setOf(KEEP_WIDE)
    data class State(val keepWide: Boolean = false)
    fun state(context: Context) = LspPreferenceStore.prefs(context).let {
        State(it.getBoolean(KEEP_WIDE, false))
    }
    fun stateXposed() = State(
        HookConfigSnapshot.boolean(KEEP_WIDE, false) == true)
    fun configurationTokenXposed(): Int? = stateXposed().takeIf { it.keepWide }?.hashCode()
    fun setKeepWide(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEEP_WIDE, enabled).commit())
        LspConfig.syncReadableState(context)
    }
}
