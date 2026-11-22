package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object ThemeStoreSettings {
    const val PACKAGE_NAME = "com.heytap.themestore"
    const val ENABLED = "theme_store_vip_enabled"
    const val ADS = "theme_store_hide_ads"
    const val NIGHT_RESOURCES = "theme_store_night_resources"
    fun nightResources(context: Context) = LspPreferenceStore.prefs(context).getBoolean(NIGHT_RESOURCES, false)
    fun setNightResources(context: Context, enabled: Boolean) =
        ModernXposedPreferenceSync.setBooleanAndSync(context, NIGHT_RESOURCES, enabled)
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(ENABLED, false) == true
    fun set(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun adsEnabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(ADS, false)
    fun adsEnabledXposed() = HookConfigSnapshot.boolean(ADS, false) == true
    fun setAds(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ADS, enabled).commit())
        LspConfig.syncReadableState(context)
    }
}
