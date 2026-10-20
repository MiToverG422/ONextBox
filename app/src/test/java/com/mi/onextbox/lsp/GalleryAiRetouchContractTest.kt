package com.mi.onextbox.lsp

import com.mi.onextbox.R
import com.mi.onextbox.ui.screens.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class GalleryAiRetouchContractTest {
    private fun source(path: String) = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()

    @Test fun galleryScopeIsLimitedToItsMainProcessAndDefaultsOff() {
        assertEquals(1, source("main/resources/META-INF/xposed/scope.list").lineSequence().count {
            it == GalleryAiRetouchSettings.PACKAGE_NAME
        })
        assertTrue(source("main/AndroidManifest.xml").contains("<package android:name=\"com.coloros.gallery3d\" />"))
        assertTrue(source("main/java/com/mi/onextbox/lsp/OosLspModuleEntry.kt")
            .contains("if (processName == GalleryAiRetouchSettings.PACKAGE_NAME)"))
        val settings = source("main/java/com/mi/onextbox/lsp/GalleryAiRetouchSettings.kt")
        assertTrue(settings.contains("getBoolean(KEY, false)"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(KEY, false) == true"))
    }

    @Test fun galleryPageSearchInventoryAndQuickSwitchShareTheSameDestination() {
        assertFalse(FeaturePageMode.Gallery.isNestedPage)
        assertEquals(listOf(GalleryAiRetouchSettings.PACKAGE_NAME), featureRestartPackages(FeaturePageMode.Gallery))
        assertEquals(FeaturePageMode.Gallery, FeatureSearchIndex.entries.single { it.title == R.string.gallery_page_title }.page)
        assertEquals(FeaturePageMode.Gallery, FeatureSearchIndex.entries.single { it.title == R.string.gallery_ai_retouch_title }.page)
        assertEquals(FeaturePageMode.Gallery, EnabledFeatureCatalog.entries.single { it.title == R.string.gallery_ai_retouch_title }.page)
        assertTrue(EnabledFeatureActions().supports(R.string.gallery_ai_retouch_title))
        val routes = source("main/java/com/mi/onextbox/ui/Root.kt")
        assertTrue(routes.contains("subclass(RootRoute.FeatureGallery::class)"))
        assertTrue(routes.contains("entry<RootRoute.FeatureGallery>"))
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/FeaturesScreen.kt")
            .contains("FeaturePageMode.Gallery -> GalleryFeaturesPage()"))
    }

    @Test fun noPartialHooksRemainOnFailureAndOnlyExactConfigRequestsAreChanged() {
        val hooks = source("main/java/com/mi/onextbox/lsp/GalleryAiRetouchHooker.kt")
        assertTrue(hooks.contains("if (!GalleryAiRetouchSettings.enabledXposed()"))
        assertTrue(hooks.contains("GalleryAiRetouchRules.shouldOverride(enabled, key)"))
        assertTrue(hooks.contains("ModernHookRegistry.unhookPrefix(prefix)"))
        assertTrue(hooks.contains("HotReloadLifecycle.register(stateKey, capture = { targets })"))
        listOf("setSystemProperty", "Build.MODEL", "Settings.Global", "setResult(true)").forEach { assertFalse(hooks.contains(it)) }
        val targets = source("main/java/com/mi/onextbox/lsp/GalleryAiRetouchTargets.kt")
        assertTrue(targets.contains("config.first.invokes"))
        assertTrue(targets.contains("GalleryAiRetouchRules.keyGetter"))
    }

    @Test fun allNineLocalesContainTheFourGalleryStrings() {
        val keys = setOf("gallery_page_title", "gallery_ai_retouch_title", "gallery_ai_retouch_summary", "gallery_save_failed")
        listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja",
            "values-ja-rCN", "values-ko", "values-vi", "values-ru").forEach { locale ->
            val file = listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml")).first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            val entries = (0 until nodes.length).map(nodes::item).filter { it.attributes.getNamedItem("name").nodeValue in keys }
            assertEquals(locale, keys.size, entries.size)
            entries.forEach { assertTrue(locale, it.textContent.isNotBlank()) }
        }
    }
}
