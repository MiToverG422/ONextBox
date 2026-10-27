package com.mi.onextbox.lsp

import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccountCardGeometryTest {
    @Test fun resizeScalesBothTextRowsAndRestoresWithoutChangingHorizontalSpacing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = 200
                setPaddingRelative(32, 28, 36, 30)
            }
            val avatar = ImageView(context)
            val params = LinearLayout.LayoutParams(128, 128).apply { marginEnd = 24 }
            card.addView(avatar, params)
            val title = TextView(context).apply { id = android.R.id.title; text = "Account"; textSize = 20f }
            card.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val summary = TextView(context).apply { id = android.R.id.summary; text = "Account details"; textSize = 14f }
            card.addView(summary)
            val fontSize = title.textSize
            val summaryFontSize = summary.textSize
            val geometry = SettingsAccountCardGeometry(card, avatar)
            geometry.apply(75)
            assertEquals(96, avatar.layoutParams.width)
            assertEquals(96, avatar.layoutParams.height)
            assertEquals(21, card.paddingTop)
            assertEquals(23, card.paddingBottom)
            assertEquals(32, card.paddingStart)
            assertEquals(36, card.paddingEnd)
            assertEquals(fontSize * .75f, title.textSize, .001f)
            assertEquals(summaryFontSize * .75f, summary.textSize, .001f)
            assertEquals(TypedValue.COMPLEX_UNIT_SP, title.textSizeUnit)
            assertEquals(TypedValue.COMPLEX_UNIT_SP, summary.textSizeUnit)
            assertEquals(24, (avatar.layoutParams as LinearLayout.LayoutParams).marginEnd)
            geometry.apply(150)
            geometry.apply(150)
            assertEquals(192, avatar.layoutParams.width)
            assertEquals(fontSize * 1.5f, title.textSize, .001f)
            assertEquals(summaryFontSize * 1.5f, summary.textSize, .001f)
            geometry.restore()
            assertEquals(200, card.minimumHeight)
            assertEquals(28, card.paddingTop)
            assertEquals(30, card.paddingBottom)
            assertEquals(128, avatar.layoutParams.width)
            assertEquals(128, avatar.layoutParams.height)
            assertEquals(fontSize, title.textSize, 0f)
            assertEquals(summaryFontSize, summary.textSize, 0f)
            assertEquals(TypedValue.COMPLEX_UNIT_SP, title.textSizeUnit)
        }
    }

    @Test fun selectingOneHundredPercentRestoresTheNativeCard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val card = LinearLayout(context).apply { minimumHeight = 200; setPadding(16, 28, 16, 28) }
            val avatar = ImageView(context)
            card.addView(avatar, LinearLayout.LayoutParams(128, 128))
            val title = TextView(context).apply { id = android.R.id.title; textSize = 20f }
            card.addView(title)
            val fontSize = title.textSize
            val geometry = SettingsAccountCardGeometry(card, avatar)
            geometry.apply(60)
            geometry.apply(100)
            assertEquals(200, card.minimumHeight)
            assertEquals(28, card.paddingTop)
            assertEquals(128, avatar.layoutParams.width)
            assertEquals(fontSize, title.textSize, 0f)
            assertEquals(TypedValue.COMPLEX_UNIT_SP, title.textSizeUnit)
        }
    }

    @Test fun fontRestorationKeepsLaterNativeFontChangesAndOnlyScalesAccountText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val card = LinearLayout(context)
            val avatar = ImageView(context)
            card.addView(avatar, LinearLayout.LayoutParams(128, 128))
            val title = TextView(context).apply {
                id = android.R.id.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 29.75f)
            }
            card.addView(title)
            val other = TextView(context).apply { textSize = 12f }
            card.addView(other)
            val otherSize = other.textSize
            val geometry = SettingsAccountCardGeometry(card, avatar)
            geometry.apply(75)
            assertEquals(22.3125f, title.textSize, .001f)
            assertEquals(TypedValue.COMPLEX_UNIT_PX, title.textSizeUnit)
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 31.5f)
            geometry.apply(150)
            assertEquals(47.25f, title.textSize, .001f)
            geometry.restore()
            assertEquals(31.5f, title.textSize, 0f)
            geometry.apply(60)
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 36.25f)
            geometry.restore()
            assertEquals(36.25f, title.textSize, 0f)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.25f)
            val newSystemSize = title.textSize
            geometry.apply(75)
            assertEquals(newSystemSize * .75f, title.textSize, .001f)
            assertEquals(TypedValue.COMPLEX_UNIT_SP, title.textSizeUnit)
            geometry.restore()
            assertEquals(newSystemSize, title.textSize, .001f)
            assertEquals(TypedValue.COMPLEX_UNIT_SP, title.textSizeUnit)
            assertEquals(otherSize, other.textSize, 0f)
        }
    }
}
