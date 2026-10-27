package com.mi.onextbox.lsp

import android.util.TypedValue
import android.view.View
import android.widget.TextView
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal class SettingsAccountCardGeometry(card: View, avatar: View) {
    private val cardRef = WeakReference(card)
    private val avatarRef = WeakReference(avatar)
    private val minimum = SettingsOwnedDimension(card.minimumHeight)
    private val top = SettingsOwnedDimension(card.paddingTop)
    private val bottom = SettingsOwnedDimension(card.paddingBottom)
    private val width = SettingsOwnedDimension(avatar.layoutParams.width)
    private val height = SettingsOwnedDimension(avatar.layoutParams.height)
    private val textSizes = listOf(android.R.id.title, android.R.id.summary).mapNotNull { id ->
        (card.findViewById<View>(id) as? TextView)?.let(::TextSize)
    }

    fun apply(percent: Int) {
        if (percent == SettingsAppearanceSettings.DEFAULT_PERCENT) { restore(); return }
        val card = cardRef.get() ?: return
        val avatar = avatarRef.get() ?: return
        val minimumTouchHeight = (48 * card.resources.displayMetrics.density).roundToInt()
        update(card, minimum.apply(card.minimumHeight, percent, minimumTouchHeight),
            top.apply(card.paddingTop, percent), bottom.apply(card.paddingBottom, percent),
            avatar, width.apply(avatar.layoutParams.width, percent), height.apply(avatar.layoutParams.height, percent))
        textSizes.forEach { it.apply(percent) }
    }

    fun restore() {
        val card = cardRef.get() ?: return
        val avatar = avatarRef.get() ?: return
        update(card, minimum.restore(card.minimumHeight), top.restore(card.paddingTop), bottom.restore(card.paddingBottom),
            avatar, width.restore(avatar.layoutParams.width), height.restore(avatar.layoutParams.height))
        textSizes.forEach { it.apply(null) }
    }

    private fun update(card: View, minHeight: Int, paddingTop: Int, paddingBottom: Int, avatar: View, w: Int, h: Int) {
        if (card.minimumHeight != minHeight) card.minimumHeight = minHeight
        if (card.paddingTop != paddingTop || card.paddingBottom != paddingBottom) {
            card.setPaddingRelative(card.paddingStart, paddingTop, card.paddingEnd, paddingBottom)
        }
        val params = avatar.layoutParams
        if (params.width != w || params.height != h) {
            params.width = w
            params.height = h
            avatar.layoutParams = params
        }
    }

    private class TextSize(view: TextView) {
        private val reference = WeakReference(view)
        private var native = view.textSize
        private var unit = view.textSizeUnit
        private var applied: Float? = null

        fun apply(percent: Int?) {
            val view = reference.get() ?: return
            val current = view.textSize
            if (applied == null || current != applied || view.textSizeUnit != unit) {
                native = current
                unit = view.textSizeUnit
            }
            val size = if (percent == null) native else native * (SettingsAppearanceSettings.normalize(percent) / 100f)
            if (current != size) {
                view.setTextSize(unit, TypedValue.deriveDimension(unit, size, view.resources.displayMetrics))
            }
            applied = if (percent == null) null else view.textSize
        }
    }
}
