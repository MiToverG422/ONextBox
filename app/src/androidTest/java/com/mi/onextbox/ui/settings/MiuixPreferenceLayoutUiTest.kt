package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class MiuixPreferenceLayoutUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedValuesAreFullyReadableAtCompactPhoneDensity() {
        val rows = listOf(
            "适配版本" to "ColorOS 17",
            "界面风格" to "Miuix",
            "主题模式" to "跟随系统",
            "应用语言" to "简体中文",
            "设置图示" to "顶部",
            "图示样式" to "App 图标",
        )
        showRows(rows, fontScale = 1f)
        rows.forEach { (title, value) ->
            assertFullText(value)
            val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val valueBounds = compose.onNodeWithText(value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertFalse("$title overlaps $value", titleBounds.overlaps(valueBounds))
        }
    }

    @Test fun longSelectionsAndAccessibilityFontsWrapWithoutEllipses() {
        val title = "Application interface style"
        val value = "Material 3 Expressive — follow the system appearance"
        showRows(listOf(title to value), fontScale = 1.5f)
        assertFullText(title)
        assertFullText(value)
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val valueBounds = compose.onNodeWithText(value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Long value should be below the title", valueBounds.top >= titleBounds.bottom)
    }

    @Test fun descriptionDoesNotPullTheArrowBelowTheSelectedValue() {
        showValueAndArrow(LayoutDirection.Ltr, fontScale = 1f, value = "顶部")
        assertValueAndArrowAligned("顶部", LayoutDirection.Ltr)
    }

    @Test fun wrappedSelectionsKeepTheirArrowAlignedInRtlAndLargeFonts() {
        val value = "Material 3 Expressive — follow the system appearance"
        showValueAndArrow(LayoutDirection.Rtl, fontScale = 1.5f, value = value)
        assertFullText(value)
        assertValueAndArrowAligned(value, LayoutDirection.Rtl)
    }

    private fun showValueAndArrow(direction: LayoutDirection, fontScale: Float, value: String) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale), LocalLayoutDirection provides direction) {
                MiuixTheme {
                    Column(Modifier.width(300.dp)) {
                        MiuixPreferenceValueLayout(
                            title = "设置图示", summary = "设置模块入口在系统设置中的位置，描述允许换行显示",
                            value = value, enabled = true,
                            endAction = { Box(Modifier.size(20.dp).testTag("arrow")) },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertValueAndArrowAligned(value: String, direction: LayoutDirection) {
        val valueBounds = compose.onNodeWithText(value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrowBounds = compose.onNodeWithTag("arrow", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Arrow must follow the value row, not the description height", valueBounds.center.y, arrowBounds.center.y, 1f)
        assertFalse("Arrow overlaps the value", valueBounds.overlaps(arrowBounds))
        if (direction == LayoutDirection.Ltr) assertTrue(arrowBounds.left > valueBounds.right)
        else assertTrue(arrowBounds.right < valueBounds.left)
    }

    private fun showRows(rows: List<Pair<String, String>>, fontScale: Float) {
        compose.setContent {
            // 640 dpi phone with the app's 90% Miuix scale; no system preferences are written.
            CompositionLocalProvider(LocalDensity provides Density(3.6f, fontScale)) {
                MiuixTheme {
                    Column(Modifier.width(320.dp)) {
                        rows.forEach { (title, value) ->
                            MiuixDropdownPreference(
                                entry = DropdownEntry(listOf(DropdownItem(text = value, selected = true))),
                                title = title, modifier = Modifier, summary = null,
                                startAction = null, bottomAction = null, maxHeight = null,
                                enabled = true, showValue = true, collapseOnSelection = true, onExpandedChange = null,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue("Missing layout for $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(text, layout.hasVisualOverflow)
            repeat(layout.lineCount) { assertFalse(text, layout.isLineEllipsized(it)) }
        }
    }
}
