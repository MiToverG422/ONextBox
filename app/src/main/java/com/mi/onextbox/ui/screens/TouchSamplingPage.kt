package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.touch.TouchRateApplyResult
import com.mi.onextbox.touch.TouchRatePreset
import com.mi.onextbox.touch.TouchSamplingController
import com.mi.onextbox.touch.TouchSamplingPreferences
import com.mi.onextbox.touch.TouchSamplingState
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.launch

@Composable
internal fun TouchSamplingPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TouchSamplingState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var autoStartEnabled by remember(context) {
        mutableStateOf(TouchSamplingPreferences.readAutoStartEnabled(context))
    }
    val snapshot = state
    val selectablePresets = snapshot?.presets?.filter { preset ->
        snapshot.defaultChipValue == null || preset.chipValue != snapshot.defaultChipValue
    }.orEmpty()

    LaunchedEffect(Unit) { state = TouchSamplingController.read() }

    fun showResult(result: TouchRateApplyResult) {
        state = result.state
        if (result.accepted) {
            val selected = result.selected
            if (selected == null) {
                TouchSamplingPreferences.clearSelectedPreset(context)
            } else {
                TouchSamplingPreferences.saveSelectedPreset(context, selected)
            }
        }
        val message = when {
            !result.accepted -> context.getString(R.string.feature_touch_rate_apply_failed)
            result.selected == null -> context.getString(R.string.feature_touch_rate_restore_success)
            else -> context.getString(R.string.feature_touch_rate_apply_success, result.selected.hz)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun applyPreset(index: Int) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                showResult(TouchSamplingController.applyPreset(index))
            } finally {
                busy = false
            }
        }
    }

    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.feature_touch_rate_tip_title),
            summary = stringResource(R.string.feature_touch_rate_driver_note),
        )
    }

    SettingsSection(title = stringResource(R.string.feature_touch_rate_status_group))
    SettingsGroup {
        when {
            snapshot == null -> SettingsCardRow(
                title = stringResource(R.string.feature_touch_rate_loading),
                summary = "",
            )
            !snapshot.available -> SettingsCardRow(
                title = stringResource(R.string.feature_touch_rate_unavailable),
                summary = "",
            )
            else -> {
                val current = snapshot.presets.firstOrNull { it.index == snapshot.currentIndex }
                val currentName = when {
                    snapshot.currentIndex == 0 -> stringResource(R.string.feature_touch_rate_default)
                    current != null -> presetName(current)
                    else -> stringResource(R.string.feature_touch_rate_unknown)
                }
                val currentDetail = if (snapshot.currentIndex != null && snapshot.currentChipValue != null) {
                    val detail = stringResource(
                        R.string.feature_touch_rate_preset_summary,
                        snapshot.currentIndex,
                        snapshot.currentChipValue,
                    )
                    if (snapshot.currentIndex != 0 && snapshot.currentChipValue == snapshot.defaultChipValue) {
                        "$detail · ${stringResource(R.string.feature_touch_rate_same_chip_as_default)}"
                    } else {
                        detail
                    }
                } else {
                    stringResource(R.string.feature_touch_rate_unknown)
                }
                SettingsCardRow(
                    title = stringResource(R.string.feature_touch_rate_current),
                    summary = "$currentName · $currentDetail",
                    hasDividerBelow = true,
                )
                SettingsDivider()
                val min = selectablePresets.minOfOrNull { it.hz }
                val max = selectablePresets.maxOfOrNull { it.hz }
                SettingsCardRow(
                    title = stringResource(R.string.feature_touch_rate_range),
                    summary = if (min != null && max != null) {
                        stringResource(R.string.feature_touch_rate_range_value, min, max)
                    } else if (snapshot.presets.isNotEmpty()) {
                        stringResource(R.string.feature_touch_rate_no_distinct_presets)
                    } else {
                        stringResource(R.string.feature_touch_rate_no_config)
                    },
                    hasDividerAbove = true,
                )
            }
        }
    }

    if (snapshot == null || !snapshot.available) return

    if (selectablePresets.isNotEmpty()) {
        SettingsSection(title = stringResource(R.string.feature_touch_rate_presets_group))
        SettingsGroup {
            selectablePresets.forEachIndexed { position, preset ->
                if (position > 0) SettingsDivider()
                val active = snapshot.currentIndex == preset.index
                val detail = stringResource(
                    R.string.feature_touch_rate_preset_summary,
                    preset.index,
                    preset.chipValue,
                )
                SettingsCardRow(
                    title = presetName(preset),
                    summary = if (active) {
                        "${stringResource(R.string.feature_touch_rate_active)} · $detail"
                    } else {
                        detail
                    },
                    onClick = if (busy) null else ({ applyPreset(preset.index) }),
                    hasDividerAbove = position > 0,
                    hasDividerBelow = position < selectablePresets.lastIndex,
                )
            }
        }
    }

    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.feature_touch_rate_restore),
            summary = "",
            onClick = if (busy) null else ({
                scope.launch {
                    busy = true
                    try {
                        showResult(TouchSamplingController.restoreDefault())
                    } finally {
                        busy = false
                    }
                }
            }),
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.feature_touch_rate_refresh),
            summary = "",
            onClick = if (busy) null else ({
                scope.launch {
                    busy = true
                    try {
                        state = TouchSamplingController.read()
                    } finally {
                        busy = false
                    }
                }
            }),
            hasDividerAbove = true,
        )
    }
    SettingsSection(title = stringResource(R.string.tab_settings))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_touch_rate_override_title),
            summary = stringResource(R.string.feature_touch_rate_override_summary),
            checked = snapshot.overrideEnabled,
            enabled = !busy,
            onCheckedChange = { enabled ->
                if (!busy) scope.launch {
                    busy = true
                    try {
                        val result = TouchSamplingController.setOverrideEnabled(context, enabled)
                        state = result.state
                        val message = when {
                            result.needsPreset -> R.string.feature_touch_rate_override_need_preset
                            !result.accepted -> R.string.feature_touch_rate_override_failed
                            enabled -> R.string.feature_touch_rate_override_on
                            else -> R.string.feature_touch_rate_override_off
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    } finally {
                        busy = false
                    }
                }
            },
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_touch_rate_auto_start),
            summary = "",
            checked = autoStartEnabled,
            enabled = !busy,
            onCheckedChange = { enabled ->
                if (!busy) {
                    val saved = TouchSamplingPreferences.readSelectedPreset(context)
                    val preset = saved?.takeIf { it in selectablePresets }
                        ?: selectablePresets.firstOrNull { it.index == snapshot.currentIndex }
                    if (enabled && preset == null) {
                        Toast.makeText(
                            context,
                            R.string.feature_touch_rate_override_need_preset,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        if (enabled && preset != null) {
                            TouchSamplingPreferences.saveSelectedPreset(context, preset)
                        }
                        TouchSamplingPreferences.setAutoStartEnabled(context, enabled)
                        autoStartEnabled = enabled
                    }
                }
            },
            hasDividerAbove = true,
        )
    }
}

@Composable
private fun presetName(preset: TouchRatePreset): String = if (preset.isIstMode) {
    "${preset.hz} Hz · ${stringResource(R.string.feature_touch_rate_ist)}"
} else {
    "${preset.hz} Hz"
}

