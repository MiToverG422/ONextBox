package com.mi.onextbox.lsp

import kotlin.math.roundToInt

internal object AospVolumeSize {
    fun percent(value: Int) = value.coerceIn(50, 150)
    fun pixels(original: Int, requestedPercent: Int): Int =
        if (original <= 0) original else (original * (percent(requestedPercent) / 100f)).roundToInt().coerceAtLeast(1)
}
