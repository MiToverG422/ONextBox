package com.mi.onextbox.lsp

import android.content.Context

/** The same keys/defaults drive page settings, legacy mirrors, quick edits and reset. */
internal object KeyboardFeatureSettings {
    enum class Option(val key: String) {
        Material("keyboard_material_unlock"), FlagEmoji("keyboard_flag_emoji"),
        BottomLift("keyboard_bottom_lift_fix"), HideDismiss("keyboard_hide_dismiss"),
    }

    fun enabled(context: Context, option: Option): Boolean = LspPreferenceStore.readBoolean(context, option.key, false)
    fun enabledXposed(option: Option): Boolean = LspXposedReads.boolean(
        "persist.sys.oost.${option.key}", "oost.${option.key}", "oost_${option.key}", option.key, false,
    )
    fun set(context: Context, option: Option, enabled: Boolean) = LspConfig.setSyncedBooleanPreference(
        context, option.key, enabled, listOf("persist.sys.oost.${option.key}", "oost.${option.key}"), "oost_${option.key}",
    )
    fun reset(context: Context) = Option.entries.forEach { set(context, it, false) }
}
