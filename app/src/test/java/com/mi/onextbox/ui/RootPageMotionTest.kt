package com.mi.onextbox.ui

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootPageMotionTest {
    @Test fun reversalKeepsItsInitialPositionAndVelocity() {
        val motion = TargetBasedAnimation(RootPageSpring, Float.VectorConverter, 0.6f, 1f, -2f)
        assertEquals(0.6f, motion.getValueFromNanos(0), 0.0001f)
        assertEquals(-2f, motion.getVelocityVectorFromNanos(0).value, 0.0001f)
    }

    @Test fun partialReturnDoesNotCompressTheCurveIntoAFewFrames() {
        val full = TargetBasedAnimation(RootPageSpring, Float.VectorConverter, 0f, 1f)
        val partial = TargetBasedAnimation(RootPageSpring, Float.VectorConverter, 0.8f, 1f)
        assertTrue(partial.durationNanos > full.durationNanos * 0.5)
        assertTrue(partial.getValueFromNanos(80_000_000) < 0.99f)
    }

    @Test fun gestureStartsAtTheInterruptedPositions() {
        assertEquals(0.6f, rootGesturePageOffset(0.6f, 1f, 0f), 0f)
        assertEquals(-0.15f, rootGesturePageOffset(-0.15f, 0f, 0f), 0f)
        assertEquals(0.8f, rootGesturePageOffset(0.6f, 1f, 0.5f), 0.0001f)
        assertEquals(-0.075f, rootGesturePageOffset(-0.15f, 0f, 0.5f), 0.0001f)
        assertEquals(1f, rootGesturePageOffset(0.6f, 1f, 1f), 0f)
        assertEquals(0f, rootGesturePageOffset(-0.15f, 0f, 1f), 0f)
    }
}
