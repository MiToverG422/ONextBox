package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object LargeNetworkTypeSettings {
    const val KEY = "systemui_large_network_type"
    const val FONT_KEY = "systemui_large_network_type_system_font"
    const val BOLD_KEY = "systemui_large_network_type_bold"
    const val ARTWORK_KEY = "systemui_large_network_type_artwork"
    // New key: do not resurrect the old, ineffective LTE text preference.
    const val LTE_KEY = "systemui_large_network_type_artwork_lte"
    const val POSITION_KEY = "systemui_large_network_type_position"
    fun position(context: Context) = LargeNetworkPositionRules.normalize(
        LspPreferenceStore.prefs(context).getInt(POSITION_KEY, LargeNetworkPositionRules.RIGHT))
    fun positionXposed() = LargeNetworkPositionRules.normalize(
        HookConfigSnapshot.int(POSITION_KEY, LargeNetworkPositionRules.RIGHT) ?: LargeNetworkPositionRules.RIGHT)
    fun setPosition(context: Context, position: Int) {
        check(LspPreferenceStore.prefs(context).edit().putInt(POSITION_KEY,
            LargeNetworkPositionRules.normalize(position)).commit())
        LspConfig.syncReadableState(context)
    }
    fun usesArtwork(context: Context) = LspPreferenceStore.prefs(context).getBoolean(ARTWORK_KEY, false)
    fun usesArtworkXposed() = enabledXposed() && HookConfigSnapshot.boolean(ARTWORK_KEY, false) == true
    fun usesLte(context: Context) = LspPreferenceStore.prefs(context).getBoolean(LTE_KEY, false)
    fun usesLteXposed() = usesArtworkXposed() && HookConfigSnapshot.boolean(LTE_KEY, false) == true
    fun setArtwork(context: Context, enabled: Boolean) = save(context, ARTWORK_KEY, enabled)
    fun setLte(context: Context, enabled: Boolean) = save(context, LTE_KEY, enabled)
    private fun save(context: Context, key: String, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(key, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun usesBold(context: Context) = LspPreferenceStore.prefs(context).getBoolean(BOLD_KEY, false)
    fun usesBoldXposed() = HookConfigSnapshot.boolean(BOLD_KEY, false) == true
    fun setBold(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(BOLD_KEY, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun followsSystemFont(context: Context) = LspPreferenceStore.prefs(context).getBoolean(FONT_KEY, false)
    fun followsSystemFontXposed() = HookConfigSnapshot.boolean(FONT_KEY, false) == true
    fun setSystemFont(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(FONT_KEY, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(KEY, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(KEY, false) == true
    fun set(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEY, enabled).commit())
        LspConfig.syncReadableState(context)
    }
}
