package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object SettingsHomeEntrySettings {
    const val LEGACY_KEY = "settings_home_onextbox_entry"
    const val POSITION_KEY = "settings_home_entry_position"
    const val ICON_STYLE_KEY = "settings_home_entry_icon_style"
    val keys = setOf(LEGACY_KEY, POSITION_KEY, ICON_STYLE_KEY)

    fun options(context: Context): SettingsHomeEntryOptions {
        val values = LspPreferenceStore.prefs(context).all
        return SettingsHomeEntryOptions.resolve(values[POSITION_KEY] as? Int, values[LEGACY_KEY] == true, values[ICON_STYLE_KEY] as? Int)
    }

    fun setPosition(context: Context, position: SettingsEntryPosition) {
        ModernXposedPreferenceSync.setIntAndSync(context, POSITION_KEY, position.value)
    }

    fun setIconStyle(context: Context, style: SettingsEntryIconStyle) {
        ModernXposedPreferenceSync.setIntAndSync(context, ICON_STYLE_KEY, style.value)
    }

    fun optionsXposed(): SettingsHomeEntryOptions = SettingsHomeEntryOptions.resolve(
        HookConfigSnapshot.int(POSITION_KEY, Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE },
        HookConfigSnapshot.boolean(LEGACY_KEY, false) == true,
        HookConfigSnapshot.int(ICON_STYLE_KEY, SettingsEntryIconStyle.System.value),
    )
}
