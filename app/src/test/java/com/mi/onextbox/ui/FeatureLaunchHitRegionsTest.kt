package com.mi.onextbox.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureLaunchHitRegionsTest {
    private val viewport = Rect(0f, 0f, 1080f, 2400f)

    @Test fun partiallyCoveredIconKeepsItsUncoveredUpperHitArea() {
        val hit = Rect(270f, 2140f, 540f, 2452f)
        val bar = Rect(120f, 2220f, 960f, 2352f)
        val result = featureLaunchHitRegions(hit, viewport, bar)
        assertEquals(listOf(Rect(270f, 2140f, 540f, 2220f), Rect(270f, 2352f, 540f, 2400f)), result)
        assertValidPartition(result, hit.intersect(viewport), bar)
    }

    @Test fun interiorOcclusionSplitsIntoFourDisjointRegionsWithoutLosingVisibleArea() {
        val hit = Rect(10f, 20f, 110f, 120f)
        val cut = Rect(30f, 40f, 80f, 90f)
        val result = featureLaunchHitRegions(hit, viewport, cut)
        assertEquals(4, result.size)
        assertValidPartition(result, hit, cut)
        assertEquals(7500f, result.sumOf { (it.width * it.height).toDouble() }.toFloat(), 0f)
    }

    @Test fun fullyCoveredOrOffscreenIconsHaveNoHitTargets() {
        val hit = Rect(300f, 2220f, 540f, 2352f)
        assertTrue(featureLaunchHitRegions(hit, viewport, Rect(120f, 2200f, 960f, 2360f)).isEmpty())
        assertTrue(featureLaunchHitRegions(Rect(0f, 2400f, 54f, 2454f), viewport, null).isEmpty())
        assertTrue(featureLaunchHitRegions(Rect(-100f, 0f, 0f, 100f), viewport, null).isEmpty())
    }

    @Test fun clipsEveryEdgeToTheViewportBeforeSubtractingTheBar() {
        val hit = Rect(-10f, -20f, 1100f, 2420f)
        val result = featureLaunchHitRegions(hit, viewport, Rect(0f, 2200f, 1080f, 2400f))
        assertEquals(listOf(Rect(0f, 0f, 1080f, 2200f)), result)
    }

    @Test fun touchingEdgesDoNotRemovePositiveArea() {
        val hit = Rect(10f, 20f, 110f, 120f)
        listOf(Rect(110f, 20f, 130f, 120f), Rect(10f, 120f, 110f, 140f),
            Rect(-10f, 20f, 10f, 120f), Rect(10f, 0f, 110f, 20f)).forEach {
            assertEquals(listOf(hit), featureLaunchHitRegions(hit, viewport, it))
        }
    }

    @Test fun invalidHitOrViewportCannotCreatePointerRegions() {
        val hit = Rect(10f, 20f, 110f, 120f)
        val invalid = listOf(Rect.Zero, Rect(100f, 0f, 0f, 100f),
            Rect(Float.NaN, 0f, 100f, 100f), Rect(0f, 0f, Float.POSITIVE_INFINITY, 100f),
            Rect(-Float.MAX_VALUE, 0f, Float.MAX_VALUE, 100f))
        invalid.forEach {
            assertTrue(featureLaunchHitRegions(it, viewport, null).isEmpty())
            assertTrue(featureLaunchHitRegions(hit, it, null).isEmpty())
        }
    }

    @Test fun absentOrDisposedBarLeavesTheVisibleHitAreaIntact() {
        val hit = Rect(10f, 20f, 110f, 120f)
        assertEquals(listOf(hit), featureLaunchHitRegions(hit, viewport, null))
        assertEquals(listOf(hit), featureLaunchHitRegions(hit, viewport, Rect.Zero))
    }

    private fun assertValidPartition(regions: List<Rect>, visible: Rect, cut: Rect) {
        assertTrue(regions.size <= 4)
        regions.forEachIndexed { index, region ->
            assertTrue(region.width > 0f && region.height > 0f)
            assertTrue(region.left >= visible.left && region.top >= visible.top)
            assertTrue(region.right <= visible.right && region.bottom <= visible.bottom)
            assertFalse(region.overlaps(cut))
            regions.drop(index + 1).forEach { assertFalse(region.overlaps(it)) }
        }
    }
}
