package com.mi.onextbox.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.mi.onextbox.ui.screens.FeatureLaunchOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeatureLaunchGeometryTest {
    private val origin = FeatureLaunchOrigin(
        left = 108f,
        top = 328f,
        width = 46f,
        height = 46f,
        restingLeft = 104f,
        restingTop = 324f,
        restingWidth = 54f,
        restingHeight = 54f,
        hitLeft = 84f,
        hitTop = 312f,
        hitWidth = 92f,
        hitHeight = 104f,
        pressScale = 0.85f,
        gridLeft = 16f,
        gridTop = 300f,
    )

    @Test fun returningIconAndHitTargetFollowUpwardScrolling() {
        val moved = origin.atGridPosition(Offset(16f, 180f))
        assertEquals(208f, moved.top, 0.001f)
        assertEquals(204f, moved.restingTop, 0.001f)
        assertEquals(192f, moved.hitTop, 0.001f)
        assertEquals(origin.left, moved.left, 0.001f)
    }

    @Test fun toolbarExpansionAndHorizontalMovementUseTheSameAnchor() {
        val moved = origin.atGridPosition(Offset(36f, 340f))
        assertEquals(128f, moved.left, 0.001f)
        assertEquals(124f, moved.restingLeft, 0.001f)
        assertEquals(104f, moved.hitLeft, 0.001f)
        assertEquals(368f, moved.top, 0.001f)
        assertEquals(352f, moved.hitTop, 0.001f)
    }

    @Test fun repeatedFramesDoNotAccumulateScrollOffsets() {
        val position = Offset(16f, 180f)
        val moved = origin.atGridPosition(position)
        assertEquals(moved, moved.atGridPosition(position))
        assertEquals(origin, moved.atGridPosition(Offset(16f, 300f)))
    }

    @Test fun rapidBackAndForthMatchesTheLatestGridPosition() {
        var moved = origin
        for (top in listOf(100f, 400f, -200f, 250f, -80f)) {
            moved = moved.atGridPosition(Offset(16f, top))
            assertEquals(origin.atGridPosition(Offset(16f, top)), moved)
        }
    }

    @Test fun movementPreservesIconDimensionsAndPressFeedback() {
        val moved = origin.atGridPosition(Offset(16f, -100f))
        assertEquals(origin.width, moved.width, 0f)
        assertEquals(origin.height, moved.height, 0f)
        assertEquals(origin.restingWidth, moved.restingWidth, 0f)
        assertEquals(origin.restingHeight, moved.restingHeight, 0f)
        assertEquals(origin.hitWidth, moved.hitWidth, 0f)
        assertEquals(origin.hitHeight, moved.hitHeight, 0f)
        assertEquals(origin.pressScale, moved.pressScale, 0f)
        assertEquals(-72f, moved.top, 0.001f)
    }

    @Test fun missingGridMeasurementPreservesTheCapturedOrigin() {
        assertSame(origin, origin.atGridPosition(null))
    }

    @Test fun workspaceScalingDoesNotMoveTheCanonicalGridAnchor() {
        val viewport = Rect(10f, 20f, 1090f, 2420f)
        val position = Offset(26f, 320f)
        for (scale in listOf(1f, 0.96f, 0.9f)) {
            val visualPosition = viewport.center + (position - viewport.center) * scale
            val canonicalPosition = unscaledFeatureGridPosition(visualPosition, viewport, scale)
            assertEquals(position.x, canonicalPosition.x, 0.001f)
            assertEquals(position.y, canonicalPosition.y, 0.001f)
        }
    }

    @Test fun scrollMovementIsAppliedBeforeWorkspaceScaling() {
        val viewport = Rect(0f, 0f, 1080f, 2400f)
        val initial = origin.toReturnOrigin(0.9f, viewport)
        val moved = origin.atGridPosition(Offset(16f, 180f)).toReturnOrigin(0.9f, viewport)
        assertEquals(-108f, moved.top - initial.top, 0.001f)
        assertEquals(initial.width, moved.width, 0.001f)
        assertEquals(initial.height, moved.height, 0.001f)
    }
}
