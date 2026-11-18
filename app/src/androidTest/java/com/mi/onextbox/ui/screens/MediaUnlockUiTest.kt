package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.MediaUnlockCatalog
import io.github.suqi8.coui.kmp.theme.COUITheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaUnlockUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun row(title: Int) = compose.onNodeWithText(context.getString(title))
    private fun settings() = LspPreferenceStore.prefs(context).all.filterKeys { it in MediaUnlockCatalog.keys }

    @Test fun cameraMenuNavigatesToSeparateFilterAndWatermarkPages() {
        val before = settings()
        var page by mutableStateOf<FeaturePageMode?>(null)
        compose.setContent {
            COUITheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (page == null) CameraFeaturesPage { page = it } else MediaUnlockSettingsPage(page!!)
            } }
        }
        row(R.string.camera_unlock_filter_portra).assertDoesNotExist()
        row(R.string.media_unlock_group_filters).performClick()
        compose.runOnIdle { assertEquals(FeaturePageMode.CameraFilters, page) }
        row(R.string.camera_unlock_filter_portra).assertExists()
        row(R.string.camera_unlock_filter_tx400).assertExists()
        row(R.string.camera_unlock_watermark_hasselblad).assertDoesNotExist()
        compose.runOnIdle { page = null }
        row(R.string.media_unlock_group_watermarks).performClick()
        compose.runOnIdle { assertEquals(FeaturePageMode.CameraWatermarks, page) }
        row(R.string.camera_unlock_watermark_hasselblad).assertExists()
        row(R.string.camera_unlock_filter_portra).assertDoesNotExist()
        assertEquals(before, settings())
    }

    @Test fun gallerySearchDestinationShowsOnlyTheSelectedCategoryWithoutDescriptions() {
        val before = settings()
        val entry = FeatureSearchIndex.entries.single { it.title == R.string.gallery_unlock_watermark_lumo }
        assertEquals(FeaturePageMode.GalleryWatermarks, entry.page)
        compose.setContent {
            COUITheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MediaUnlockSettingsPage(entry.page)
            } }
        }
        row(R.string.gallery_unlock_watermark_lumo).assertExists()
        row(R.string.gallery_unlock_filter_ai).assertDoesNotExist()
        compose.onAllNodes(hasText("Each type is independent.", substring = true)).assertCountEquals(0)
        assertEquals(before, settings())
    }
}
