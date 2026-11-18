package com.mi.onextbox.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.mi.onextbox.ui.common.bottomTabs
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BottomBarCollapseUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: BottomBarCollapseState
    private var selections = 0
    private var backgroundTaps = 0
    private lateinit var contentScroll: ScrollState
    private lateinit var selected: MutableIntState
    private val liquidMode = mutableStateOf(false)

    private fun start(liquid: Boolean = false) {
        liquidMode.value = liquid
        compose.setContent {
            COUITheme {
                state = rememberBottomBarCollapseState()
                selected = remember { mutableIntStateOf(1) }
                val backdrop = rememberLayerBackdrop()
                val pager = rememberPagerState(initialPage = 1) { 3 }
                contentScroll = rememberScrollState()
                Box(Modifier.width(360.dp).height(520.dp).testTag("host")) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF15243B)).layerBackdrop(backdrop)
                        .nestedScroll(state.nestedScrollConnection).verticalScroll(contentScroll)
                        .testTag("scroll")) {
                        Box(Modifier.height(1800.dp).width(360.dp))
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 16.dp)
                        .size(56.dp).testTag("behindRight").clickable { backgroundTaps++ })
                    if (liquidMode.value) LiquidGlassBottomNavigationBar(
                        tabs = bottomTabs, selectedIndex = selected.intValue, pagerState = pager,
                        onTabSelected = { selections++; selected.intValue = it }, backdrop = backdrop,
                        collapseState = state, hideLabels = true, modifier = Modifier.align(Alignment.BottomCenter),
                    ) else BottomNavigationBar(
                        items = bottomTabs.mapIndexed { index, tab -> NavigationItem("Tab $index", tab.unselectedIcon) },
                        selectedIndex = selected.intValue, onItemSelected = { selections++; selected.intValue = it },
                        collapseState = state, hideLabels = true, modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(liquid: Boolean = false) = compose.onNodeWithTag(
        if (liquid) "liquidBottomBarSurface" else "standardBottomBarSurface",
    ).getUnclippedBoundsInRoot()

    @Test fun scrollingFoldsIntoALeftCircleAndTappingRestoresTheOriginalBar() {
        start()
        val expanded = bounds()
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(16f, bounds().left.value, .5f)
        assertEquals(56f, bounds().width.value, .5f)
        assertEquals(bounds().width.value, bounds().height.value, .5f)
        compose.onNodeWithTag("bottomBarFolder").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(expanded, bounds())
        assertEquals(0, selections)
        compose.onNodeWithTag("standardBottomBarSurface").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(1, selections)
    }

    @Test fun barDownSwipeAndBallUpSwipeDoNotSelectATab() {
        start()
        compose.onNodeWithTag("standardBottomBarSurface").performTouchInput {
            swipe(Offset(center.x, height * .1f), Offset(center.x, height * .9f), 300)
        }
        compose.waitForIdle()
        assertTrue(state.collapsed)
        compose.onNodeWithTag("bottomBarFolder").performTouchInput {
            swipe(Offset(center.x, height * .9f), Offset(center.x, height * .1f), 300)
        }
        compose.waitForIdle()
        assertFalse(state.collapsed)
        assertEquals(0, selections)
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("scroll").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertFalse(state.collapsed)
    }

    @Test fun reversingAnAnimationStartsFromItsCurrentGeometry() {
        compose.mainClock.autoAdvance = false
        start()
        val original = bounds()
        compose.runOnIdle { state.collapse() }
        var previous = original
        repeat(9) {
            compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
            val frame = bounds()
            assertTrue(frame.left <= previous.left + .5.dp)
            assertTrue(frame.left >= 15.5.dp)
            assertTrue(frame.width <= previous.width + .5.dp)
            previous = frame
        }
        val intermediate = bounds()
        assertTrue(intermediate.width < original.width && intermediate.width > 56.dp)
        compose.runOnIdle { state.expand() }
        assertEquals(intermediate, bounds())
        compose.mainClock.advanceTimeBy(112)
        compose.waitForIdle()
        assertTrue(bounds().width > intermediate.width)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(original, bounds())
    }

    @Test fun foldedSurfaceDoesNotInterceptThePreviousRightHandArea() {
        start()
        compose.runOnIdle { state.collapse() }
        compose.waitForIdle()
        compose.onNodeWithTag("behindRight").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, backgroundTaps)
        assertEquals(0, selections)
    }

    @Test fun liquidGlassAlsoMorphsAndRestoresItsOriginalGeometry() {
        start(liquid = true)
        val original = bounds(true)
        compose.runOnIdle { state.collapse() }
        compose.waitForIdle()
        assertEquals(16f, bounds(true).left.value, .5f)
        assertEquals(56f, bounds(true).width.value, .5f)
        assertEquals(bounds(true).width.value, bounds(true).height.value, .5f)
        compose.onNodeWithTag("behindRight").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, backgroundTaps)
        compose.onNodeWithTag("bottomBarFolder").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(original, bounds(true))
        assertEquals(0, selections)
    }

    @Test fun repeatedShortAndInitiallyDiagonalSwipesAlwaysFoldAndRestore() {
        start()
        repeat(8) { index ->
            compose.onNodeWithTag("standardBottomBarSurface").performTouchInput {
                val start = Offset(center.x, height * .2f)
                down(start)
                moveTo(start + Offset(height * .15f, height * .15f), delayMillis = 32)
                moveTo(start + Offset(height * .15f, height * .38f), delayMillis = 80)
                up()
            }
            compose.waitForIdle()
            assertTrue("fold $index", state.collapsed)
            if (index % 2 == 0) compose.onNodeWithTag("bottomBarFolder").performTouchInput { click() }
            else compose.onNodeWithTag("bottomBarFolder").performTouchInput {
                swipe(Offset(center.x, height * .7f), Offset(center.x, height * .3f), 180)
            }
            compose.waitForIdle()
            assertFalse("restore $index", state.collapsed)
        }
        assertEquals(0, selections)
    }

    @Test fun draggingBeyondTheEndOfThePageStillFoldsWithoutAReboundReopeningIt() {
        start()
        compose.runOnIdle { contentScroll.dispatchRawDelta(100_000f) }
        compose.waitForIdle()
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(state.collapsed)
        assertEquals(56f, bounds().width.value, .5f)
    }

    @Test fun changingTheMaterialKeepsExactlyTheSameBarSizeAndPosition() {
        start()
        val native = bounds()
        compose.runOnIdle { liquidMode.value = true }
        compose.waitForIdle()
        assertEquals(native, bounds(true))
        repeat(3) { index ->
            val icon = compose.onAllNodesWithTag("liquidBottomBarIcon-$index", useUnmergedTree = true)[0]
                .getUnclippedBoundsInRoot()
            assertEquals(24f, icon.width.value, .5f)
            assertEquals(24f, icon.height.value, .5f)
        }
    }

    @Test fun theSelectedLiquidGlassPillTracksAllThreeTabsInsideTheBar() {
        start(liquid = true)
        repeat(3) { index ->
            compose.runOnIdle { selected.intValue = index }
            compose.waitForIdle()
            val bar = bounds(true)
            val pill = compose.onNodeWithTag("liquidBottomBarSelection").getUnclippedBoundsInRoot()
            assertTrue(pill.left >= bar.left - .5.dp)
            assertTrue(pill.right <= bar.right + .5.dp)
            val expected = bar.left + 4.dp + (bar.width - 8.dp) / 3 * (index + .5f)
            assertEquals(expected.value, (pill.left + pill.right).value / 2f, .5f)
        }
    }
}
