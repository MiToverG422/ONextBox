package com.mi.onextbox.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ColorOsScrollEntranceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eagerCardsDoNotAnimateWhenThePageOpens() {
        assertInitialPlacement(lazy = false)
    }

    @Test fun lazyCardsDoNotAnimateWhenThePageOpens() {
        assertInitialPlacement(lazy = true)
    }

    @Test fun eagerCardsEnterInBothDirectionsAndFinishAfterScrollingStops() {
        assertTouchEntrance(lazy = false)
    }

    @Test fun lazyCardsEnterInBothDirectionsAndFinishAfterScrollingStops() {
        assertTouchEntrance(lazy = true)
    }

    @Test fun materialEagerCardsKeepTheirOriginalPlacementWhileScrolling() {
        assertMaterialPlacement(lazy = false)
    }

    @Test fun materialLazyCardsKeepTheirOriginalPlacementWhileScrolling() {
        assertMaterialPlacement(lazy = true)
    }

    @Test fun replacingEagerCardsWhileIdleDoesNotReplayTheEntrance() {
        assertIdleReplacement(lazy = false)
    }

    @Test fun replacingLazyCardsWhileIdleDoesNotReplayTheEntrance() {
        assertIdleReplacement(lazy = true)
    }

    @Test fun eagerBottomEntranceStartsBehindTheOverlayNotAtTheContentPadding() {
        assertPaddedBoundary(lazy = false, fromTop = false)
    }

    @Test fun lazyBottomEntranceStartsBehindTheOverlayNotAtTheContentPadding() {
        assertPaddedBoundary(lazy = true, fromTop = false)
    }

    @Test fun eagerTopEntranceStartsBehindTheOverlayNotAtTheContentPadding() {
        assertPaddedBoundary(lazy = false, fromTop = true)
    }

    @Test fun lazyTopEntranceStartsBehindTheOverlayNotAtTheContentPadding() {
        assertPaddedBoundary(lazy = true, fromTop = true)
    }

    @Test fun disablingTheHostKeepsEagerCardsAtTheirScrolledPositions() {
        assertDisabledEntrance(lazy = false, hostEnabled = false, preferenceEnabled = true)
    }

    @Test fun disablingTheGlobalPreferenceKeepsLazyCardsAtTheirScrolledPositions() {
        assertDisabledEntrance(lazy = true, hostEnabled = true, preferenceEnabled = false)
    }

    @Test fun disablingThePreferenceDuringPlaybackResetsCardsAndCanBeEnabledAgain() {
        val harness = showCards(lazy = true)
        val pinned = compose.onNodeWithTag(PinnedTag).getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        beginDrag(upward = true)
        finishDrag(upward = true)
        compose.runOnIdle {
            assertFalse(harness.isScrolling())
            assertTrue("An entrance must be playing before the preference is disabled",
                harness.entrance.offsetFor(5) > 0f)
            harness.preferenceEnabled.value = false
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertEquals("Disabling the preference must immediately clear the running entrance", 0f,
                harness.entrance.offsetFor(5), 0.001f)
        }
        assertRenderedTop(harness, index = 5, key = 5)
        compose.runOnIdle { harness.preferenceEnabled.value = true }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertEquals("Enabling while idle must not replay cards that are already visible", 0f,
                harness.entrance.offsetFor(5), 0.001f)
        }
        beginDrag(upward = false)
        compose.runOnIdle {
            assertTrue("New cards should animate again after the preference is re-enabled",
                harness.entrance.offsetFor(0) < 0f)
        }
        assertEquals("Changing the preference must not move fixed controls", pinned,
            compose.onNodeWithTag(PinnedTag).getUnclippedBoundsInRoot())
        finishDrag(upward = false)
        compose.mainClock.advanceTimeBy(420)
        compose.runOnIdle { assertEquals(0f, harness.entrance.offsetFor(0), 0.001f) }
    }

    @Test fun eagerEntranceUsesTheAncestorClipInsteadOfTheExtendedMeasuredHeight() {
        val harness = showCards(lazy = false, extendedHost = true)
        val viewport = compose.onNodeWithTag(ViewportTag).getUnclippedBoundsInRoot()
        val measured = compose.onNodeWithTag(MeasuredHostTag).getUnclippedBoundsInRoot()
        assertEquals(240f, (viewport.bottom - viewport.top).value, 0.001f)
        assertEquals(320f, (measured.bottom - measured.top).value, 0.001f)
        compose.mainClock.autoAdvance = false
        var pointer = Offset.Zero
        compose.onNodeWithTag(ViewportTag).performTouchInput {
            val start = Offset(center.x, height * 0.7f)
            pointer = start - Offset(0f, with(harness.density) { 80.dp.toPx() })
            down(start)
            moveTo(pointer, delayMillis = 16)
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            val top = with(harness.density) { harness.baseTop(4).toDp().value }
            assertTrue("The new card should be crossing the ancestor's actual bottom edge",
                top > 200f && top < 240f)
            assertTrue("The entrance must use the clipped 240 dp viewport, not its 320 dp measurement",
                harness.entrance.offsetFor(4) > 0f)
            assertEquals("Cards already inside the ancestor clip should not replay their entrance", 0f,
                harness.entrance.offsetFor(3), 0.001f)
        }
        assertRenderedTop(harness, index = 4, key = 4)
        compose.onNodeWithTag(ViewportTag).performTouchInput {
            moveTo(pointer, delayMillis = 120)
            up()
        }
        compose.mainClock.advanceTimeBy(420)
        compose.runOnIdle { assertEquals(0f, harness.entrance.offsetFor(4), 0.001f) }
    }

    private fun assertInitialPlacement(lazy: Boolean) {
        compose.mainClock.autoAdvance = false
        val harness = showCards(lazy)
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            (0..3).forEach { key ->
                assertEquals("Opening a page must not animate its visible cards", 0f,
                    harness.entrance.offsetFor(key), 0.001f)
            }
        }
        assertRenderedTop(harness, index = 0, key = 0)
    }

    private fun assertTouchEntrance(lazy: Boolean) {
        val harness = showCards(lazy)
        val pinned = compose.onNodeWithTag(PinnedTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        beginDrag(upward = true)

        compose.runOnIdle {
            assertTrue("A new card entering from below should start below its final position",
                harness.entrance.offsetFor(5) > 0f)
            assertEquals("A card that was already visible must not replay its entrance", 0f,
                harness.entrance.offsetFor(3), 0.001f)
        }
        assertRenderedTop(harness, index = 5, key = 5)
        assertEquals("Pinned search and filter controls must not move with the cards", pinned,
            compose.onNodeWithTag(PinnedTag).getUnclippedBoundsInRoot())

        finishDrag(upward = true)
        compose.runOnIdle {
            assertFalse("The held touch should not leave a fling running", harness.isScrolling())
            assertTrue("Entrance playback should continue after the scroll becomes idle",
                abs(harness.entrance.offsetFor(5)) > 0.001f)
        }
        compose.mainClock.advanceTimeBy(420)
        compose.runOnIdle {
            assertEquals("The card should settle after the native 360 ms entrance", 0f,
                harness.entrance.offsetFor(5), 0.001f)
        }
        assertRenderedTop(harness, index = 5, key = 5)

        beginDrag(upward = false)
        compose.runOnIdle {
            assertTrue("A new card entering from above should start above its final position",
                harness.entrance.offsetFor(0) < 0f)
        }
        assertRenderedTop(harness, index = 0, key = 0)
        finishDrag(upward = false)
        compose.mainClock.advanceTimeBy(420)
        compose.runOnIdle {
            assertEquals("Returning to the top should also settle the card", 0f,
                harness.entrance.offsetFor(0), 0.001f)
        }
        assertEquals(pinned, compose.onNodeWithTag(PinnedTag).getUnclippedBoundsInRoot())
    }

    private fun assertMaterialPlacement(lazy: Boolean) {
        val harness = showCards(lazy, AppUiStyle.Material3Expressive)
        compose.mainClock.autoAdvance = false
        beginDrag(upward = true)
        compose.runOnIdle {
            (0 until CardCount).forEach { key ->
                assertEquals("Material cards must not receive the COUI entrance", 0f,
                    harness.entrance.offsetFor(key), 0.001f)
            }
        }
        assertRenderedTop(harness, index = 5, key = 5)
        finishDrag(upward = true)
    }

    private fun assertDisabledEntrance(lazy: Boolean, hostEnabled: Boolean, preferenceEnabled: Boolean) {
        val harness = showCards(lazy, hostEnabled = hostEnabled, preferenceEnabled = preferenceEnabled)
        compose.mainClock.autoAdvance = false
        beginDrag(upward = true)
        compose.runOnIdle {
            (0 until CardCount).forEach { key ->
                assertEquals("Disabled entrance must not add a render offset during a real drag", 0f,
                    harness.entrance.offsetFor(key), 0.001f)
            }
        }
        assertRenderedTop(harness, index = 5, key = 5)
        finishDrag(upward = true)
    }

    private fun assertIdleReplacement(lazy: Boolean) {
        val harness = showCards(lazy)
        compose.mainClock.autoAdvance = false
        beginDrag(upward = true)
        finishDrag(upward = true)
        compose.mainClock.advanceTimeBy(420)
        compose.runOnIdle {
            assertFalse(harness.isScrolling())
            harness.generation.intValue = 1
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            (0 until CardCount).forEach { index ->
                assertEquals("A refresh or filter replacement while idle must not animate", 0f,
                    harness.entrance.offsetFor(100 + index), 0.001f)
            }
        }
        assertRenderedTop(harness, index = 5, key = 105)
    }

    private fun assertPaddedBoundary(lazy: Boolean, fromTop: Boolean) {
        val harness = showCards(lazy, edgeOverlays = true)
        val fixed = listOf(PinnedTag, TopOverlayTag, BottomOverlayTag).associateWith { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        compose.mainClock.autoAdvance = false
        if (fromTop) {
            beginDrag(upward = true)
            finishDrag(upward = true)
            compose.mainClock.advanceTimeBy(420)
        }

        val key = if (fromTop) 0 else 3
        val firstDistance = with(harness.density) { (if (fromTop) 80.dp else (-52).dp).toPx() }
        var pointer = Offset.Zero
        compose.onNodeWithTag(ViewportTag).performTouchInput {
            val start = Offset(center.x, height * if (fromTop) 0.3f else 0.7f)
            pointer = start + Offset(0f, firstDistance)
            down(start)
            moveTo(pointer, delayMillis = 16)
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val before = compose.runOnIdle {
            val edge = with(harness.density) {
                (harness.baseTop(key) + if (fromTop) 64.dp.toPx() else 0f).toDp().value
            }
            if (fromTop) {
                assertTrue("The card should have entered the full viewport behind the top overlay",
                    edge > 0f && edge < 40f)
                assertTrue("Top entrance must start before reaching the padded content edge",
                    harness.entrance.offsetFor(key) < 0f)
            } else {
                assertTrue("The card should have entered the full viewport behind the bottom overlay",
                    edge > 200f && edge < 240f)
                assertTrue("Bottom entrance must start before reaching the padded content edge",
                    harness.entrance.offsetFor(key) > 0f)
            }
            abs(harness.entrance.offsetFor(key))
        }
        assertRenderedTop(harness, index = key, key = key)

        pointer += Offset(0f, with(harness.density) { (if (fromTop) 48.dp else (-48).dp).toPx() })
        compose.onNodeWithTag(ViewportTag).performTouchInput { moveTo(pointer, delayMillis = 16) }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            val edge = with(harness.density) {
                (harness.baseTop(key) + if (fromTop) 64.dp.toPx() else 0f).toDp().value
            }
            assertTrue("The card should now have crossed the overlay's inner edge",
                if (fromTop) edge > 40f else edge < 200f)
            val after = abs(harness.entrance.offsetFor(key))
            assertTrue("Crossing content padding must continue the same entrance, not replay it",
                after > 0f && after <= before)
        }
        fixed.forEach { (tag, bounds) ->
            assertEquals("The $tag overlay must stay fixed while a card enters", bounds,
                compose.onNodeWithTag(tag).getUnclippedBoundsInRoot())
        }
        compose.onNodeWithTag(ViewportTag).performTouchInput {
            moveTo(pointer, delayMillis = 120)
            up()
        }
        compose.mainClock.advanceTimeBy(420)
        compose.runOnIdle { assertEquals(0f, harness.entrance.offsetFor(key), 0.001f) }
    }

    private fun beginDrag(upward: Boolean) {
        compose.onNodeWithTag(ViewportTag).performTouchInput {
            val from = if (upward) 0.85f else 0.15f
            val to = if (upward) 0.15f else 0.85f
            down(Offset(center.x, height * from))
            moveTo(Offset(center.x, height * to), delayMillis = 16)
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun finishDrag(upward: Boolean) {
        compose.onNodeWithTag(ViewportTag).performTouchInput {
            val end = if (upward) 0.15f else 0.85f
            // Let touch velocity expire, then release without creating a fling.
            moveTo(Offset(center.x, height * end), delayMillis = 120)
            up()
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun assertRenderedTop(harness: Harness, index: Int, key: Int) {
        val viewport = compose.onNodeWithTag(ViewportTag).getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("card-$key", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val expectedOffset = compose.runOnIdle {
            val base = harness.baseTop(index, key)
            with(harness.density) { (base + harness.entrance.offsetFor(key)).toDp().value }
        }
        assertEquals("The card's rendered position should include its entrance offset",
            viewport.top.value + expectedOffset, card.top.value, 1f / harness.density.density)
    }

    private fun showCards(
        lazy: Boolean,
        style: AppUiStyle = AppUiStyle.ColorOs,
        edgeOverlays: Boolean = false,
        extendedHost: Boolean = false,
        hostEnabled: Boolean = true,
        preferenceEnabled: Boolean = true,
    ): Harness {
        val harness = Harness()
        harness.contentPadding = if (edgeOverlays) 40.dp else 0.dp
        harness.hostEnabled.value = hostEnabled
        harness.preferenceEnabled.value = preferenceEnabled
        compose.setContent {
            CompositionLocalProvider(
                LocalAppUiStyle provides style,
                LocalColorOsProgressiveCardAnimationEnabled provides harness.preferenceEnabled.value,
            ) {
                harness.density = LocalDensity.current
                if (lazy) {
                    val scroll = rememberLazyListState()
                    harness.lazy = scroll
                    harness.entrance = rememberColorOsScrollEntrance(scroll)
                } else {
                    val scroll = rememberScrollState()
                    harness.eager = scroll
                    harness.entrance = rememberColorOsScrollEntrance(scroll)
                }
                Column(Modifier.width(320.dp).height(296.dp)) {
                    Box(Modifier.fillMaxWidth().height(56.dp).background(Color.DarkGray).testTag(PinnedTag))
                    Box(Modifier.fillMaxWidth().height(240.dp).clipToBounds().testTag(ViewportTag)) {
                        val extension = if (extendedHost) Modifier.layout { measurable, constraints ->
                            val extra = 80.dp.roundToPx()
                            val child = measurable.measure(constraints.copy(
                                minHeight = constraints.minHeight + extra,
                                maxHeight = constraints.maxHeight + extra,
                            ))
                            layout(child.width, constraints.maxHeight) { child.place(0, 0) }
                        } else Modifier
                        ColorOsScrollEntranceHost(
                            state = harness.entrance,
                            modifier = Modifier.fillMaxSize().then(extension).testTag(MeasuredHostTag),
                            enabled = harness.hostEnabled.value,
                        ) {
                            val generation = harness.generation.intValue
                            if (lazy) {
                                LazyColumn(
                                    state = requireNotNull(harness.lazy),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = harness.contentPadding),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(CardCount, key = { generation * 100 + it }) { index ->
                                        TestCard(generation * 100 + index)
                                    }
                                }
                            } else {
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(requireNotNull(harness.eager))
                                        .padding(vertical = harness.contentPadding),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    repeat(CardCount) { index -> TestCard(generation * 100 + index) }
                                }
                            }
                            if (edgeOverlays) {
                                Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.TopCenter)
                                    .background(Color.DarkGray).testTag(TopOverlayTag))
                                Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter)
                                    .background(Color.DarkGray).testTag(BottomOverlayTag))
                            }
                        }
                    }
                }
            }
        }
        return harness
    }

    @Composable
    private fun TestCard(key: Int) {
        Box(Modifier.fillMaxWidth().height(64.dp).colorOsScrollEntrance(key)) {
            Box(Modifier.fillMaxSize().background(if (key % 2 == 0) Color.Blue else Color.Red)
                .testTag("card-$key"))
        }
    }

    private class Harness {
        lateinit var entrance: ColorOsScrollEntranceState
        lateinit var density: Density
        var eager: ScrollState? = null
        var lazy: LazyListState? = null
        var contentPadding = 0.dp
        val generation = mutableIntStateOf(0)
        val hostEnabled = mutableStateOf(true)
        val preferenceEnabled = mutableStateOf(true)
        fun isScrolling() = lazy?.isScrollInProgress ?: requireNotNull(eager).isScrollInProgress
        fun baseTop(index: Int, key: Int = index): Float {
            val padding = with(density) { contentPadding.toPx() }
            return lazy?.layoutInfo?.visibleItemsInfo?.first { it.key == key }?.offset?.toFloat()
                ?.plus(padding)
                ?: with(density) { (72.dp * index).toPx() } + padding - requireNotNull(eager).value
        }
    }

    private companion object {
        const val CardCount = 20
        const val ViewportTag = "entrance-viewport"
        const val PinnedTag = "pinned-search-and-filters"
        const val TopOverlayTag = "fixed-top-overlay"
        const val BottomOverlayTag = "fixed-bottom-overlay"
        const val MeasuredHostTag = "measured-scroll-host"
    }
}
