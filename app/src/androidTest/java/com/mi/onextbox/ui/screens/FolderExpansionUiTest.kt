package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SystemCustomizationSettings.Feature
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.layout.BottomBarHostHeight
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsPageSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FolderExpansionUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = LspPreferenceStore.prefs(context)
    private val flags = listOf(Feature.FolderLayout.key, Feature.FolderIconSize.key)
    private var original = emptyMap<String, Boolean?>()

    @Before fun setUpPreferences() {
        assumeTrue("Run on the isolated test application", context.packageName != "com.mi.onextbox")
        original = flags.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        prefs.edit().apply { flags.forEach { putBoolean(it, true) } }.commit()
        compose.mainClock.autoAdvance = false
    }

    private fun showFolderRows() {
        compose.setContent {
            COUITheme {
                SettingsGroup {
                    SystemCustomizationRows(Feature.FolderLayout, hasDividerBelow = true)
                    SystemCustomizationRows(Feature.FolderIconSize, hasDividerAbove = true, hasDividerBelow = true)
                    Box(Modifier.height(12.dp).testTag("following"))
                }
            }
        }
        advance(400)
    }

    @After fun restoreFlags() {
        prefs.edit().apply {
            original.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) }
        }.commit()
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun show(feature: Feature, visible: Boolean) = compose.runOnIdle {
        prefs.edit().putBoolean(feature.key, visible).apply()
    }

    private fun sliderTop(title: Int) = compose
        .onNodeWithText(context.getString(title), useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private fun followingTop() = compose.onNodeWithTag("following").getUnclippedBoundsInRoot().top.value

    @Test fun layoutSlidersStayAlignedWhileCollapsingAndReversing() {
        showFolderRows()
        val columnsTop = sliderTop(R.string.custom_folder_columns)
        val rowsTop = sliderTop(R.string.custom_folder_rows)
        val expanded = followingTop()

        show(Feature.FolderLayout, false)
        advance(112)
        val closing = followingTop()
        assertTrue(closing < expanded)
        assertEquals(columnsTop, sliderTop(R.string.custom_folder_columns), 0.5f)
        assertEquals(rowsTop, sliderTop(R.string.custom_folder_rows), 0.5f)

        show(Feature.FolderLayout, true)
        advance(80)
        assertEquals(rowsTop, sliderTop(R.string.custom_folder_rows), 0.5f)
        advance(400)
        assertEquals(expanded, followingTop(), 0.5f)

        show(Feature.FolderLayout, false)
        advance(400)
        assertTrue(followingTop() < closing)
        compose.onNodeWithText(context.getString(R.string.custom_folder_rows)).assertDoesNotExist()
    }

    @Test fun folderIconSizeMovesFollowingRowInBothDirections() {
        showFolderRows()
        val expanded = followingTop()
        show(Feature.FolderIconSize, false)
        advance(112)
        val closing = followingTop()
        advance(400)
        val closed = followingTop()
        assertTrue(closing > closed && closing < expanded)
        show(Feature.FolderIconSize, true)
        advance(112)
        val opening = followingTop()
        assertTrue(opening > closed && opening < expanded)
        advance(400)
        assertEquals(expanded, followingTop(), 0.5f)
    }

    @Test fun tappingLayoutOnScrolledDesktopPageExpandsBeforeTheOtherSwitches() {
        prefs.edit().putBoolean(Feature.FolderLayout.key, false).putBoolean(Feature.FolderIconSize.key, false).commit()
        checkScrolledPageTransition(R.string.custom_folder_layout, expanding = true)
    }

    @Test fun tappingFolderIconSizeAtPageEndCollapsesWithoutOverlappingTheNextSwitch() {
        checkScrolledPageTransition(R.string.launcher_folder_icon_size, expanding = false)
    }

    private fun checkScrolledPageTransition(toggleTitle: Int, expanding: Boolean) {
        lateinit var scroll: ScrollState
        compose.setContent {
            COUITheme {
                scroll = rememberScrollState()
                SettingsPageSurface(
                    title = featurePageTitle(FeaturePageMode.DesktopIcons),
                    showBack = true, onBack = {}, externalScrollState = scroll,
                    bottomContentPadding = BottomBarHostHeight,
                ) {
                    DesktopFeaturesPage(FeaturePageMode.DesktopIcons, false, {}, AssistantScreenOption.Default,
                        {}, false, {}, 0, {}, {})
                }
            }
        }
        advance(400)
        compose.runOnIdle {
            assertTrue(scroll.maxValue > 0)
            scroll.dispatchRawDelta(scroll.maxValue.toFloat())
        }
        advance(400)

        fun label(title: Int, index: Int = 0) = compose
            .onAllNodesWithText(context.getString(title), useUnmergedTree = true)[index]
        fun top(title: Int) = label(title).getUnclippedBoundsInRoot().top.value
        val followingTitles = LauncherFeatureItems.folder.map { it.title }
        val firstFollowing = followingTitles.first()
        val spacing = followingTitles.map { top(it) - top(firstFollowing) }
        val anchor = label(toggleTitle)
        val startAnchor = top(toggleTitle)
        val startScroll = scroll.value / context.resources.displayMetrics.density
        val startFollowing = top(firstFollowing)
        val startDistance = startFollowing - startAnchor
        val gapAfterIconSize = startFollowing - top(R.string.launcher_folder_icon_size)
        var previousDistance = startDistance
        var movingFrames = 0

        // Keep a slow preference write pending while the real row handles the tap.
        val release = CountDownLatch(1)
        val writes = FeatureSettingWriteQueue.application
        writes.enqueue { check(release.await(30, TimeUnit.SECONDS)) }
        try {
            anchor.assertIsDisplayed().performTouchInput { click() }
            for (elapsed in 0 until 12) {
                advance(32)
                val anchorTop = top(toggleTitle)
                val scrollOffset = scroll.value / context.resources.displayMetrics.density
                assertEquals("Toggle moved within the scroll content at frame $elapsed",
                    startAnchor + startScroll, anchorTop + scrollOffset, 1f)
                val firstTop = top(firstFollowing)
                if (expanding) {
                    assertEquals("A gap opened between the two adjacent switches at frame $elapsed",
                        gapAfterIconSize, firstTop - top(R.string.launcher_folder_icon_size), 1f)
                } else {
                    assertEquals("The rows below the collapsing slider bounced at the bottom of the page",
                        startFollowing, firstTop, 1f)
                }
                followingTitles.forEachIndexed { index, title ->
                    assertEquals("Following switch $index drifted at frame $elapsed", spacing[index], top(title) - firstTop, 1f)
                }
                val distance = firstTop - anchorTop
                val travel = if (expanding) distance - previousDistance else previousDistance - distance
                assertTrue("Following switches reversed direction at frame $elapsed", travel >= -1f)
                if (travel > 1f) movingFrames++
                previousDistance = distance

                val sliderTitles = if (expanding) listOf(R.string.custom_folder_columns, R.string.custom_folder_rows)
                    else listOf(R.string.launcher_folder_icon_size)
                val nextHeader = if (expanding) R.string.launcher_folder_icon_size else firstFollowing
                val headerBounds = label(nextHeader).fetchSemanticsNode().boundsInRoot
                for (title in sliderTitles) {
                    val nodes = compose.onAllNodesWithText(context.getString(title), useUnmergedTree = true).fetchSemanticsNodes()
                    val slider = nodes.getOrNull(if (expanding) 0 else 1) ?: continue
                    val bounds = slider.boundsInRoot
                    if (!bounds.isEmpty && !headerBounds.isEmpty) {
                        assertTrue("Slider covers the next switch at frame $elapsed", bounds.bottom <= headerBounds.top + 1f)
                    }
                }
            }
            assertTrue("Expected multiple distinct intermediate layouts", movingFrames >= 2)
            assertTrue("The folder setting must change the height of its card",
                kotlin.math.abs(previousDistance - startDistance) > 40f)
        } finally {
            release.countDown()
            runBlocking { withTimeout(10_000) { writes.enqueue {}.await() } }
            advance(400)
        }
    }
}
