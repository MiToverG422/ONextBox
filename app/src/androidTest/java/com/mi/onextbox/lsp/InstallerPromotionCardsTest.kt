package com.mi.onextbox.lsp

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallerPromotionCardsTest {
    @Test fun hidesBothNativeRootIdsWithoutLeavingCardMargins() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.createPackageContext(InstallerCleanupSettings.APP_DETAIL, 0)
        instrumentation.runOnMainSync {
            listOf("install_done_suggest_B", "layout_no_recommend").forEach { name ->
                val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val status = View(context)
                parent.addView(status, LinearLayout.LayoutParams(200, 40))
                val card = FrameLayout(context).apply {
                    id = context.resources.getIdentifier(name, "id", InstallerCleanupSettings.APP_DETAIL)
                }
                assertTrue(card.id != 0)
                parent.addView(card, LinearLayout.LayoutParams(200, 100).apply { topMargin = 12 })
                assertEquals(1, InstallerPromotionCards.hide(parent))
                assertEquals(View.GONE, card.visibility)
                assertEquals(View.VISIBLE, status.visibility)
                parent.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                assertEquals(40, parent.measuredHeight)
            }
        }
    }
}
