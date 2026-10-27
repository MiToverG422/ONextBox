package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class QsTileCornerRulesTest {
    @Test fun savedRadiusIsBounded() {
        assertEquals(-1, QsTileCornerRules.normalize(Int.MIN_VALUE))
        assertEquals(0, QsTileCornerRules.normalize(0))
        assertEquals(50, QsTileCornerRules.normalize(50))
        assertEquals(100, QsTileCornerRules.normalize(Int.MAX_VALUE))
    }
    @Test fun percentageScalesWithActualBoundsAndKeepsTheSameProportion() {
        assertEquals(25f, QsTileCornerRules.pixels(50, 300, 100)!!, 0f)
        assertEquals(75f, QsTileCornerRules.pixels(50, 900, 300)!!, 0f)
        assertEquals(12.5f, QsTileCornerRules.pixels(25, 100, 300)!!, 0f)
    }
    @Test fun radiusNeverExceedsHalfOfTheActualBackgroundShortSide() {
        assertEquals(50f, QsTileCornerRules.pixels(100, 300, 100)!!, 0f)
        assertEquals(50f, QsTileCornerRules.pixels(100, 100, 300)!!, 0f)
        assertEquals(51f, QsTileCornerRules.pixels(Int.MAX_VALUE, 102, 102)!!, 0f)
    }
    @Test fun zeroProducesSquareCornersEvenOnCircularTiles() {
        assertEquals(0f, QsTileCornerRules.pixels(0, 100, 100)!!, 0f)
    }
    @Test fun systemDefaultAndDisabledSettingsNeverProduceCustomGeometry() {
        assertNull(QsTileCornerRules.customPercent(-1))
        assertNull(QsTileCornerRules.pixels(-1, 100, 100))
        assertNull(QsTileCornerRules.customPercent(50, enabled = false))
        assertEquals(0, QsTileCornerRules.customPercent(0))
        assertEquals(100, QsTileCornerRules.customPercent(100))
    }
    @Test fun notYetLaidOutTilesRetainNativeGeometryUntilRealBoundsAreKnown() {
        assertNull(QsTileCornerRules.pixels(50, 0, 100))
        assertNull(QsTileCornerRules.pixels(50, 100, 0))
        assertNull(QsTileCornerRules.pixels(50, -10, 100))
        assertEquals(10f, QsTileCornerRules.pixels(50, 100, 40)!!, 0f)
    }
}
