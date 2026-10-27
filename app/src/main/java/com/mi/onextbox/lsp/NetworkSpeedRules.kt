package com.mi.onextbox.lsp

internal object NetworkSpeedRules {
    data class Text(val number: String, val unit: String, val singleLine: Boolean)

    fun refreshDelay(original: Long, custom: Boolean, intervalMs: Int = 1000): Long =
        if (custom && original > 0L) intervalMs.coerceIn(500, 10000).toLong() else original

    fun fittedGlyphSize(requested: Float, measuredAtReferenceSize: Int, referenceSize: Float, available: Float): Float {
        if (measuredAtReferenceSize <= 0 || available <= 0f || !requested.isFinite() || requested <= 0f) return requested
        return requested.coerceAtMost(available * referenceSize / measuredAtReferenceSize)
    }

    fun fitSingleLine(size: Float, scaleX: Float, textWidth: Float, available: Float): Pair<Float, Float> {
        if (textWidth <= 0f || available <= 0f || !textWidth.isFinite() || !available.isFinite()) return size to scaleX
        val ratio = (available / textWidth).coerceAtMost(1f)
        return (size * ratio) to scaleX
    }

    fun text(number: String, unit: String, hidePerSecond: Boolean, hideUnits: Boolean, singleLine: Boolean): Text {
        val slash = unit.lastIndexOf('/')
        val measure = if (slash >= 0) unit.substring(0, slash) else unit
        val rate = if (slash >= 0) unit.substring(slash) else ""
        val suffix = (if (hideUnits) "" else measure.trim()) + (if (hidePerSecond) "" else rate.trim())
        val compact = singleLine || suffix.isEmpty()
        val display = if (compact && suffix.isNotEmpty()) {
            number + (if (suffix.startsWith('/')) "" else " ") + suffix
        } else number
        return Text(display, if (compact) "" else suffix, compact)
    }
}
