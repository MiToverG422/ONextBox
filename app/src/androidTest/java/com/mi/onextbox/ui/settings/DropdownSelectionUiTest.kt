package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DropdownSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun couiTapsTheVisibleOptionAfterScrolling() = assertSelectionAfterScroll(AppUiStyle.ColorOs)

    @Test fun couiScrollingDoesNotSelectAnOption() {
        val selections = mutableListOf<Int>()
        showMenu(AppUiStyle.ColorOs, selections)
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(selections.isEmpty())
    }

    @Test fun couiReleasingOutsideTheMenuDoesNotSelectThePreviousRow() {
        val selections = mutableListOf<Int>()
        showMenu(AppUiStyle.ColorOs, selections, count = 3)
        compose.onNodeWithText("Option 1").performTouchInput {
            down(center)
            moveTo(Offset(width + 12f, center.y))
            up()
        }
        compose.waitForIdle()
        assertTrue(selections.isEmpty())
    }

    @Test fun couiShortMenuCanDragFromTheFirstToTheThirdOption() {
        val selections = mutableListOf<Int>()
        showMenu(AppUiStyle.ColorOs, selections, count = 3)
        compose.onNodeWithText("Option 0").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, height * 2f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        assertEquals(listOf(2), selections)
    }

    @Test fun couiReplacingOptionsCancelsTheCurrentPress() =
        assertReplacingOptionsCancelsPress(AppUiStyle.ColorOs)

    @Test fun actionMenuRefreshDoesNotInterruptDragAndUsesLatestCallback() {
        val revision = mutableStateOf(0)
        val selections = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            COUITheme {
                val currentRevision = revision.value
                Box(Modifier.fillMaxSize().padding(24.dp)) {
                    ColorOsDropdownPopup(
                        show = true,
                        selectionMenu = false,
                        entry = DropdownEntry((0..2).map { DropdownItem(text = "Action $it") }),
                        onDismissRequest = {},
                        onItemClick = { selections += currentRevision to it },
                    )
                }
            }
        }
        compose.onNodeWithText("Action 0").performTouchInput { down(center) }
        compose.runOnIdle { revision.value++ }
        compose.onNodeWithText("Action 0").performTouchInput {
            moveTo(center + Offset(0f, height * 2f))
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(1 to 2), selections)
    }

    @Test fun couiDisabledOptionsCannotBeChosen() {
        val selections = mutableListOf<Int>()
        showMenu(AppUiStyle.ColorOs, selections, count = 3, disabledIndex = 1)
        compose.onNodeWithText("Option 1").assertIsNotEnabled()
            .performTouchInput { click() }
        compose.waitForIdle()
        assertTrue(selections.isEmpty())
    }

    private fun assertSelectionAfterScroll(style: AppUiStyle) {
        val selections = mutableListOf<Int>()
        showMenu(style, selections)
        compose.onNodeWithText("Option 18").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("Option 18").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(18), selections)
    }

    private fun assertReplacingOptionsCancelsPress(style: AppUiStyle) {
        val selections = mutableListOf<Int>()
        val options = mutableStateOf(listOf(0, 1, 2))
        setMenu(style) {
            DropdownEntry(options.value.map { value ->
                DropdownItem(text = "Option $value", onClick = { selections += value })
            })
        }
        compose.onNodeWithText("Option 0").performTouchInput { down(center) }
        compose.runOnIdle { options.value = listOf(2, 1, 0) }
        compose.onNodeWithText("Option 2").performTouchInput { up() }
        compose.waitForIdle()
        assertTrue(selections.isEmpty())
    }

    private fun showMenu(
        style: AppUiStyle,
        selections: MutableList<Int>,
        count: Int = 30,
        disabledIndex: Int = -1,
    ) {
        val entry = DropdownEntry((0 until count).map { index ->
            DropdownItem(
                text = "Option $index",
                enabled = index != disabledIndex,
                onClick = { selections += index },
            )
        })
        setMenu(style) { entry }
    }

    private fun setMenu(style: AppUiStyle, entry: () -> DropdownEntry) {
        compose.setContent {
            COUITheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalAppUiStyle provides style) {
                        Box(Modifier.fillMaxSize().padding(24.dp)) {
                            SettingsWindowDropdownPreference(
                                entry = entry(),
                                title = "Choose action",
                                maxHeight = 216.dp,
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Choose action").performClick()
        compose.waitForIdle()
    }
}
