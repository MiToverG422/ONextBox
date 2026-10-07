package com.mi.onextbox.lsp.compat

import android.content.SharedPreferences

internal const val MODERN_PREFERENCES_READY_KEY = "__onextbox_api102_snapshot_ready"

/** Immutable preference snapshot, replaced atomically outside Hook callbacks. */
internal object HookConfigSnapshot {
    @Volatile
    private var values: Map<String, Any?>? = null

    @Volatile
    private var attachedPreferences: SharedPreferences? = null

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, _ ->
        refresh(preferences)
    }

    val isAvailable: Boolean
        get() = values != null

    fun attach(preferences: SharedPreferences) {
        if (attachedPreferences === preferences) {
            refresh(preferences)
            return
        }
        runCatching {
            attachedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
        }
        attachedPreferences = preferences
        refresh(preferences)
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    fun boolean(key: String, defaultValue: Boolean): Boolean? =
        values?.let { snapshot -> snapshot[key] as? Boolean ?: defaultValue }

    fun int(key: String, defaultValue: Int): Int? =
        values?.let { snapshot -> (snapshot[key] as? Number)?.toInt() ?: defaultValue }

    fun float(key: String, defaultValue: Float): Float? =
        values?.let { snapshot -> (snapshot[key] as? Number)?.toFloat() ?: defaultValue }

    fun string(key: String, defaultValue: String?): String? =
        values?.let { snapshot -> snapshot[key] as? String ?: defaultValue }

    fun stringSet(key: String, defaultValue: Set<String>): Set<String>? =
        values?.let { snapshot ->
            @Suppress("UNCHECKED_CAST")
            (snapshot[key] as? Set<String>) ?: defaultValue
        }

    private fun refresh(preferences: SharedPreferences) {
        val current = runCatching { preferences.all }.getOrNull() ?: return
        // Before the app-side service performs its first API 102 sync, retain the legacy mirrors
        // for upgrade compatibility. Once marked ready, an empty map is a valid all-default config.
        values = if (current[MODERN_PREFERENCES_READY_KEY] == true) {
            current
                .filterKeys { key -> key != MODERN_PREFERENCES_READY_KEY }
                .mapValues { (_, value) ->
                    if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
                }
        } else {
            null
        }
    }
}
