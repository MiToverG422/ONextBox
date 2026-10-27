package com.mi.onextbox.lsp

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
class NetworkSpeedAppearanceTest {
    @Test fun singleLineRestoresNativeGeometryTextAndVisibility() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val root = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(72, 90) }
            val number = TextView(context).apply {
                text = "12.3"
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                visibility = View.INVISIBLE
                layoutParams = FrameLayout.LayoutParams(60, 40).apply { bottomMargin = 8 }
            }
            val unit = TextView(context).apply { text = "MB/S" }
            root.addView(number)
            root.addView(unit)
            val style = NetworkSpeedAppearance(root, number, unit)
            style.apply(false, false, true)
            assertEquals("12.3 MB/S", number.text.toString())
            assertEquals("", unit.text.toString())
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, root.layoutParams.width)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, number.layoutParams.height)
            assertEquals(Gravity.CENTER, number.gravity)
            assertEquals(View.INVISIBLE, number.visibility)
            style.restore()
            assertEquals("12.3", number.text.toString())
            assertEquals("MB/S", unit.text.toString())
            assertEquals(72, root.layoutParams.width)
            assertEquals(40, number.layoutParams.height)
            assertEquals(8, (number.layoutParams as FrameLayout.LayoutParams).bottomMargin)
            assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, number.gravity)
            assertEquals(View.INVISIBLE, number.visibility)
        }
    }
}
