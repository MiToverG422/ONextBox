package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.ScreenTranslationSettings
import com.mi.onextbox.lsp.SystemAdaptationVersion
import org.junit.Assert.*
import org.junit.Test

class ScreenTranslationPageTest {
    @Test fun translationHasItsOwnRestartTargetAndSearchDestination() {
        assertEquals(listOf(ScreenTranslationSettings.PACKAGE_NAME), featureRestartPackages(FeaturePageMode.Translation))
        val entries = featureSearchEntries(SystemAdaptationVersion.ColorOs17)
        assertTrue(entries.any { it.page == FeaturePageMode.Translation && it.title == R.string.translation_allow_floating_title })
        assertTrue(EnabledFeatureCatalog.entries.any { it.page == FeaturePageMode.Translation && it.title == R.string.translation_allow_floating_title })
    }

    @Test fun unauditedColorOs16TranslationIsNotAdvertisedAsAdapted() {
        assertFalse(ColorOs16FeaturePolicy.isPageAvailable(FeaturePageMode.Translation))
        assertFalse(featureSearchEntries(SystemAdaptationVersion.ColorOs16).any { it.page == FeaturePageMode.Translation })
    }

    @Test fun newRestrictionsFollowFloatingInTheRequestedOrderWithCompleteCatalogEntries() {
        val rows = SharedFeatureDefinitions.translationRows
        assertEquals(listOf(ScreenTranslationSettings.ALLOW_FLOATING, ScreenTranslationSettings.BYPASS_APP_BLACKLIST,
            ScreenTranslationSettings.ALLOW_RESTRICTED_PAGES, ScreenTranslationSettings.ALLOW_LANDSCAPE,
            ScreenTranslationSettings.UNLOCK_BROWSER), rows.map { it.first })
        assertEquals(ScreenTranslationSettings.keys.toList(), rows.map { it.first })
        val titles = listOf(R.string.translation_allow_floating_title, R.string.translation_bypass_app_blacklist_title,
            R.string.translation_allow_restricted_pages_title, R.string.translation_allow_landscape_title,
            R.string.translation_unlock_browser_title)
        assertEquals(titles, rows.map { it.second.title })
        val search = featureSearchEntries(SystemAdaptationVersion.ColorOs17)
        val actions = EnabledFeatureActions()
        titles.forEach { title ->
            assertEquals(FeaturePageMode.Translation, search.single { it.title == title }.page)
            assertEquals(FeaturePageMode.Translation, EnabledFeatureCatalog.entries.single { it.title == title }.page)
            assertTrue(actions.supports(title))
        }
    }
}
