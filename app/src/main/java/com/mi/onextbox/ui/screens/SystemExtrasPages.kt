package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.SystemExtrasSettings

internal fun systemExtrasPage(pkg: String): FeaturePageMode = when (pkg) {
    SystemExtrasSettings.WEATHER -> FeaturePageMode.Weather
    SystemExtrasSettings.PICKER -> FeaturePageMode.PhotoPicker
    SystemExtrasSettings.ENGINEER -> FeaturePageMode.EngineerMode
    "com.oplus.battery" -> FeaturePageMode.Battery
    else -> error("Unknown system feature package: $pkg")
}
