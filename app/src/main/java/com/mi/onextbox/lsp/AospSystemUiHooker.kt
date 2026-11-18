package com.mi.onextbox.lsp

import android.os.Build

internal object AospSystemUiHooker {
    fun hook(loader: ClassLoader) {
        if (Build.VERSION.SDK_INT < 37) return
        if (AospUiSettings.enabledXposed(AospUiSettings.CHARGING_EFFECT) || AospUiSettings.enabledXposed(AospUiSettings.CHARGING_SOUND))
            AospChargingHooker.hook(loader)
    }
}
