package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AnimatedDataText
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DataRefreshMotionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun advance(time: Long) {
        compose.mainClock.advanceTimeBy(time)
        compose.waitForIdle()
    }

    private fun height() = compose.onNodeWithTag("dataSurface", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.height

    @Test fun refreshedTextResizesSmoothlyAndCanReverseBeforeSettling() {
        val short = "Updated"
        val long = (1..10).joinToString("\n") { "Device data row $it" }
        val text = mutableStateOf(short)
        compose.setContent {
            COUITheme {
                Box(Modifier.width(320.dp).testTag("dataSurface")) { AnimatedDataText(text.value) }
            }
        }
        compose.mainClock.autoAdvance = false
        val initial = height()
        compose.runOnIdle { text.value = long }
        advance(112)
        val opening = height()
        advance(400)
        val full = height()
        assertTrue(opening > initial && opening < full)
        compose.runOnIdle { text.value = short }
        advance(112)
        assertTrue(height() > initial && height() < full)
        compose.runOnIdle { text.value = long }
        advance(400)
        assertEquals(full, height(), 1f)
        compose.onNodeWithText(long).assertIsDisplayed()
        compose.runOnIdle { text.value = short }
        advance(400)
        assertEquals(initial, height(), 1f)
    }

    @Test fun releaseNotesKeepTheirBodyDuringCollapseAndAnimateBothDirections() {
        val notes = (1..10).joinToString("\n") { "Release note $it" }
        var title = ""
        compose.setContent {
            title = stringResource(R.string.software_update_release_notes_title)
            COUITheme {
                Box(Modifier.width(320.dp).testTag("dataSurface")) {
                    SoftwareUpdateReleaseNotesGroup(notes, "motionTest")
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val full = height()
        compose.onNodeWithText(title).performClick()
        advance(112)
        val closing = height()
        compose.onNodeWithText(notes, useUnmergedTree = true).assertExists()
        advance(400)
        val collapsed = height()
        assertTrue(closing > collapsed && closing < full)
        compose.onNodeWithText(notes, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(title).performClick()
        advance(112)
        assertTrue(height() > collapsed && height() < full)
        advance(400)
        assertEquals(full, height(), 1f)
    }
}
