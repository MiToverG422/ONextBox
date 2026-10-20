package com.mi.onextbox.ui.common

/** Scale the complete page, including popups, without changing the user's font-size preference. */
internal val AppUiStyle.pageDensityScale: Float
    get() = when (this) {
        AppUiStyle.ColorOs -> 1f
        AppUiStyle.Material3Expressive -> 0.95f
        AppUiStyle.Miuix -> 0.90f
    }
