package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LargeNetworkTypeSettings
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.settings.SettingsGroup
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LargeNetworkSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = LspPreferenceStore.prefs(context)
    private val keys = listOf(LargeNetworkTypeSettings.KEY, LargeNetworkTypeSettings.FONT_KEY,
        LargeNetworkTypeSettings.BOLD_KEY, LargeNetworkTypeSettings.ARTWORK_KEY,
        LargeNetworkTypeSettings.LTE_KEY, LargeNetworkTypeSettings.POSITION_KEY)
    private var before: Map<String, Any?> = emptyMap()

    @Before fun saveSettings() {
        before = prefs.all.filterKeys { it in keys }
        check(prefs.edit().putBoolean(LargeNetworkTypeSettings.KEY, false)
            .putBoolean(LargeNetworkTypeSettings.ARTWORK_KEY, false)
            .putBoolean(LargeNetworkTypeSettings.FONT_KEY, false)
            .remove(LargeNetworkTypeSettings.POSITION_KEY).commit())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            COUITheme {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.testTag("networkGroup")) {
                        SettingsGroup { LargeNetworkTypeSettingsRow() }
                    }
                }
            }
        }
    }

    @After fun restoreSettings() {
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        before.forEach { (key, value) -> when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
        } }
        check(editor.commit())
        LspConfig.syncReadableState(context)
    }

    private fun row(title: Int) = compose.onNodeWithText(context.getString(title))
    private fun height(): Float {
        val bounds = compose.onNodeWithTag("networkGroup").getUnclippedBoundsInRoot()
        return bounds.bottom.value - bounds.top.value
    }
    private fun settle() {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitUntil(15_000) {
            !row(R.string.feature_large_network_type_title).fetchSemanticsNode().config
                .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        }
        compose.waitForIdle()
    }
    private fun saved(key: String, value: Boolean) {
        compose.waitUntil(15_000) { prefs.getBoolean(key, !value) == value }
    }

    @Test fun groupInterpolatesDuringBothExpansionAndCollapse() {
        val closed = height()
        row(R.string.feature_large_network_type_title).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val entering = height()
        settle()
        val expanded = height()
        assertTrue("Entry must interpolate: $closed < $entering < $expanded", entering > closed && entering < expanded)
        saved(LargeNetworkTypeSettings.KEY, true)
        row(R.string.feature_large_network_type_title).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val exiting = height()
        assertTrue("Exit must retain shrinking rows", exiting > closed && exiting < expanded)
        settle()
        assertEquals(closed, height(), 0.5f)
        row(R.string.feature_large_network_position_title).assertDoesNotExist()
    }

    @Test fun positionMenuPersistsBothChoicesAndSurvivesClosingTheGroup() {
        row(R.string.feature_large_network_type_title).performClick()
        saved(LargeNetworkTypeSettings.KEY, true)
        settle()
        row(R.string.feature_large_network_position_title).performClick()
        settle()
        row(R.string.feature_large_network_position_left).performClick()
        compose.waitUntil(15_000) { LargeNetworkTypeSettings.position(context) == 0 }
        settle()
        row(R.string.feature_large_network_position_title).performClick()
        settle()
        // The menu option has RadioButton semantics; the row also shows the selected value.
        compose.onNode(hasText(context.getString(R.string.feature_large_network_position_right)) and
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton)).performClick()
        compose.waitUntil(15_000) { LargeNetworkTypeSettings.position(context) == 1 }
        settle()
        row(R.string.feature_large_network_type_title).performClick()
        saved(LargeNetworkTypeSettings.KEY, false)
        settle()
        assertEquals(1, LargeNetworkTypeSettings.position(context))
        row(R.string.feature_large_network_position_title).assertDoesNotExist()
    }

    @Test fun artworkAndFontBranchesAlsoAnimateWithoutLeavingGaps() {
        row(R.string.feature_large_network_type_title).performClick()
        saved(LargeNetworkTypeSettings.KEY, true)
        settle()
        row(R.string.feature_large_network_font_title).performClick()
        saved(LargeNetworkTypeSettings.FONT_KEY, true)
        settle()
        row(R.string.feature_large_network_bold_title).assertExists()
        row(R.string.feature_large_network_artwork_title).performClick()
        saved(LargeNetworkTypeSettings.ARTWORK_KEY, true)
        settle()
        row(R.string.feature_large_network_font_title).assertDoesNotExist()
        row(R.string.feature_large_network_bold_title).assertDoesNotExist()
        row(R.string.feature_large_network_lte_title).assertExists()
        row(R.string.feature_large_network_position_title).assertExists()
    }
}
