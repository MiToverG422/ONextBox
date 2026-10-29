package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemAdaptationVersion
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeatureUpdatesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun updateActionIsLeftOfBothExistingActionsAndOpensTheCollection() {
        var opened: FeaturePageMode? = null
        lateinit var enabled: String
        lateinit var restart: String
        compose.setContent {
            enabled = LocalResources.current.getString(R.string.enabled_features_title)
            restart = LocalResources.current.getString(R.string.feature_restart_all_scope_title)
            COUITheme {
                FeatureMainRoute(Modifier.fillMaxSize(), 0.dp, null, false,
                    onOpen = { mode, _ -> opened = mode })
            }
        }
        val updatesBounds = compose.onNodeWithTag("featureUpdatesAction").getUnclippedBoundsInRoot()
        val enabledBounds = compose.onNodeWithContentDescription(enabled).getUnclippedBoundsInRoot()
        val restartBounds = compose.onNodeWithContentDescription(restart).getUnclippedBoundsInRoot()
        assertTrue(updatesBounds.right <= enabledBounds.left)
        assertTrue(enabledBounds.right <= restartBounds.left)
        compose.onNodeWithTag("featureUpdatesAction").performClick()
        compose.runOnIdle { assertEquals(FeaturePageMode.Updates, opened) }
    }

    @Test fun unreadDotCanDisappearWithoutRemovingOrMovingTheAction() {
        val unread = mutableStateOf(true)
        var clicks = 0
        compose.setContent {
            COUITheme { FeatureUpdatesButton(unread.value) { clicks++; unread.value = false } }
        }
        val before = compose.onNodeWithTag("featureUpdatesAction").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("featureUpdatesUnread", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("featureUpdatesAction").performClick()
        compose.onNodeWithTag("featureUpdatesUnread", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("featureUpdatesAction").getUnclippedBoundsInRoot())
        assertEquals(1, clicks)
    }

    @Test fun collectionLinksToTheExactFeatureAndCanFilterOtherEntries() {
        var opened: FeatureUpdatesCatalog.Item? = null
        lateinit var clockTitle: String
        lateinit var otherTitle: String
        compose.setContent {
            clockTitle = LocalResources.current.getString(R.string.clock_tabular_digits)
            otherTitle = LocalResources.current.getString(R.string.browser_page_title)
            COUITheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    FeatureUpdatesContent(FeatureUpdatesCatalog.items(SystemAdaptationVersion.ColorOs17)) { opened = it }
                }
            }
        }
        compose.onNodeWithText(clockTitle).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(FeaturePageMode.SystemUiClock, opened?.destination?.page)
            assertEquals(R.string.clock_tabular_digits, opened?.destination?.title)
        }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(clockTitle)
        compose.onAllNodesWithText(otherTitle).assertCountEquals(0)
        compose.onAllNodesWithText(clockTitle).assertCountEquals(2)
    }

    @Test fun targetScrollsIntoViewWithoutChangingTheSwitch() {
        val spotlight = FeatureSpotlightState().apply {
            show(FeatureSearchIndex.Entry(FeaturePageMode.SystemUiStatusBar, R.string.power_indicator_title))
        }
        var changes = 0
        lateinit var title: String
        lateinit var scroll: androidx.compose.foundation.ScrollState
        compose.setContent {
            title = LocalResources.current.getString(R.string.power_indicator_title)
            scroll = rememberScrollState()
            CompositionLocalProvider(LocalFeatureSpotlight provides spotlight) {
                COUITheme {
                    FeatureSpotlightPage(FeaturePageMode.SystemUiStatusBar, 56.dp) {
                        Column(Modifier.height(280.dp).verticalScroll(scroll)) {
                            Spacer(Modifier.height(800.dp))
                            SettingsGroup {
                                SettingsToggleRow(title = title, summary = "", checked = false,
                                    onCheckedChange = { changes++ })
                                SettingsToggleRow(title = "Other", summary = "", checked = false,
                                    onCheckedChange = { changes++ })
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5000) { scroll.value > 0 }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, changes) }
    }
}
