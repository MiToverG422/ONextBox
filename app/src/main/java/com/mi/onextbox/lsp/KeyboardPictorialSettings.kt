package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal data class CustomOption(val key: String, val title: Int, val summary: Int = 0, val group: String, val default: Int = 0, val min: Int = 0, val max: Int = 0, val unit: String = "")

internal object KeyboardPictorialSettings {
    const val PICTORIAL = "com.heytap.pictorial"
    val options = listOf(
        CustomOption("keyboard_monet", R.string.keyboard_monet_title, R.string.keyboard_monet_summary, "配色"),
        CustomOption("keyboard_blur_radius", R.string.keyboard_blur_radius_title, 0, "材质", 150, 0, 250, "px"),
        CustomOption("keyboard_blur_opacity", R.string.keyboard_blur_opacity_title, 0, "材质", 14, 0, 100, "%"),
        CustomOption("keyboard_blur_corner", R.string.keyboard_blur_corner_title, 0, "材质", 30, 0, 100, "%"),
        CustomOption("keyboard_toolbar_icon_alpha", R.string.keyboard_toolbar_icon_alpha_title, 0, "工具栏", 100, 0, 100, "%"),
        CustomOption("keyboard_toolbar_bg_alpha", R.string.keyboard_toolbar_bg_alpha_title, 0, "工具栏", 63, 0, 100, "%"),
        CustomOption("keyboard_toolbar_outline", R.string.keyboard_toolbar_outline_title, 0, "工具栏"),
        CustomOption("keyboard_toolbar_no_shadow", R.string.keyboard_toolbar_no_shadow_title, 0, "工具栏"),
        CustomOption("keyboard_key_corner", R.string.keyboard_key_corner_title, 0, "按键", 100, 0, 100, "%"),
        CustomOption("keyboard_key_alpha", R.string.keyboard_key_alpha_title, 0, "按键", 100, 0, 100, "%"),
        CustomOption("keyboard_key_font_scale", R.string.keyboard_key_font_scale_title, 0, "按键", 100, 50, 150, "%"),
        CustomOption("keyboard_key_no_outline", R.string.keyboard_key_no_outline_title, 0, "按键"),
        CustomOption("keyboard_key_no_shadow", R.string.keyboard_key_no_shadow_title, 0, "按键"),
        CustomOption("keyboard_hide_t9_marks", R.string.keyboard_hide_t9_marks_title, 0, "角标"),
        CustomOption("keyboard_hide_qwerty_marks", R.string.keyboard_hide_qwerty_marks_title, 0, "角标"),
        CustomOption("keyboard_center_marks", R.string.keyboard_center_marks_title, 0, "角标"),
        CustomOption("pictorial_remove_download_watermark", R.string.pictorial_remove_download_watermark_title, R.string.pictorial_remove_download_watermark_summary, "壁纸下载"),
        CustomOption("pictorial_sync_home", R.string.pictorial_sync_home_title, R.string.pictorial_sync_home_summary, "轮播壁纸"),
        CustomOption("pictorial_clock_color", R.string.pictorial_clock_color_title, R.string.pictorial_clock_color_summary, "轮播壁纸"),
        CustomOption("pictorial_screen_off", R.string.pictorial_screen_off_title, R.string.pictorial_screen_off_summary, "轮播壁纸"),
        CustomOption("pictorial_keep_clock", R.string.pictorial_keep_clock_title, R.string.pictorial_keep_clock_summary, "轮播壁纸"),
    )
    val keyboard = options.filter { it.key.startsWith("keyboard_") }
    val pictorial = options.filter { it.key.startsWith("pictorial_") }
    fun active(key: String) = HookConfigSnapshot.boolean(key, false) == true
    fun number(key: String): Int {
        val option = options.first { it.key == key }
        return (HookConfigSnapshot.int("${key}_value", option.default) ?: option.default).coerceIn(option.min, option.max)
    }
    fun enabled(context: Context, key: String) = LspPreferenceStore.prefs(context).getBoolean(key, false)
    fun set(context: Context, key: String, value: Boolean) {
        require(options.any { it.key == key })
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, value)
    }
}
