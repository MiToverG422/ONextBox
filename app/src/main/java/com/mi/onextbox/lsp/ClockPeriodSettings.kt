package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object ClockPeriodSettings {
    const val ENABLED = "systemui_clock_period_enabled"
    const val FORMAT = "systemui_clock_period_format"
    const val SMALL = "systemui_clock_period_small"
    const val COMPACT = "systemui_clock_compact_spacing"
    const val SHADE = "systemui_clock_period_shade"
    const val LOCK_SECONDS = "systemui_clock_lock_seconds"
    const val TABULAR_DIGITS = "systemui_clock_tabular_digits"
    val keys = setOf(ENABLED, FORMAT, SMALL, COMPACT, SHADE, LOCK_SECONDS, TABULAR_DIGITS)
    data class State(val enabled: Boolean = false, val format: Int = 1, val small: Boolean = false, val compact: Boolean = false, val shade: Boolean = false, val lockSeconds: Boolean = false, val tabularDigits: Boolean = false)

    fun state(context: Context) = LspPreferenceStore.prefs(context).let {
        State(it.getBoolean(ENABLED, false), ClockPeriodRules.normalizeFormat(it.getInt(FORMAT, 1)), it.getBoolean(SMALL, false), it.getBoolean(COMPACT, false), it.getBoolean(SHADE, false), it.getBoolean(LOCK_SECONDS, false), it.getBoolean(TABULAR_DIGITS, false))
    }
    fun snapshot() = State(HookConfigSnapshot.boolean(ENABLED, false) == true,
        ClockPeriodRules.normalizeFormat(HookConfigSnapshot.int(FORMAT, 1) ?: 1), HookConfigSnapshot.boolean(SMALL, false) == true, compactXposed(), HookConfigSnapshot.boolean(SHADE, false) == true, HookConfigSnapshot.boolean(LOCK_SECONDS, false) == true, tabularDigitsXposed())

    fun compactXposed() = HookConfigSnapshot.boolean(COMPACT, false) == true
    fun tabularDigitsXposed() = HookConfigSnapshot.boolean(TABULAR_DIGITS, false) == true

    fun save(context: Context, state: State) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, state.enabled)
            .putInt(FORMAT, ClockPeriodRules.normalizeFormat(state.format)).putBoolean(SMALL, state.small).putBoolean(COMPACT, state.compact).putBoolean(SHADE, state.shade).putBoolean(LOCK_SECONDS, state.lockSeconds).putBoolean(TABULAR_DIGITS, state.tabularDigits).commit())
        LspConfig.syncReadableState(context)
    }

    fun secondsLockedXposed() = LspConfig.isPermanentClockSecondsEnabledXposed() &&
        HookConfigSnapshot.boolean(LOCK_SECONDS, false) == true

    fun setSecondsLocked(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(LOCK_SECONDS, enabled).commit())
        LspConfig.syncReadableState(context)
    }

    fun setShade(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(SHADE, enabled).commit())
        LspConfig.syncReadableState(context)
    }

    fun setCompact(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(COMPACT, enabled).commit())
        LspConfig.syncReadableState(context)
    }

    fun setTabularDigits(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(TABULAR_DIGITS, enabled).commit())
        LspConfig.syncReadableState(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, enabled).commit())
        LspConfig.syncReadableState(context)
    }
}
