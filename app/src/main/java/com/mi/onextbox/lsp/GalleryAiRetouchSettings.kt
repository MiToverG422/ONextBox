package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object GalleryAiRetouchSettings {
    const val PACKAGE_NAME = "com.coloros.gallery3d"
    const val KEY = "gallery_ai_retouch_unlock"

    fun enabled(context: Context): Boolean = LspPreferenceStore.prefs(context).getBoolean(KEY, false)

    fun set(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEY, enabled).commit())
        LspConfig.syncReadableState(context)
    }

    fun enabledXposed(): Boolean = HookConfigSnapshot.boolean(KEY, false) == true
}
