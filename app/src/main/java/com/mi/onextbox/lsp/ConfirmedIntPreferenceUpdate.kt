package com.mi.onextbox.lsp

import android.content.SharedPreferences

internal fun commitConfirmedIntPreference(local: SharedPreferences, remote: SharedPreferences, key: String, value: Int, isCurrent: () -> Boolean) {
    check(isCurrent()) { "Xposed service changed before saving" }
    val previous = if (local.contains(key)) local.getInt(key, 0) else null
    try {
        check(local.edit().putInt(key, value).commit()) { "Local preference commit failed" }
        check(isCurrent()) { "Xposed service disconnected before syncing" }
        check(remote.edit().putInt(key, value).commit()) { "Framework preference commit failed" }
        check(isCurrent() && remote.contains(key) && remote.getInt(key, 0) == value) { "Framework did not confirm the preference" }
    } catch (error: Throwable) {
        fun restore(preferences: SharedPreferences) {
            val editor = preferences.edit()
            if (previous == null) editor.remove(key) else editor.putInt(key, previous)
            check(editor.commit()) { "Could not restore the previous preference" }
        }
        runCatching { restore(local) }.onFailure(error::addSuppressed)
        if (isCurrent()) runCatching { restore(remote) }.onFailure(error::addSuppressed)
        throw error
    }
}
