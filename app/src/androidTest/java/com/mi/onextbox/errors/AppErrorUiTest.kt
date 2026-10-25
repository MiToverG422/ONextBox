package com.mi.onextbox.errors

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.screens.AppErrorSummaryCard
import com.mi.onextbox.ui.screens.AppErrorDetailContent
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppErrorUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun couiCardOpensAndCopies() = assertCard(AppUiStyle.ColorOs)

    @Test fun couiDetailsShowMetadataAndAllActions() = assertDetails(AppUiStyle.ColorOs)

    private fun assertDetails(style: AppUiStyle) {
        var copies = 0
        var shares = 0
        var exports = 0
        compose.setContent {
            COUITheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalAppUiStyle provides style) {
                        AppErrorDetailContent(
                            AppErrorRecord(time = 1, packageName = "com.example.test", processName = "com.example.test:worker", uid = 10123, pid = 123, kind = AppErrorKind.CRASH, summary = "Test error", detail = "Full test stack", metadata = AppErrorMetadata(throwFile = "Test.kt", throwClass = "Test", throwMethod = "run", throwLine = 42, device = "Test device", sdk = 37)),
                            onCopy = { copies++ }, onShare = { shares++ }, onExport = { exports++ }, exportEnabled = true,
                        )
                    }
                }
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(com.mi.onextbox.R.string.app_errors_copy)).performClick()
        compose.onNodeWithText(context.getString(com.mi.onextbox.R.string.app_errors_share)).performClick()
        compose.onNodeWithText(context.getString(com.mi.onextbox.R.string.app_errors_export)).performClick()
        compose.runOnIdle { assertEquals(1, copies); assertEquals(1, shares); assertEquals(1, exports) }
        compose.onNodeWithTag("appErrorDetails").performScrollToNode(androidx.compose.ui.test.hasText("Test.run · Test.kt:42"))
        compose.onNodeWithText("Test.run · Test.kt:42").assertIsDisplayed()
        compose.onNodeWithTag("appErrorDetails").performScrollToNode(androidx.compose.ui.test.hasText("Full test stack"))
        compose.onNodeWithText("Full test stack").assertIsDisplayed()
    }

    private fun assertCard(style: AppUiStyle) {
        var opens = 0
        var copies = 0
        compose.setContent {
            COUITheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalAppUiStyle provides style) {
                        AppErrorSummaryCard(
                            AppErrorRecord(id = 1, time = 1, appName = "Test app", packageName = "com.example.test", processName = "com.example.test:worker", uid = 10123, pid = 123, kind = AppErrorKind.CRASH, summary = "Test error"),
                            onOpen = { opens++ }, onCopy = { copies++ },
                        )
                    }
                }
            }
        }
        compose.onAllNodesWithText("com.example.test", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("com.example.test:worker", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("com.example.test").performClick()
        compose.onNodeWithText("com.example.test").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, opens); assertEquals(1, copies) }
    }
}
