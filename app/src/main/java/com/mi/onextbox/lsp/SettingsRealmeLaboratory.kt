package com.mi.onextbox.lsp

internal object SettingsRealmeLaboratory {
    val children = setOf(SettingsRealmeFeature.DcDimming, SettingsRealmeFeature.DualMusic,
        SettingsRealmeFeature.SleepCapsule, SettingsRealmeFeature.HeartRate)
    private val defaultSelected = setOf(SettingsRealmeFeature.DcDimming, SettingsRealmeFeature.DualMusic)

    fun selected(feature: SettingsRealmeFeature, read: (SettingsRealmeFeature, Boolean) -> Boolean): Boolean =
        when (feature) {
            // Before this switch existed, any explicitly enabled Lab entry exposed the native page.
            SettingsRealmeFeature.Laboratory -> read(feature, children.any { read(it, false) })
            else -> read(feature, feature in defaultSelected)
        }

    fun effective(feature: SettingsRealmeFeature, read: (SettingsRealmeFeature, Boolean) -> Boolean): Boolean =
        selected(feature, read) && (feature !in children || selected(SettingsRealmeFeature.Laboratory, read))
}
