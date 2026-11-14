package com.mi.onextbox.lsp

internal data class FaceAuthCapsuleBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun offset(x: Float, y: Float) = FaceAuthCapsuleBounds(left + x, top + y, right + x, bottom + y)

    fun toward(target: FaceAuthCapsuleBounds, amount: Float) = FaceAuthCapsuleBounds(
        left + (target.left - left) * amount, top + (target.top - top) * amount,
        right + (target.right - right) * amount, bottom + (target.bottom - bottom) * amount,
    )
}

internal data class FaceAuthCapsuleNativeLayout(
    val top: Float,
    val height: Float,
    val minWidth: Float,
    val radius: Float,
    val centerX: Float? = null,
    val hole: FaceAuthCapsuleBounds? = null,
) {
    fun offset(x: Float, y: Float) = copy(top = top + y, centerX = centerX?.plus(x), hole = hole?.offset(x, y))
}

/** Mirrors the plugin's centered child layout inside its camera-adjusted parent padding. */
internal object FaceAuthCapsulePosition {
    fun fromNativeArea(
        area: FaceAuthCapsuleBounds, height: Float, minWidth: Float, radius: Float,
        blankWidth: Float?, blankTop: Float?, blankBottom: Float?,
    ): FaceAuthCapsuleNativeLayout? {
        if (!listOf(area.left, area.top, area.right, area.bottom, height, minWidth, radius).all { it.isFinite() } ||
            area.width <= 0f || height <= 0f || height > area.height || minWidth <= 0f || radius < 0f) return null
        val top = area.centerY - height / 2f
        val hole = if (blankWidth != null && blankTop != null && blankBottom != null &&
            blankWidth.isFinite() && blankWidth > 0f && blankWidth <= minWidth &&
            blankTop >= 0f && blankBottom > blankTop && blankBottom <= height) {
            FaceAuthCapsuleBounds(area.centerX - blankWidth / 2f, top + blankTop,
                area.centerX + blankWidth / 2f, top + blankBottom)
        } else null
        return FaceAuthCapsuleNativeLayout(top, height, minWidth, minOf(radius, height / 2f), area.centerX, hole)
    }

    fun fallback(
        width: Float, statusHeight: Float, preferredHeight: Float, minWidth: Float, radius: Float,
        camera: FaceAuthCapsuleBounds?, holeSize: Float, safeGap: Float,
    ): FaceAuthCapsuleNativeLayout {
        // The OEM keeps the cloud centered even on phones with a corner camera.
        val cx = width / 2f
        val central = camera?.takeIf { kotlin.math.abs(it.centerX - cx) <= minWidth / 2f }
        val holeHeight = central?.height ?: holeSize
        val height = maxOf(preferredHeight, holeHeight + safeGap)
            .coerceAtMost(maxOf(statusHeight - 3f, holeHeight + safeGap))
        val top = ((camera?.centerY ?: statusHeight / 2f) - height / 2f)
            .coerceIn(3f, maxOf(3f, statusHeight - height))
        val hole = central ?: FaceAuthCapsuleBounds(cx - holeSize / 2f,
            top + (height - holeSize) / 2f, cx + holeSize / 2f, top + (height + holeSize) / 2f)
        return FaceAuthCapsuleNativeLayout(top, height, minWidth, minOf(radius, height / 2f), cx, hole)
    }
}

/** Reserves the native lockscreen content's space for the entire authentication sequence. */
internal data class FaceAuthCapsuleLayout(
    val expandedHeight: Float,
    val faceOffsetX: Float,
    val faceCenterY: Float,
    val iconScale: Float,
    val vertical: Boolean,
) {
    companion object {
        fun resolve(top: Float, height: Float, density: Float, contentTop: Float?, windowHeight: Float): FaceAuthCapsuleLayout {
            val expandedHeight = height + 52f * density
            val safeBottom = minOf(contentTop ?: 0f, windowHeight) - 6f * density
            val vertical = top + expandedHeight * 1.025f <= safeBottom
            return if (vertical) FaceAuthCapsuleLayout(
                expandedHeight, 0f, top + height + 26f * density, 0.78f, true,
            ) else FaceAuthCapsuleLayout(
                height, -40f * density, top + height / 2f,
                ((height / density - 10f) / 36f).coerceIn(0.35f, 0.72f), false,
            )
        }
    }
}
