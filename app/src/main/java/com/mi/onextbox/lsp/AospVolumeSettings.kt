package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object AospVolumeSettings {
    const val KEY = "systemui_aosp_volume"
    const val SCALE = "systemui_aosp_volume_scale"
    const val HIDE_IN_QS = "systemui_aosp_volume_hide_in_qs"
    fun scale(context: Context) = AospVolumeSize.percent(LspPreferenceStore.prefs(context).getInt(SCALE, 100))
    fun hideInQs(context: Context) = LspPreferenceStore.prefs(context).getBoolean(HIDE_IN_QS, false)
    fun scaleXposed() = if (enabledXposed()) AospVolumeSize.percent(HookConfigSnapshot.int(SCALE, 100) ?: 100) else 100
    fun hideInQsXposed() = enabledXposed() && HookConfigSnapshot.boolean(HIDE_IN_QS, false) == true
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(KEY, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(KEY, false) == true
    fun set(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEY, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun setScale(context: Context, percent: Int) {
        check(LspPreferenceStore.prefs(context).edit().putInt(SCALE, AospVolumeSize.percent(percent)).commit())
        LspConfig.syncReadableState(context)
    }
    fun setHideInQs(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(HIDE_IN_QS, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun reset(context: Context) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEY, false).remove(SCALE).remove(HIDE_IN_QS).commit())
        LspConfig.syncReadableState(context)
    }
}
