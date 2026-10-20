package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import java.util.Locale
import kotlin.math.abs

internal object PowerIndicatorSettings {
    const val PREFIX = "statusbar_power_"
    const val ENABLED = "${PREFIX}enabled"
    const val HIDE_DISCHARGE_SIGN = "${PREFIX}hide_discharge_sign"
    const val SHOW_TEMPERATURE = "${PREFIX}show_temperature"
    const val TEMPERATURE_UNIT = "${PREFIX}temperature_unit"
    const val FOLLOW_FONT_WEIGHT = "${PREFIX}follow_font_weight"
    data class State(val enabled: Boolean = false, val interval: Int = 2000,
        val decimals: Int = 1, val scale: Int = 0, val source: Int = 0,
        val hideCharging: Boolean = true, val fontSize: Int = 10,
        val hideDischargeSign: Boolean = false, val showTemperature: Boolean = false,
        val temperatureUnit: Int = 0, val followFontWeight: Boolean = true)
    fun state(context: Context): State {
        val p = LspPreferenceStore.prefs(context)
        return normalize(State(p.getBoolean(ENABLED, false), p.getInt("${PREFIX}interval", 2000),
            p.getInt("${PREFIX}decimals", 1), p.getInt("${PREFIX}scale", 0), p.getInt("${PREFIX}source", 0),
            p.getBoolean("${PREFIX}hide_charging", true), p.getInt("${PREFIX}font", 10),
            p.getBoolean(HIDE_DISCHARGE_SIGN, false), p.getBoolean(SHOW_TEMPERATURE, false),
            p.getInt(TEMPERATURE_UNIT, 0), p.getBoolean(FOLLOW_FONT_WEIGHT, true)))
    }
    fun snapshot() = normalize(State(HookConfigSnapshot.boolean(ENABLED, false) == true,
        HookConfigSnapshot.int("${PREFIX}interval", 2000) ?: 2000,
        HookConfigSnapshot.int("${PREFIX}decimals", 1) ?: 1,
        HookConfigSnapshot.int("${PREFIX}scale", 0) ?: 0,
        HookConfigSnapshot.int("${PREFIX}source", 0) ?: 0,
        HookConfigSnapshot.boolean("${PREFIX}hide_charging", true) != false,
        HookConfigSnapshot.int("${PREFIX}font", 10) ?: 10,
        HookConfigSnapshot.boolean(HIDE_DISCHARGE_SIGN, false) == true,
        HookConfigSnapshot.boolean(SHOW_TEMPERATURE, false) == true,
        HookConfigSnapshot.int(TEMPERATURE_UNIT, 0) ?: 0,
        HookConfigSnapshot.boolean(FOLLOW_FONT_WEIGHT, true) != false))
    fun normalize(s: State) = s.copy(interval = s.interval.coerceIn(500, 10000), decimals = s.decimals.coerceIn(0, 2),
        scale = s.scale.coerceIn(0, 2), source = s.source.coerceIn(0, 2), fontSize = s.fontSize.coerceIn(8, 16),
        temperatureUnit = s.temperatureUnit.coerceIn(0, 1))
    fun setEnabled(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, enabled).commit())
        LspConfig.syncReadableState(context)
    }
    fun save(context: Context, value: State) {
        val s = normalize(value)
        check(LspPreferenceStore.prefs(context).edit().putBoolean(ENABLED, s.enabled)
            .putInt("${PREFIX}interval", s.interval).putInt("${PREFIX}decimals", s.decimals)
            .putInt("${PREFIX}scale", s.scale).putInt("${PREFIX}source", s.source)
            .putBoolean("${PREFIX}hide_charging", s.hideCharging).putInt("${PREFIX}font", s.fontSize)
            .putBoolean(HIDE_DISCHARGE_SIGN, s.hideDischargeSign).putBoolean(SHOW_TEMPERATURE, s.showTemperature)
            .putInt(TEMPERATURE_UNIT, s.temperatureUnit)
            .putBoolean(FOLLOW_FONT_WEIGHT, s.followFontWeight).commit())
        LspConfig.syncReadableState(context)
    }
}

internal object PowerIndicatorRules {
    // API current: microamps; explicit vendor sysfs mode may be milliamps. Never guess from magnitude.
    fun watts(current: Long, voltageMv: Long, source: Int, scale: Int): Double? {
        if (current == Long.MIN_VALUE || current == Int.MIN_VALUE.toLong() || current == Long.MAX_VALUE ||
            voltageMv !in 2000..20000) return null
        val amps = abs(current.toDouble()) / if (source == 1) 1000.0 else 1_000_000.0
        val multiplier = when (scale) { 1 -> 2.0; 2 -> 0.5; else -> 1.0 }
        return (amps * voltageMv / 1000.0 * multiplier).takeIf { it.isFinite() && it in 0.0..200.0 }
    }
    fun format(watts: Double, decimals: Int, charging: Boolean, hideDischargeSign: Boolean = false,
        temperatureTenthsC: Int? = null, temperatureUnit: Int = 0): String {
        val sign = if (charging) "+" else if (hideDischargeSign) "" else "-"
        val power = sign + String.format(Locale.ROOT, "%.${decimals.coerceIn(0, 2)}f W", watts)
        val temperature = temperatureTenthsC?.takeIf { it in -500..1000 } ?: return power
        val celsius = temperature / 10.0
        val value = if (temperatureUnit == 1) celsius * 9.0 / 5.0 + 32.0 else celsius
        val unit = if (temperatureUnit == 1) "°F" else "°C"
        return power + "\n" + String.format(Locale.ROOT, "%.1f%s", value, unit)
    }

    /** Keep two lines within the native status-bar height without enlarging its layout. */
    fun fittedTextSizePx(requested: Float, lineHeight: Float, lines: Int, availableHeight: Int): Float {
        if (lines <= 1 || availableHeight <= 0 || lineHeight <= 0f) return requested
        val fitted = requested * (availableHeight - 1).coerceAtLeast(1) / (lineHeight * lines)
        return minOf(requested, fitted.coerceAtLeast(1f))
    }
}
