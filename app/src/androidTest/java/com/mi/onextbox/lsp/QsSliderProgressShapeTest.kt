package com.mi.onextbox.lsp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.drawable.ColorDrawable
import android.hardware.HardwareBuffer
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.lsposed.hiddenapibypass.HiddenApiBypass

@RunWith(AndroidJUnit4::class)
class QsSliderProgressShapeTest {
    private val shapes = QsSliderProgressShape()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    @Before fun allowVendorPathApi() {
        HiddenApiBypass.addHiddenApiExemptions("Lcom/oplus/graphics/", "Landroid/graphics/")
    }

    @Test fun customSmoothCornersDifferFromTheOldCircularWhiteFill() {
        val drawable = ColorDrawable().apply { setBounds(12, 12, 196, 420) }
        val radius = 184f * .86f / 2f
        val actual = renderHardware { it.drawPath(shapes.path(drawable, radius, 3f), paint) }
        val old = renderHardware { it.drawRoundRect(RectF(drawable.bounds), radius, radius, paint) }
        var changed = 0
        for (y in 12 until 104) for (x in 12 until 104)
            if (actual.getPixel(x, y) != old.getPixel(x, y)) changed++
        assertTrue("The progress fill must retain the native G2 corner: changed=$changed", changed > 30)
        actual.recycle()
        old.recycle()
    }

    @Test fun nativeProgressClipIsRetainedAtAllLevelsAndAfterBoundsOrRadiusChanges() {
        val drawable = ColorDrawable()
        for (width in listOf(184, 208)) for (percent in listOf(0, 25, 86, 100)) {
            drawable.setBounds(12, 12, 12 + width, 420)
            val radius = width * percent / 200f
            val path = shapes.path(drawable, radius, 3f)
            val measured = RectF().also { path.computeBounds(it, true) }
            assertEquals(drawable.bounds.left.toFloat(), measured.left, .001f)
            assertEquals(drawable.bounds.top.toFloat(), measured.top, .001f)
            assertEquals(drawable.bounds.right.toFloat(), measured.right, .001f)
            assertEquals(drawable.bounds.bottom.toFloat(), measured.bottom, .001f)
            for (level in listOf(0, 1, 50, 99, 100)) {
                val bitmap = Bitmap.createBitmap(232, 432, Bitmap.Config.ARGB_8888)
                val top = 420 - 408 * level / 100
                Canvas(bitmap).apply {
                    clipRect(12, top, 12 + width, 420)
                    drawPath(path, paint)
                }
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(12 + width / 2, 5))
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(5, 216))
                if (top > 12) assertEquals(Color.TRANSPARENT, bitmap.getPixel(12 + width / 2, top - 1))
                if (level > 0) assertEquals(Color.WHITE, bitmap.getPixel(12 + width / 2, 418))
                bitmap.recycle()
            }
        }
    }

    @Test fun nativeFullCornersDoNotAcquireASmoothCornerWhenWeightIsAbsent() {
        val drawable = ColorDrawable().apply { setBounds(10, 10, 194, 418) }
        val expected = Path().apply { addRoundRect(RectF(drawable.bounds), 92f, 92f, Path.Direction.CW) }
        val actual = shapes.path(drawable, 92f, null)
        val first = Bitmap.createBitmap(208, 430, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(208, 430, Bitmap.Config.ARGB_8888)
        Canvas(first).drawPath(expected, paint)
        Canvas(second).drawPath(actual, paint)
        assertTrue(first.sameAs(second))
    }

    @Test fun maskPathAndProgressKeepTheSameGpuShapeWhileCornersAreChanging() {
        val drawable = ColorDrawable().apply { setBounds(12, 12, 196, 420) }
        val mask = Path()
        for (percent in listOf(86, 0, 25, 100, 50)) {
            val radius = 184f * percent / 200f
            shapes.replace(mask, RectF(drawable.bounds), radius, 3f)
            val actual = renderHardware { it.drawPath(shapes.path(drawable, radius, 3f), paint) }
            val spotlight = renderHardware { it.drawPath(mask, paint) }
            assertTrue("Mask diverged from material at $percent%", actual.sameAs(spotlight))
            if (percent == 0) {
                val square = renderHardware { it.drawRect(RectF(drawable.bounds), paint) }
                assertTrue("A previous smooth corner must not survive a switch to square corners", actual.sameAs(square))
                square.recycle()
            }
            actual.recycle()
            spotlight.recycle()
        }
    }

    @Test fun installedSystemUiRetainsTheSpotlightAndTheNewBoundsAnimationEntrypoints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.distinct().joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val seek = Class.forName(QsControlCenterCornerTargets.SEEK_BAR, false, loader)
        assertEquals(Void.TYPE, seek.getDeclaredMethod("updateSpotLightPath", Path::class.java, Any::class.java).returnType)
        val manager = Class.forName(QsControlCenterCornerTargets.DETAIL_ANIMATION, false, loader)
        val entrypoints = manager.declaredMethods.filter { it.name == "beginTransition" }
        assertTrue(entrypoints.any { it.parameterCount in setOf(3, 5) })
        for (method in entrypoints) if (method.parameterCount == 5)
            assertTrue(method.parameterTypes.takeLast(2).all { it == android.graphics.Rect::class.java })
        for (name in listOf("\$doStartAnimation\$endListener\$1", "\$doHideAnimation\$endListener\$1")) {
            val end = Class.forName(QsControlCenterCornerTargets.DETAIL_ANIMATION + name, false, loader)
            assertTrue(end.declaredMethods.any { it.name == "onAnimationEnd" && it.parameterCount in setOf(1, 4) })
        }
    }

    private fun renderHardware(draw: (Canvas) -> Unit): Bitmap {
        val node = RenderNode("qs-slider-progress-test").apply { setPosition(0, 0, 210, 432) }
        val canvas = node.beginRecording(210, 432)
        draw(canvas)
        node.endRecording()
        val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        HardwareBuffer.create(210, 432, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT).use { buffer ->
            HardwareBufferRenderer(buffer).use { renderer ->
                renderer.setContentRoot(node)
                val done = CountDownLatch(1)
                var success = false
                renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(Executor { it.run() }) { result ->
                    success = result.status == HardwareBufferRenderer.RenderResult.SUCCESS &&
                        result.fence.use { it.await(Duration.ofSeconds(3)) }
                    done.countDown()
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertTrue(success)
                val hardware = requireNotNull(Bitmap.wrapHardwareBuffer(buffer, colorSpace))
                return requireNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false)).also { hardware.recycle() }
            }
        }
    }
}
