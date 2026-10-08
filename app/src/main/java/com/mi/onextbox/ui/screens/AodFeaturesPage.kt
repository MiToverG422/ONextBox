package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.FeatureSliderRules
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
        FeatureSliderRow(
            heading = stringResource(R.string.feature_aod_dark_brightness_label),
            currentValue = if (aodInitDarkBrightness == FeatureSliderRules.SYSTEM_DEFAULT) {
                stringResource(R.string.feature_slider_current_default)
            } else stringResource(R.string.feature_slider_current_value, aodInitDarkBrightness.toString()),
            value = if (aodInitDarkBrightness == FeatureSliderRules.SYSTEM_DEFAULT) {
                FeatureSliderRules.DARK_BRIGHTNESS_PREVIEW.toFloat()
            } else aodInitDarkBrightness.toFloat(),
            valueRange = 0f..255f,
            steps = 254,
            onValueChange = { onAodInitDarkBrightnessChange(it.roundToInt()) },
            onReset = { onAodInitDarkBrightnessChange(FeatureSliderRules.SYSTEM_DEFAULT) },
            enabled = aodEnhanceEnabled,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        FeatureSliderRow(
            heading = stringResource(R.string.feature_aod_bright_brightness_label),
            currentValue = if (aodInitBrightBrightness == FeatureSliderRules.SYSTEM_DEFAULT) {
                stringResource(R.string.feature_slider_current_default)
            } else stringResource(R.string.feature_slider_current_value, aodInitBrightBrightness.toString()),
            value = if (aodInitBrightBrightness == FeatureSliderRules.SYSTEM_DEFAULT) {
                FeatureSliderRules.BRIGHT_BRIGHTNESS_PREVIEW.toFloat()
            } else aodInitBrightBrightness.toFloat(),
            valueRange = 0f..255f,
            steps = 254,
            onValueChange = { onAodInitBrightBrightnessChange(it.roundToInt()) },
            onReset = { onAodInitBrightBrightnessChange(FeatureSliderRules.SYSTEM_DEFAULT) },
            enabled = aodEnhanceEnabled,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        FeatureSliderRow(
            heading = stringResource(R.string.feature_aod_multiplier_label),
            currentValue = if (FeatureSliderRules.normalizeMultiplier(aodRunningBrightnessMultiplier) == FeatureSliderRules.SYSTEM_DEFAULT_MULTIPLIER) {
                stringResource(R.string.feature_slider_current_default)
            } else stringResource(R.string.feature_slider_current_value, formatAodMultiplier(aodRunningBrightnessMultiplier)),
            value = if (aodRunningBrightnessMultiplier == FeatureSliderRules.SYSTEM_DEFAULT_MULTIPLIER) 1f
                else aodRunningBrightnessMultiplier,
            valueRange = 1f..3f,
            steps = 19,
            onValueChange = { onAodRunningBrightnessMultiplierChange(FeatureSliderRules.multiplierFromSlider(it)) },
            onReset = { onAodRunningBrightnessMultiplierChange(FeatureSliderRules.SYSTEM_DEFAULT_MULTIPLIER) },
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
