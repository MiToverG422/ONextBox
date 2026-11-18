package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object MediaWatermarkSettings {
    const val ENABLED = "camera_unlock_watermark_custom"
    const val MODEL = "media_watermark_model"
    const val MAKE = "media_watermark_make"
    const val LOCATION = "media_watermark_location"
    const val TIME = "media_watermark_time"
    const val AUTHOR = "media_watermark_author"
    const val HIDE_LOCATION = "media_watermark_hide_location"
    const val HIDE_TIME = "media_watermark_hide_time"
    const val MAX_CODE_POINTS = 64
    val textKeys = listOf(MODEL, MAKE, LOCATION, TIME, AUTHOR)
    val keys = textKeys.toSet() + setOf(ENABLED, HIDE_LOCATION, HIDE_TIME)
    fun normalize(value: String?): String {
        val text = value.orEmpty().filterNot { it.isISOControl() }.trim()
        return if (text.codePointCount(0, text.length) <= MAX_CODE_POINTS) text
            else text.substring(0, text.offsetByCodePoints(0, MAX_CODE_POINTS))
    }
    data class State(val enabled: Boolean, val text: Map<String, String>, val hideLocation: Boolean, val hideTime: Boolean)
    fun effectiveEnabled(explicit: Boolean?, hasLegacyContent: Boolean): Boolean = explicit ?: hasLegacyContent
    fun enabled(prefs: SharedPreferences): Boolean {
        if (prefs.contains(ENABLED)) return prefs.getBoolean(ENABLED, false)
        return effectiveEnabled(null, textKeys.any { normalize(prefs.getString(it, "")).isNotEmpty() } ||
            prefs.getBoolean(HIDE_LOCATION, false) || prefs.getBoolean(HIDE_TIME, false))
    }
    fun state(context: Context): State = LspPreferenceStore.prefs(context).let { prefs ->
        State(enabled(prefs), textKeys.associateWith { normalize(prefs.getString(it, "")) },
            prefs.getBoolean(HIDE_LOCATION, false), prefs.getBoolean(HIDE_TIME, false))
    }
    fun enabledXposed(): Boolean {
        if (HookConfigSnapshot.contains(ENABLED)) return HookConfigSnapshot.boolean(ENABLED, false) == true
        val legacy = textKeys.any { normalize(HookConfigSnapshot.string(it, "")).isNotEmpty() } ||
            HookConfigSnapshot.boolean(HIDE_LOCATION, false) == true || HookConfigSnapshot.boolean(HIDE_TIME, false) == true
        return effectiveEnabled(null, legacy) && HookConfigSnapshot.boolean(ENABLED, true) == true
    }
    fun textXposed(key: String): String = if (enabledXposed()) normalize(HookConfigSnapshot.string(key, "")) else ""
    fun hiddenXposed(key: String) = enabledXposed() && HookConfigSnapshot.boolean(key, false) == true
    fun setText(context: Context, key: String, value: String) {
        require(key in textKeys)
        val normalized = normalize(value)
        ModernXposedPreferenceSync.setStringAndSync(context, key, normalized)
        if (normalized.isNotEmpty() && !LspPreferenceStore.prefs(context).getBoolean(ENABLED, false))
            ModernXposedPreferenceSync.setBooleanAndSync(context, ENABLED, true)
    }
    fun setFlag(context: Context, key: String, value: Boolean) {
        require(key in setOf(ENABLED, HIDE_LOCATION, HIDE_TIME))
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, value)
        if (key != ENABLED && value && !LspPreferenceStore.prefs(context).getBoolean(ENABLED, false))
            ModernXposedPreferenceSync.setBooleanAndSync(context, ENABLED, true)
    }
}
