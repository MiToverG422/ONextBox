package com.mi.onextbox.lsp

internal class LauncherIosIconProjection {
    private val window = FloatArray(16)
    private val icon = FloatArray(16)
    private val bounds = FloatArray(8)
    private val centers = FloatArray(4)

    fun deform(points: FloatArray, shape: LauncherIosDeformation, appLeft: Float, appTop: Float,
        appWidth: Float, appHeight: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val centerX = (points[0] + points[4]) * .5f
        val centerY = (points[1] + points[5]) * .5f
        val width = points[2] - points[0]
        val height = points[7] - points[1]
        if (width <= 0f || height <= 0f || appWidth <= 0f || appHeight <= 0f) return false
        shape.writeMatrix(window, appLeft, appTop, appWidth, appHeight)
        shape.writeMatrix(icon, points[0], points[1], width, height)
        if (!map(window, centerX, centerY, centers, 0) || !map(icon, centerX, centerY, centers, 2)) return false
        val shiftX = centers[0] - centers[2]
        val shiftY = centers[1] - centers[3]
        for (index in 0..6 step 2) {
            if (!map(icon, points[index], points[index + 1], points, index)) return false
            points[index] += shiftX
            points[index + 1] += shiftY
        }
        bounds[0] = left; bounds[1] = top
        bounds[2] = right; bounds[3] = top
        bounds[4] = right; bounds[5] = bottom
        bounds[6] = left; bounds[7] = bottom
        for (index in 0..6 step 2) if (!map(window, bounds[index], bounds[index + 1], bounds, index)) return false
        var containment = 1f
        for (edge in 0..3) {
            val start = edge * 2
            val end = (start + 2) % 8
            val x = bounds[end] - bounds[start]
            val y = bounds[end + 1] - bounds[start + 1]
            val distance = x * (centers[1] - bounds[start + 1]) - y * (centers[0] - bounds[start])
            if (!distance.isFinite() || distance <= 0f) return false
            for (point in 0..6 step 2) {
                val outside = x * (points[point + 1] - bounds[start + 1]) - y * (points[point] - bounds[start])
                if (!outside.isFinite()) return false
                if (outside < 0f) containment = minOf(containment, distance / (distance - outside))
            }
        }
        if (containment < 1f) for (index in 0..6 step 2) {
            points[index] = centers[0] + (points[index] - centers[0]) * containment
            points[index + 1] = centers[1] + (points[index + 1] - centers[1]) * containment
        }
        return points.all(Float::isFinite)
    }

    private fun map(matrix: FloatArray, x: Float, y: Float, out: FloatArray, offset: Int): Boolean {
        val w = matrix[12] * x + matrix[13] * y + matrix[15]
        if (!w.isFinite() || w <= .00001f) return false
        out[offset] = (matrix[0] * x + matrix[1] * y + matrix[3]) / w
        out[offset + 1] = (matrix[4] * x + matrix[5] * y + matrix[7]) / w
        return out[offset].isFinite() && out[offset + 1].isFinite()
    }
}
