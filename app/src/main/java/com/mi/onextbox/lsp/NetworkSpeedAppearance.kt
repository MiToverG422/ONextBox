package com.mi.onextbox.lsp

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.lang.ref.WeakReference

internal class NetworkSpeedAppearance(
    root: FrameLayout,
    number: TextView,
    unit: TextView,
) {
    private val rootRef = WeakReference(root)
    private val numberRef = WeakReference(number)
    private val unitRef = WeakReference(unit)
    private val width = root.layoutParams.width
    private val numberParams = FrameLayout.LayoutParams(number.layoutParams as FrameLayout.LayoutParams)
    private val gravity = number.gravity
    private val numberText = number.text.toString()
    private val unitText = unit.text.toString()
    private var typography: NetworkSpeedTypography? = null

    fun apply(hidePerSecond: Boolean, hideUnits: Boolean, singleLine: Boolean,
              followFontWeight: Boolean = false, fontSizeDp: Int? = null) {
        val root = rootRef.get() ?: return
        val number = numberRef.get() ?: return
        val unit = unitRef.get() ?: return
        val text = NetworkSpeedRules.text(numberText, unitText, hidePerSecond, hideUnits, singleLine)
        number.text = text.number
        unit.text = text.unit
        if (text.singleLine) {
            root.layoutParams = root.layoutParams.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
            number.layoutParams = FrameLayout.LayoutParams(numberParams).apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                topMargin = 0
                bottomMargin = 0
                gravity = Gravity.CENTER
            }
            number.gravity = Gravity.CENTER
        }
        if (followFontWeight || fontSizeDp != null) {
            typography = NetworkSpeedTypography(root, number, unit, text.singleLine, followFontWeight, fontSizeDp).also { it.start() }
        }
    }

    fun restore() {
        typography?.restore()
        typography = null
        val root = rootRef.get() ?: return
        val number = numberRef.get() ?: return
        val unit = unitRef.get() ?: return
        root.layoutParams = root.layoutParams.apply { width = this@NetworkSpeedAppearance.width }
        number.layoutParams = FrameLayout.LayoutParams(numberParams)
        number.gravity = gravity
        number.text = numberText
        unit.text = unitText
    }
}
