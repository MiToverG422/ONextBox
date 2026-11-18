package com.mi.onextbox.lsp

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF


internal class LauncherIosFlyingIconBounds {
    private val inverseApp = Matrix()
    private val inverseIcon = Matrix()
    private val iconToApp = Matrix()
    private val projective = Matrix()
    private val fitted = RectF()
    private val source = FloatArray(8)
    private val target = FloatArray(8)
    private val values = FloatArray(9)
    private val projection = LauncherIosIconProjection()
    val matrix = FloatArray(16)

    fun fit(
        iconCrop: Rect, iconAffine: Matrix, appCrop: Rect, appAffine: Matrix,
        radius: Float, deformation: LauncherIosDeformation?, attachment: Float = 1f,
    ): Boolean {
        if (iconCrop.isEmpty || appCrop.isEmpty ||
            !appAffine.invert(inverseApp) || !iconAffine.invert(inverseIcon)) return false
        iconToApp.setConcat(inverseApp, iconAffine)
        fitted.set(iconCrop)
        iconToApp.mapRect(fitted)
        val inset = (radius.coerceAtLeast(0f) * .5f + 1f)
            .coerceAtMost(minOf(appCrop.width(), appCrop.height()) * .28f)
        val left = appCrop.left + inset
        val right = appCrop.right - inset
        val top = appCrop.top + inset
        val bottom = appCrop.bottom - inset
        if (fitted.isEmpty || !fitted.width().isFinite() || !fitted.height().isFinite()) return false
        val scale = minOf(1f, (right - left) / fitted.width(), (bottom - top) / fitted.height())
        val halfWidth = fitted.width() * scale * .5f
        val halfHeight = fitted.height() * scale * .5f
        val centerX = fitted.centerX().coerceIn(left + halfWidth, maxOf(left + halfWidth, right - halfWidth))
        val centerY = fitted.centerY().coerceIn(top + halfHeight, maxOf(top + halfHeight, bottom - halfHeight))
        corners(source, iconCrop.left.toFloat(), iconCrop.top.toFloat(), iconCrop.right.toFloat(), iconCrop.bottom.toFloat())
        corners(target, centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
        if (deformation != null && !projection.deform(target, deformation, appCrop.left.toFloat(), appCrop.top.toFloat(),
                appCrop.width().toFloat(), appCrop.height().toFloat(), left, top, right, bottom)) return false
        appAffine.mapPoints(target)
        inverseIcon.mapPoints(target)
        val weight = attachment.coerceIn(0f, 1f)
        for (index in target.indices) target[index] = source[index] + (target[index] - source[index]) * weight
        if (target.any { !it.isFinite() } || !projective.setPolyToPoly(source, 0, target, 0, 4)) return false
        projective.getValues(values)
        matrix.fill(0f)
        matrix[0] = values[0]
        matrix[1] = values[1]
        matrix[3] = values[2]
        matrix[4] = values[3]
        matrix[5] = values[4]
        matrix[7] = values[5]
        matrix[10] = 1f
        matrix[12] = values[6]
        matrix[13] = values[7]
        matrix[15] = values[8]
        return values.all { it.isFinite() }
    }

    private fun corners(out: FloatArray, left: Float, top: Float, right: Float, bottom: Float) {
        out[0] = left
        out[1] = top
        out[2] = right
        out[3] = top
        out[4] = right
        out[5] = bottom
        out[6] = left
        out[7] = bottom
    }
}
