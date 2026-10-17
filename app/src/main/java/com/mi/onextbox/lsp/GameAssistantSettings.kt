package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object GameAssistantSettings {
    const val PACKAGE_NAME = GameAssistantRules.PACKAGE_NAME
    const val PROMOTIONS = "game_assistant_hide_promotions"
    const val REALME_STARTUP_ANIMATION = "game_assistant_block_realme_startup_animation"
    const val UNLIMITED = "game_assistant_unlimited_sidebar"
    const val BLOCK_ROOT = "game_assistant_block_root"
    const val DEVELOPER = "game_assistant_developer_mode"
    const val SKINS = "game_assistant_all_skins"
    const val SKIN = "game_assistant_skin"
    const val MUSIC = "game_assistant_unlock_music_whitelist"
    val toggles = setOf(PROMOTIONS, REALME_STARTUP_ANIMATION, UNLIMITED, BLOCK_ROOT, DEVELOPER, SKINS, MUSIC)
    val keys = toggles + SKIN

    fun enabled(context: Context, key: String): Boolean {
        require(key in toggles)
        return LspPreferenceStore.prefs(context).getBoolean(key, false)
    }
    fun enabledXposed(key: String): Boolean = key in toggles && HookConfigSnapshot.boolean(key, false) == true
    fun skin(context: Context): String = GameAssistantRules.skin(LspPreferenceStore.prefs(context).getString(SKIN, null))
    fun skinXposed(): String = GameAssistantRules.skin(HookConfigSnapshot.string(SKIN, GameAssistantRules.DEFAULT_SKIN))
    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in toggles)
        check(LspPreferenceStore.prefs(context).edit().putBoolean(key, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun setSkin(context: Context, skin: String) {
        require(skin in GameAssistantRules.skins)
        check(LspPreferenceStore.prefs(context).edit().putString(SKIN, skin).commit())
        LspConfig.syncReadableState(context)
    }
}
