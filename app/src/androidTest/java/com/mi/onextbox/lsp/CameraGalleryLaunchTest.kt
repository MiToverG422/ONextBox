package com.mi.onextbox.lsp

import android.app.KeyguardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mi.onextbox.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraGalleryLaunchTest {
    @Test fun selectedGooglePhotosCanOpenOnlyOurTemporaryPublishedTestImage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val target = "com.google.android.apps.photos"
        assumeFalse(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        assumeTrue(CameraGalleryIntents.selectedGallery(context) == target)
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ONextBox-gallery-test-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
            try {
                resolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)).use { scenario ->
                val opened = AtomicBoolean(false)
                scenario.onActivity { opened.set(CameraGalleryIntents.tryOpen(it, uri, false, "test-image")) }
                assertTrue("The selected gallery must launch for a published test image", opened.get())
                fun googlePhotosResumed(): Boolean = ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                ).bufferedReader().use { reader ->
                    reader.lineSequence().any { "topResumedActivity=" in it && "$target/" in it }
                }
                val deadline = SystemClock.uptimeMillis() + 5_000
                while (!googlePhotosResumed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                assertTrue("Google Photos must be the resumed viewer", googlePhotosResumed())
                val uid = context.packageManager.getApplicationInfo(target, 0).uid
                assertEquals(PackageManager.PERMISSION_GRANTED,
                    context.checkUriPermission(uri, -1, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        } finally {
            resolver.delete(uri, null, null)
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }
}
