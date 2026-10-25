package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.AppUiStyleAppTheme
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.screens.FeatureAnimatedGroup
import com.mi.onextbox.ui.screens.FeatureSliderRow
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsInteractionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun colorOsPendingWriteDoesNotFlashControls() = assertStablePendingWrite(AppUiStyle.ColorOs)

    private fun assertStablePendingWrite(style: AppUiStyle) {
        val pending = mutableStateOf(false)
        var callbacks = 0
        compose.setContent {
            Theme(style) {
                Column(Modifier.width(320.dp).testTag("group")) {
                    SettingsSwitchPreference(
                        checked = true, onCheckedChange = { callbacks++ }, title = "First feature",
                        summary = "A saved preference", interactionEnabled = !pending.value,
                        modifier = Modifier.testTag("first"),
                    )
                    SettingsSwitchPreference(
                        checked = false, onCheckedChange = { callbacks++ }, title = "Sibling feature",
                        interactionEnabled = !pending.value,
                    )
                    FeatureSliderRow(
                        heading = "Amount", currentValue = "50", value = 50f, valueRange = 0f..100f, steps = 0,
                        interactionEnabled = !pending.value, onValueChange = { callbacks++ }, onReset = { callbacks++ },
                    )
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("group").captureToImage().pixels()
        repeat(3) {
            compose.runOnIdle { pending.value = true }
            compose.waitForIdle()
            assertArrayEquals("Saving must not alter text/switch/slider colors in $style", before,
                compose.onNodeWithTag("group").captureToImage().pixels())
            compose.onNodeWithTag("first").performClick()
            compose.onNodeWithTag("first").performTouchInput { click() }
            compose.runOnIdle { assertEquals("Pending input reached the writer", 0, callbacks); pending.value = false }
            compose.waitForIdle()
            assertArrayEquals(before, compose.onNodeWithTag("group").captureToImage().pixels())
        }
        compose.onNodeWithTag("first").performClick()
        compose.runOnIdle { assertEquals(1, callbacks) }
    }

    @Test fun auxiliaryControlsDoNotRecreateTheCouiSwitch() {
        val expanded = mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        compose.setContent {
            Theme(AppUiStyle.ColorOs) {
                val auxiliary: (@Composable () -> Unit)? = if (expanded.value) ({ Text("Extra option") }) else null
                SettingsSwitchPreference(
                    checked = true, onCheckedChange = {}, title = "Stable feature", bottomAction = auxiliary,
                    startAction = {
                        DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                        Text("Icon")
                    },
                )
            }
        }
        repeat(4) {
            compose.runOnIdle { expanded.value = !expanded.value }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(1, mounts); assertEquals(0, disposals) }
        }
    }

    @Test fun animatedGroupKeepsSiblingIdentityWhenAFeatureExpands() {
        val expanded = mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        compose.setContent {
            Theme(AppUiStyle.ColorOs) {
                FeatureAnimatedGroup(animateChanges = true) {
                    row("parent") { _, _ -> Text("Parent") }
                    row("extra", visible = expanded.value) { _, _ -> Text("Extra option") }
                    row("sibling") { _, _ ->
                        DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                        SettingsToggleRow(title = "Sibling", summary = "", checked = false, onCheckedChange = {})
                    }
                }
            }
        }
        repeat(4) {
            compose.runOnIdle { expanded.value = !expanded.value }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(1, mounts); assertEquals(0, disposals) }
        }
    }

    @Composable private fun Theme(style: AppUiStyle, content: @Composable () -> Unit) {
        COUITheme {
            AppUiStyleAppTheme(style, AppThemeMode.Light, null) {
                CompositionLocalProvider(LocalAppUiStyle provides style, content = content)
            }
        }
    }

    private fun ImageBitmap.pixels() = IntArray(width * height).also { readPixels(it) }
}
