package com.mi.onextbox.lsp

/** Visual-only radius interpolation driven by the native bounds spring's scale. */
internal data class QsSliderDetailCornerTransition(
    val percent: Int,
    val width: Int,
    val height: Int,
    val restingRadius: Float,
    val startScale: Scale,
    val endScale: Scale,
    val startRadius: Float,
    val endRadius: Float,
) {
    fun radius(currentPercent: Int?, currentWidth: Int, currentHeight: Int,
               currentRestingRadius: Float, scale: Scale): Float? {
        if (currentPercent != percent || currentWidth != width || currentHeight != height ||
            currentRestingRadius != restingRadius || !scale.isValid()) return null
        val dx = endScale.x - startScale.x
        val dy = endScale.y - startScale.y
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared < 0.0000000001f) return endRadius
        // Keep the native spring's overshoot rather than flattening it at 0/1.
        // Both axes use the same bounds spring. Their average can stay constant
        // when one axis grows and the other shrinks, so project onto both deltas.
        val fraction = ((scale.x - startScale.x) * dx + (scale.y - startScale.y) * dy) / distanceSquared
        return (startRadius + (endRadius - startRadius) * fraction)
            .takeIf { it.isFinite() }?.coerceAtLeast(0f)
    }

    data class Scale(val x: Float, val y: Float) {
        val average get() = (x + y) / 2f
        fun isValid() = x.isFinite() && y.isFinite() && x > 0f && y > 0f
    }

    companion object {
        fun create(percent: Int, width: Int, height: Int, restingRadius: Float,
                   startScale: Scale, endScale: Scale, startRadius: Float,
                   endRadius: Float): QsSliderDetailCornerTransition? {
            if (QsTileCornerRules.customPercent(percent) == null || width <= 0 || height <= 0 ||
                listOf(restingRadius, startRadius, endRadius).any { !it.isFinite() || it < 0f } ||
                !startScale.isValid() || !endScale.isValid()) return null
            return QsSliderDetailCornerTransition(percent, width, height, restingRadius,
                startScale, endScale, startRadius, endRadius)
        }
    }
}
