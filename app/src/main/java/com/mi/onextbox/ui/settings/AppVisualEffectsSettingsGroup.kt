package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppVisualEffect
import com.mi.onextbox.ui.common.LocalVisualEffectSettings
import com.mi.onextbox.ui.common.VisualEffectMode

@Composable
internal fun AppVisualEffectsSettingsGroup() {
    val settings = LocalVisualEffectSettings.current ?: return
    val labels = listOf(
        stringResource(R.string.theme_mode_system),
        stringResource(R.string.setting_visual_effect_off),
        stringResource(R.string.setting_visual_effect_on),
    )
    SettingsSection(title = stringResource(R.string.setting_visual_effects))
    SettingsGroup {
        AppVisualEffect.entries.forEachIndexed { index, effect ->
            if (index > 0) SettingsDivider()
            SettingsThemeTextDropdown(
                title = stringResource(when (effect) {
                    AppVisualEffect.BackgroundBlur -> R.string.setting_visual_background_blur
                    AppVisualEffect.Outline -> R.string.setting_visual_outline
                    AppVisualEffect.AnimationBlur -> R.string.setting_visual_animation_blur
                }),
                labels = labels,
                selectedIndex = settings.mode(effect).ordinal,
                onSelectedIndexChange = { settings.set(effect, VisualEffectMode.entries[it]) },
            )
        }
    }
}
