package com.mi.onextbox.lsp

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class LauncherIosIconProjectionTest {
    private val projection = LauncherIosIconProjection()
    private val shape = LauncherIosDeformation(.06f, -.03f, .88f, .94f, .47f, .53f)
    private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
        floatArrayOf(left, top, right, top, right, bottom, left, bottom)

    @Test fun smallIconHasTheSameEdgeWidthRatioAsTheWindowRatherThanAnAlmostRigidSubrect() {
        val points = rect(450f, 900f, 550f, 1000f)
        assertTrue(projection.deform(points, shape, 0f, 0f, 1000f, 2200f, 1f, 1f, 999f, 2199f))
        val top = points[2] - points[0]
        val bottom = points[4] - points[6]
        assertEquals(.88f / .94f, top / bottom, .00002f)
        assertTrue(abs(top / bottom - 1f) > .05f)
    }

    @Test fun extremeCornerIconsStayInsideTheProjectedInsetWindow() {
        for ((width, height) in listOf(1000f to 2200f, 2200f to 1000f, 2800f to 2000f)) {
            assertCornerIconContainment(width, height)
        }
    }

    private fun assertCornerIconContainment(width: Float, height: Float) {
        val right = width - 100f
        val bottom = height - 100f
        val bounds = rect(100f, 100f, right, bottom)
        val matrix = FloatArray(16)
        shape.writeMatrix(matrix, 0f, 0f, width, height)
        for (index in 0..6 step 2) {
            val x = bounds[index]; val y = bounds[index + 1]
            val w = matrix[13] * y + matrix[15]
            bounds[index] = (x + matrix[1] * y + matrix[3]) / w
            bounds[index + 1] = (matrix[5] * y + matrix[7]) / w
        }
        for (left in listOf(100f, right - 150f)) for (top in listOf(100f, bottom - 150f)) {
            val points = rect(left, top, left + 150f, top + 150f)
            assertTrue(projection.deform(points, shape, 0f, 0f, width, height, 100f, 100f, right, bottom))
            for (edge in 0..3) {
                val a = edge * 2; val b = (a + 2) % 8
                for (p in 0..6 step 2) {
                    val cross = (bounds[b] - bounds[a]) * (points[p + 1] - bounds[a + 1]) -
                        (bounds[b + 1] - bounds[a + 1]) * (points[p] - bounds[a])
                    assertTrue("edge=$edge point=$p distance=$cross", cross >= -1f)
                }
            }
        }
    }

    @Test fun neutralDeformationDoesNotDistortTheIcon() {
        val original = rect(400f, 900f, 550f, 1050f)
        val points = original.copyOf()
        assertTrue(projection.deform(points, LauncherIosDeformation(0f, 0f, 1f, 1f, .5f, .5f),
            0f, 0f, 1000f, 2200f, 1f, 1f, 999f, 2199f))
        assertArrayEquals(original, points, .001f)
    }
}
