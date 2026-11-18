package com.mi.onextbox.ui.screens

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.AospUiSettings
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow

@Composable
internal fun AospChargingSettingsGroup() {
    if (Build.VERSION.SDK_INT < 37) return
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(AospUiSettings.chargingKeys, AospUiSettings::state)
    val state = editor.state
    SettingsSection(stringResource(R.string.aosp_ui_charging_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row(AospUiSettings.CHARGING_EFFECT) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.aosp_ui_charging_effect_title), summary = "", checked = state.chargingEffect,
                interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { value -> editor.save(AospUiSettings.CHARGING_EFFECT, state.copy(chargingEffect = value)) {
                    AospUiSettings.set(context, AospUiSettings.CHARGING_EFFECT, value)
                } })
        }
        row(AospUiSettings.CHARGING_SOUND) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.aosp_ui_charging_sound_title), summary = "", checked = state.chargingSound,
                interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { value -> editor.save(AospUiSettings.CHARGING_SOUND, state.copy(chargingSound = value)) {
                    AospUiSettings.set(context, AospUiSettings.CHARGING_SOUND, value)
                } })
        }
    }
}
