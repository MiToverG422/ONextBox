package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.rememberColorOsHapticTick
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlin.math.roundToInt

@Composable
internal fun AodFeaturesPage(
    aodEnhanceEnabled: Boolean,
    onAodEnhanceEnabledChange: (Boolean) -> Unit,
    aodInitDarkBrightness: Int,
    onAodInitDarkBrightnessChange: (Int) -> Unit,
    aodInitBrightBrightness: Int,
    onAodInitBrightBrightnessChange: (Int) -> Unit,
    aodRunningBrightnessMultiplier: Float,
    onAodRunningBrightnessMultiplierChange: (Float) -> Unit,
    aodPanoramicSupportEnabled: Boolean,
    onAodPanoramicSupportEnabledChange: (Boolean) -> Unit,
    aodSettingsSwitchEnabled: Boolean,
    onAodSettingsSwitchEnabledChange: (Boolean) -> Unit,
    aodSingleClickBlockEnabled: Boolean,
    onAodSingleClickBlockEnabledChange: (Boolean) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.feature_group_aod_brightness))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_aod_enhance_toggle_title),
            summary = "",
            checked = aodEnhanceEnabled,
            onCheckedChange = onAodEnhanceEnabledChange,
            hasDividerBelow = true,
        )
        SettingsDivider()
        AodIntSliderRow(
            title = stringResource(R.string.feature_aod_dark_brightness_current, aodInitDarkBrightness),
            value = aodInitDarkBrightness,
            onValueChange = onAodInitDarkBrightnessChange,
            enabled = aodEnhanceEnabled,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        AodIntSliderRow(
            title = stringResource(R.string.feature_aod_bright_brightness_current, aodInitBrightBrightness),
            value = aodInitBrightBrightness,
            onValueChange = onAodInitBrightBrightnessChange,
            enabled = aodEnhanceEnabled,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        AodFloatSliderRow(
            title = stringResource(
                R.string.feature_aod_multiplier_current,
                formatAodMultiplier(aodRunningBrightnessMultiplier),
            ),
            value = aodRunningBrightnessMultiplier,
            onValueChange = onAodRunningBrightnessMultiplierChange,
            enabled = aodEnhanceEnabled,
            hasDividerAbove = true,
        )
    }
    SettingsSection(title = stringResource(R.string.feature_group_aod_features))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_aod_panoramic_title),
            summary = "",
            checked = aodPanoramicSupportEnabled,
            onCheckedChange = onAodPanoramicSupportEnabledChange,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_aod_settings_switch_title),
            summary = "",
            checked = aodSettingsSwitchEnabled,
            onCheckedChange = onAodSettingsSwitchEnabledChange,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_aod_single_click_block_title),
            summary = "",
            checked = aodSingleClickBlockEnabled,
            onCheckedChange = onAodSingleClickBlockEnabledChange,
            hasDividerAbove = true,
        )
    }
}

@Composable
internal fun AodIntSliderRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    val hapticTick = rememberColorOsHapticTick()
    ColorOsSettingsSliderRow(
        title = title,
        enabled = enabled,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
    ) {
        FeatureSettingsSlider(
            value = value.toFloat(),
            onValueChange = { next ->
                val nextValue = next.roundToInt().coerceIn(0, 255)
                if (enabled && nextValue != value) {
                    hapticTick()
                    onValueChange(nextValue)
                }
            },
            enabled = enabled,
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AodFloatSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    val hapticTick = rememberColorOsHapticTick()
    ColorOsSettingsSliderRow(
        title = title,
        enabled = enabled,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
    ) {
        FeatureSettingsSlider(
            value = value,
            onValueChange = { next ->
                val nextValue = (next * 10f).roundToInt().div(10f).coerceIn(1.0f, 3.0f)
                if (enabled && nextValue != value) {
                    hapticTick()
                    onValueChange(nextValue)
                }
            },
            enabled = enabled,
            valueRange = 1.0f..3.0f,
            steps = 19,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
