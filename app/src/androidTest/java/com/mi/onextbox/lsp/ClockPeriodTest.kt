package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockPeriodTest {
    private fun date(hour: Int) = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
        clear(); set(2026, Calendar.OCTOBER, 27, hour, 23, 45)
    }

    @Test fun detailedPeriodsUseSystemLocaleDataAndKeepNativeHourCycleAndSeconds() {
        val formatter = ClockPeriodFormatter()
        val labels = mapOf(3 to "凌晨", 6 to "清晨", 9 to "上午", 12 to "中午", 15 to "下午", 21 to "晚上")
        for ((hour, label) in labels) {
            val text = formatter.format(date(hour), Locale.SIMPLIFIED_CHINESE, "HH:mm:ss", 1, false).toString()
            assertEquals("$label ${hour.toString().padStart(2, '0')}:23:45", text)
        }
        assertEquals("下午 3:23:45", formatter.format(date(15), Locale.SIMPLIFIED_CHINESE, "\uef00a\uef01h:mm:ss", 0, false).toString())
        assertEquals("清晨 06:23", formatter.format(date(6), Locale.SIMPLIFIED_CHINESE, "HH:mm", 2, false).toString())
    }

    @Test fun localeTimeZoneAndSmallStyleAreUpdatedWithoutChangingCalendar() {
        val formatter = ClockPeriodFormatter()
        val calendar = date(15)
        val millis = calendar.timeInMillis
        assertEquals("PM 3:23", formatter.format(calendar, Locale.US, "h:mm a", 0, false).toString())
        calendar.timeZone = TimeZone.getTimeZone("America/Los_Angeles")
        val text = formatter.format(calendar, Locale.US, "h:mm a", 0, true) as Spanned
        assertEquals("AM 12:23", text.toString())
        val span = text.getSpans(0, text.length, RelativeSizeSpan::class.java).single()
        assertEquals(0.7f, span.sizeChange, 0.001f)
        assertEquals(0, text.getSpanStart(span))
        assertEquals(2, text.getSpanEnd(span))
        assertEquals(millis, calendar.timeInMillis)
    }

    @Test fun installedSystemUiRetainsNativeClockFormatInterfaces() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val type = Class.forName(ClockPeriodHooker.CLOCK, false, loader)
        assertTrue(type.isAssignableFrom(Class.forName(ClockPeriodHooker.STAT_CLOCK, false, loader)))
        assertTrue(type.isAssignableFrom(Class.forName(ClockPeriodHooker.QS_CLOCK, false, loader)))
        assertEquals(CharSequence::class.java, type.getDeclaredMethod("getSmallTime").returnType)
        assertEquals(Calendar::class.java, type.getDeclaredField("mCalendar").type)
        assertEquals(java.text.SimpleDateFormat::class.java, type.getDeclaredField("mClockFormat").type)
        assertEquals(Void.TYPE, type.getDeclaredMethod("updateClock").returnType)
        assertNotNull(type.getDeclaredField("mAmPmStyle"))
    }
}
