package com.mi.onextbox.lsp

import android.content.SharedPreferences

internal fun commitConfirmedStringPreference(local: SharedPreferences, remote: SharedPreferences, key: String, value: String, isCurrent: () -> Boolean) {
    check(isCurrent()) { "Xposed service changed before saving" }
    val previous = local.getString(key, null)
    try {
        check(local.edit().putString(key, value).commit()) { "Local preference commit failed" }
        check(isCurrent()) { "Xposed service disconnected before syncing" }
        check(remote.edit().putString(key, value).commit()) { "Framework preference commit failed" }
        check(isCurrent() && remote.getString(key, null) == value) { "Framework did not confirm the preference" }
    } catch (error: Throwable) {
        fun restore(preferences: SharedPreferences) {
            val editor = preferences.edit()
            if (previous == null) editor.remove(key) else editor.putString(key, previous)
            check(editor.commit()) { "Could not restore the previous preference" }
        }
        runCatching { restore(local) }.onFailure(error::addSuppressed)
        if (isCurrent()) runCatching { restore(remote) }.onFailure(error::addSuppressed)
        throw error
    }
}
