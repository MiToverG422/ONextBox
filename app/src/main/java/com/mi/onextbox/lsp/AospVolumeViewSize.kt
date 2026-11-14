package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.TypedValue
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Resize the measured native layout; its own insets and touch regions then use the new bounds. */
internal class AospVolumeViewSize {
    private data class Original(
        val width: Int, val height: Int, val minWidth: Int, val minHeight: Int,
        val padding: IntArray, val margins: IntArray?, val constraints: Map<String, Int>, val textSize: Float?,
    )
    private val originals = WeakHashMap<View, Original>()
    private val lastPercents = WeakHashMap<View, Int>()
    private val ringer = AospVolumeRingerSize()

    fun apply(view: View, percent: Int) {
        lastPercents[view] = percent
        if (ringer.accepts(view)) {
            ringer.apply(view, percent)
            return
        }
        val params = view.layoutParams
        val original = originals.getOrPut(view) {
            Original(params?.width ?: -2, params?.height ?: -2, view.minimumWidth, view.minimumHeight,
                intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom),
                (params as? ViewGroup.MarginLayoutParams)?.let {
                    intArrayOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin, it.marginStart, it.marginEnd)
                }, listOf("matchConstraintMaxWidth", "matchConstraintMaxHeight", "matchConstraintMinWidth", "matchConstraintMinHeight")
                    .mapNotNull { name -> runCatching { name to params!!.javaClass.getField(name).getInt(params) }.getOrNull() }.toMap(),
                (view as? TextView)?.textSize)
        }
        val scale = AospVolumeSize.percent(percent) / 100f
        fun size(value: Int) = AospVolumeSize.pixels(value, percent)
        if (params != null) {
            params.width = size(original.width)
            params.height = size(original.height)
            original.constraints.forEach { (name, value) ->
                runCatching { params.javaClass.getField(name).setInt(params, size(value)) }
            }
            if (params is ViewGroup.MarginLayoutParams) original.margins?.let { m ->
                params.setMargins((m[0] * scale).roundToInt(), (m[1] * scale).roundToInt(),
                    (m[2] * scale).roundToInt(), (m[3] * scale).roundToInt())
                params.marginStart = (m[4] * scale).roundToInt()
                params.marginEnd = (m[5] * scale).roundToInt()
            }
            view.layoutParams = params
        }
        view.minimumWidth = size(original.minWidth)
        view.minimumHeight = size(original.minHeight)
        original.padding.let { p -> view.setPadding(size(p[0]), size(p[1]), size(p[2]), size(p[3])) }
        if (view is TextView && original.textSize != null) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, original.textSize * scale)
        if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i), percent)
    }

    fun restore() {
        val saved = lastPercents.toMap()
        lastPercents.keys.toList().forEach { apply(it, 100) }
        lastPercents.putAll(saved)
    }
    fun reapply() { lastPercents.toMap().forEach { (view, percent) -> apply(view, percent) } }
    fun isShowing() = originals.keys.any { it.isAttachedToWindow && it.isShown }
}
