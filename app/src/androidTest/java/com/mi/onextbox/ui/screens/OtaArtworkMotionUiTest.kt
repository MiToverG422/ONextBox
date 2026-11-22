package com.mi.onextbox.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.OtaArtwork
import com.mi.onextbox.lsp.OtaCrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class OtaArtworkMotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelKeepsCropDialogUntilSlideOutFinishes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Run on the isolated test application", context.packageName != "com.mi.onextbox")
        val prefs = LspPreferenceStore.prefs(context)
        val original = prefs.getString(OtaArtwork.KEY, null)
        val artwork = OtaArtwork(UUID.randomUUID().toString(), OtaCrop(.4f, .6f, 1.2f))
        val file = OtaArtwork.file(context, artwork.id)
        try {
            file.parentFile!!.mkdirs()
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
            assertTrue(prefs.edit().putString(OtaArtwork.KEY, artwork.encode()).commit())
            compose.mainClock.autoAdvance = false
            compose.setContent { COUITheme { Column { OtaArtworkSettings() } } }
            advance(32)
            compose.onNodeWithText(context.getString(R.string.ota_art_crop)).performClick()
            advance(32)
            advance(64)

            val help = compose.onNodeWithText(context.getString(R.string.ota_art_help), useUnmergedTree = true)
            val openingTop = help.getUnclippedBoundsInRoot().top
            advance(400)
            val shownTop = help.getUnclippedBoundsInRoot().top
            compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
            advance(96)
            val closingTop = help.getUnclippedBoundsInRoot().top
            if (context.resources.configuration.screenWidthDp < 600) {
                assertTrue("Dialog slides upward when opened", openingTop > shownTop)
                assertTrue("Dialog slides downward after Cancel", closingTop > shownTop)
            }
            advance(400)
            help.assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.ota_art_crop)).assertExists()
            assertEquals(artwork.encode(), prefs.getString(OtaArtwork.KEY, null))
            assertTrue(file.isFile)
        } finally {
            prefs.edit().apply {
                if (original == null) remove(OtaArtwork.KEY) else putString(OtaArtwork.KEY, original)
            }.commit()
            file.delete()
        }
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }
}
