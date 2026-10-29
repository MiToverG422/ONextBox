package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object DefaultFileManagerSettings {
    const val PACKAGE = "file_manager_default_package"
    const val MT_PACKAGE = "bin.mt.plus"

    fun enabled(context: Context): Boolean = LspPreferenceStore.prefs(context).getString(PACKAGE, "") == MT_PACKAGE
    fun selectedXposed(): String = if (HookConfigSnapshot.string(PACKAGE, "") == MT_PACKAGE) MT_PACKAGE else ""

    fun setEnabled(context: Context, enabled: Boolean) {
        // Preserve an existing MT selection without changing the stored preference type.
        ModernXposedPreferenceSync.setStringAndSync(context, PACKAGE, if (enabled) MT_PACKAGE else "")
    }
}
