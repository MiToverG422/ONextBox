package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.MediaUnlockCatalog
import org.junit.Assert.*
import org.junit.Test

class MediaUnlockPagesTest {
    @Test fun everyOptionHasExactlyOneCategoryAndSearchDestination() {
        MediaUnlockCatalog.options.forEach { option ->
            val page = MediaUnlockPage.forOption(option)
            assertEquals(option.app, page.app)
            assertEquals(option.group, page.group)
            assertTrue(page.mode.isNestedPage)
            assertEquals(page.mode, FeatureSearchIndex.entries.single { it.title == option.title }.page)
            assertEquals(page.mode, EnabledFeatureCatalog.entries.single { it.title == option.title }.page)
        }
    }

    @Test fun categoryPagesRetainTheCorrectAppRestartAndInventoryGroup() {
        MediaUnlockPage.entries.forEach { page ->
            val expected = if (page.group == MediaUnlockCatalog.Group.Watermarks)
                listOf(MediaUnlockCatalog.App.Camera.packageName, MediaUnlockCatalog.App.Gallery.packageName)
                else listOf(page.app.packageName)
            assertEquals(expected, featureRestartPackages(page.mode))
            assertEquals(if (page.app == MediaUnlockCatalog.App.Camera) FeaturePageMode.Camera else FeaturePageMode.Gallery,
                EnabledFeatureCatalog.group(page.mode))
        }
        assertEquals(MediaUnlockPage.entries.size, MediaUnlockPage.byMode.size)
    }

    @Test fun newRowsHaveNoDescriptionsAndNoParentSwitchDependency() {
        MediaUnlockFeatureDefinitions.all.forEach { definition ->
            assertEquals(0, definition.summary)
            assertEquals(0, FeatureSearchIndex.entries.single { it.title == definition.title }.description)
            assertTrue(EnabledFeatureCatalog.entries.single { it.title == definition.title }.parents.isEmpty())
        }
    }
}
