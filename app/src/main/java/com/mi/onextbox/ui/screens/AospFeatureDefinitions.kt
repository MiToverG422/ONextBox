package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.AospUiSettings
import com.mi.onextbox.lsp.LspPreferenceStore

internal object AospFeatureDefinitions {
    private fun option(key: String, title: Int) = SharedFeatureDefinitions.Toggle(
        title, 0, FeaturePageMode.SystemUiNative, { LspPreferenceStore.prefs(it).getBoolean(key, false) },
        { context, enabled -> AospUiSettings.set(context, key, enabled) }, preferenceKey = key)
    val all = listOf(
        option(AospUiSettings.CHARGING_EFFECT, R.string.aosp_ui_charging_effect_title),
        option(AospUiSettings.CHARGING_SOUND, R.string.aosp_ui_charging_sound_title),
    )
}
