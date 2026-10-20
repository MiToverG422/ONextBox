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
        refreshIfAttached(preferences)
    }

    val isAvailable: Boolean
        get() = values != null

    val isListening: Boolean
        get() = attachedPreferences != null

    /** True only when updates are subscribed; a readable snapshot alone is not reload-ready. */
    @Synchronized
    fun attach(preferences: SharedPreferences): Boolean {
        if (attachedPreferences === preferences) {
            refresh(preferences)
            return true
        }
        // Never lose track of a listener that could not be removed from the previous store.
        if (!detach()) return false
        refresh(preferences)
        if (runCatching {
            preferences.registerOnSharedPreferenceChangeListener(listener)
        }.isFailure) return false
        attachedPreferences = preferences
        // Catch changes between the initial read and listener registration, including callbacks
        // dispatched synchronously while the new store was not yet recorded as attached.
        refresh(preferences)
        return true
    }

    /** Keep the last values for in-flight hooks, but release the externally held old listener. */
    @Synchronized
    fun detach(): Boolean {
        val preferences = attachedPreferences ?: return true
        return runCatching {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            attachedPreferences = null
        }.isSuccess
    }

    @Synchronized
    private fun refreshIfAttached(preferences: SharedPreferences) {
        // A queued callback from an old store must not overwrite the new generation's snapshot.
        if (attachedPreferences === preferences) refresh(preferences)
    }

    fun boolean(key: String, defaultValue: Boolean): Boolean? =
        values?.let { snapshot ->
            if (!AdaptationRuntimePolicy.allowsKey(key, AdaptationRuntimePolicy.selected(snapshot))) false
            else snapshot[key] as? Boolean ?: defaultValue
        }

    fun adaptationVersion() = AdaptationRuntimePolicy.selected(values)

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
