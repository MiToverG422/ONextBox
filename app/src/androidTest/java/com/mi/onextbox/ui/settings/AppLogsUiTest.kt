package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mi.onextbox.ui.common.AppLogLevel
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.LogRecord
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLogsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun couiExpansionAndCollapseKeepFollowingRowsOutsideTheCardEveryFrame() {
        assertNoOverlap(AppUiStyle.ColorOs, interrupt = false)
    }

    @Test fun couiRapidExpansionReversalsDoNotOverlap() {
        assertNoOverlap(AppUiStyle.ColorOs, interrupt = true)
    }

    @Test fun couiInfoBadgeCentersItsText() = assertCenteredBadge("信息", 1f)
    @Test fun couiDebugBadgeCentersItsText() = assertCenteredBadge("调试", 1f)
    @Test fun couiBadgeKeepsTextCenteredWithLargeFonts() = assertCenteredBadge("调试", 1.8f)

    private fun assertNoOverlap(style: AppUiStyle, interrupt: Boolean) {
        val expanded = mutableStateOf(false)
        val records = listOf(
            record("expanding", (1..12).joinToString("\n") { "Log line $it with details" }),
            record("following", "Next record"),
            record("last", "Last record"),
        )
        compose.setContent {
            LogTestTheme(style) {
                LazyColumn(Modifier.width(360.dp).height(620.dp)) {
                    items(records, key = { it.key }) { record ->
                        LogRecordCard(
                            record = record,
                            levelLabel = "调试",
                            expanded = record.key == "expanding" && expanded.value,
                            modifier = logRecordListAnimation().testTag(record.key),
                            onClick = { expanded.value = !expanded.value },
                            onCopy = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val collapsed = compose.onNodeWithTag("expanding").getUnclippedBoundsInRoot()
        val collapsedHeight = (collapsed.bottom - collapsed.top).value
        compose.runOnIdle { expanded.value = true }
        advanceAndCheck(if (interrupt) 6 else 45)
        if (!interrupt) {
            val bounds = compose.onNodeWithTag("expanding").getUnclippedBoundsInRoot()
            assertTrue((bounds.bottom - bounds.top).value > collapsedHeight)
        }
        compose.runOnIdle { expanded.value = false }
        advanceAndCheck(if (interrupt) 4 else 45)
        if (interrupt) {
            compose.runOnIdle { expanded.value = true }
            advanceAndCheck(7)
            compose.runOnIdle { expanded.value = false }
            advanceAndCheck(45)
        }
        val finalBounds = compose.onNodeWithTag("expanding").getUnclippedBoundsInRoot()
        assertEquals(collapsedHeight, (finalBounds.bottom - finalBounds.top).value, .5f)
    }

    private fun advanceAndCheck(frames: Int) {
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            val expanding = compose.onNodeWithTag("expanding").getUnclippedBoundsInRoot()
            val following = compose.onNodeWithTag("following").getUnclippedBoundsInRoot()
            assertTrue("The next row must follow the card's current animated height",
                following.top.value >= expanding.bottom.value - .5f)
        }
    }

    private fun assertCenteredBadge(label: String, fontScale: Float) {
        compose.setContent {
            LogTestTheme(AppUiStyle.ColorOs, fontScale) {
                LogLevelBadge(label, Color.Blue, Modifier.testTag("badge"))
            }
        }
        val badge = compose.onNodeWithTag("badge", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((badge.left + badge.right).value / 2f, (text.left + text.right).value / 2f, .5f)
        assertEquals((badge.top + badge.bottom).value / 2f, (text.top + text.bottom).value / 2f, .5f)
        assertTrue(badge.bottom - badge.top >= 24.dp)
    }

    private fun record(key: String, message: String) = LogRecord(
        key = key, level = AppLogLevel.DEBUG, metadata = "Debug · Shell", message = message, raw = message,
        timestamp = "2026-10-10 15:35:09.764", origin = "Shell",
    )

    @Composable
    private fun LogTestTheme(style: AppUiStyle, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        COUITheme {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppUiStyle provides style,
                    LocalDensity provides Density(density, fontScale),
                    content = content,
                )
            }
        }
    }
}
