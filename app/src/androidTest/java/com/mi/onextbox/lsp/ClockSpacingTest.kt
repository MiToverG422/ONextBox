package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import kotlin.math.ceil
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockSpacingTest {
    private fun small(text: String) = SpannableString(text).apply {
        setSpan(RelativeSizeSpan(0.7f), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    @Test fun removesInvisibleSpanWidthAndTrailingSpaceButKeepsSecondsReserve() {
        val paint = TextPaint().apply { textSize = 39f }
        val text = small("下午 18:10:00")
        val native = ceil(Layout.getDesiredWidth(text.toString(), paint)).toInt() + 10 + 18 + 8
        val compact = ClockSpacingMeasure.compactWidth(native, text, paint, 18, 10)
        val expected = Layout.getDesiredWidth(text, paint) + 10 + 8
        assertEquals(expected, compact.toFloat(), 2f)
        assertTrue(compact < native - 18)
        assertEquals(compact + 12, ClockSpacingMeasure.compactWidth(native + 12, text, paint, 18, 10))
    }

    @Test fun smallPeriodCorrectionDoesNotChangeWhenSecondsChange() {
        val paint = TextPaint().apply { textSize = 39f }
        val native = 300
        val first = ClockSpacingMeasure.compactWidth(native, small("下午 18:10:11"), paint, 18, 10)
        val second = ClockSpacingMeasure.compactWidth(native, small("下午 18:10:58"), paint, 18, 10)
        assertEquals(first.toFloat(), second.toFloat(), 1f)
    }

    @Test fun ordinaryClockOnlyLosesTrailingSpaceAndInvalidCacheSentinelsRemain() {
        val paint = TextPaint().apply { textSize = 39f }
        assertEquals(182, ClockSpacingMeasure.compactWidth(200, "18:10:00", paint, 18, 10))
        assertEquals(-1, ClockSpacingMeasure.compactWidth(-1, "18:10:00", paint, 18, 10))
        assertEquals(10, ClockSpacingMeasure.compactWidth(1, "", paint, 18, 10))
    }

    @Test fun staleMinuteWidthCannotEllipsizeRestoredSeconds() {
        val paint = TextPaint().apply { textSize = 39f }
        val padding = 10
        for (text in listOf<CharSequence>(small("下午 18:13:00"), "下午 18:13:00", "18:13:58")) {
            val native = ceil(Layout.getDesiredWidth(ClockSpacingMeasure.minutesText(text), paint)).toInt() + padding + 18
            val compact = ClockSpacingMeasure.compactWidth(native, text, paint, 18, padding)
            val contentWidth = compact - padding
            assertTrue(contentWidth >= ceil(Layout.getDesiredWidth(text, paint)).toInt())
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(contentWidth)
                .build()
            assertEquals(0, layout.getEllipsisCount(0))
        }
    }

    @Test fun minuteAreaKeepsPeriodSpanWithoutReservingSeconds() {
        for (separator in listOf(':', '.')) {
            val text = small("下午 18${separator}13${separator}00")
            val minutes = ClockSpacingMeasure.minutesText(text)
            assertEquals("下午 18${separator}13", minutes.toString())
            assertTrue(minutes is Spanned)
            val spans = (minutes as Spanned).getSpans(0, minutes.length, RelativeSizeSpan::class.java)
            assertEquals(1, spans.size)
            assertEquals(0.7f, spans.single().sizeChange, 0f)
            assertEquals(minutes, ClockSpacingMeasure.minutesText(minutes))
        }
    }

    @Test fun installedClockExposesMeasurementAndAreaCaches() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val type = Class.forName(ClockPeriodHooker.CLOCK, false, loader)
        val stat = Class.forName(ClockPeriodHooker.STAT_CLOCK, false, loader)
        assertNotNull(stat.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        assertEquals(Int::class.javaPrimitiveType, stat.getDeclaredField("paddingEnd").type)
        assertEquals(Int::class.javaPrimitiveType, stat.getDeclaredField("cachedMinutesLayoutWidth").type)
        assertNotNull(stat.getDeclaredMethod("refreshWidth"))
        assertEquals(Int::class.javaPrimitiveType, type.getDeclaredMethod("getSecondsFixedWidth").returnType)
        assertNotNull(TextView::class.java.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
    }
}
