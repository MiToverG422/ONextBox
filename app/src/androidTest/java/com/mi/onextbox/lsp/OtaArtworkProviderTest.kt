package com.mi.onextbox.lsp

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OtaArtworkProviderTest {
    @Test fun providerRetainsAnimatedFramesWhileThePickerGrantCanExpire() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH/C05FVFNDQVBFMi4wAwEAAAAh+QQADAAAACwAAAAAAQABAAACAkQBACH5BAAMAAAALAAAAAABAAEAAAICTAEAOw==", Base64.DEFAULT)
        val input = File.createTempFile("test-art-", ".gif", context.cacheDir)
        val id = UUID.randomUUID().toString()
        val file = OtaArtwork.file(context, id)
        var staged: File? = null
        try {
            input.writeBytes(data)
            val copy = OtaArtwork.stage(context, Uri.fromFile(input))
            staged = copy
            file.parentFile!!.mkdirs()
            copy.copyTo(file)
            assertTrue(input.delete())
            val resolver = context.contentResolver
            val uri = OtaArtwork.uri(id)
            assertArrayEquals(data, resolver.openInputStream(uri)!!.use { it.readBytes() })
            val decoded: Drawable = OtaArtwork.decode(ImageDecoder.createSource(resolver, uri), 720)
            assertTrue(decoded is AnimatedImageDrawable)
            assertEquals(1, decoded.intrinsicWidth)
            assertThrows(Exception::class.java) { resolver.openFileDescriptor(uri, "w")?.close() }
        } finally {
            input.delete()
            staged?.delete()
            file.delete()
        }
    }
}
