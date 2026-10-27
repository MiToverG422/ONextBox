package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class QsCornerSizeSettingsTest {
    @Test fun allSixSizesStartIndependentlyWithTheSystemRadius() {
        val state = QsTileCornerSettings.State()
        assertFalse(state.enabled)
        assertEquals(listOf("1x1", "1x2", "2x1", "2x2", "4x1", "4x2"),
            QsTileCornerSettings.Size.entries.map { it.label })
        assertEquals(6, QsTileCornerSettings.Size.entries.map { it.key }.toSet().size)
        QsTileCornerSettings.Size.entries.forEach { size ->
            assertEquals(-1, state.radius(size))
            assertNull(QsTileCornerRules.customPercent(state.radius(size), enabled = true))
            assertTrue(size.key.contains("_percent_"))
        }
    }

    @Test fun editingOrResettingOneSizeDoesNotChangeAnother() {
        val sizes = QsTileCornerSettings.Size.entries
        var state = QsTileCornerSettings.State(enabled = true)
        sizes.forEachIndexed { index, size -> state = state.withRadius(size, index * 10) }
        sizes.forEachIndexed { index, size -> assertEquals(index * 10, state.radius(size)) }
        val reset = state.withRadius(sizes.first(), QsTileCornerRules.SYSTEM_DEFAULT)
        assertTrue(reset.enabled)
        assertEquals(-1, reset.radius(sizes.first()))
        assertNull(QsTileCornerRules.customPercent(reset.radius(sizes.first())))
        sizes.drop(1).forEach { assertEquals(state.radius(it), reset.radius(it)) }
        assertEquals(100, state.withRadius(sizes.first(), Int.MAX_VALUE).radius(sizes.first()))
    }

    @Test fun changingOneSizeDoesNotEnableAnyOtherSize() {
        val selected = QsTileCornerSettings.Size.TwoXOne
        val state = QsTileCornerSettings.State(enabled = true).withRadius(selected, 50)
        QsTileCornerSettings.Size.entries.forEach { size ->
            val percent = QsTileCornerRules.customPercent(state.radius(size), state.enabled)
            if (size == selected) assertEquals(50, percent) else assertNull(percent)
        }
    }

    @Test fun unsupportedSpansAndUnrelatedViewsRetainNativeShapes() {
        assertNull(QsTileCornerSettings.Size.fromSpan(3, 1))
        assertNull(QsTileCornerSettings.Size.fromSpan(0, 0))
        assertNull(QsCornerSizeResolver.fromClassNames(listOf("android.widget.FrameLayout")))
        assertNull(QsCornerSizeResolver.fromClassNames(listOf("some.app.OneXOne")))
        QsTileCornerSettings.Size.entries.forEach {
            assertEquals(it, QsTileCornerSettings.Size.fromSpan(it.columns, it.rows))
        }
    }

    @Test fun concreteMediaSliderAndTileClassesSelectTheirOwnSize() {
        assertEquals(QsTileCornerSettings.Size.OneXTwo, QsCornerSizeResolver.fromClassNames(
            listOf("com.oplus.systemui.plugins.qs.widget.OplusQsToggleSliderLayout")))
        assertEquals(QsTileCornerSettings.Size.FourXTwo, QsCornerSizeResolver.fromClassNames(
            listOf("com.oplus.systemui.qs.media.SepQsLargeMediaPanelView",
                "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView")))
        assertEquals(QsTileCornerSettings.Size.TwoXTwo, QsCornerSizeResolver.fromClassNames(
            listOf("com.oplus.systemui.qs.media.OplusQsNormalMediaPanelView")))
        assertEquals(QsTileCornerSettings.Size.FourXOne, QsCornerSizeResolver.fromClassNames(
            listOf("com.oplus.systemui.qs.scenario.ui.template.ScenarioTileViewFourXOne")))
    }
}
