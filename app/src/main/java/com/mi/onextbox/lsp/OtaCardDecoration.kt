package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal open class OtaArtworkView(context: Context) : ImageView(context) {
    var crop = OtaCrop()
        set(value) {
            field = value
            fit()
        }

    init {
        scaleType = ScaleType.MATRIX
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun setImageDrawable(drawable: Drawable?) {
        (this.drawable as? Animatable)?.stop()
        super.setImageDrawable(drawable)
        fit()
        if (isAttachedToWindow && isShown && windowVisibility == VISIBLE) (drawable as? Animatable)?.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    fun displayScale(zoom: Float): Float {
        val d = drawable ?: return 0f
        if (d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return 0f
        return maxOf(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight) * zoom
    }

    private fun fit() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val scale = displayScale(crop.zoom)
        imageMatrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - d.intrinsicWidth * scale) * crop.x, (height - d.intrinsicHeight * scale) * crop.y)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown && windowVisibility == VISIBLE) (drawable as? Animatable)?.start()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        val animation = drawable as? Animatable ?: return
        if (isVisible && isAttachedToWindow) animation.start() else animation.stop()
    }

    override fun onDetachedFromWindow() {
        (drawable as? Animatable)?.stop()
        super.onDetachedFromWindow()
    }
}

internal object OtaCardDecoration {
    private class Layer(context: Context, val artwork: OtaArtwork?) : OtaArtworkView(context) {
        private val hiddenViews = mutableListOf<Triple<View, Float, Int>>()
        private var clipped: ViewGroup? = null
        private var job: Job? = null

        fun hide(view: View) {
            hiddenViews.add(Triple(view, view.alpha, view.importantForAccessibility))
            view.alpha = 0f
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        fun clipCard(card: ViewGroup) {
            if (!card.clipToOutline) {
                clipped = card
                card.clipToOutline = true
            }
        }
        fun restore() {
            job?.cancel()
            setImageDrawable(null)
            clipped?.clipToOutline = false
            clipped = null
            hiddenViews.forEach { (view, originalAlpha, accessibility) ->
                view.alpha = originalAlpha
                view.importantForAccessibility = accessibility
            }
            hiddenViews.clear()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            val item = artwork ?: return
            if (drawable != null || job?.isActive == true) return
            crop = item.crop
            job = CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    val image = withContext(Dispatchers.IO) {
                        val source = ImageDecoder.createSource(context.contentResolver, OtaArtwork.uri(item.id))
                        OtaArtwork.decode(source, 1440)
                    }
                    if (isAttachedToWindow) setImageDrawable(image)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    HookLog.w("OtaArtwork", "Artwork could not be loaded", e)
                }
            }
        }

        override fun onDetachedFromWindow() {
            job?.cancel()
            job = null
            super.onDetachedFromWindow()
        }
    }

    fun clear(container: ViewGroup) {
        val layer = (0 until container.childCount).map(container::getChildAt).filterIsInstance<Layer>().firstOrNull() ?: return
        layer.restore()
        container.removeView(layer)
    }

    fun bind(root: ViewGroup, card: ViewGroup, classicContent: ViewGroup?) {
        val container = classicContent ?: card
        clear(container)
        val image = OtaArtwork.parse(HookConfigSnapshot.string(OtaArtwork.KEY, null))
        val hide = HookConfigSnapshot.boolean(OtaArtwork.HIDE, false) == true
        if (image == null && !hide) return
        val layer = Layer(root.context, image)
        if (classicContent != null) {
            val background = classicContent.getChildAt(0)?.takeIf { it is ImageView && it.layoutParams.height == -1 }
            if (hide) for (i in 0 until container.childCount) {
                container.getChildAt(i).takeIf { it !== background }?.let(layer::hide)
            }
            container.addView(layer, if (background == null) 0 else 1, ViewGroup.LayoutParams(-1, -1))
        } else {
            if (image != null) layer.clipCard(card)
            if (hide) for (i in 0 until root.childCount) {
                root.getChildAt(i).takeIf { it !== card }?.let(layer::hide)
            }
            card.addView(layer, ViewGroup.LayoutParams(-1, -1))
        }
    }
}
