package com.mi.onextbox.lsp

import android.text.Layout
import android.text.TextPaint
import kotlin.math.ceil

internal object ClockSpacingMeasure {
    fun compactWidth(nativeWidth: Int, text: CharSequence, paint: TextPaint, endSpace: Int, contentPadding: Int): Int {
        if (nativeWidth <= 0) return nativeWidth
        val plain = Layout.getDesiredWidth(text.toString(), paint)
        val styled = Layout.getDesiredWidth(text, paint)
        val excess = (plain - styled).coerceAtLeast(0f)
        val minimum = ceil(styled).toInt() + contentPadding.coerceAtLeast(0)
        return ceil(nativeWidth - excess - endSpace.coerceAtLeast(0)).toInt().coerceAtLeast(minimum.coerceAtLeast(1))
    }

    fun minutesText(text: CharSequence): CharSequence {
        val separator = if (':' !in text && '.' in text) '.' else ':'
        val first = text.indexOf(separator)
        val seconds = if (first < 0) -1 else text.indexOf(separator, first + 1)
        return if (seconds < 0) text else text.subSequence(0, seconds)
    }
}
