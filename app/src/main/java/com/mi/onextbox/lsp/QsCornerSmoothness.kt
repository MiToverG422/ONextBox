package com.mi.onextbox.lsp

/** Native OplusQsSmoothRoundUtil uses 1 for full corners and 3 for smooth QS corners. */
internal object QsCornerSmoothness {
    private const val FULL_CORNER_WEIGHT = 1f
    private const val SMOOTH_WEIGHT = 3f

    fun weight(native: Float?): Float =
        native?.takeIf { it.isFinite() && it > FULL_CORNER_WEIGHT } ?: SMOOTH_WEIGHT
}
