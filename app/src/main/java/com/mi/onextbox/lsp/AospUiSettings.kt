package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object AospUiSettings {
    const val LOCKSCREEN = "systemui_aosp_lockscreen_clock"
    const val CLOCK = "systemui_aosp_clock_style"
    const val AOD_LAYOUT = "systemui_aosp_aod_layout"
    const val FINGERPRINT = "systemui_aosp_fingerprint_icon"
    const val CHARGING_EFFECT = "systemui_aosp_charging_ripple"
    const val CHARGING_SOUND = "systemui_aosp_charging_sound"
    val chargingKeys = setOf(CHARGING_EFFECT, CHARGING_SOUND)
    private val pausedKeys = setOf(LOCKSCREEN, AOD_LAYOUT, FINGERPRINT)
    val clockIds = listOf("DEFAULT", "DIGITAL_CLOCK_FLEX", "ANALOG_CLOCK_BIGNUM", "DIGITAL_CLOCK_CALLIGRAPHY",
        "DIGITAL_CLOCK_GROWTH", "DIGITAL_CLOCK_INFLATE", "DIGITAL_CLOCK_METRO",
        "DIGITAL_CLOCK_NUMBEROVERLAP")

    data class State(
        val chargingEffect: Boolean = false,
        val chargingSound: Boolean = false,
    )

    fun state(context: Context): State {
        val prefs = LspPreferenceStore.prefs(context)
        return State(chargingEffect = prefs.getBoolean(CHARGING_EFFECT, false),
            chargingSound = prefs.getBoolean(CHARGING_SOUND, false))
    }

    fun normalizeClock(value: String?) = value?.takeIf { it in clockIds } ?: "ANALOG_CLOCK_BIGNUM"
    fun enabledXposed(key: String) = key in chargingKeys && HookConfigSnapshot.boolean(key, false) == true
    fun clockXposed() = normalizeClock(HookConfigSnapshot.string(CLOCK, null))

    fun enforcePaused(preferences: SharedPreferences) {
        val values = preferences.all
        val enabled = pausedKeys.filter { values[it] == true }
        if (enabled.isEmpty()) return
        val editor = preferences.edit()
        enabled.forEach { editor.putBoolean(it, false) }
        check(editor.commit())
    }

    fun set(context: Context, key: String, enabled: Boolean) {
        require(key in chargingKeys)
        check(LspPreferenceStore.prefs(context).edit().putBoolean(key, enabled).commit())
        LspConfig.syncReadableState(context)
    }

}
