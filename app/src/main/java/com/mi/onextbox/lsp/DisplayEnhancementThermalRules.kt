package com.mi.onextbox.lsp

internal object DisplayEnhancementThermalRules {
    fun canBypassSuperResolution(
        supported: Boolean, screenOn: Boolean, lowPower: Boolean, split: Boolean, throttled: Boolean,
    ): Boolean = supported && screenOn && !lowPower && !split && !throttled
}
