package com.mi.onextbox.lsp

import android.content.SharedPreferences

/** Keep a failed framework write from presenting unconfirmed lifecycle consent as saved. */
internal fun commitConfirmedBooleanPreference(
    local: SharedPreferences,
    remote: SharedPreferences,
    key: String,
    value: Boolean,
    prepareRemote: (SharedPreferences.Editor) -> Unit = {},
    isCurrent: () -> Boolean,
) {
    check(isCurrent()) { "Xposed service changed before saving" }
    val previous = if (local.contains(key)) local.getBoolean(key, false) else null
    try {
        check(local.edit().putBoolean(key, value).commit()) { "Local preference commit failed" }
        check(isCurrent()) { "Xposed service disconnected before syncing" }
        val publication = remote.edit().putBoolean(key, value)
        prepareRemote(publication)
        check(publication.commit()) { "Framework preference commit failed" }
        check(isCurrent() && remote.getBoolean(key, !value) == value) {
            "Framework did not confirm the preference"
        }
    } catch (error: Throwable) {
        fun restore(preferences: SharedPreferences) {
            val editor = preferences.edit()
            if (previous == null) editor.remove(key) else editor.putBoolean(key, previous)
            if (preferences === remote) prepareRemote(editor)
            check(editor.commit()) { "Could not restore the previous preference" }
        }
        runCatching { restore(local) }.onFailure(error::addSuppressed)
        // The normal mirror queue also retries from the restored local value on reconnection.
        if (isCurrent()) runCatching { restore(remote) }.onFailure(error::addSuppressed)
        throw error
    }
}
