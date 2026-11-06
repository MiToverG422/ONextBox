package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FaceAuthCapsuleMotionTest {
    private val curves = FaceAuthCapsuleMotion.Curves({ it }, { it * it * (3f - 2f * it) })
    private fun success(time: Long, opacity: Float = 0f) = FaceAuthCapsuleMotion.success(time, curves, opacity)

    @Test fun theLockOpensContinuouslyBeforeTheCapsuleRetracts() {
        assertEquals(0f, success(40).expansion, 0f)
        assertEquals(0f, success(340).unlock, 0f)
        assertTrue(success(540).unlock in 0.1f..0.9f)
        assertEquals(1f, success(740).unlock, 0f)
        assertEquals(0f, success(1000).retraction, 0f)
        assertTrue(success(1320).retraction > 0f)
        assertEquals(1f, success(FaceAuthCapsuleMotion.SUCCESS_DURATION).retraction, 0f)
    }

    @Test fun completionEndsTransparentAndAllFramesRemainBounded() {
        for (time in -50L..1900L step 10) {
            val frame = success(time)
            assertTrue(listOf(frame.opacity, frame.expansion, frame.retraction, frame.unlock,
                frame.contentOpacity).all { it in 0f..1f })
            assertTrue(frame.shapeStretch in 1f..1.025f)
            assertTrue(frame.contentScale in 0.88f..1.01f)
        }
        assertEquals(0f, success(FaceAuthCapsuleMotion.SUCCESS_DURATION).opacity, 0f)
    }

    @Test fun successfulAuthenticationDoesNotRestartTheOpacityOfAnExistingCapsule() {
        for (initial in listOf(0.2f, 0.7f, 1f)) for (time in 0L..120L step 5) {
            assertTrue(success(time, initial).opacity >= initial)
        }
        assertEquals(1f, success(0, 1f).opacity, 0f)
        assertEquals(0f, success(FaceAuthCapsuleMotion.SUCCESS_DURATION, 1f).opacity, 0f)
    }

    @Test fun stageBoundariesDoNotIntroduceVisibleJumps() {
        var previous = success(0)
        for (time in 1L..FaceAuthCapsuleMotion.SUCCESS_DURATION) {
            val current = success(time)
            val a = listOf(previous.opacity, previous.expansion, previous.retraction, previous.unlock,
                previous.contentOpacity, previous.contentScale, previous.shapeStretch)
            val b = listOf(current.opacity, current.expansion, current.retraction, current.unlock,
                current.contentOpacity, current.contentScale, current.shapeStretch)
            a.zip(b).forEach { (before, after) -> assertTrue("Motion discontinuity at " + time,
                kotlin.math.abs(after - before) < 0.025f) }
            previous = current
        }
    }

    @Test fun theUnlockedGlyphFadesBeforeTheCapsuleShrinksIntoTheCamera() {
        assertEquals(1f, success(1000).unlock, 0f)
        assertEquals(1f, success(1000).contentOpacity, 0f)
        assertTrue(success(1280).retraction > 0f)
        assertEquals(0f, success(1280).contentOpacity, 0f)
    }
}
