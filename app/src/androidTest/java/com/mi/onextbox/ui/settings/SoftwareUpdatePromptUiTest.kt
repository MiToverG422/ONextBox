package com.mi.onextbox.ui.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.ColorOs17AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoftwareUpdatePromptUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun anAvailableVersionOffersCancelAndOpenUpdate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as ComponentActivity
        try {
            val version = mutableStateOf<String?>(null)
            var opens = 0
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.setContent {
                    ColorOs17AppTheme(nativeAccent = true) {
                        SoftwareUpdatePrompt(
                            versionName = version.value,
                            onDismiss = { version.value = null },
                            onOpenSoftwareUpdate = {
                                opens++
                                version.value = null
                            },
                        )
                    }
                }
            }

            val title = compose.onNodeWithText(context.getString(R.string.update_notification_title))
            val cancel = compose.onNodeWithText(context.getString(android.R.string.cancel))
            val open = compose.onNodeWithText(context.getString(R.string.update_prompt_open))
            title.assertDoesNotExist()

            compose.runOnIdle { version.value = "99.0.1" }
            compose.onNodeWithText(context.getString(R.string.update_prompt_body, "99.0.1")).assertIsDisplayed()
            assertTrue(cancel.fetchSemanticsNode().boundsInRoot.center.x < open.fetchSemanticsNode().boundsInRoot.center.x)
            cancel.performClick()
            title.assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, opens) }

            compose.runOnIdle { version.value = "99.0.2" }
            open.performClick()
            title.assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, opens) }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }
}
