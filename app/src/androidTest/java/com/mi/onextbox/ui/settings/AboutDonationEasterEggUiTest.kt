package com.mi.onextbox.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AboutDonationEasterEggUiTest {
    @get:Rule val compose = createComposeRule()

    private fun advance(time: Long) {
        compose.mainClock.advanceTimeBy(time)
        compose.waitForIdle()
    }

    private fun pixels(): IntArray {
        val image = compose.onNodeWithTag("donationFireworks", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        return IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
    }

    @Test fun donationFollowsTheAuthorAndAboutDoesNotExposeLogs() {
        var author = ""; var donation = ""; var contributors = ""; var logs = ""; var greeting = ""
        compose.setContent {
            author = stringResource(R.string.about_author_info_title)
            donation = stringResource(R.string.about_donate_title)
            contributors = stringResource(R.string.about_contributors_title)
            logs = stringResource(R.string.about_logs_title)
            greeting = stringResource(R.string.about_donation_greeting_title)
            COUITheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AboutMainPage({}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText(logs).assertDoesNotExist()
        val authorBounds = compose.onNodeWithText(author).getUnclippedBoundsInRoot()
        val donationBounds = compose.onNodeWithText(donation).getUnclippedBoundsInRoot()
        val contributorBounds = compose.onNodeWithText(contributors).getUnclippedBoundsInRoot()
        assertTrue(donationBounds.top > authorBounds.top && contributorBounds.top > donationBounds.top)
        compose.onNodeWithText(donation).performScrollTo().performClick()
        compose.onNodeWithText(greeting).assertExists()
        compose.onNodeWithTag("donationFireworks", useUnmergedTree = true).assertExists()
    }

    @Test fun fireworksFinishStayIdleAndTheRightButtonDismisses() {
        val show = mutableStateOf(true)
        var close = ""; var donate = ""; var donations = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            close = stringResource(R.string.about_donation_keep_money)
            donate = stringResource(R.string.about_donation_continue)
            COUITheme { DonationGreetingDialog(show.value, { show.value = false }, { donations++ }) }
        }
        advance(600)
        val launching = pixels()
        advance(1000)
        val burst = pixels()
        assertFalse("Fireworks did not move", launching.contentEquals(burst))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithTag("donationFireworks", useUnmergedTree = true).captureToImage().asAndroidBitmap().let { image ->
            File(context.cacheDir, "donation-fireworks.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        advance(5000)
        val finished = pixels()
        advance(1000)
        assertArrayEquals("Fireworks should stop after one finite celebration", finished, pixels())
        val left = compose.onNodeWithText(donate).getUnclippedBoundsInRoot()
        val right = compose.onNodeWithText(close).getUnclippedBoundsInRoot()
        assertTrue(left.right < right.left)
        compose.onNodeWithText(close).performClick()
        advance(600)
        compose.onNodeWithTag("donationFireworks", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, donations)
    }

    @Test fun theLeftButtonOpensDonationOnceAfterTheDialogFinishesClosing() {
        val show = mutableStateOf(true)
        var donate = ""; var donations = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            donate = stringResource(R.string.about_donation_continue)
            COUITheme { DonationGreetingDialog(show.value, { show.value = false }, { donations++ }) }
        }
        advance(600)
        compose.onNodeWithText(donate).performClick()
        assertEquals(0, donations)
        advance(600)
        assertEquals(1, donations)
        advance(600)
        assertEquals(1, donations)
        compose.onNodeWithTag("donationFireworks", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun donationPageShowsTheFullRecordAndAllSortOptionsWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var instructions = ""; var sort = ""; var labels = emptyList<String>()
        val record = DonationRecord("Test supporter", 2000, LocalDate.of(2026, 10, 20))
        compose.setContent {
            instructions = stringResource(R.string.about_donation_instructions)
            sort = stringResource(R.string.about_donation_sort)
            val resources = LocalResources.current
            labels = listOf(R.string.about_donation_sort_amount_desc, R.string.about_donation_sort_amount_asc,
                R.string.about_donation_sort_time_desc, R.string.about_donation_sort_time_asc).map(resources::getString)
            COUITheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AboutDonationPage(listOf(record), refreshFailed = false)
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.about_donation_responsible)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.about_donation_wechat_code)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_donation_wechat_note)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(instructions).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(record.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("20 CNY").assertIsDisplayed()
        compose.onNodeWithText("2026/10/20").assertIsDisplayed()
        labels.forEach { label ->
            compose.onNodeWithText(sort).performScrollTo().performClick()
            val options = compose.onAllNodesWithText(label)
            options[options.fetchSemanticsNodes().lastIndex].performClick()
            compose.waitForIdle()
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithText(record.name).performScrollTo().assertIsDisplayed()
        }
    }
}
