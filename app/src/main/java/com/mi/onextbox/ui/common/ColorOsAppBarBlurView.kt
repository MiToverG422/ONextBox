package com.mi.onextbox.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import java.lang.reflect.Method
import kotlin.math.abs

/**
 * Native ColorOS 17 app-bar gradient blur.
 *
 * This follows AppBarBlurHelper rather than approximating it with a foreground bitmap blur:
 * OplusRenderEffect supplies the compositor background blur and the drawable supplies the
 * theme-dependent vertical tint. Keeping both on the same View also preserves the system's
 * status-bar sampling and the soft transition below the toolbar.
 */
internal class ColorOsAppBarBlurView(
    context: Context,
    darkTheme: Boolean,
    baseColor: Int,
) : View(context) {
    private var darkTheme = darkTheme
    private var baseColor = baseColor
    private var blurFactor = Float.NaN
    private var nativeEffectApplied = false
    private var gradientDrawable = createGradientDrawable()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = gradientDrawable
    }

    fun updateAppearance(darkTheme: Boolean, baseColor: Int, blurFactor: Float) {
        val clampedFactor = blurFactor.coerceIn(0f, 1f)
        val appearanceChanged = this.darkTheme != darkTheme || this.baseColor != baseColor
        val factorChanged = this.blurFactor.isNaN() || abs(this.blurFactor - clampedFactor) > 0.001f

        if (appearanceChanged) {
            this.darkTheme = darkTheme
            this.baseColor = baseColor
            gradientDrawable = createGradientDrawable()
            background = gradientDrawable
        }
        if (appearanceChanged || factorChanged) {
            this.blurFactor = clampedFactor
            gradientDrawable.alpha = (clampedFactor * 255f).toInt().coerceIn(0, 255)
            if (isAttachedToWindow && width > 0 && height > 0) {
                applyNativeGradientBlur()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(::applyNativeGradientBlur)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        post(::applyNativeGradientBlur)
    }

    override fun onDetachedFromWindow() {
        clearNativeGradientBlur()
        super.onDetachedFromWindow()
    }

    private fun applyNativeGradientBlur() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val factor = if (blurFactor.isNaN()) 1f else blurFactor.coerceIn(0f, 1f)
        val api = NativeApi.instance ?: return
        nativeEffectApplied = runCatching {
            // AppBarBlurHelper.updateGradientBlurFraction scales the radius with the same
            // factor used for the tint drawable. The remaining parameters are the C17 defaults.
            val effect = api.createGradientBlurEffect.invoke(
                null,
                30f * factor,
                0f,
                true,
                3f,
                0,
                0,
                0,
            ) as RenderEffect
            api.setBackgroundRenderEffect.invoke(null, effect, this)
            true
        }.onFailure {
            if (!nativeEffectApplied) {
                Log.w(TAG, "Unable to apply native ColorOS app-bar blur", it)
            }
        }.getOrDefault(false)
    }

    private fun clearNativeGradientBlur() {
        val api = NativeApi.instance ?: return
        if (!nativeEffectApplied) return
        runCatching {
            api.setBackgroundRenderEffect.invoke(null, null, this)
        }
        nativeEffectApplied = false
    }

    private fun createGradientDrawable(): ColorOsAppBarGradientDrawable {
        return if (darkTheme) {
            ColorOsAppBarGradientDrawable(
                baseColor = baseColor,
                positions = floatArrayOf(0f, 1f),
                alphas = floatArrayOf(0f, 0.4f),
            )
        } else {
            ColorOsAppBarGradientDrawable(
                baseColor = baseColor,
                positions = floatArrayOf(0f, 0.4f, 1f),
                alphas = floatArrayOf(0f, 0.7f, 1f),
            )
        }
    }

    internal companion object {
        private const val TAG = "ONextBoxAppBarBlur"

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            NativeApi.instance != null
    }

    private data class NativeApi(
        val createGradientBlurEffect: Method,
        val setBackgroundRenderEffect: Method,
    ) {
        companion object {
            val instance: NativeApi? by lazy(LazyThreadSafetyMode.PUBLICATION) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@lazy null
                runCatching {
                    val renderEffectClass = Class.forName("com.oplus.graphics.OplusRenderEffect")
                    val backgroundEffectClass =
                        Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect")
                    NativeApi(
                        createGradientBlurEffect = renderEffectClass.getMethod(
                            "createGradientBlurEffect",
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                        ),
                        setBackgroundRenderEffect = backgroundEffectClass.getMethod(
                            "setBackgroundRenderEffect",
                            RenderEffect::class.java,
                            View::class.java,
                        ),
                    )
                }.onFailure {
                    Log.d(TAG, "Native ColorOS app-bar blur API unavailable", it)
                }.getOrNull()
            }
        }
    }
}

/** Exact port of C17's GradientBackgroundDrawable. */
private class ColorOsAppBarGradientDrawable(
    private val baseColor: Int,
    private val positions: FloatArray,
    private val alphas: FloatArray,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return
        val colors = IntArray(positions.size) { index ->
            val channelAlpha = (alphas[index] * 255f).toInt().coerceIn(0, 255)
            Color.argb(
                channelAlpha,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor),
            )
        }
        // C17 intentionally defines the gradient from the bottom towards the top.
        paint.shader = LinearGradient(
            0f,
            bounds.height().toFloat(),
            0f,
            0f,
            colors,
            positions,
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (!bounds.isEmpty) canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        if (paint.alpha == clamped) return
        paint.alpha = clamped
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
