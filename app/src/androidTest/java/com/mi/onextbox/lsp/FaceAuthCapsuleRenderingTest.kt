package com.mi.onextbox.lsp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import java.lang.ref.WeakReference
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FaceAuthCapsuleRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val d = context.resources.displayMetrics.density

    private fun motion(view: FaceAuthCapsuleView) =
        FaceAuthCapsuleView::class.java.getDeclaredField("motion").apply { isAccessible = true }.get(view) as ValueAnimator

    private fun value(view: FaceAuthCapsuleView, name: String) =
        FaceAuthCapsuleView::class.java.getDeclaredField(name).apply { isAccessible = true }.getFloat(view)

    private fun view(contentTop: () -> Float? = { 135f * d }) = FaceAuthCapsuleView(context).apply {
        layout(0, 0, (400 * d).toInt(), (150 * d).toInt())
        nativeLayout = { FaceAuthCapsuleNativeLayout(5f * d, 30f * d, 100f * d, 15f * d) }
        protectedContentTop = contentTop
        onRenderFailure = { throw AssertionError("Capsule rendering failed", it) }
    }

    private fun capture(view: FaceAuthCapsuleView, time: Long, name: String? = null, background: Bitmap? = null): Bitmap {
        motion(view).currentPlayTime = time
        return (background?.copy(Bitmap.Config.ARGB_8888, true) ?:
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)).also { bitmap ->
            view.draw(Canvas(bitmap))
            if (name != null) {
                val output = File(context.getExternalFilesDir(null), "face_capsule_v6_test").apply { mkdirs() }
                File(output, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun greenPixels(bitmap: Bitmap, fromY: Int = 0): Int {
        var count = 0
        for (y in fromY until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 32 && Color.green(pixel) > 140) count++
        }
        return count
    }

    @Test fun aClosedLockBecomesAnOpenLockWithoutASeparateFaceOrCheckmark() {
        instrumentation.runOnMainSync {
            val view = view()
            try {
                view.show(FaceAuthCapsuleState.Phase.Success)
                motion(view).pause()
                val closed = capture(view, 320, "lock_closed")
                assertEquals(0f, value(view, "unlock"), 0f)
                val opening = capture(view, 540, "lock_opening")
                assertTrue(value(view, "unlock") > 0f && value(view, "unlock") < 1f)
                val open = capture(view, 950, "lock_open")
                assertEquals(1f, value(view, "unlock"), 0f)
                assertTrue(greenPixels(closed, (40 * d).toInt()) > 50)
                assertTrue(greenPixels(open, (40 * d).toInt()) > 20)
                val done = capture(view, FaceAuthCapsuleMotion.SUCCESS_DURATION, "done")
                assertEquals(0, done.getPixel(view.width / 2, (20 * d).toInt()))
                listOf(closed, opening, open, done).forEach(Bitmap::recycle)
            } finally { view.dispose() }
        }
    }

    @Test fun lateChargingTextCannotResizeTheCapsuleOrBeCoveredTogetherWithTheDate() {
        instrumentation.runOnMainSync {
            var protectedTop = 44f * d
            var boundaryReads = 0
            val view = view { boundaryReads++; protectedTop }
            val text = TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams((200 * d).toInt(), (24 * d).toInt())
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 13f * d)
                gravity = Gravity.CENTER
                measure(View.MeasureSpec.makeMeasureSpec((200 * d).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((24 * d).toInt(), View.MeasureSpec.EXACTLY))
                layout(0, 0, measuredWidth, measuredHeight)
            }
            fun background(charging: Boolean): Bitmap {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(0xFF635B51.toInt())
                val labels = listOf("11月1日 周日 九月廿三" to 74f, "16:06" to 104f) +
                    if (charging) listOf("充电完成" to 44f) else emptyList()
                for ((label, y) in labels) {
                    text.text = label
                    val save = canvas.save()
                    canvas.translate(100f * d, y * d)
                    text.draw(canvas)
                    canvas.restoreToCount(save)
                }
                return bitmap
            }
            try {
                view.show(FaceAuthCapsuleState.Phase.Success)
                motion(view).pause()
                for (time in listOf(40L, 400L, 950L, 1080L, 1280L, 1460L)) {
                    // Late metric changes cannot turn a horizontal success into a tall card.
                    if (time >= 1080L) protectedTop = 135f * d
                    val base = background(time >= 1080L)
                    val image = capture(view, time, "protected_" + time, base)
                    for (y in (40 * d).toInt() until view.height) for (x in 0 until view.width) {
                        assertEquals("Native content overwritten at y=" + y + ", time=" + time,
                            base.getPixel(x, y), image.getPixel(x, y))
                    }
                    if (time == 400L || time == 950L) {
                        assertTrue("Horizontal icon was clipped", greenPixels(image) > 20)
                        for (y in (6 * d).toInt() until (34 * d).toInt()) {
                            for (x in (189 * d).toInt() until (211 * d).toInt()) {
                                assertEquals("Icon crosses the camera cutout", Color.BLACK, image.getPixel(x, y))
                            }
                        }
                    }
                    base.recycle(); image.recycle()
                }
                assertEquals("Placement changed during an authentication sequence", 1, boundaryReads)
                assertEquals(0f, text.translationY, 0f)
            } finally { view.dispose() }
        }
    }

    @Test fun rendererUsesTheFrameworkNativeCapsuleInterpolators() {
        val geometry = PathInterpolator(0.3f, 0f, 0.1f, 1f)
        instrumentation.runOnMainSync {
            val view = view()
            try {
                view.show(FaceAuthCapsuleState.Phase.Success)
                val animator = motion(view).apply { pause() }
                for (sample in 0..340 step 2) {
                    animator.currentPlayTime = 40L + sample
                    assertEquals(geometry.getInterpolation(sample / 340f), value(view, "expansion"), 0.00001f)
                }
                for (sample in 0..400 step 2) {
                    animator.currentPlayTime = 340L + sample
                    assertEquals(geometry.getInterpolation(sample / 400f), value(view, "unlock"), 0.00001f)
                }
            } finally { view.dispose() }
        }
    }

    @Test fun scanningToSuccessKeepsTheCapsuleVisibleWithoutAFlash() {
        instrumentation.runOnMainSync {
            val view = view()
            try {
                view.show(FaceAuthCapsuleState.Phase.Scanning)
                motion(view).apply { pause(); currentPlayTime = duration }
                view.show(FaceAuthCapsuleState.Phase.Success)
                motion(view).apply { pause(); currentPlayTime = 0 }
                val bitmap = capture(view, 0)
                assertEquals(255, Color.alpha(bitmap.getPixel(view.width / 2, (20 * d).toInt())))
                bitmap.recycle()
            } finally { view.dispose() }
        }
    }

    @Test fun idleLockIsStaticAndCredentialSuccessKeepsItVisibleWhileOpening() {
        instrumentation.runOnMainSync {
            val view = view()
            try {
                view.show(FaceAuthCapsuleState.Phase.Locked)
                motion(view).apply { pause(); currentPlayTime = duration }
                val idle = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(idle))
                assertEquals(0f, value(view, "unlock"), 0f)
                assertEquals(0f, value(view, "expansion"), 0f)
                assertNull(FaceAuthCapsuleView::class.java.getDeclaredField("scan")
                    .apply { isAccessible = true }.get(view))
                assertTrue(greenPixels(idle) > 20)
                view.show(FaceAuthCapsuleState.Phase.Success)
                motion(view).pause()
                val accepted = capture(view, 0)
                assertEquals(255, Color.alpha(accepted.getPixel(view.width / 2, (20 * d).toInt())))
                capture(view, 950).recycle()
                assertEquals(1f, value(view, "unlock"), 0f)
                idle.recycle(); accepted.recycle()
            } finally { view.dispose() }
        }
    }

    @Test fun completionWaitsForTheActualLastFrameAndDisposalDoesNotCompleteAnOldResult() {
        instrumentation.runOnMainSync {
            val view = view()
            val finished = mutableListOf<FaceAuthCapsuleState.Phase>()
            view.onTransitionFinished = { finished += it }
            try {
                view.show(FaceAuthCapsuleState.Phase.Success)
                motion(view).apply { pause(); currentPlayTime = 1460L }
                assertTrue(finished.isEmpty())
                assertTrue(value(view, "opacity") > 0f)
                motion(view).end()
                assertEquals(listOf(FaceAuthCapsuleState.Phase.Success), finished)
                assertEquals(0f, value(view, "opacity"), 0f)
                view.show(FaceAuthCapsuleState.Phase.Hidden)
                assertEquals(listOf(FaceAuthCapsuleState.Phase.Success, FaceAuthCapsuleState.Phase.Hidden), finished)
                view.show(FaceAuthCapsuleState.Phase.Success)
                view.dispose()
                assertEquals(2, finished.size)
            } finally { view.dispose() }
        }
    }

    @Test fun cancellationFadeFinishesBeforeWindowCleanupIsRequested() {
        instrumentation.runOnMainSync {
            val view = view()
            var finished = false
            view.onTransitionFinished = { if (it == FaceAuthCapsuleState.Phase.Hidden) finished = true }
            try {
                view.show(FaceAuthCapsuleState.Phase.Scanning)
                motion(view).apply { pause(); currentPlayTime = duration }
                view.show(FaceAuthCapsuleState.Phase.Hidden)
                motion(view).apply { pause(); currentPlayTime = duration / 2 }
                assertFalse(finished)
                assertTrue(value(view, "opacity") > 0f)
                motion(view).end()
                assertTrue(finished)
                assertEquals(View.INVISIBLE, view.visibility)
            } finally { view.dispose() }
        }
    }

    @Test fun retractionReturnsToTheNativeCameraCoordinatesAndReservesIconsUntilTheEnd() {
        instrumentation.runOnMainSync {
            val camera = FaceAuthCapsuleBounds(204f * d, 12f * d, 224f * d, 32f * d)
            val view = view { 44f * d }.apply {
                nativeLayout = { FaceAuthCapsuleNativeLayout(5f * d, 30f * d, 100f * d, 15f * d, 210f * d, camera) }
            }
            var reservation: FaceAuthCapsuleBounds? = null
            var reservations = 0
            view.reserveIcons = { reservations++; reservation = it; true }
            try {
                view.show(FaceAuthCapsuleState.Phase.Success)
                motion(view).pause()
                for (time in listOf(40L, 540L, 950L, 1280L, 1519L)) {
                    capture(view, time, "camera_" + time).recycle()
                    val rect = FaceAuthCapsuleView::class.java.getDeclaredField("box")
                        .apply { isAccessible = true }.get(view) as RectF
                    assertTrue(rect.left >= reservation!!.left)
                    assertTrue(rect.right <= reservation!!.right)
                }
                val rect = FaceAuthCapsuleView::class.java.getDeclaredField("box")
                    .apply { isAccessible = true }.get(view) as RectF
                assertEquals(camera.centerX, rect.centerX(), 0.1f * d)
                assertEquals(camera.centerY, rect.centerY(), 0.1f * d)
                assertEquals(camera.width, rect.width(), 0.1f * d)
                assertEquals(camera.height, rect.height(), 0.1f * d)
                assertEquals(1, reservations)
            } finally { view.dispose() }
        }
    }

    @Test fun lateNativeLayoutReplacesTheInitialFallbackWithoutWaitingForAnotherUnlock() {
        instrumentation.runOnMainSync {
            var native: FaceAuthCapsuleNativeLayout? = null
            val view = view { 44f * d }.apply { nativeLayout = { native } }
            var reserved: FaceAuthCapsuleBounds? = null
            view.reserveIcons = { reserved = it; true }
            try {
                view.show(FaceAuthCapsuleState.Phase.Locked)
                motion(view).pause()
                capture(view, 160).recycle()
                native = FaceAuthCapsuleNativeLayout(12f * d, 30f * d, 100f * d, 15f * d, 215f * d)
                view.show(FaceAuthCapsuleState.Phase.Locked)
                capture(view, 160).recycle()
                val box = FaceAuthCapsuleView::class.java.getDeclaredField("box")
                    .apply { isAccessible = true }.get(view) as RectF
                assertEquals(12f * d, box.top, 0.01f)
                assertEquals(215f * d, box.centerX(), 0.01f)
                assertEquals(215f * d, reserved!!.centerX, 0.01f)
                native = native!!.copy(top = 8f * d, centerX = 200f * d)
                view.layout(0, 0, (420 * d).toInt(), view.height)
                capture(view, 160).recycle()
                assertEquals(8f * d, box.top, 0.01f)
                assertEquals(200f * d, box.centerX(), 0.01f)
            } finally { view.dispose() }
        }
    }

    class NativeGeometryView(context: Context) : FrameLayout(context) {
        fun getMaxHeight() = 90
        fun getBlankWidth() = 61
        fun cameraOffsets(height: Int) = ((measuredHeight - height) / 2 + 14) to ((measuredHeight - height) / 2 + 76)
    }

    @Test fun goneNativeChildUsesCurrentParentPaddingAndNotItsZeroOrStaleBounds() {
        instrumentation.runOnMainSync {
            val pluginContext = context.createPackageContext("com.oplus.systemui.plugins", 0)
            val parent = FrameLayout(pluginContext).apply { setPadding(0, 2, 0, 0) }
            val child = NativeGeometryView(pluginContext).apply {
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
            }
            parent.addView(child)
            fun layoutParent(width: Int, height: Int) {
                parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, width, height)
            }
            layoutParent(1080, 120)
            assertEquals(0, child.height)
            val empty = FaceAuthCapsuleNativePosition.read(child)!!
            assertEquals(16f, empty.top, 0f)
            assertEquals(30f, empty.hole!!.top, 0f)
            assertEquals(92f, empty.hole.bottom, 0f)
            // Reproduce a GONE child retaining an old measured size after a resolution change.
            child.measure(View.MeasureSpec.makeMeasureSpec(433, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(158, View.MeasureSpec.EXACTLY))
            child.layout(503, 2, 936, 160)
            layoutParent(1080, 120)
            assertEquals(empty, FaceAuthCapsuleNativePosition.read(child))
        }
    }

    @Test fun fingerprintOnlyUnlockReplacesTheNativeLockGlyphWithoutFaceEnrollment() {
        instrumentation.runOnMainSync {
            val native = FaceAuthCapsuleNativeIndicators({ true }, { true }, {})
            val controller = IndicatorController().apply { faceSwitchHelper.mFaceUnlockEnable = false }
            val glyph = View(context).apply { visibility = View.VISIBLE }
            try {
                FaceAuthCapsuleNativeIndicators::class.java.getDeclaredField("controller")
                    .apply { isAccessible = true }.set(native, WeakReference(controller))
                FaceAuthCapsuleNativeIndicators::class.java.getDeclaredField("icon")
                    .apply { isAccessible = true }.set(native, WeakReference(glyph))
                native.reconcile()
                assertEquals(View.INVISIBLE, glyph.visibility)
                native.clear()
                assertEquals(View.VISIBLE, glyph.visibility)
            } finally { native.clear() }
        }
    }

    @Test fun theLockFollowsTheNativeContentInsetAsTheCapsuleExpands() {
        instrumentation.runOnMainSync {
            val view = view { 44f * d }
            fun symbolLeft(image: Bitmap): Int {
                var left = image.width
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val pixel = image.getPixel(x, y)
                    if (Color.alpha(pixel) > 32 && Color.green(pixel) > 140) left = minOf(left, x)
                }
                return left
            }
            fun leftEdge() = (FaceAuthCapsuleView::class.java.getDeclaredField("box")
                .apply { isAccessible = true }.get(view) as RectF).left
            try {
                view.show(FaceAuthCapsuleState.Phase.Success)
                motion(view).pause()
                val closed = capture(view, 40, "compact_lock_short_shackle")
                val closedEdge = leftEdge()
                val open = capture(view, 950, "expanded_lock_short_shackle")
                val distance = symbolLeft(closed) - symbolLeft(open)
                assertTrue("Lock stayed still while its capsule expanded", distance > 12f * d)
                assertEquals("Lock no longer follows the native content inset", closedEdge - leftEdge(),
                    distance.toFloat(), 2f * d)
                closed.recycle(); open.recycle()
            } finally { view.dispose() }
        }
    }

    class FaceSettings { @JvmField var mFaceUnlockEnable = true }
    class IndicatorController { @JvmField val faceSwitchHelper = FaceSettings() }

    @Test fun nativeLockGlyphDoesNotPopBackAfterTheCapsuleFinishesAndFeatureOffRestoresIt() {
        instrumentation.runOnMainSync {
            var enabled = true
            var active = true
            val native = FaceAuthCapsuleNativeIndicators({ enabled }, { active }, {})
            val controller = IndicatorController()
            val glyph = View(context).apply { visibility = View.VISIBLE }
            fun attach(name: String, value: Any) {
                FaceAuthCapsuleNativeIndicators::class.java.getDeclaredField(name)
                    .apply { isAccessible = true }.set(native, value)
            }
            try {
                attach("controller", WeakReference(controller))
                attach("icon", WeakReference(glyph))
                native.reconcile()
                assertEquals(View.INVISIBLE, glyph.visibility)
                active = false
                native.reconcile()
                assertEquals("Native open padlock reappeared after completion", View.INVISIBLE, glyph.visibility)
                enabled = false
                native.reconcile()
                assertEquals(View.VISIBLE, glyph.visibility)
            } finally { native.clear() }
        }
    }

}
