package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureExpansionUiTest {
    @get:Rule val compose = createComposeRule()

    private val extraMounts = AtomicInteger()
    private val extraDisposals = AtomicInteger()
    private val siblingMounts = AtomicInteger()
    private val siblingDisposals = AtomicInteger()

    private fun setFixture(expanded: MutableState<Boolean>, standaloneRows: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            COUITheme {
                Box(Modifier.width(320.dp).testTag("surface")) {
                    val rows: FeatureAnimatedGroupScope.() -> Unit = {
                        row("parent") { _, _ ->
                            Box(Modifier.fillMaxWidth().height(48.dp))
                        }
                        row("extra", visible = expanded.value) { _, _ ->
                            DisposableEffect(Unit) {
                                extraMounts.incrementAndGet()
                                onDispose { extraDisposals.incrementAndGet() }
                            }
                            Box(Modifier.fillMaxWidth().height(96.dp).testTag("extra"))
                        }
                        row("sibling") { _, _ ->
                            DisposableEffect(Unit) {
                                siblingMounts.incrementAndGet()
                                onDispose { siblingDisposals.incrementAndGet() }
                            }
                            Box(Modifier.fillMaxWidth().height(48.dp).testTag("sibling"))
                        }
                    }
                    if (standaloneRows) FeatureAnimatedRows(content = rows)
                    else FeatureAnimatedGroup(content = rows)
                }
            }
        }
        advance(500)
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun height(): Float = compose.onNodeWithTag("surface", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.height

    @Test
    fun defaultGroupAnimatesBothDirectionsAndRetainsContentUntilExitFinishes() {
        val expanded = mutableStateOf(false)
        setFixture(expanded, standaloneRows = false)
        val collapsedHeight = height()
        compose.runOnIdle { expanded.value = true }
        advance(112)
        val openingHeight = height()
        advance(500)
        val expandedHeight = height()
        assertTrue(openingHeight > collapsedHeight)
        assertTrue(openingHeight < expandedHeight)

        compose.runOnIdle { expanded.value = false }
        advance(112)
        compose.onNodeWithTag("extra", useUnmergedTree = true).assertExists()
        assertTrue(height() > collapsedHeight)
        assertTrue(height() < expandedHeight)
        assertEquals(0, extraDisposals.get())
        advance(500)
        compose.onNodeWithTag("extra", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(collapsedHeight, height(), 1f)
        assertEquals(1, extraMounts.get())
        assertEquals(1, extraDisposals.get())
        assertEquals(1, siblingMounts.get())
        assertEquals(0, siblingDisposals.get())
    }

    @Test
    fun defaultRowsCanReverseCollapseWithoutRemountingOrLeavingHiddenSpace() {
        val expanded = mutableStateOf(true)
        setFixture(expanded, standaloneRows = true)
        val expandedHeight = height()
        repeat(3) {
            compose.runOnIdle { expanded.value = false }
            advance(80)
            compose.onNodeWithTag("extra", useUnmergedTree = true).assertExists()
            compose.runOnIdle { expanded.value = true }
            advance(80)
        }
        advance(500)
        compose.onAllNodesWithTag("extra", useUnmergedTree = true).assertCountEquals(1)
        assertEquals(expandedHeight, height(), 1f)
        assertEquals(1, extraMounts.get())
        assertEquals(0, extraDisposals.get())

        compose.runOnIdle { expanded.value = false }
        advance(500)
        compose.onNodeWithTag("extra", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(height() < expandedHeight)
        assertEquals(1, extraDisposals.get())
        assertEquals(1, siblingMounts.get())
        assertEquals(0, siblingDisposals.get())
    }
}
