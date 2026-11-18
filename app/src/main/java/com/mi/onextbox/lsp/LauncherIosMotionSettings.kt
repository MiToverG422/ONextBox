package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object LauncherIosMotionSettings {
    const val ENABLED = "launcher_ios_motion"
    const val GENIE = "launcher_ios_motion_genie"
    const val OPENING_APPS = "launcher_ios_motion_opening_apps"
    const val CLOSING_APPS = "launcher_ios_motion_closing_apps"
    const val DROPLET_CORNERS = "launcher_ios_motion_droplet_corners"
    const val BOUNCE = "launcher_ios_motion_bounce"

    val bend = Number("launcher_ios_bend", 0, 200, 100)
    val openSpeed = Number("launcher_ios_open_speed", 50, 200, 100)
    val closeSpeed = Number("launcher_ios_close_speed", 50, 200, 100)
    val bounce = Number("launcher_ios_bounce_strength", 0, 100, 100)
    val slow = Number("launcher_ios_slow_motion", 100, 500, 100)

    val defaults = linkedMapOf(
        ENABLED to false,
        GENIE to true,
        OPENING_APPS to true,
        CLOSING_APPS to true,
        DROPLET_CORNERS to true,
        BOUNCE to true,
    )
    val numbers = listOf(bend, openSpeed, closeSpeed, bounce, slow)

    data class Number(val key: String, val min: Int, val max: Int, val default: Int) {
        fun normalize(value: Int): Int = value.coerceIn(min, max)
    }

    data class State(val switches: Map<String, Boolean>, val values: Map<String, Int>) {
        fun enabled(key: String): Boolean = switches[key] ?: defaults.getValue(key)
        fun value(number: Number): Int = values[number.key] ?: number.default
    }

    fun state(context: Context): State {
        val prefs = LspPreferenceStore.prefs(context)
        return State(
            switches = defaults.mapValues { (key, default) -> prefs.getBoolean(key, default) },
            values = numbers.associate { it.key to it.normalize(prefs.getInt(it.key, it.default)) },
        )
    }

    fun enabled(context: Context): Boolean = LspPreferenceStore.prefs(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) = set(context, ENABLED, enabled)

    fun active(key: String = ENABLED): Boolean = HookConfigSnapshot.boolean(key, defaults.getValue(key)) == true

    fun value(number: Number): Int = number.normalize(HookConfigSnapshot.int(number.key, number.default) ?: number.default)

    fun set(context: Context, key: String, enabled: Boolean) =
        ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)

    fun set(context: Context, number: Number, value: Int) =
        ModernXposedPreferenceSync.setIntAndSync(context, number.key, number.normalize(value))
}
