package com.mi.onextbox.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AssistantFeaturesPage(
    assistantPowerMode: Int,
    onAssistantPowerModeChange: (Int) -> Unit,
    assistantGestureCircleEnabled: Boolean,
    onAssistantGestureCircleEnabledChange: (Boolean) -> Unit,
    assistantGestureCircleC17Enabled: Boolean,
    onAssistantGestureCircleC17EnabledChange: (Boolean) -> Unit,
    assistantNativePowerEnabled: Boolean,
    onAssistantNativePowerEnabledChange: (Boolean) -> Unit,
    assistantNativeCircleEnabled: Boolean,
    onAssistantNativeCircleEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var okGoogleHotwordCompatibilityEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isOkGoogleHotwordCompatibilityEnabled(context))
    }
    var assistantInternationalPowerChordEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isAssistantInternationalPowerChordEnabled(context))
    }
    var showHiddenAssistantContent by remember { mutableStateOf(false) }
    val nativeAssistantFeaturesEnabled =
        assistantNativePowerEnabled && assistantNativeCircleEnabled

    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_assistant_native_combined_title),
            summary = stringResource(R.string.feature_assistant_native_combined_summary),
            checked = nativeAssistantFeaturesEnabled,
            onCheckedChange = { enabled ->
                onAssistantNativePowerEnabledChange(enabled)
                onAssistantNativeCircleEnabledChange(enabled)
            },
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_assistant_international_power_chord_title),
            summary = stringResource(R.string.feature_assistant_international_power_chord_summary),
            checked = assistantInternationalPowerChordEnabled,
            onCheckedChange = { enabled ->
                assistantInternationalPowerChordEnabled = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setAssistantInternationalPowerChordEnabled(context, enabled)
                    }
                }
            },
            enabled = nativeAssistantFeaturesEnabled,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.feature_assistant_default_settings_title),
            summary = stringResource(R.string.feature_assistant_default_settings_summary),
            onClick = { openDefaultAssistantSettings(context) },
            showArrow = true,
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_assistant_ok_google_compatibility_title),
            summary = stringResource(R.string.feature_assistant_ok_google_compatibility_summary),
            checked = okGoogleHotwordCompatibilityEnabled,
            onCheckedChange = { enabled ->
                okGoogleHotwordCompatibilityEnabled = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setOkGoogleHotwordCompatibilityEnabled(context, enabled)
                    }
                }
            },
            hasDividerAbove = true,
        )
    }

    val hiddenAssistantRow: @Composable (Int) -> Unit = { index ->
        when (index) {
            1 -> SettingsToggleRow(
                title = stringResource(R.string.feature_assistant_power_title),
                summary = stringResource(R.string.feature_assistant_power_summary),
                checked = assistantPowerMode != LspConfig.ASSISTANT_POWER_MODE_NONE,
                onCheckedChange = { enabled ->
                    onAssistantPowerModeChange(
                        if (enabled) LspConfig.ASSISTANT_POWER_MODE_SYSTEM_DEFAULT
                        else LspConfig.ASSISTANT_POWER_MODE_NONE,
                    )
                },
                hasDividerAbove = true,
                hasDividerBelow = true,
            )
            2 -> SettingsToggleRow(
                title = stringResource(R.string.feature_assistant_gesture_title),
                summary = stringResource(R.string.feature_assistant_gesture_summary),
                checked = assistantGestureCircleEnabled,
                onCheckedChange = onAssistantGestureCircleEnabledChange,
                hasDividerAbove = true,
                hasDividerBelow = true,
            )
            3 -> SettingsToggleRow(
                title = stringResource(R.string.feature_assistant_gesture_c17_title),
                summary = stringResource(R.string.feature_assistant_gesture_c17_summary),
                checked = assistantGestureCircleC17Enabled,
                onCheckedChange = onAssistantGestureCircleC17EnabledChange,
                hasDividerAbove = true,
                hasDividerBelow = true,
            )
            4 -> SettingsCardRow(
                title = stringResource(R.string.feature_assistant_legacy_notice_title),
                summary = stringResource(R.string.feature_assistant_legacy_notice_summary),
                hasDividerAbove = true,
            )
            else -> Unit
        }
    }
    val expressive = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
    SettingsGroup {
        FeatureAnimatedSegmentPosition(index = 0, count = if (showHiddenAssistantContent) 5 else 1) {
            SettingsToggleRow(
                title = stringResource(R.string.feature_assistant_show_hidden_content),
                summary = "",
                checked = showHiddenAssistantContent,
                onCheckedChange = { showHiddenAssistantContent = it },
                hasDividerBelow = showHiddenAssistantContent,
            )
        }
        if (expressive) {
            (1..4).forEach { index ->
                FeatureExpandableVisibility(visible = showHiddenAssistantContent) {
                    FeatureSegmentPosition(index = index, count = 5) {
                        hiddenAssistantRow(index)
                    }
                }
            }
        } else {
            FeatureExpandableVisibility(visible = showHiddenAssistantContent) {
                Column {
                    (1..4).forEach { index ->
                        SettingsDivider()
                        hiddenAssistantRow(index)
                    }
                }
            }
        }
    }
}

internal fun openDefaultAssistantSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    )
    val opened = intents.any { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
    if (!opened) {
        Toast.makeText(
            context,
            context.getString(R.string.feature_assistant_default_settings_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
