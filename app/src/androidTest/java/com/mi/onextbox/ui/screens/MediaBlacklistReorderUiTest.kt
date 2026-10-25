package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.SettingsLazyGroupItem
import com.mi.onextbox.ui.settings.SettingsSwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MediaBlacklistReorderUiTest {
    @get:Rule val compose = createComposeRule()
    private val apps = listOf("Alpha", "Beta", "Gamma", "Zeta").map {
        MediaBlacklistApp("com.test.${it.lowercase(Locale.US)}", it)
    }
    private val moving = "com.test.zeta"

    @Test fun couiSelectionAndRemovalMoveThroughIntermediatePositions() = assertTransition(AppUiStyle.ColorOs, false)

    @Test fun couiRapidSelectionReversalDoesNotSnap() = assertTransition(AppUiStyle.ColorOs, true)

    @Test fun couiReorderKeepsTheViewportInsteadOfFollowingTheMovedKey() = assertViewport(AppUiStyle.ColorOs)

    private fun assertTransition(style: AppUiStyle, reverseEarly: Boolean) {
        val selected = mutableStateOf(emptySet<String>())
        val state = LazyListState()
        compose.setContent { TestList(style, state, selected, 640) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val original = top(moving)
        val target = top("com.test.alpha")
        update(state, selected, setOf(moving))
        compose.mainClock.advanceTimeBy(64)
        val intermediate = top(moving)
        assertTrue("The row must move rather than instantly jump", intermediate > target + 1f && intermediate < original - 1f)
        if (reverseEarly) {
            update(state, selected, emptySet())
            compose.mainClock.advanceTimeByFrame()
            assertTrue("Reversing must continue from the current position", abs(top(moving) - intermediate) < (original - target) / 2f)
        } else {
            compose.mainClock.advanceTimeBy(600)
            assertEquals(target, top(moving), .5f)
            update(state, selected, emptySet())
            compose.mainClock.advanceTimeBy(64)
            assertTrue(top(moving) > target + 1f && top(moving) < original - 1f)
        }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(original, top(moving), .5f)
        apps.zipWithNext().forEach { (a, b) ->
            val first = compose.onNodeWithTag(a.packageName).getUnclippedBoundsInRoot()
            val following = compose.onNodeWithTag(b.packageName).getUnclippedBoundsInRoot()
            assertTrue("Joined rows must settle without overlap", following.top.value >= first.bottom.value - .5f)
        }
    }

    private fun assertViewport(style: AppUiStyle) {
        val selected = mutableStateOf(emptySet<String>())
        val state = LazyListState()
        compose.setContent { TestList(style, state, selected, 210) }
        compose.waitForIdle()
        compose.runOnIdle { state.requestScrollToItem(1, 8) }
        compose.waitForIdle()
        update(state, selected, setOf("com.test.beta"))
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, state.firstVisibleItemIndex)
            assertEquals(8, state.firstVisibleItemScrollOffset)
        }
    }

    private fun update(state: LazyListState, selected: MutableState<Set<String>>, next: Set<String>) {
        compose.runOnIdle {
            state.requestScrollToItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            selected.value = next
        }
    }

    private fun top(packageName: String) = compose.onNodeWithTag(packageName).getUnclippedBoundsInRoot().top.value

    @Composable
    private fun TestList(style: AppUiStyle, state: LazyListState, selected: MutableState<Set<String>>, height: Int) {
        COUITheme {
            MaterialTheme {
                CompositionLocalProvider(LocalAppUiStyle provides style) {
                    val listed = mediaBlacklistApps(apps, selected.value, "", Locale.US)
                    LazyColumn(Modifier.width(360.dp).height(height.dp), state = state) {
                        itemsIndexed(listed, key = { _, app -> app.packageName }) { index, app ->
                            SettingsLazyGroupItem(index, listed.size,
                                modifier = mediaBlacklistItemAnimation(raised = app.packageName == moving).testTag(app.packageName)) {
                                SettingsSwitchPreference(checked = app.packageName in selected.value,
                                    title = app.label, summary = app.packageName, onCheckedChange = {})
                            }
                        }
                    }
                }
            }
        }
    }
}
