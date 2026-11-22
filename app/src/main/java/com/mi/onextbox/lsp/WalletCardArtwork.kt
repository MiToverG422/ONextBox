package com.mi.onextbox.lsp

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Attached only to the background image of a known card layout. Wallet still owns its binding and siblings. */
internal object WalletCardArtwork {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var cached: Pair<String, Bitmap>? = null // worker thread only
    private val bindings = WeakHashMap<ImageView, WeakReference<Binding>>()

    fun bind(image: ImageView, key: String?) {
        val current = bindings[image]?.get()
        if (current != null) {
            current.rebind(key)
            return
        }
        if (key == null) return
        val binding = Binding(image, key)
        bindings[image] = WeakReference(binding)
        image.addOnAttachStateChangeListener(binding)
        if (image.isAttachedToWindow) binding.onViewAttachedToWindow(image)
    }

    private class Binding(val image: ImageView, private var key: String?) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private var encoded: String? = null
        private var layer: CardLayer? = null

        fun rebind(next: String?) {
            if (next == key) return
            key = next
            clear()
            if (image.isAttachedToWindow) onPreDraw()
        }

        override fun onViewAttachedToWindow(v: View) {
            image.viewTreeObserver.addOnPreDrawListener(this)
            onPreDraw()
        }

        override fun onViewDetachedFromWindow(v: View) {
            if (image.viewTreeObserver.isAlive) image.viewTreeObserver.removeOnPreDrawListener(this)
            clear()
        }

        private fun clear() {
            layer?.let(image.overlay::remove)
            layer = null
            encoded = null
        }

        override fun onPreDraw(): Boolean {
            val next = key?.let(WalletCardReader::preference)?.let { HookConfigSnapshot.string(it, null) }
            if (next != encoded) {
                clear()
                encoded = next
                OtaArtwork.parse(next)?.let { load(it, next) }
            }
            val active = layer
            if (active != null && (active.bounds.width() != image.width || active.bounds.height() != image.height))
                active.setBounds(0, 0, image.width, image.height)
            return true
        }

        private fun load(art: OtaArtwork, expected: String?) {
            val context = image.context.applicationContext
            worker.execute {
                val bitmap = cached?.takeIf { it.first == art.id }?.second ?: runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, OtaArtwork.uri(art.id, ArtworkTarget.WALLET))
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val size = info.size
                        val scale = minOf(1f, 1280f / maxOf(size.width, size.height))
                        decoder.setTargetSize((size.width * scale).roundToInt().coerceAtLeast(1), (size.height * scale).roundToInt().coerceAtLeast(1))
                    }.also { cached = art.id to it }
                }.getOrNull() ?: return@execute
                main.post {
                    if (!image.isAttachedToWindow || encoded != expected || layer != null) return@post
                    layer = CardLayer(bitmap, art.crop, image).also {
                        it.setBounds(0, 0, image.width, image.height)
                        image.overlay.add(it)
                    }
                }
            }
        }
    }

    private class NativeCorners(owner: Class<*>) {
        private val bitmapRadius = field(owner, "q", Int::class.javaPrimitiveType!!)
        private val imageType = field(owner, "p", Int::class.javaPrimitiveType!!)
        private val viewRadius = field(owner, "L", Float::class.javaPrimitiveType!!)
        private val clipsView = runCatching { owner.getDeclaredMethod("v1") }.getOrNull()
        fun radius(view: ImageView): Float {
            if (viewRadius != null && clipsView?.invoke(view) == true) return viewRadius.getFloat(view)
            return if (imageType?.getInt(view) == 576) bitmapRadius?.getInt(view)?.toFloat() ?: 0f else 0f
        }
        companion object {
            private val cache = mutableMapOf<Class<*>, NativeCorners>()
            private fun field(owner: Class<*>, name: String, type: Class<*>) =
                runCatching { owner.getDeclaredField(name).takeIf { it.type == type }?.apply { isAccessible = true } }.getOrNull()
            fun forImage(image: ImageView): NativeCorners? {
                val owner = generateSequence<Class<*>>(image.javaClass) { it.superclass }
                    .firstOrNull { it.name == "com.finshell.finui.widget.imageview.CircleNetworkImageView" } ?: return null
                return cache.getOrPut(owner) { NativeCorners(owner) }
            }
        }
    }

    private class CardLayer(val bitmap: Bitmap, val crop: OtaCrop, val image: ImageView) : Drawable() {
        private val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).also { it.shader = shader }
        private val outline = Outline()
        private val nativeCorners = NativeCorners.forImage(image)
        override fun onBoundsChange(bounds: Rect) {
            val scale = maxOf(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height) * crop.zoom
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(bounds.left + (bounds.width() - bitmap.width * scale) * crop.x,
                    bounds.top + (bounds.height() - bitmap.height * scale) * crop.y)
            })
        }
        override fun draw(canvas: Canvas) {
            outline.setEmpty()
            image.outlineProvider?.getOutline(image, outline)
            val radius = maxOf(outline.radius, nativeCorners?.radius(image) ?: 0f, 0f)
            canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
                radius, radius, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
