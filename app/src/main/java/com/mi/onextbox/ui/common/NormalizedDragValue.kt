package com.mi.onextbox.ui.common

internal fun normalizedDragValue(value: Float, range: ClosedRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (!value.isFinite() || !span.isFinite() || span <= 0f) return 0f
    return (value / span).takeIf { it.isFinite() } ?: 0f
}
