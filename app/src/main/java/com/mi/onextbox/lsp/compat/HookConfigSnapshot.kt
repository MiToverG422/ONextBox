package com.mi.onextbox.lsp.compat

import android.content.SharedPreferences
import com.mi.onextbox.lsp.SystemAdaptationDetector
import com.mi.onextbox.lsp.SystemAdaptationVersion

internal const val MODERN_PREFERENCES_READY_KEY = "__onextbox_api102_snapshot_ready"
internal const val MODERN_PREFERENCES_REVISION_KEY = "__onextbox_api102_snapshot_revision"

/** Immutable preference snapshot, replaced atomically outside Hook callbacks. */
internal object HookConfigSnapshot : HookPreferenceSnapshot(SystemAdaptationDetector::current)

internal open class HookPreferenceSnapshot(private val systemVersion: () -> SystemAdaptationVersion) {
    @Volatile
    private var values: Map<String, Any?>? = null

    @Volatile
    private var attachedPreferences: SharedPreferences? = null
    private var loadedRevision: Long? = null

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        refreshIfAttached(preferences, key)
    }

    val isAvailable: Boolean
        get() = values != null

    /** Replaced together with the immutable settings map, including when availability changes. */
    val identity: Any? get() = values

    val isListening: Boolean
        get() = attachedPreferences != null

    fun controlBoolean(key: String, defaultValue: Boolean): Boolean = values?.get(key) as? Boolean ?: defaultValue

    fun contains(key: String): Boolean = values?.containsKey(key) == true

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
    private fun refreshIfAttached(preferences: SharedPreferences, key: String?) {
        // A queued callback from an old store must not overwrite the new generation's snapshot.
        if (attachedPreferences !== preferences) return
        // Every publication updates its revision in the same atomic commit as its values. The
        // first callback reads the complete new map; the remaining per-key callbacks are free.
        val revision = runCatching {
            if (preferences.contains(MODERN_PREFERENCES_REVISION_KEY)) preferences.getLong(MODERN_PREFERENCES_REVISION_KEY, 0) else null
        }.getOrNull()
        if (key != null && key != MODERN_PREFERENCES_READY_KEY && revision != null && revision == loadedRevision) return
        refresh(preferences)
    }

    fun boolean(key: String, defaultValue: Boolean): Boolean? =
        values?.let { snapshot ->
            if (!AdaptationRuntimePolicy.allowsFeatureBoolean(adaptationVersion(), key)) false
            else snapshot[key] as? Boolean ?: defaultValue
        }

    fun adaptationVersion() = systemVersion()

    fun int(key: String, defaultValue: Int): Int? =
        values?.let { snapshot -> if (!allowsValue(key)) defaultValue else (snapshot[key] as? Number)?.toInt() ?: defaultValue }

    fun float(key: String, defaultValue: Float): Float? =
        values?.let { snapshot -> if (!allowsValue(key)) defaultValue else (snapshot[key] as? Number)?.toFloat() ?: defaultValue }

    fun long(key: String, defaultValue: Long? = null): Long? =
        values?.let { snapshot -> if (!allowsValue(key)) defaultValue else (snapshot[key] as? Number)?.toLong() ?: defaultValue }

    fun string(key: String, defaultValue: String?): String? =
        values?.let { snapshot -> if (!allowsValue(key)) defaultValue else snapshot[key] as? String ?: defaultValue }

    fun stringSet(key: String, defaultValue: Set<String>): Set<String>? =
        values?.let { snapshot ->
            @Suppress("UNCHECKED_CAST")
            if (!allowsValue(key)) defaultValue else (snapshot[key] as? Set<String>) ?: defaultValue
        }

    private fun allowsValue(key: String) = AdaptationRuntimePolicy.allowsFeatureValue(adaptationVersion(), key)

    private fun refresh(preferences: SharedPreferences) {
        val current = runCatching { preferences.all }.getOrNull() ?: return
        loadedRevision = (current[MODERN_PREFERENCES_REVISION_KEY] as? Number)?.toLong()
        val previousValues = values
        // Before the app-side service performs its first API 102 sync, retain the legacy mirrors
        // for upgrade compatibility. Once marked ready, an empty map is a valid all-default config.
        values = if (current[MODERN_PREFERENCES_READY_KEY] == true) {
            current
                .filterKeys { key -> key != MODERN_PREFERENCES_READY_KEY && key != MODERN_PREFERENCES_REVISION_KEY }
                .mapValues { (_, value) ->
                    if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
                }
        } else {
            null
        }
        if (values != null && values != previousValues) runCatching {
            com.mi.onextbox.diagnostics.RuntimeDiagnostics.config(values!!)
        }
    }
}
