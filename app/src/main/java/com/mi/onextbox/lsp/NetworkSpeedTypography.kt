package com.mi.onextbox.lsp

import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import java.lang.ref.WeakReference
import kotlin.math.abs

internal class NetworkSpeedTypography(
    root: FrameLayout,
    number: TextView,
    unit: TextView,
    private val singleLine: Boolean,
    private val followWeight: Boolean,
    private val customSizeDp: Int?,
    clock: TextView? = null,
) {
    private val rootRef = WeakReference(root)
    private val numberRef = WeakReference(number)
    private val unitRef = WeakReference(unit)
    private var clockRef = WeakReference<TextView>(clock)
    private val numberSize = number.textSize
    private val unitSize = unit.textSize
    private val numberFace = number.typeface
    private val unitFace = unit.typeface
    private val numberVariation = number.fontVariationSettings
    private val unitVariation = unit.fontVariationSettings
    private val numberBold = number.paint.isFakeBoldText
    private val unitBold = unit.paint.isFakeBoldText
    private val scaleX = number.textScaleX
    private val measuringPaint = Paint()
    private val glyph = Rect()
    private var treeRef = WeakReference<ViewTreeObserver>(null)
    private var listener: ViewTreeObserver.OnPreDrawListener? = null

    fun start() {
        update()
        val tree = rootRef.get()?.viewTreeObserver ?: return
        val weak = WeakReference(this)
        val observer = ViewTreeObserver.OnPreDrawListener {
            val current = weak.get()
            var ready = true
            if (current != null && !HotReloadLifecycle.isRetiring) runCatching { current.update() }
                .onSuccess { ready = !it }
                .onFailure {
                    current.restore()
                    HookLog.w("ONextBox-NetworkSpeed", "Native typography retained after a display update failure", it)
                }
            ready
        }
        tree.addOnPreDrawListener(observer)
        treeRef = WeakReference(tree)
        listener = observer
    }

    private fun update(): Boolean {
        val root = rootRef.get() ?: return false
        val number = numberRef.get() ?: return false
        val unit = unitRef.get() ?: return false
        var changed = false
        if (followWeight) {
            val host = root.rootView
            val clock = clockRef.get()?.takeIf { it.rootView === host }
                ?: host.findViewById<TextView>(root.resources.getIdentifier("clock", "id", "com.android.systemui"))
                    ?.also { clockRef = WeakReference(it) }
            if (clock != null) listOf(number, unit).forEach { text ->
                if (text.typeface !== clock.typeface) { text.typeface = clock.typeface; changed = true }
                if (text.fontVariationSettings != clock.fontVariationSettings) {
                    text.fontVariationSettings = clock.fontVariationSettings
                    changed = true
                }
                if (text.paint.isFakeBoldText != clock.paint.isFakeBoldText) {
                    text.paint.isFakeBoldText = clock.paint.isFakeBoldText
                    text.requestLayout()
                    text.invalidate()
                    changed = true
                }
            }
        }
        val requested = customSizeDp?.let {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, it.coerceIn(2, 14).toFloat(), number.resources.displayMetrics)
        } ?: return changed
        if (singleLine) {
            measuringPaint.set(number.paint)
            measuringPaint.textSize = 100f
            measuringPaint.textScaleX = scaleX
            val content = number.text.toString().ifEmpty { "0123456789" }
            measuringPaint.getTextBounds(content, 0, content.length, glyph)
            val fitted = NetworkSpeedRules.fittedGlyphSize(requested, glyph.height(), 100f, root.height.toFloat())
            measuringPaint.textSize = fitted
            val available = availableWidth(root)?.minus(number.compoundPaddingLeft + number.compoundPaddingRight)
            val size = if (available != null) {
                NetworkSpeedRules.fitSingleLine(fitted, scaleX, measuringPaint.measureText(content), available).first
            } else fitted
            changed = setSize(number, size) || changed
        } else {
            changed = setFittedSize(number, requested) || changed
            changed = setFittedSize(unit, requested * unitSize / numberSize.coerceAtLeast(1f)) || changed
        }
        return changed
    }

    private fun availableWidth(root: View): Float? {
        val id = root.resources.getIdentifier("status_bar_end_side_container", "id", "com.android.systemui")
        val candidate = root.rootView.findViewById<ViewGroup>(id)
        var ancestor = root.parent
        while (ancestor is View && ancestor !== candidate) ancestor = ancestor.parent
        val limit = if (candidate != null && ancestor === candidate) candidate else root.parent as? ViewGroup ?: return null
        if (limit.width <= 0) return null
        var remaining = limit.width.toFloat()
        var child = root
        while (child !== limit) {
            val parent = child.parent as? ViewGroup ?: return null
            remaining -= parent.paddingLeft + parent.paddingRight
            (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let { remaining -= it.leftMargin + it.rightMargin }
            if (parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL) {
                for (index in 0 until parent.childCount) {
                    val sibling = parent.getChildAt(index)
                    if (sibling === child || sibling.visibility == View.GONE) continue
                    remaining -= sibling.measuredWidth
                    (sibling.layoutParams as? ViewGroup.MarginLayoutParams)?.let { remaining -= it.leftMargin + it.rightMargin }
                }
            }
            child = parent
        }
        remaining -= root.paddingLeft + root.paddingRight + root.resources.displayMetrics.density
        return remaining.coerceAtLeast(1f)
    }

    private fun setFittedSize(text: TextView, requested: Float): Boolean {
        measuringPaint.set(text.paint)
        measuringPaint.textSize = 100f
        measuringPaint.getTextBounds("0123456789", 0, 10, glyph)
        return setSize(text, NetworkSpeedRules.fittedGlyphSize(requested, glyph.height(), 100f, text.height.toFloat()))
    }

    private fun setSize(text: TextView, size: Float): Boolean {
        if (abs(text.textSize - size) <= 0.1f) return false
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        return true
    }

    fun restore() {
        val tree = treeRef.get()
        listener?.let { if (tree?.isAlive == true) tree.removeOnPreDrawListener(it) }
        listener = null
        numberRef.get()?.let {
            it.typeface = numberFace
            it.fontVariationSettings = numberVariation
            it.paint.isFakeBoldText = numberBold
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, numberSize)
        }
        unitRef.get()?.let {
            it.typeface = unitFace
            it.fontVariationSettings = unitVariation
            it.paint.isFakeBoldText = unitBold
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, unitSize)
        }
    }
}
