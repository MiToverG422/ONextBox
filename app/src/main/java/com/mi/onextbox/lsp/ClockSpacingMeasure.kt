package com.mi.onextbox.lsp

import android.text.Layout
import android.text.TextPaint
import kotlin.math.ceil

internal object ClockSpacingMeasure {
    fun compactWidth(nativeWidth: Int, text: CharSequence, paint: TextPaint, endSpace: Int, minimum: Int): Int {
        if (nativeWidth <= 0) return nativeWidth
        val plain = Layout.getDesiredWidth(text.toString(), paint)
        val styled = Layout.getDesiredWidth(text, paint)
        val excess = (plain - styled).coerceAtLeast(0f)
        return ceil(nativeWidth - excess - endSpace.coerceAtLeast(0)).toInt().coerceAtLeast(minimum.coerceAtLeast(1))
    }
}
