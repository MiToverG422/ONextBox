package com.mi.onextbox.lsp

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import java.util.Calendar
import java.util.Locale

internal class ClockPeriodFormatter {
    private data class Key(val pattern: String, val period: String, val locale: Locale, val zone: String)
    private var key: Key? = null
    private var time: SimpleDateFormat? = null
    private var period: SimpleDateFormat? = null

    fun format(calendar: Calendar, locale: Locale, nativePattern: String, style: Int, small: Boolean): CharSequence {
        val next = Key(ClockPeriodRules.timePattern(nativePattern), ClockPeriodRules.periodPattern(style), locale, calendar.timeZone.id)
        if (key != next) {
            val zone = TimeZone.getTimeZone(next.zone)
            time = SimpleDateFormat(next.pattern, locale).apply { timeZone = zone }
            period = SimpleDateFormat(next.period, locale).apply { timeZone = zone }
            key = next
        }
        val date = calendar.time
        val label = checkNotNull(period).format(date)
        val result = SpannableStringBuilder(label).append(' ').append(checkNotNull(time).format(date))
        if (small && label.isNotEmpty()) result.setSpan(RelativeSizeSpan(0.7f), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return result
    }
}
