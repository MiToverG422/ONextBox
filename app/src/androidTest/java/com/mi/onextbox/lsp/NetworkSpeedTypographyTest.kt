package com.mi.onextbox.lsp

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSpeedTypographyTest {
    @Test fun longSingleLineTextFitsBesideOtherStatusIcons() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val parent = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 10, 0)
            }
            val root = FrameLayout(context)
            val number = TextView(context).apply { text = "999.99 MB/S"; gravity = Gravity.CENTER }
            val unit = TextView(context)
            root.addView(number, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 60))
            root.addView(unit)
            parent.addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 60))
            parent.addView(View(context), LinearLayout.LayoutParams(150, 60))
            fun layout() {
                parent.measure(View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, 260, 60)
            }
            layout()
            val originalScale = number.textScaleX
            val style = NetworkSpeedTypography(root, number, unit, true, false, 14)
            style.start()
            repeat(2) { layout(); root.viewTreeObserver.dispatchOnPreDraw() }
            assertTrue(root.measuredWidth.toString(), root.measuredWidth in 1..100)
            assertEquals(View.VISIBLE, number.visibility)
            assertEquals(originalScale, number.textScaleX, 0.001f)
            style.restore()
            assertEquals(originalScale, number.textScaleX, 0.001f)
        }
    }

    @Test fun followingSystemWeightPreservesNativeSizeSpacingAndPosition() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val parent = FrameLayout(context)
            val root = FrameLayout(context)
            val number = TextView(context).apply {
                text = "0.00 KB/S"
                gravity = Gravity.CENTER
                includeFontPadding = false
                typeface = Typeface.create(Typeface.SANS_SERIF, 400, false)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                textScaleX = 1.1f
                translationY = 2f
            }
            val unit = TextView(context).apply { text = "" }
            root.addView(number, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 60))
            root.addView(unit)
            parent.addView(root, FrameLayout.LayoutParams(190, 60))
            val clock = TextView(context).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, 600, false)
                paint.isFakeBoldText = true
            }
            parent.addView(clock, FrameLayout.LayoutParams(50, 60))
            fun layout() {
                parent.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, 300, 60)
            }
            layout()
            val size = number.textSize
            val face = number.typeface
            val style = NetworkSpeedTypography(root, number, unit, true, true, null, clock)
            style.start()
            repeat(2) { layout(); root.viewTreeObserver.dispatchOnPreDraw() }
            assertEquals(size, number.textSize, 0.01f)
            assertEquals(1.1f, number.textScaleX, 0.001f)
            assertEquals(2f, number.translationY, 0.01f)
            assertEquals(Gravity.CENTER, number.gravity)
            assertSame(clock.typeface, number.typeface)
            assertTrue(number.paint.isFakeBoldText)
            style.restore()
            assertEquals(size, number.textSize, 0.01f)
            assertSame(face, number.typeface)
            assertFalse(number.paint.isFakeBoldText)
            assertEquals(2f, number.translationY, 0.01f)
        }
    }

    @Test fun requestedSingleLineSizeIsAppliedAndRestored() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val root = FrameLayout(context)
            val number = TextView(context).apply { text = "12.3" }
            val unit = TextView(context)
            root.addView(number)
            root.addView(unit)
            val original = number.textSize
            for (size in listOf(2, 14)) {
                val style = NetworkSpeedTypography(root, number, unit, true, false, size)
                style.start()
                val expected = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size.toFloat(), context.resources.displayMetrics)
                assertEquals(expected, number.textSize, 0.1f)
                style.restore()
                assertEquals(original, number.textSize, 0.01f)
            }
        }
    }
}
