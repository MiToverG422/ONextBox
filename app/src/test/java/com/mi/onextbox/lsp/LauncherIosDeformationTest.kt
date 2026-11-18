package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LauncherIosDeformationTest {
    private fun sample(motion: LauncherIosGenieMotion, minimized: Double, time: Long, row: Double = .8,
                       opening: Boolean = true, intensity: Double = 1.0) =
        motion.update(minimized, .25, .12, row, intensity, opening, .24, .336, 1.0, time)

    @Test fun centeredSourceAndRestingEndpointsUseNativeGeometry() {
        assertNull(sample(LauncherIosGenieMotion(), .5, 1L, row = 0.0))
        assertNull(sample(LauncherIosGenieMotion(), .5, 1L, intensity = 0.0))
        assertNull(sample(LauncherIosGenieMotion(), 0.0, 1L))
        assertNull(sample(LauncherIosGenieMotion(), 1.0, 1L, opening = false))
    }

    @Test fun springShapeChangesDirectionAboveAndBelowCenter() {
        val upper = sample(LauncherIosGenieMotion(), .5, 1L, row = -.8)!!
        val lower = sample(LauncherIosGenieMotion(), .5, 1L)!!
        val inner = sample(LauncherIosGenieMotion(), .5, 1L, row = -.2)!!
        assertTrue(upper.topWidth < upper.bottomWidth)
        assertTrue(lower.bottomWidth < lower.topWidth)
        assertTrue(abs(upper.topOffset) > abs(inner.topOffset))
    }

    @Test fun springsKeepMovingAfterNativeBoundsLand() {
        val motion = LauncherIosGenieMotion()
        sample(motion, .05, 1L)
        val first = sample(motion, 0.0, 8_333_334L)!!
        val second = sample(motion, 0.0, 16_666_667L)!!
        assertTrue(abs(second.bottomOffset) < abs(first.bottomOffset))
        for (frame in 3..119) sample(motion, 0.0, frame * 8_333_333L)
        assertNull(sample(motion, 0.0, 120 * 8_333_333L))
    }

    @Test fun projectiveMatrixMapsBothEdgesOfAnOffsetCrop() {
        val shape = sample(LauncherIosGenieMotion(), .48, 1L, row = -.8)!!
        val matrix = FloatArray(16)
        val left = 25f
        val top = 130f
        val width = 720f
        val height = 1100f
        shape.writeMatrix(matrix, left, top, width, height)
        fun x(x: Float, y: Float) = (matrix[0] * x + matrix[1] * y + matrix[3]) /
            (matrix[13] * y + matrix[15])
        fun y(y: Float) = (matrix[5] * y + matrix[7]) / (matrix[13] * y + matrix[15])
        assertEquals(top + shape.topOffset * height, y(top), .001f)
        assertEquals(top + height + shape.bottomOffset * height, y(top + height), .001f)
        assertEquals(width * shape.topWidth, x(left + width, top) - x(left, top), .001f)
        assertEquals(width * shape.bottomWidth,
            x(left + width, top + height) - x(left, top + height), .001f)
    }

    @Test fun retargetKeepsTheDisplayedShapeEvenWhenTheHostChangesLeashAfterAPause() {
        val motion = LauncherIosGenieMotion()
        sample(motion, .9, 1L)
        sample(motion, .7, 16_666_667L)
        val displayed = sample(motion, .5, 33_333_334L)!!
        motion.rebaseClock(400_000_000L)
        val firstReversed = motion.update(.5, .25, .12, .8, 1.0, false, .24, .336, 1.0,
            400_000_000L, preserveState = true)
        assertEquals(displayed, firstReversed)
        val next = motion.update(.52, .25, .12, .8, 1.0, false, .24, .336, 1.0,
            408_333_333L, preserveState = true)!!
        assertTrue(abs(next.topWidth - displayed.topWidth) < .08f)
        assertTrue(abs(next.bottomWidth - displayed.bottomWidth) < .08f)
    }

    @Test fun duplicateAndLateHostSamplesCannotReverseOrClearTheCurrentShape() {
        val motion = LauncherIosGenieMotion()
        sample(motion, .9, 1L)
        val shape = sample(motion, .6, 16_666_667L)!!
        assertEquals(shape, sample(motion, .85, 16_666_667L, opening = false))
        assertEquals(shape, sample(motion, 1.0, 8_333_333L, opening = false))
    }

    @Test fun fullScreenLandingPreservesTheExistingShapeUntilTheFinalEightPercent() {
        val shape = LauncherIosDeformation(-.04f, -.06f, 1.08f, .88f, .46f, .54f)
        assertSame(shape, shape.settleAtFullscreen(.5))
        assertSame(shape, shape.settleAtFullscreen(.08))
        val halfway = shape.settleAtFullscreen(.04)!!
        assertEquals(-.02f, halfway.topOffset, 1e-6f)
        assertEquals(-.03f, halfway.bottomOffset, 1e-6f)
        assertEquals(1.04f, halfway.topWidth, 1e-6f)
        assertEquals(.94f, halfway.bottomWidth, 1e-6f)
        assertEquals(.48f, halfway.topCenter, 1e-6f)
        assertEquals(.52f, halfway.bottomCenter, 1e-6f)
    }

    @Test fun fullScreenCannotRetainARetargetedSpringsDisplacement() {
        val motion = LauncherIosGenieMotion()
        sample(motion, .7, 1L)
        sample(motion, .4, 16_666_667L)
        sample(motion, .6, 33_333_334L, opening = false)
        val residual = sample(motion, 0.0, 50_000_001L)!!
        assertNull(residual.settleAtFullscreen(0.0))
        var prior = Float.POSITIVE_INFINITY
        for (phase in listOf(.08, .07, .06, .04, .02, .005, .0001)) {
            val tail = residual.settleAtFullscreen(phase)!!
            val displacement = abs(tail.topOffset) + abs(tail.bottomOffset)
            assertTrue(displacement < prior)
            prior = displacement
        }
        assertTrue(prior < .00001f)
    }

}
