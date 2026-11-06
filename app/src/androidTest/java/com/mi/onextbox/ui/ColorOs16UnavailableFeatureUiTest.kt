package com.mi.onextbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.screens.ColorOs16UnsupportedFeatures
import com.mi.onextbox.ui.screens.FeaturePageMode
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsGroup
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorOs16UnavailableFeatureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledEntryIgnoresTouches() {
        var opens = 0
        compose.setContent {
            COUITheme {
                SettingsGroup {
                    SettingsCardRow(title = "Unavailable entry", summary = "ColorOS 16", showArrow = true,
                        enabled = false, onClick = { opens++ })
                }
            }
        }
        compose.onNodeWithText("Unavailable entry").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun unsupportedControlsAreOffAndCannotWritePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = LspPreferenceStore.prefs(context).all.toMap()
        compose.setContent {
            COUITheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ColorOs16UnsupportedFeatures(FeaturePageMode.SystemUiLockScreen)
                }
            }
        }
        val switches = compose.onAllNodes(isToggleable())
        val nodes = switches.fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        nodes.forEach {
            assertTrue(SemanticsProperties.Disabled in it.config)
            assertEquals(ToggleableState.Off, it.config[SemanticsProperties.ToggleableState])
        }
        switches.onFirst().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(before, LspPreferenceStore.prefs(context).all) }
    }
}
