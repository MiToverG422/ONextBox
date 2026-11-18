package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernRemotePreferences

/** Shared fallback order for legacy callers; hot Hook callbacks normally use the snapshot. */
internal object LspXposedReads {
    val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) { ModernRemotePreferences("lsp_features") }

    fun boolean(persistPropertyKey: String, propertyKey: String, settingsKey: String,
        prefsKey: String, defaultValue: Boolean): Boolean {
        HookConfigSnapshot.boolean(prefsKey, defaultValue)?.let { return it }
        LspPreferenceStore.readSystemPropertyToggle(persistPropertyKey)?.let { return it }
        LspPreferenceStore.readSystemPropertyToggle(propertyKey)?.let { return it }
        LspPreferenceStore.readSettingsGlobalToggle(settingsKey)?.let { return it }
        return runCatching { preferences.getBoolean(prefsKey, defaultValue) }.getOrDefault(defaultValue)
    }

    fun string(key: String, defaultValue: String): String? {
        HookConfigSnapshot.string(key, defaultValue)?.let { return it }
        return runCatching { preferences.getString(key, defaultValue) }.getOrDefault(defaultValue)
    }

    fun stringSet(key: String): Set<String> {
        HookConfigSnapshot.stringSet(key, emptySet())?.let { return it }
        return runCatching { preferences.getStringSet(key, emptySet()).orEmpty() }.getOrDefault(emptySet())
    }
}
