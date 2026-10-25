package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mi.onextbox.notifications.NotificationAppState
import com.mi.onextbox.notifications.NotificationAppTarget
import com.mi.onextbox.notifications.NotificationBatchIssue
import com.mi.onextbox.notifications.NotificationPermissionKind
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsCardRow
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class NotificationBatchUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = NotificationBatchApp(NotificationAppTarget("com.test.notifications", 10123), "Test app")

    @Test fun couiSelectionOnlyChangesSelection() = selection(AppUiStyle.ColorOs)

    @Test fun couiProtectedAppsCannotBeSelected() = protected(AppUiStyle.ColorOs)

    @Test fun couiFloatingActionsDoNotCutOffTheListViewport() = actionsLayout(AppUiStyle.ColorOs)

    @Test fun couiLargeFontsKeepTheLastAppAboveFloatingActions() = actionsLayout(AppUiStyle.ColorOs, 1.8f)

    @Test fun couiExpansionDoesNotOverlapFollowingCards() = expansion(AppUiStyle.ColorOs, false)

    @Test fun couiRapidExpansionReversalsDoNotOverlap() = expansion(AppUiStyle.ColorOs, true)

    @Test fun couiSelectAllCanBeClearedWithoutApplyingChanges() = selectAll(AppUiStyle.ColorOs)

    @Test fun couiActionsAreDisabledWhenNoPermissionIsChosen() = noPermissions(AppUiStyle.ColorOs)

    @Test fun couiNativeButtonsDispatchTheChosenAction() = actionsClick(AppUiStyle.ColorOs)

    @Test fun couiDisabledButtonsDoNotRevealContentBehindThem() = opaqueDisabledActions(AppUiStyle.ColorOs, false)
    @Test fun couiDarkDisabledButtonsDoNotRevealContentBehindThem() = opaqueDisabledActions(AppUiStyle.ColorOs, true)

    private fun actionsLayout(style: AppUiStyle, fontScale: Float = 1f) {
        val apps = (1..12).map { NotificationBatchApp(NotificationAppTarget("com.test.app$it", 10100 + it), "Long application name number $it") }
        compose.setContent {
            TestTheme(style, fontScale) {
                Box(Modifier.width(360.dp).height(620.dp)) {
                    NotificationBatchLayout(NotificationBatchUiState(apps = apps, states = apps.map { state(it) }, selected = setOf(apps[0].target.packageName)))
                }
            }
        }
        compose.onNodeWithTag("batchEnable").assertIsEnabled()
        compose.onNodeWithTag("batchDisable").assertIsEnabled()
        compose.onNodeWithTag("batchFooter").assertDoesNotExist()
        val list = compose.onNodeWithTag("batchList").getUnclippedBoundsInRoot()
        val actions = compose.onNodeWithTag("batchActions").getUnclippedBoundsInRoot()
        assertTrue("The list viewport must continue behind the floating buttons", list.bottom >= actions.bottom)
        listOf("batchEnable", "batchDisable").forEach { tag ->
            val button = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(button.bottom - button.top >= 48.dp)
            assertTrue(button.top >= actions.top && button.bottom <= actions.bottom)
        }
        val last = "batchApp:${apps.last().target.packageName}"
        compose.onNodeWithTag("batchList").performScrollToNode(hasTestTag(last))
        compose.onNodeWithTag("batchList").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag(last).getUnclippedBoundsInRoot().bottom <= actions.top)
    }

    private fun expansion(style: AppUiStyle, interrupt: Boolean) {
        val expanded = mutableStateOf(false)
        compose.setContent {
            TestTheme(style) {
                LazyColumn(Modifier.width(360.dp).height(620.dp)) {
                    item("expanding") {
                        NotificationBatchExpandableCard("Permissions", "Three permissions selected", expanded.value, 3, "expanding", { expanded.value = it }) {
                            repeat(3) { SettingsCardRow("Permission $it", "Selected permission") }
                        }
                    }
                    item("following") {
                        Box(notificationBatchListAnimation().testTag("following")) { SettingsGroup { SettingsCardRow("Following", "Next card") } }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val initial = compose.onNodeWithTag("expanding").getUnclippedBoundsInRoot()
        val height = (initial.bottom - initial.top).value
        compose.runOnIdle { expanded.value = true }
        advanceAndCheck(if (interrupt) 6 else 45)
        compose.runOnIdle { expanded.value = false }
        advanceAndCheck(if (interrupt) 4 else 45)
        if (interrupt) {
            compose.runOnIdle { expanded.value = true }
            advanceAndCheck(7)
            compose.runOnIdle { expanded.value = false }
            advanceAndCheck(45)
        }
        val final = compose.onNodeWithTag("expanding").getUnclippedBoundsInRoot()
        assertEquals(height, (final.bottom - final.top).value, .5f)
    }

    private fun advanceAndCheck(frames: Int) {
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            val card = compose.onNodeWithTag("expanding").getUnclippedBoundsInRoot()
            val following = compose.onNodeWithTag("following").getUnclippedBoundsInRoot()
            assertTrue("The next card must follow the current animated height", following.top.value >= card.bottom.value - .5f)
        }
    }

    private fun selectAll(style: AppUiStyle) {
        val protected = NotificationBatchApp(NotificationAppTarget("com.test.protected", 10200), "Protected")
        val selected = mutableStateOf(emptySet<String>())
        var applied = false
        compose.setContent {
            TestTheme(style) {
                Box(Modifier.width(360.dp).height(620.dp)) {
                    NotificationBatchLayout(
                        NotificationBatchUiState(apps = listOf(app, protected), states = listOf(state(app), NotificationAppState(protected.target, issue = NotificationBatchIssue.Protected)), selected = selected.value),
                        onSelectionChange = { selected.value = it }, onAction = { applied = true },
                    )
                }
            }
        }
        compose.onNodeWithTag("batchEnable").assertIsNotEnabled()
        compose.onNodeWithTag("batchSelectVisible").performClick()
        compose.runOnIdle { assertEquals(setOf(app.target.packageName), selected.value); assertEquals(false, applied) }
        compose.onNodeWithTag("batchEnable").assertIsEnabled()
        compose.onNodeWithTag("batchSelectVisible").performClick()
        compose.runOnIdle { assertTrue(selected.value.isEmpty()); assertEquals(false, applied) }
        compose.onNodeWithTag("batchEnable").assertIsNotEnabled()
    }

    private fun noPermissions(style: AppUiStyle) {
        compose.setContent {
            TestTheme(style) {
                Box(Modifier.width(360.dp).height(620.dp)) {
                    NotificationBatchLayout(NotificationBatchUiState(apps = listOf(app), states = listOf(state(app)), selected = setOf(app.target.packageName), types = emptySet()))
                }
            }
        }
        compose.onNodeWithTag("batchEnable").assertIsNotEnabled()
        compose.onNodeWithTag("batchDisable").assertIsNotEnabled()
    }

    private fun actionsClick(style: AppUiStyle) {
        val actions = mutableListOf<Int>()
        compose.setContent {
            TestTheme(style) {
                Box(Modifier.width(360.dp).height(620.dp)) {
                    NotificationBatchLayout(
                        NotificationBatchUiState(apps = listOf(app), states = listOf(state(app)), selected = setOf(app.target.packageName)),
                        onAction = { actions += it },
                    )
                }
            }
        }
        compose.onNodeWithTag("batchEnable").performClick()
        compose.onNodeWithTag("batchDisable").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 2), actions) }
    }

    private fun opaqueDisabledActions(style: AppUiStyle, dark: Boolean) {
        val background = mutableStateOf(Color.Magenta)
        var actions = 0
        compose.setContent {
            COUITheme {
                val surface = if (dark) Color(0xFF141218) else Color(0xFFFFFBFE)
                COUITheme(colors = COUITheme.colorScheme.copy(surface = surface, background = surface)) {
                    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                        CompositionLocalProvider(LocalAppUiStyle provides style) {
                            Box(Modifier.width(360.dp).height(180.dp).background(background.value)) {
                                NotificationBatchActions(false, false, { actions++ }, { actions++ },
                                    Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
        }
        val tags = listOf("batchEnable", "batchDisable")
        tags.forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
        val before = tags.associateWith { compose.onNodeWithTag(it).captureToImage().toPixelMap() }
        compose.runOnIdle { background.value = Color.Cyan }
        compose.waitForIdle()
        for (tag in tags) {
            val first = before.getValue(tag)
            val second = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            assertEquals(first.width, second.width)
            assertEquals(first.height, second.height)
            for (xFraction in listOf(.2f, .5f, .8f)) {
                for (yFraction in listOf(.3f, .5f, .7f)) {
                    val x = (first.width * xFraction).toInt()
                    val y = (first.height * yFraction).toInt()
                    val a = first[x, y]
                    val b = second[x, y]
                    assertTrue("$style/$dark/$tag must not reveal the changed backdrop",
                        abs(a.red - b.red) < .05f && abs(a.green - b.green) < .05f && abs(a.blue - b.blue) < .05f)
                }
            }
            compose.onNodeWithTag(tag).performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(0, actions) }
    }

    private fun state(app: NotificationBatchApp) = NotificationAppState(app.target, NotificationPermissionKind.entries.associateWith { 1 })

    @Composable
    private fun TestTheme(style: AppUiStyle, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        COUITheme { MaterialTheme {
            CompositionLocalProvider(LocalAppUiStyle provides style, LocalDensity provides Density(density, fontScale), content = content)
        } }
    }

    private fun selection(style: AppUiStyle) {
        var selected: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalAppUiStyle provides style) {
                MaterialTheme { COUITheme {
                    Box(Modifier.width(320.dp)) { SettingsGroup {
                        NotificationBatchAppRow(app, NotificationAppState(app.target, NotificationPermissionKind.entries.associateWith { 1 }), false, true, Modifier.testTag("notificationApp")) { selected = it }
                    } }
                } }
            }
        }
        compose.onNodeWithTag("notificationApp").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(true, selected) }
    }

    private fun protected(style: AppUiStyle) {
        compose.setContent {
            CompositionLocalProvider(LocalAppUiStyle provides style) {
                MaterialTheme { COUITheme {
                    Box(Modifier.width(320.dp)) { SettingsGroup {
                        NotificationBatchAppRow(app, NotificationAppState(app.target, issue = NotificationBatchIssue.Protected), false, false, Modifier.testTag("notificationApp")) { error("Protected app selected") }
                    } }
                } }
            }
        }
        compose.onNodeWithTag("notificationApp").assertIsNotEnabled()
    }
}
