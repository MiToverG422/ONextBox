package com.mi.onextbox.lsp

internal object ColorOs16RefreshRateRules {
    data class Mode(val id: Int, val width: Int, val height: Int, val rate: Float)

    fun modeId(modes: List<Mode>, width: Int, height: Int): Int? = modes.firstOrNull {
        it.id > 0 && kotlin.math.abs(it.rate - 165f) < 1f &&
            width > 0 && height > 0 && it.width == width && it.height == height
    }?.id
}
