package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object MediaUnlockSettings {
    private val readEnabled: (String) -> Boolean = ::enabledXposed
    fun enabled(context: Context, key: String): Boolean =
        key in MediaUnlockCatalog.keys && (key !in CameraPaletteSupport.options || CameraPaletteSupport.available()) &&
            LspPreferenceStore.prefs(context).let { prefs ->
                if (key == MediaWatermarkSettings.ENABLED) MediaWatermarkSettings.enabled(prefs) else prefs.getBoolean(key, false)
            }

    fun state(context: Context): Map<String, Boolean> {
        val prefs = LspPreferenceStore.prefs(context)
        return MediaUnlockCatalog.keys.associateWith { key ->
            (if (key == MediaWatermarkSettings.ENABLED) MediaWatermarkSettings.enabled(prefs) else prefs.getBoolean(key, false)) &&
                (key !in CameraPaletteSupport.options || CameraPaletteSupport.available()) }
    }

    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in MediaUnlockCatalog.keys)
        require(!enabled || key !in CameraPaletteSupport.options || CameraPaletteSupport.available()) { "Camera palette resources are unavailable" }
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
    }

    fun enabledXposed(key: String): Boolean = if (key == MediaWatermarkSettings.ENABLED) MediaWatermarkSettings.enabledXposed()
        else (key !in CameraPaletteSupport.options || CameraPaletteSupport.available()) && HookConfigSnapshot.boolean(key, false) == true
    fun active(app: MediaUnlockCatalog.App): Boolean =
        MediaUnlockCatalog.byApp.getValue(app).any { it.overrides.isNotEmpty() && enabledXposed(it.key) } ||
            (app == MediaUnlockCatalog.App.Camera && MediaUnlockCatalog.sharedStyleOptions.any { enabledXposed(it.key) })

    fun overrideValue(app: MediaUnlockCatalog.App, gate: String?): Boolean? =
        MediaUnlockCatalog.overrideValue(app, gate, readEnabled)
}
