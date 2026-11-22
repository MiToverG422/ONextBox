package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal enum class SystemUiAppearance(val key: String, val title: Int, val default: Int = 0,
    val range: IntRange = 0..1) {
    Mirror("statusbar_mirror_layout", R.string.sb_mirror_layout),
    Sides("statusbar_adjust_sides", R.string.sb_adjust_sides),
    LeftX("statusbar_icon_left_x", R.string.sb_left_x, 0, -40..40),
    LeftY("statusbar_icon_left_y", R.string.sb_left_y, 0, -28..28),
    LeftScale("statusbar_icon_left_scale", R.string.sb_left_scale, 100, 50..150),
    RightX("statusbar_icon_right_x", R.string.sb_right_x, 0, -40..40),
    RightY("statusbar_icon_right_y", R.string.sb_right_y, 0, -28..28),
    RightScale("statusbar_icon_right_scale", R.string.sb_right_scale, 100, 50..150),
    HideSettings("shade_hide_notification_settings", R.string.shade_hide_notification_settings),
    Corners("shade_custom_notification_corners", R.string.shade_custom_notification_corners),
    Radius("shade_notification_radius_tenths_dp", R.string.shade_notification_radius, 180, 0..640),
    Popup("shade_notification_radius_popup", R.string.shade_notification_popup, 1),
    Shade("shade_notification_radius_center", R.string.shade_notification_center, 1),
    Lock("shade_notification_radius_lock", R.string.keyguard_page_title, 1);

    val flag get() = range == 0..1
    fun read(context: Context): Int {
        if (this in StatusBarIconLayout.options) return StatusBarIconLayout.read(context).value(this)
        val prefs = LspPreferenceStore.prefs(context)
        return if (flag) (if (prefs.getBoolean(key, default != 0)) 1 else 0)
        else prefs.getInt(key, default).coerceIn(range)
    }
    fun current() = if (flag) (if (HookConfigSnapshot.boolean(key, default != 0) == true) 1 else 0)
        else (HookConfigSnapshot.int(key, default) ?: default).coerceIn(range)

    fun save(context: Context, value: Int) {
        if (this in StatusBarIconLayout.options) return StatusBarIconLayout.save(context, this, value)
        val editor = LspPreferenceStore.prefs(context).edit()
        if (flag) editor.putBoolean(key, value != 0) else editor.putInt(key, value.coerceIn(range))
        check(editor.commit())
        LspConfig.syncReadableState(context)
    }
}
