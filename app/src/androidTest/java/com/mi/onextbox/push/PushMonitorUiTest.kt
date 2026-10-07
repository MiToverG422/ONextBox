package com.mi.onextbox.push

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.screens.PushMonitorContent
import com.mi.onextbox.ui.settings.SettingsPageSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushMonitorUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val apps = listOf(
        PushApp(MCS_PACKAGE, "System messages", PushRegistration.SystemService),
        PushApp("com.example.registered", "Registered app", PushRegistration.Registered),
        PushApp("com.example.unregistered", "Unregistered app", PushRegistration.Unregistered),
        PushApp("com.example.unknown", "Unknown app", PushRegistration.Unknown),
    )

    @Test fun couiRowsKeepNamePackageAndStatusTogetherWithoutSwitch() {
        assertJoinedRows(AppUiStyle.ColorOs)
    }

    @Test fun materialRowsKeepNamePackageAndStatusTogetherWithoutSwitch() {
        assertJoinedRows(AppUiStyle.Material3Expressive)
    }

    @Test fun couiSearchAndUnregisteredFilterExcludeUnknownApps() {
        assertSearchAndFilter(AppUiStyle.ColorOs)
    }

    @Test fun materialSearchAndUnregisteredFilterExcludeUnknownApps() {
        assertSearchAndFilter(AppUiStyle.Material3Expressive)
    }

    @Test fun couiRefreshAnimatesStatusBeforeRemovingOldText() {
        assertStatusTransition(AppUiStyle.ColorOs)
    }

    @Test fun materialRefreshAnimatesStatusBeforeRemovingOldText() {
        assertStatusTransition(AppUiStyle.Material3Expressive)
    }

    @Test fun couiSearchClearRestoresRows() {
        assertSearchClear(AppUiStyle.ColorOs)
    }

    @Test fun materialSearchClearRestoresRows() {
        assertSearchClear(AppUiStyle.Material3Expressive)
    }

    @Test fun couiFooterAlignsWithCardContents() {
        assertFooterAlignment(AppUiStyle.ColorOs)
    }

    @Test fun materialFooterAlignsWithCardContents() {
        assertFooterAlignment(AppUiStyle.Material3Expressive)
    }

    @Test fun couiSearchAndFiltersStayPinnedWhenScrollingToFooter() {
        assertSearchAndFiltersPinned(AppUiStyle.ColorOs)
    }

    @Test fun materialSearchAndFiltersStayPinnedWhenScrollingToFooter() {
        assertSearchAndFiltersPinned(AppUiStyle.Material3Expressive)
    }

    @Test fun couiListDrawsBehindBottomInsetButFooterScrollsAboveIt() {
        assertBottomEdgeToEdge(AppUiStyle.ColorOs)
    }

    @Test fun materialListDrawsBehindBottomInsetButFooterScrollsAboveIt() {
        assertBottomEdgeToEdge(AppUiStyle.Material3Expressive)
    }

    @Test fun couiFiltersReturnToTopOnEveryClick() {
        assertFilterScrollReset(AppUiStyle.ColorOs)
    }

    @Test fun materialFiltersReturnToTopOnEveryClick() {
        assertFilterScrollReset(AppUiStyle.Material3Expressive)
    }

    private fun assertJoinedRows(style: AppUiStyle) {
        compose.setContent { MonitorTheme(style) { PushMonitorContent(apps, null, 0) } }
        compose.onNode(hasText("Registered app") and hasText("com.example.registered") and
            hasText(context.getString(R.string.push_monitor_registered))).assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
    }

    private fun assertSearchAndFilter(style: AppUiStyle) {
        compose.setContent { MonitorTheme(style) { PushMonitorContent(apps, null, 0) } }
        compose.onNode(filterMatcher(R.string.push_monitor_unregistered)).performClick()
        compose.onNodeWithText("com.example.unregistered").assertExists()
        compose.onNodeWithText("com.example.unknown").assertDoesNotExist()
        compose.onNodeWithText("com.example.registered").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("no matching package")
        compose.onNodeWithText(context.getString(R.string.push_monitor_empty)).assertExists()
    }

    private fun assertSearchClear(style: AppUiStyle) {
        compose.setContent { MonitorTheme(style) { PushMonitorContent(apps, null, 0) } }
        compose.onNode(hasSetTextAction()).performTextInput("no matching package")
        compose.onNodeWithText(context.getString(R.string.push_monitor_empty)).assertExists()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText(context.getString(R.string.push_monitor_empty)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.feature_search_clear)).performClick()
        apps.forEach { app -> compose.onNodeWithText(app.packageName).assertExists() }
        compose.onNodeWithText(context.getString(R.string.push_monitor_empty)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.push_monitor_search)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.feature_search_clear)).assertDoesNotExist()
    }

    private fun assertFooterAlignment(style: AppUiStyle) {
        val app = apps[1]
        val footer = context.getString(R.string.push_monitor_privacy)
        compose.setContent { MonitorTheme(style) { PushMonitorContent(listOf(app), null, 0) } }
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(footer))
        val packageBounds = compose.onNodeWithText(app.packageName, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val footerBounds = compose.onNodeWithText(footer, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Footer should share the card content start edge", packageBounds.left, footerBounds.left, 1f)
    }

    private fun assertSearchAndFiltersPinned(style: AppUiStyle) {
        val manyApps = (0 until 40).map { index ->
            PushApp("com.example.app$index", "App $index", PushRegistration.Registered)
        }
        compose.setContent { MonitorTheme(style) { PushMonitorContent(manyApps, null, 0) } }
        val filterMatchers = listOf(
            R.string.push_monitor_all,
            R.string.push_monitor_registered,
            R.string.push_monitor_unregistered,
        ).map(::filterMatcher)
        val pinnedMatchers = listOf(hasSetTextAction()) + filterMatchers
        val initialBounds = pinnedMatchers.map { matcher ->
            compose.onNode(matcher).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }

        val footer = context.getString(R.string.push_monitor_privacy)
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(footer))
        compose.onNodeWithText(footer).assertIsDisplayed()
        compose.onNodeWithText(manyApps.first().packageName).assertDoesNotExist()
        pinnedMatchers.zip(initialBounds).forEach { (matcher, before) ->
            val after = compose.onNode(matcher).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("Pinned control start edge should not move", before.left, after.left, 1f)
            assertEquals("Pinned control top edge should not move", before.top, after.top, 1f)
            assertEquals("Pinned control end edge should not move", before.right, after.right, 1f)
            assertEquals("Pinned control bottom edge should not move", before.bottom, after.bottom, 1f)
        }
    }

    private fun assertBottomEdgeToEdge(style: AppUiStyle) {
        val manyApps = (0 until 40).map { index ->
            PushApp("com.example.app$index", "App $index", PushRegistration.Registered)
        }
        val inset = 56.dp
        lateinit var density: Density
        compose.setContent {
            density = LocalDensity.current
            MonitorTheme(style) {
                Box(Modifier.fillMaxSize().testTag("monitorRoot")) {
                    SettingsPageSurface(
                        title = "System messages",
                        showBack = true,
                        contentScrollable = false,
                        edgeToEdgeBottom = true,
                    ) {
                        PushMonitorContent(manyApps, null, 0, bottomInsets = WindowInsets(bottom = inset))
                    }
                }
            }
        }
        val list = compose.onNode(hasScrollAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val rootBounds = compose.onNodeWithTag("monitorRoot").fetchSemanticsNode().boundsInRoot
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        assertEquals("List viewport should reach the window bottom", rootBounds.bottom, listBounds.bottom, 1f)

        list.performScrollToIndex(manyApps.size)
        val footerBounds = compose.onNodeWithText(context.getString(R.string.push_monitor_privacy))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val gap = rootBounds.bottom - footerBounds.bottom
        val expectedGap = with(density) { (inset + 24.dp).toPx() }
        val footerPaddingTolerance = with(density) { 8.dp.toPx() }
        assertTrue("Footer should clear the bottom inset and list padding", gap >= expectedGap - 1f)
        assertTrue("Bottom inset should not be applied twice", gap <= expectedGap + footerPaddingTolerance)
    }

    private fun assertFilterScrollReset(style: AppUiStyle) {
        val now = 4_000_000L
        val manyApps = listOf(PushApp(MCS_PACKAGE, "System messages", PushRegistration.SystemService)) +
            (0 until 40).flatMap { index ->
                listOf(
                    PushApp("com.example.registered$index", "Registered $index", PushRegistration.Registered,
                        lastPush = now - (index + 1) * 60_000L),
                    PushApp("com.example.unregistered$index", "Unregistered $index", PushRegistration.Unregistered),
                )
            }
        compose.setContent { MonitorTheme(style) { PushMonitorContent(manyApps, null, now) } }
        val list = compose.onNode(hasScrollAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val filters = listOf(
            PushFilter.Registered to R.string.push_monitor_registered,
            PushFilter.Unregistered to R.string.push_monitor_unregistered,
            PushFilter.All to R.string.push_monitor_all,
        )
        filters.forEach { (filter, title) ->
            repeat(2) {
                list.performScrollToIndex(12)
                list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 32f) }
                val scrolled = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                assertTrue("The list should be scrolled before selecting a filter", scrolled > 0f)
                compose.onNode(filterMatcher(title)).performClick()
                val offset = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                assertEquals("Every filter click should reset the index and pixel offset", 0f, offset, 0.0001f)
                val firstPackage = PushMonitorRules.shown(manyApps, "", filter).first().packageName
                compose.onNodeWithText(firstPackage).assertIsDisplayed()
            }
        }
    }

    private fun filterMatcher(titleRes: Int) = hasText(context.getString(titleRes)) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun assertStatusTransition(style: AppUiStyle) {
        val rows = mutableStateOf(listOf(PushApp("com.example.refresh", "Refresh app", PushRegistration.Unknown)))
        val refreshId = mutableStateOf("before-refresh")
        compose.setContent {
            MonitorTheme(style) { PushMonitorContent(rows.value, null, 0, refreshId.value) }
        }
        compose.onNodeWithText(context.getString(R.string.push_monitor_unknown)).assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { refreshId.value = "unchanged-refresh" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(context.getString(R.string.push_monitor_unknown), useUnmergedTree = true)
            .assertCountEquals(2)
        compose.mainClock.advanceTimeBy(600)
        compose.onAllNodesWithText(context.getString(R.string.push_monitor_unknown), useUnmergedTree = true)
            .assertCountEquals(1)
        compose.runOnIdle {
            rows.value = rows.value.map { it.copy(registration = PushRegistration.Registered) }
            refreshId.value = "after-refresh"
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(context.getString(R.string.push_monitor_unknown)).assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(context.getString(R.string.push_monitor_unknown)).assertDoesNotExist()
        compose.onNode(hasText("com.example.refresh") and
            hasText(context.getString(R.string.push_monitor_registered))).assertExists()
    }

    @Composable
    private fun MonitorTheme(style: AppUiStyle, content: @Composable () -> Unit) {
        COUITheme {
            MaterialTheme {
                CompositionLocalProvider(LocalAppUiStyle provides style, content = content)
            }
        }
    }
}
