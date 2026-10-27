package com.mi.onextbox.lsp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraGalleryTargetsTest {
    @Test fun installedCameraResolvesBothPreviewPathsAndSecureGuardWithoutLaunchingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(CameraGallerySettings.PACKAGE_NAME,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val groups = ApkDexSession.withSession(info.sourceDir) { open -> CameraGalleryTargets.resolveAll(open(), loader) }
        assertTrue(groups.containsKey(CameraGalleryTargets.SCHEMA))
        val targets = groups.getValue("ordinary")
        val preview = targets[0]
        assertEquals(Boolean::class.javaPrimitiveType, preview.returnType)
        assertEquals(3, preview.parameterCount)
        assertEquals(listOf("android.content.Intent", "boolean", "android.net.Uri"), targets[1].parameterTypes.map { it.name })
        assertEquals("com.oplus.camera.entry.CameraEntry", targets[2].declaringClass.name)
        assertEquals(Boolean::class.javaPrimitiveType, targets[2].returnType)
        assertEquals(listOf("boolean"), targets[3].parameterTypes.map { it.name })
        assertEquals(0, targets[4].parameterCount)
        assertEquals(Void.TYPE, targets[4].returnType)
        assertEquals("android.view.View", targets[5].parameterTypes.first().name)
        assertEquals("android.graphics.Bitmap", targets[5].parameterTypes.last().name)
        assertEquals(1, preview.declaringClass.declaredFields.count { it.type == Activity::class.java })
        assertEquals(1, preview.declaringClass.declaredFields.count { it.type == targets[2].declaringClass })
        assertEquals(1, preview.parameterTypes[1].declaredFields.count { it.type == Uri::class.java })
        val embedded = groups.getValue("embedded")
        assertEquals(Void.TYPE, embedded[0].returnType)
        assertEquals(listOf("android.view.View", "android.net.Uri", "java.lang.String", "android.graphics.Bitmap", "int", "int"),
            embedded[0].parameterTypes.take(6).map { it.name })
        assertEquals(targets[2].declaringClass, embedded[1].returnType)
        assertEquals(0, embedded[1].parameterCount)
        val controller = CameraGalleryTargets.instanceField(embedded[0].declaringClass, embedded[1].declaringClass)
        assertNotEquals(embedded[0].declaringClass, controller.declaringClass)
        assertEquals(Activity::class.java, CameraGalleryTargets.instanceField(targets[2].declaringClass, Activity::class.java).type)
        assertTrue(embedded.drop(2).contains(targets[5]))
    }

    @Test fun standardViewGrantsOnlyTheClickedMediaAndHasNoOemAnimationOrPrivateExtras() {
        listOf("image/jpeg", "video/mp4").forEach { mime ->
            val uri = Uri.parse("content://media/external/images/media/0")
            val intent = CameraGalleryIntents.viewIntent(uri, mime, "com.google.android.apps.photos")
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(uri, intent.data)
            assertEquals(mime, intent.type)
            assertEquals("com.google.android.apps.photos", intent.`package`)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
            assertEquals(1, intent.clipData!!.itemCount)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertNull(intent.extras)
            assertNull(intent.component)
        }
    }

    @Test fun gallerySelectionUsesTheSameCustomizationAndImagePreferenceAsSystemDefaultApps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preference = CameraGalleryIntents.galleryPreferenceIntent()
        assertEquals(Intent.ACTION_VIEW, preference.action)
        assertEquals("image/*", preference.type)
        assertEquals("content://media", preference.dataString)
        assertTrue(preference.hasCategory(Intent.CATEGORY_DEFAULT))
        val expected = CameraGalleryIntents.customizedGallery(context) ?: context.packageManager.resolveActivity(
            preference, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        assertNotNull(expected)
        assertEquals(expected, CameraGalleryIntents.selectedGallery(context))
    }
}
