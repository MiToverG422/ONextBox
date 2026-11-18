package com.mi.onextbox.lsp

/** Null keys represent clear/import/recovery, which require a complete reconciliation. */
internal class PreferenceSyncBatch {
    data class Changes(val full: Boolean, val keys: Set<String>)
    private var full = false
    private val keys = linkedSetOf<String>()

    @Synchronized fun add(key: String?) {
        if (key == null) { full = true; keys.clear() }
        else if (!full) keys += key
    }

    @Synchronized fun drain(): Changes = Changes(full, keys.toSet()).also { full = false; keys.clear() }
    @Synchronized fun isPending(): Boolean = full || keys.isNotEmpty()
}

internal fun preferenceDelta(previous: Map<String, *>, desired: Map<String, *>, keys: Set<String>?): Map<String, Any?> =
    (keys ?: (previous.keys + desired.keys)).mapNotNull { key ->
        if (previous.containsKey(key) == desired.containsKey(key) && previous[key] == desired[key]) null
        else key to desired[key]
    }.toMap()

/** Defensive publication values, including the global disabled-feature policy. */
internal fun frameworkPreferenceValues(source: Map<String, *>, sideKeyAvailable: Boolean): Map<String, Any?> =
    source.filterKeys {
        it != com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY &&
            it != com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY
    }.mapValues { (_, value) -> if (value is Set<*>) frameworkStringSet(value) else value }.toMutableMap().apply {
        if (!sideKeyAvailable) put(SideKeySettings.ENABLED, false)
    }
