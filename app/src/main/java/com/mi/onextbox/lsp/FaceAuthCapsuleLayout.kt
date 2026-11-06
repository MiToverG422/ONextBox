package com.mi.onextbox.lsp

internal data class FaceAuthCapsuleBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

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
)

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
