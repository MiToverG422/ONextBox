package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

/** Reattach the original native group retained by the volume-mode controller. */
internal object SettingsAppVolumeGroup {
    const val KEY = "voice_mode_category"

    fun reconcile(screen: Any, volumeModeController: Any, forced: Boolean, nativeRealme: Boolean) {
        val existing = Reflect.callMethod(screen, "findPreference", KEY)
        if (!forced) {
            if (!nativeRealme && existing != null) Reflect.callMethod(screen, "removePreference", existing)
            return
        }
        val modeRow = Reflect.getObjectField(volumeModeController, "mDialogPreference")
        val group = existing ?: modeRow?.let { Reflect.callMethod(it, "getParent") }
            ?.takeIf { Reflect.callMethod(it, "getKey") == KEY } ?: return
        if (existing == null) {
            check(Reflect.callMethod(group, "getParent") == null) { "Native volume group belongs to another screen" }
            check(Reflect.callMethod(screen, "addPreference", group) == true) { "Could not restore native volume group" }
        }
        Reflect.callMethod(group, "setVisible", true)
        // Only expose the requested app-volume row on non-Realme devices.
        if (!nativeRealme && modeRow != null) Reflect.callMethod(modeRow, "setVisible", false)
    }
}
