package com.mi.onextbox.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TouchSamplingState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var autoStartEnabled by remember(context) {
        mutableStateOf(TouchSamplingPreferences.readAutoStartEnabled(context))
    }
    val snapshot = state
    val canChange = snapshot?.canWrite == true && snapshot.backendId != null
    val savedPresetNeedsConfirmation = snapshot?.canWrite == true &&
        TouchSamplingPreferences.readSelectedPreset(context) != null &&
        TouchSamplingPreferences.readSelectedBackendId(context) != snapshot.backendId
    val selectablePresets = snapshot?.presets?.filter { preset ->
        preset.index > 0 &&
            (snapshot.defaultChipValue == null || preset.chipValue != snapshot.defaultChipValue)
    }.orEmpty()

    LaunchedEffect(Unit) { state = TouchSamplingController.read() }

    fun showResult(result: TouchRateApplyResult) {
        state = result.state
        if (result.accepted) {
            val selected = result.selected
            if (selected == null) {
                TouchSamplingPreferences.clearSelectedPreset(context)
            } else {
                TouchSamplingPreferences.saveSelectedPreset(context, selected, result.state.backendId)
            }
        }
        val selected = result.selected
        val message = when {
            !result.accepted -> resources.getString(R.string.feature_touch_rate_apply_failed)
            selected == null -> resources.getString(R.string.feature_touch_rate_restore_success)
            selected.hz != null -> resources.getString(R.string.feature_touch_rate_apply_success, selected.hz)
            else -> resources.getString(R.string.feature_touch_rate_apply_mode_success, selected.index)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun refresh() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                state = TouchSamplingController.read()
            } finally {
                busy = false
            }
        }
    }

    fun applyPreset(index: Int) {
        val backendId = snapshot?.backendId ?: return
        if (busy || !canChange) return
        scope.launch {
            busy = true
            try {
                showResult(TouchSamplingController.applyPreset(index, backendId, context))
            } finally {
                busy = false
            }
        }
    }

    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.feature_touch_rate_tip_title),
            summary = stringResource(R.string.feature_touch_rate_device_notice) + "\n\n" +
                stringResource(R.string.feature_touch_rate_driver_note),
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
                title = stringResource(touchStatusMessage(snapshot)),
                summary = "",
            )
            else -> {
                val current = snapshot.presets.firstOrNull { it.index == snapshot.currentIndex }
                val currentName = when {
                    snapshot.currentIndex == 0 -> stringResource(R.string.feature_touch_rate_default)
                    current != null -> presetName(current)
                    snapshot.currentIndex != null -> stringResource(
                        R.string.feature_touch_rate_mode_name, snapshot.currentIndex,
                    )
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
                val knownRates = selectablePresets.mapNotNull { it.hz }
                val min = knownRates.minOrNull()
                val max = knownRates.maxOrNull()
                val rangeText = when {
                    min != null && max != null -> stringResource(
                        R.string.feature_touch_rate_range_value, min, max,
                    )
                    selectablePresets.isNotEmpty() -> stringResource(
                        R.string.feature_touch_rate_modes_without_labels, selectablePresets.size,
                    )
                    snapshot.presets.isNotEmpty() -> stringResource(R.string.feature_touch_rate_no_distinct_presets)
                    else -> stringResource(R.string.feature_touch_rate_no_config)
                }
                SettingsCardRow(
                    title = stringResource(R.string.feature_touch_rate_range),
                    summary = if (knownRates.isNotEmpty() && knownRates.size < selectablePresets.size) {
                        "$rangeText\n${stringResource(R.string.feature_touch_rate_unlabeled_note)}"
                    } else {
                        rangeText
                    },
                    hasDividerAbove = true,
                )
            }
        }
    }

    if (snapshot?.available == true && !canChange) {
        SettingsGroup {
            SettingsCardRow(
                title = stringResource(touchStatusMessage(snapshot)),
                summary = "",
            )
        }
    }

    if (savedPresetNeedsConfirmation) {
        SettingsGroup {
            SettingsCardRow(
                title = stringResource(R.string.feature_touch_rate_tip_title),
                summary = stringResource(R.string.feature_touch_rate_backend_changed),
            )
        }
    }

    if (snapshot != null && selectablePresets.isNotEmpty()) {
        SettingsSection(title = stringResource(R.string.feature_touch_rate_presets_group))
        SettingsGroup {
            selectablePresets.forEachIndexed { position, preset ->
                if (position > 0) SettingsDivider()
                val detail = stringResource(
                    R.string.feature_touch_rate_preset_summary, preset.index, preset.chipValue,
                )
                SettingsCardRow(
                    title = presetName(preset),
                    summary = if (snapshot.currentIndex == preset.index) {
                        "${stringResource(R.string.feature_touch_rate_active)} · $detail"
                    } else {
                        detail
                    },
                    onClick = if (busy || !canChange) null else ({ applyPreset(preset.index) }),
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
            onClick = if (busy || !canChange) null else ({
                scope.launch {
                    busy = true
                    try {
                        showResult(TouchSamplingController.restoreDefault(snapshot.backendId))
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
            onClick = if (busy) null else ({ refresh() }),
            hasDividerAbove = true,
        )
    }

    if (snapshot != null) {
        SettingsSection(title = stringResource(R.string.tab_settings))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.feature_touch_rate_override_title),
                summary = stringResource(R.string.feature_touch_rate_override_summary),
                checked = snapshot.overrideEnabled,
                enabled = !busy && (canChange || snapshot.overrideEnabled),
                onCheckedChange = { enabled ->
                    if (!busy && (!enabled || canChange)) scope.launch {
                        busy = true
                        try {
                            val result = TouchSamplingController.setOverrideEnabled(
                                context, enabled, if (enabled) snapshot.backendId else null,
                            )
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
                enabled = !busy && (canChange || autoStartEnabled),
                onCheckedChange = { enabled ->
                    if (!busy && (!enabled || canChange)) {
                        val saved = TouchSamplingPreferences.readSelectedPreset(context)
                        val savedBackend = TouchSamplingPreferences.readSelectedBackendId(context)
                        val bindingChanged = enabled && saved != null && savedBackend != snapshot.backendId
                        val preset = saved?.takeIf { savedBackend == snapshot.backendId && it in selectablePresets }
                            ?: selectablePresets.firstOrNull { it.index == snapshot.currentIndex }
                        when {
                            bindingChanged -> Toast.makeText(
                                context, R.string.feature_touch_rate_backend_changed, Toast.LENGTH_SHORT,
                            ).show()
                            enabled && preset == null -> Toast.makeText(
                                context, R.string.feature_touch_rate_override_need_preset, Toast.LENGTH_SHORT,
                            ).show()
                            else -> {
                                if (enabled && preset != null) {
                                    TouchSamplingPreferences.saveSelectedPreset(context, preset, snapshot.backendId)
                                }
                                TouchSamplingPreferences.setAutoStartEnabled(context, enabled)
                                autoStartEnabled = enabled
                            }
                        }
                    }
                },
                hasDividerAbove = true,
            )
        }
    }

    SettingsSection(title = stringResource(R.string.feature_touch_rate_diagnostics_group))
    SettingsGroup {
        snapshot?.backendLabel?.let { backend ->
            SettingsCardRow(
                title = stringResource(R.string.feature_touch_rate_backend),
                summary = backend,
                hasDividerBelow = true,
            )
            SettingsDivider()
        }
        snapshot?.configSource?.let { source ->
            SettingsCardRow(
                title = stringResource(R.string.feature_touch_rate_config_source),
                summary = source,
                hasDividerAbove = snapshot.backendLabel != null,
                hasDividerBelow = true,
            )
            SettingsDivider()
        }
        SettingsCardRow(
            title = stringResource(R.string.feature_touch_rate_diagnostics_notice),
            summary = snapshot?.diagnostic?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.feature_touch_rate_diagnostics_unavailable),
            hasDividerAbove = snapshot?.backendLabel != null || snapshot?.configSource != null,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.feature_touch_rate_copy_diagnostics),
            summary = "",
            onClick = snapshot?.diagnostic?.takeIf { it.isNotBlank() }?.let { report ->
                {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                        resources.getString(R.string.feature_touch_rate_diagnostics_group), report,
                    ))
                    Toast.makeText(context, R.string.feature_touch_rate_diagnostics_copied, Toast.LENGTH_SHORT).show()
                }
            },
            hasDividerAbove = true,
        )
    }
}

private fun touchStatusMessage(state: TouchSamplingState): Int = when (state.error) {
    "root" -> R.string.feature_touch_rate_root_required
    "hal_unsupported" -> R.string.feature_touch_rate_node_unsupported
    "hal" -> R.string.feature_touch_rate_hal_failed
    "config" -> R.string.feature_touch_rate_no_config
    "config_ambiguous" -> R.string.feature_touch_rate_config_ambiguous
    "backend_unknown" -> R.string.feature_touch_rate_backend_unknown
    "read" -> R.string.feature_touch_rate_read_failed
    else -> R.string.feature_touch_rate_read_only
}

@Composable
private fun presetName(preset: TouchRatePreset): String {
    val name = preset.hz?.let { "$it Hz" }
        ?: stringResource(R.string.feature_touch_rate_mode_name, preset.index)
    return if (preset.isIstMode) "$name · ${stringResource(R.string.feature_touch_rate_ist)}" else name
}
