package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.LspConfig.NetworkSpeedFeature as Feature
import com.mi.onextbox.lsp.compat.HookConfigSnapshot

internal object NetworkSpeedSettings {
    const val INTERVAL = "systemui_network_speed_interval_ms"
    const val FONT_SIZE = "systemui_network_speed_font_size_dp"
    const val LOW_SPEED_THRESHOLD = "systemui_network_speed_low_speed_threshold_kbps"
    const val DEFAULT_THRESHOLD_KBPS = 100
    const val MIN_THRESHOLD_KBPS = 1
    const val MAX_THRESHOLD_KBPS = 1024
    private const val LEGACY_FONT_SIZE = "systemui_network_speed_font_size_sp"
    data class State(
        val customRefresh: Boolean = false,
        val intervalMs: Int = 1000,
        val hidePerSecond: Boolean = false,
        val hideUnits: Boolean = false,
        val singleLine: Boolean = false,
        val followFontWeight: Boolean = false,
        val customFontSize: Boolean = false,
        val fontSizeDp: Int = 9,
        val hideLowSpeed: Boolean = false,
        val lowSpeedThresholdKbps: Int = DEFAULT_THRESHOLD_KBPS,
    )

    fun normalize(state: State) = state.copy(
        intervalMs = state.intervalMs.coerceIn(500, 10000),
        fontSizeDp = state.fontSizeDp.coerceIn(2, 14),
        lowSpeedThresholdKbps = state.lowSpeedThresholdKbps.coerceIn(MIN_THRESHOLD_KBPS, MAX_THRESHOLD_KBPS),
    )

    fun state(context: Context): State {
        val prefs = LspPreferenceStore.prefs(context)
        fun enabled(feature: Feature) = prefs.getBoolean(feature.key, false)
        return normalize(State(enabled(Feature.CustomRefreshInterval), prefs.getInt(INTERVAL, 1000),
            enabled(Feature.HidePerSecond), enabled(Feature.HideUnits), enabled(Feature.SingleLine),
            enabled(Feature.FollowSystemFontWeight), enabled(Feature.CustomFontSize), prefs.getInt(FONT_SIZE, prefs.getInt(LEGACY_FONT_SIZE, 9)),
            enabled(Feature.HideLowSpeed), prefs.getInt(LOW_SPEED_THRESHOLD, DEFAULT_THRESHOLD_KBPS)))
    }

    fun snapshot(): State {
        fun enabled(feature: Feature) = LspConfig.isNetworkSpeedFeatureEnabledXposed(feature)
        return normalize(State(enabled(Feature.CustomRefreshInterval), HookConfigSnapshot.int(INTERVAL, 1000) ?: 1000,
            enabled(Feature.HidePerSecond), enabled(Feature.HideUnits), enabled(Feature.SingleLine),
            enabled(Feature.FollowSystemFontWeight), enabled(Feature.CustomFontSize),
            HookConfigSnapshot.int(FONT_SIZE, HookConfigSnapshot.int(LEGACY_FONT_SIZE, 9) ?: 9) ?: 9,
            enabled(Feature.HideLowSpeed), HookConfigSnapshot.int(LOW_SPEED_THRESHOLD, DEFAULT_THRESHOLD_KBPS) ?: DEFAULT_THRESHOLD_KBPS))
    }

    fun save(context: Context, value: State) {
        val state = normalize(value)
        check(LspPreferenceStore.prefs(context).edit()
            .putBoolean(Feature.CustomRefreshInterval.key, state.customRefresh).putInt(INTERVAL, state.intervalMs)
            .putBoolean(Feature.HidePerSecond.key, state.hidePerSecond).putBoolean(Feature.HideUnits.key, state.hideUnits)
            .putBoolean(Feature.SingleLine.key, state.singleLine).putBoolean(Feature.FollowSystemFontWeight.key, state.followFontWeight)
            .putBoolean(Feature.CustomFontSize.key, state.customFontSize).putInt(FONT_SIZE, state.fontSizeDp)
            .putBoolean(Feature.HideLowSpeed.key, state.hideLowSpeed).putInt(LOW_SPEED_THRESHOLD, state.lowSpeedThresholdKbps).commit())
        LspConfig.syncReadableState(context)
    }
}
