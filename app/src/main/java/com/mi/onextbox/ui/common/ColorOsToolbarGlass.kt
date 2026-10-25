package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Original COUI bottom-bar material shared by search, toolbar and ordinary bottom bar. */
internal object ColorOsToolbarGlass {
    // COUI dark containers are translucent white, not opaque white surfaces.
    fun fallbackColor(container: Color, surface: Color): Color = container.compositeOver(surface)

    fun firstTint(darkTheme: Boolean): Int = if (darkTheme) 0x80666666.toInt() else 0xB3666666.toInt()
    fun luminosityTint(darkTheme: Boolean): Int = if (darkTheme) 0xB3262626.toInt() else 0x99F5F5F5.toInt()
    const val BlurRadiusPx: Float = 25f
    const val FallbackAlpha: Int = 255
}
