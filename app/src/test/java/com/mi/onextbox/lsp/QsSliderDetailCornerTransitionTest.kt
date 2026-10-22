package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class QsSliderDetailCornerTransitionTest {
    private val mainRadius = QsTileCornerRules.pixels(40, 100, 200)!!
    private val detailRadius = QsTileCornerRules.pixels(40, 300, 500)!!
    private fun scale(value: Float) = QsSliderDetailCornerTransition.Scale(value, value)

    private fun launch() = QsSliderDetailCornerTransition.create(
        40, 300, 500, detailRadius, scale(0.4f), scale(1f), mainRadius / 0.4f, detailRadius)!!

    private fun QsSliderDetailCornerTransition.at(currentScale: Float) =
        radius(40, 300, 500, detailRadius, scale(currentScale))!!

    @Test fun mainAndDetailUseTheSamePercentWithTheirOwnBounds() {
        assertEquals(20f, mainRadius, 0.0001f)
        assertEquals(60f, detailRadius, 0.0001f)
        assertEquals(mainRadius / 100, detailRadius / 300, 0.0001f)
    }

    @Test fun launchStartsAtTheTilesScreenRadiusAndEndsAtTheDetailsPercent() {
        val transition = launch()
        assertEquals(mainRadius, transition.at(0.4f) * 0.4f, 0.0001f)
        assertEquals(55f, transition.at(0.7f), 0.0001f)
        assertEquals(detailRadius, transition.at(1f), 0.0001f)
    }

    @Test fun closingReturnsToTheMainTilesRadiusWithoutASnap() {
        val transition = QsSliderDetailCornerTransition.create(
            40, 300, 500, detailRadius, scale(1f), scale(0.4f), detailRadius, mainRadius / 0.4f)!!
        assertEquals(detailRadius, transition.at(1f), 0.0001f)
        assertEquals(55f, transition.at(0.7f), 0.0001f)
        assertEquals(mainRadius, transition.at(0.4f) * 0.4f, 0.0001f)
    }

    @Test fun reversingAnAnimationStartsAtItsCurrentVisualRadius() {
        val current = launch().at(0.7f)
        val reverse = QsSliderDetailCornerTransition.create(
            40, 300, 500, detailRadius, scale(0.7f), scale(0.4f), current, mainRadius / 0.4f)!!
        assertEquals(current, reverse.at(0.7f), 0.0001f)
        assertEquals(mainRadius, reverse.at(0.4f) * 0.4f, 0.0001f)
    }

    @Test fun springOvershootRemainsContinuous() {
        assertTrue(launch().at(1.01f) > detailRadius)
        assertTrue(launch().at(1.01f) - launch().at(1f) < 1f)
    }

    @Test fun disablingOrChangingPercentOrGeometryDiscardsOldEndpoints() {
        val transition = launch()
        assertNull(transition.radius(null, 300, 500, detailRadius, scale(0.7f)))
        assertNull(transition.radius(-1, 300, 500, detailRadius, scale(0.7f)))
        assertNull(transition.radius(60, 300, 500, detailRadius, scale(0.7f)))
        assertNull(transition.radius(40, 500, 300, detailRadius, scale(0.7f)))
        assertNull(transition.radius(40, 300, 500, 50f, scale(0.7f)))
    }

    @Test fun zeroRadiusAndEqualScalesNeverDivideByZero() {
        val square = QsSliderDetailCornerTransition.create(0, 100, 100, 0f, scale(1f), scale(1f), 0f, 0f)!!
        assertEquals(0f, square.radius(0, 100, 100, 0f, scale(1f))!!, 0f)
    }

    @Test fun invalidEndpointsAndScalesAreRejected() {
        for (invalidScale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(QsSliderDetailCornerTransition.create(
                40, 300, 500, detailRadius, scale(invalidScale), scale(1f), mainRadius, detailRadius))
            assertNull(launch().radius(40, 300, 500, detailRadius, scale(invalidScale)))
        }
        assertNull(QsSliderDetailCornerTransition.create(
            -1, 300, 500, detailRadius, scale(0.4f), scale(1f), mainRadius, detailRadius))
        assertNull(QsSliderDetailCornerTransition.create(
            40, 300, 500, detailRadius, scale(0.4f), scale(1f), Float.NaN, detailRadius))
    }

    @Test fun oppositeAxisScalingStillInterpolatesWhenTheirAverageDoesNotChange() {
        val transition = QsSliderDetailCornerTransition.create(40, 300, 500, detailRadius,
            QsSliderDetailCornerTransition.Scale(0.5f, 1.5f), scale(1f), mainRadius, detailRadius)!!
        assertEquals(mainRadius, transition.radius(40, 300, 500, detailRadius,
            QsSliderDetailCornerTransition.Scale(0.5f, 1.5f))!!, 0.0001f)
        assertEquals(40f, transition.radius(40, 300, 500, detailRadius,
            QsSliderDetailCornerTransition.Scale(0.75f, 1.25f))!!, 0.0001f)
        assertEquals(detailRadius, transition.at(1f), 0.0001f)
    }

    @Test fun launchIncludesTheMainTilesPressedScale() {
        val pressedScale = 0.94f
        val startScale = 0.4f * pressedScale
        val transition = QsSliderDetailCornerTransition.create(40, 300, 500, detailRadius,
            scale(startScale), scale(1f), mainRadius * pressedScale / startScale, detailRadius)!!
        assertEquals(mainRadius * pressedScale, transition.at(startScale) * startScale, 0.0001f)
    }
}
