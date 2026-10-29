package com.mi.onextbox.lsp

import com.mi.onextbox.R
import com.mi.onextbox.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class CameraGalleryRulesTest {
    private interface EntrySource
    private class Controller : EntrySource
    private open class BaseUi(val controller: Controller)
    private class EmbeddedUi(controller: Controller) : BaseUi(controller)

    @Test fun embeddedControllerCanImplementAnEntryInterfaceInheritedByItsDeclaredFieldType() {
        val controller = Controller()
        val ui = EmbeddedUi(controller)
        val field = CameraGalleryTargets.instanceField(EmbeddedUi::class.java, EntrySource::class.java)
        assertEquals(BaseUi::class.java, field.declaringClass)
        assertSame(controller, field.get(ui))
    }

    @Test fun colorOsDefaultGalleryAcceptsTheNativeFlattenedComponentAndPackageFormats() {
        assertEquals("com.google.android.apps.photos", CameraGalleryRules.packageFromDefault(
            "com.google.android.apps.photos/com.google.android.apps.photos.MainActivity"))
        assertEquals("example.gallery", CameraGalleryRules.packageFromDefault("example.gallery/.Viewer"))
        assertEquals("example.gallery", CameraGalleryRules.packageFromDefault("example.gallery"))
        listOf(null, "", "null", "none", "android", "content://media", "/.Viewer").forEach {
            assertNull(CameraGalleryRules.packageFromDefault(it))
        }
    }

    @Test fun stockAndUnknownSelectionsKeepNativePreview() {
        (CameraGalleryRules.nativeGalleries + listOf(null, "", "android", CameraGallerySettings.PACKAGE_NAME))
            .forEach { assertFalse(CameraGalleryRules.externalGallery(it)) }
        assertTrue(CameraGalleryRules.externalGallery("com.google.android.apps.photos"))
        assertTrue(CameraGalleryRules.externalGallery("example.gallery"))
    }

    @Test fun secureLockedAndCaptureResultEntriesNeverOpenExternalGallery() {
        val main = "android.intent.action.MAIN"
        assertTrue(CameraGalleryRules.ordinaryCamera(main, false, false))
        assertTrue(CameraGalleryRules.ordinaryCamera("android.media.action.STILL_IMAGE_CAMERA", false, false))
        assertFalse(CameraGalleryRules.ordinaryCamera(main, true, false))
        assertFalse(CameraGalleryRules.ordinaryCamera(main, false, true))
        listOf("android.media.action.STILL_IMAGE_CAMERA_SECURE", "android.media.action.IMAGE_CAPTURE_SECURE",
            "android.media.action.IMAGE_CAPTURE", "android.media.action.VIDEO_CAPTURE").forEach {
            assertFalse(CameraGalleryRules.ordinaryCamera(it, false, false))
        }
    }

    @Test fun onlyPublishedPublicMediaCanBeGrantedToExternalViewer() {
        fun allowed(scheme: String? = "content", authority: String? = "media", mime: String? = "image/jpeg",
            pending: Int = 0, trashed: Int = 0) = CameraGalleryRules.publishedMedia(scheme, authority, mime, pending, trashed)
        assertTrue(allowed())
        assertTrue(allowed(mime = "video/mp4"))
        assertFalse(allowed(pending = 1))
        assertFalse(allowed(trashed = 1))
        assertFalse(allowed(scheme = "file"))
        assertFalse(allowed(authority = "com.open.gallery.smart.provider"))
        assertFalse(allowed(authority = "com.oplus.camera.gallery.cache.provider"))
        assertFalse(allowed(mime = "application/octet-stream"))
        assertFalse(allowed(mime = null))
    }

    @Test fun cameraHasIndependentScopeCatalogAndResetKeyWithoutAdvertisingC16() {
        assertEquals(listOf(CameraGallerySettings.PACKAGE_NAME), featureRestartPackages(FeaturePageMode.Camera))
        assertTrue(ModuleHotReloadPolicy.supportedPackages.contains(CameraGallerySettings.PACKAGE_NAME))
        val title = R.string.camera_default_gallery_title
        assertEquals(FeaturePageMode.Camera, FeatureSearchIndex.entries.single { it.title == title }.page)
        assertEquals(FeaturePageMode.Camera, EnabledFeatureCatalog.entries.single { it.title == title }.page)
        assertTrue(EnabledFeatureActions().supports(title))
        assertFalse(ColorOs16FeaturePolicy.isPageAvailable(FeaturePageMode.Camera))
        assertFalse(featureSearchEntries(SystemAdaptationVersion.ColorOs16).any { it.page == FeaturePageMode.Camera })
        assertEquals(setOf(CameraGallerySettings.FOLLOW_DEFAULT), CameraGallerySettings.keys)
    }
}
