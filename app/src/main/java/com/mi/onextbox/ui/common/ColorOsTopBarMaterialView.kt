package com.mi.onextbox.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Toolbar-button material with vendor blur and shadows, using Canvas as a fallback. */
// High-API effects are guarded at creation and have a plain fallback.
@SuppressLint("NewApi")
internal class ColorOsTopBarMaterialView(
    context: Context,
    darkTheme: Boolean,
    fallbackColor: Int,
    private val searchCapsule: Boolean = false,
    nativeBlurEnabled: Boolean = true,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.9f * density
    }
    private val colorOsEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (android.os.Build.VERSION.SDK_INT >= 29) blendMode = BlendMode.SRC_OVER
    }
    private val colorOsEdgeShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { RuntimeShader(ColorOs17ShadowEdgeShader) }.getOrNull()
    } else {
        null
    }

    private var darkTheme = darkTheme
    private var fallbackColor = fallbackColor
    private var nativeBlurEnabled = nativeBlurEnabled
    private var nativeBackgroundApplied = false
    private var nativeCausticApplied = false
    private var searchProgress = 1f
    private var nativeStrokeApplied = false
    private var nativeInnerShadowApplied = false
    private var materialInitialized = false
    private val materialEdgeWidth: Float get() = when {
        !nativeBlurEnabled -> 1.8f
        searchCapsule -> 2.2f
        else -> 1.9f
    }
    private val materialEdgeAlpha: Float get() = if (nativeBlurEnabled) {
        if (darkTheme) .6f else .2f
    } else {
        if (darkTheme) .15f else .8f
    }

    init {
        setWillNotDraw(false)
        isForceDarkAllowed = false
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    view.width,
                    view.height,
                    min(view.width, view.height) / 2f,
                )
            }
        }
        background = ColorDrawable(Color.TRANSPARENT)
    }

    fun updateAppearance(darkTheme: Boolean, fallbackColor: Int, nativeBlurEnabled: Boolean) {
        if (this.darkTheme == darkTheme && this.fallbackColor == fallbackColor &&
            this.nativeBlurEnabled == nativeBlurEnabled) return
        this.darkTheme = darkTheme
        this.fallbackColor = fallbackColor
        this.nativeBlurEnabled = nativeBlurEnabled
        updateColorOsEdgeShader(width.toFloat(), height.toFloat())
        applyColorOsMaterial()
    }

    fun updateSearchProgress(progress: Float) {
        if (!searchCapsule || searchProgress == progress) return
        searchProgress = progress.coerceIn(0f, 1f)
        if (isAttachedToWindow && width > 0 && height > 0) {
            applyMaterialCornerAndShadow()
            nativeCausticApplied = applyCausticShadow()
            updateColorOsEdgeShader(width.toFloat(), height.toFloat())
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyShadowClipExpansion()
        applyColorOsMaterial()
    }

    override fun onDetachedFromWindow() {
        clearBackgroundRenderEffect()
        materialInitialized = false
        super.onDetachedFromWindow()
    }

    override fun hasOverlappingRendering(): Boolean = if (searchCapsule) false else super.hasOverlappingRendering()

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateColorOsEdgeShader(width.toFloat(), height.toFloat())
        invalidateOutline()
        if (!materialInitialized) {
            applyColorOsMaterial()
        } else {
            updateMaterialCorner()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        if (!nativeBackgroundApplied) {
            drawFallbackMaterial(canvas)
        }
        if (nativeStrokeApplied && nativeInnerShadowApplied) return
        val edgeShader = colorOsEdgeShader
        if (canvas.isHardwareAccelerated && edgeShader != null) {
            edgeShader.setFloatUniform("u_edge", if (nativeStrokeApplied) 0f else 1f)
            edgeShader.setFloatUniform("u_shadow", if (nativeInnerShadowApplied) 0f else 1f)
            colorOsEdgePaint.shader = edgeShader
            canvas.drawPaint(colorOsEdgePaint)
            colorOsEdgePaint.shader = null
        } else if (!nativeStrokeApplied) {
            drawFallbackEdge(canvas)
        }
    }


    private fun applyColorOsMaterial() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return

        // Keep the original background/RenderNode across size and appearance updates.
        // Moving detail pages draw the theme material locally instead of sampling the window.
        if (nativeBlurEnabled) {
            nativeBackgroundApplied = applyBackgroundRenderEffect()
        } else {
            clearBackgroundRenderEffect()
        }
        applyMaterialCornerAndShadow()
        nativeCausticApplied = applyCausticShadow()
        clipToOutline = !searchCapsule || !nativeStrokeApplied
        materialInitialized = true
        invalidateOutline()
        invalidate()
    }

    private fun updateMaterialCorner() {
        runCatching {
            val paramsClass = Class.forName("com.oplus.view.material.OplusMaterialCornerParams")
            val params = paramsClass.getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .newInstance(min(width, height) / 2f, if (searchCapsule) 1f else 2f)
            Class.forName("com.oplus.view.material.OplusMaterialUtil")
                .getMethod("setCornerParams", View::class.java, paramsClass).invoke(null, this, params)
        }
    }

    private fun applyBackgroundRenderEffect(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            val colorLayer1 = ColorOsToolbarGlass.firstTint(darkTheme)
            val blendModeLayer1 = if (darkTheme) BlendMode.OVERLAY else BlendMode.COLOR_DODGE
            val colorLayer2 = ColorOsToolbarGlass.luminosityTint(darkTheme)
            val blendModeLayer2 = BlendMode.LUMINOSITY
            val firstLayer = RenderEffect.createColorFilterEffect(
                BlendModeColorFilter(colorLayer1, blendModeLayer1),
                RenderEffect.createBlurEffect(
                    ColorOsToolbarGlass.BlurRadiusPx, ColorOsToolbarGlass.BlurRadiusPx, Shader.TileMode.MIRROR,
                ),
            )
            val material = RenderEffect.createColorFilterEffect(
                BlendModeColorFilter(colorLayer2, blendModeLayer2),
                firstLayer,
            )
            val effectClass = Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect")
            effectClass.getMethod(
                "setBackgroundRenderEffect",
                RenderEffect::class.java,
                View::class.java,
            ).invoke(null, material, this)
            true
        }.getOrDefault(false)
    }

    private fun applyMaterialCornerAndShadow() {
        val edgeType = if (searchCapsule) 1 else 2
        val useNativeMaterial = nativeBackgroundApplied
        val applied = runCatching {
            val materialUtilClass = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            val edgeParamsClass = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams")
            val cornerParamsClass = Class.forName("com.oplus.view.material.OplusMaterialCornerParams")

            val edgeAlpha = materialEdgeAlpha * (if (searchCapsule) searchProgress else 1f)
            val edgeParams = edgeParamsClass.getConstructor(
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).newInstance(edgeType, materialEdgeWidth * density, edgeAlpha, 0f)
            val cornerParams = cornerParamsClass.getConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).newInstance(min(width, height) / 2f, if (searchCapsule) 1f else 2f)

            // A native stroke must not be drawn again by the Canvas fallback.
            materialUtilClass.getMethod(
                "setCornerParams",
                View::class.java,
                cornerParamsClass,
            ).invoke(null, this, cornerParams)
            materialUtilClass.getMethod(
                "setEdgeParams",
                View::class.java,
                edgeParamsClass,
            ).invoke(null, this, if (useNativeMaterial) edgeParams else null)
            useNativeMaterial
        }
        nativeStrokeApplied = applied.getOrDefault(nativeStrokeApplied)

        val shadowApplied = runCatching {
            val materialUtilClass = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            val shadowParamsClass = Class.forName("com.oplus.view.material.OplusMaterialShadowParams")
            val shadowFadeIn = if (searchCapsule || !nativeBlurEnabled) {
                if (darkTheme) .01f else .1f
            } else {
                if (darkTheme) .1f else .15f
            }
            val shadowFadeScale = if (searchCapsule && nativeBlurEnabled && !darkTheme) 1.15f else 1f
            val shadowFadeOut = if (nativeBlurEnabled) 1f else 0f
            val shadowParams = shadowParamsClass.getConstructor(
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).newInstance(edgeType, shadowFadeScale, shadowFadeIn * (if (searchCapsule) searchProgress else 1f), shadowFadeOut)
            materialUtilClass.getMethod(
                "setShadowParams",
                View::class.java,
                shadowParamsClass,
            ).invoke(null, this, if (useNativeMaterial) shadowParams else null)
            useNativeMaterial
        }
        nativeInnerShadowApplied = shadowApplied.getOrDefault(nativeInnerShadowApplied)
    }

    private fun updateColorOsEdgeShader(width: Float, height: Float) {
        val shader = colorOsEdgeShader ?: return
        if (width <= 0f || height <= 0f) return

        val radius = min(width, height) / 2f
        shader.setFloatUniform(
            "u_commonArray",
            floatArrayOf(
                width,
                height,
                0f,
                0f,
                width,
                height,
                radius,
                radius,
                radius,
                radius,
                if (searchCapsule) 1f else 2f,
                1f,
            ),
        )

        val densityScale = max(1f, density) * 0.34f
        val normalizedWidth = max(1f, width) / densityScale
        val normalizedHeight = max(1f, height) / densityScale
        val lineScale = materialEdgeWidth * density / 8f
        val nearStart = max(2f * lineScale, 2f)
        val nearEnd = nearStart + max(6f * lineScale, 2f)
        val farStart = max(2f * lineScale, 2f)
        val farEnd = farStart + max(3f * lineScale, 2f)
        val nearFade = mapColorOsEdgeFade(
            start = if (searchCapsule) .2f else .06f,
            extent = 0.4f,
            normalizedWidth = normalizedWidth,
            normalizedHeight = normalizedHeight,
            styleWidth = if (searchCapsule) 400f else 160f,
            styleHeight = if (searchCapsule) 100f else 160f,
        )
        val farFade = mapColorOsEdgeFade(
            start = if (searchCapsule) .15f else .01f,
            extent = if (searchCapsule) .35f else .28f,
            normalizedWidth = normalizedWidth,
            normalizedHeight = normalizedHeight,
            styleWidth = if (searchCapsule) 400f else 160f,
            styleHeight = if (searchCapsule) 100f else 160f,
        )
        val edgeAlpha = materialEdgeAlpha * (if (searchCapsule) searchProgress else 1f)
        shader.setFloatUniform(
            "u_edgeArray",
            floatArrayOf(
                1f,
                1f,
                1f,
                nearStart,
                nearEnd,
                farStart,
                farEnd,
                0.7f * edgeAlpha,
                edgeAlpha,
                nearFade.first,
                nearFade.second,
                farFade.first,
                farFade.second,
                0f,
                4f,
                0.8f,
            ),
        )
        val shadowFadeIn = if (searchCapsule || !nativeBlurEnabled) {
            if (darkTheme) .01f else .1f
        } else {
            if (darkTheme) .1f else .15f
        } * (if (searchCapsule) searchProgress else 1f)
        val shadowFadeScale = if (searchCapsule && nativeBlurEnabled && !darkTheme) 1.15f else 1f
        shader.setFloatUniform(
            "u_shadowArray",
            floatArrayOf(
                1f, 1f, 1f,
                (if (searchCapsule) 1f else .4f) * shadowFadeIn,
                0f, 0f,
                (if (searchCapsule) 120f else 140f) * shadowFadeScale * densityScale,
                0f,
                20f * densityScale,
                1f + (if (searchCapsule) 120f else 64f) / normalizedWidth,
                1f + (if (searchCapsule) 15f else 56f) / normalizedHeight,
                4f, .9f,
                20f * shadowFadeScale * densityScale,
                4f, .7f,
            ),
        )
        invalidate()
    }

    private fun mapColorOsEdgeFade(
        start: Float,
        extent: Float,
        normalizedWidth: Float,
        normalizedHeight: Float,
        styleWidth: Float = 160f,
        styleHeight: Float = 160f,
    ): Pair<Float, Float> {
        val stylePerimeter = (styleWidth + styleHeight) * 2f
        val actualPerimeter = (normalizedWidth + normalizedHeight) * 2f
        val total = start + extent
        val stylePosition = total * stylePerimeter
        val mappedStart: Float
        val mappedExtent: Float
        if (stylePosition > styleWidth) {
            val styleExtent = extent * stylePerimeter
            mappedStart = ((stylePosition - styleWidth) + normalizedWidth - styleExtent) /
                actualPerimeter
            mappedExtent = styleExtent / actualPerimeter
        } else if (total <= 0f) {
            mappedStart = start
            mappedExtent = extent
        } else {
            val remaining = styleWidth - stylePosition
            val mappedTotal = (normalizedWidth - remaining) / actualPerimeter
            mappedStart = start * mappedTotal / total
            mappedExtent = extent * mappedTotal / total
        }
        return mappedStart to (mappedStart + mappedExtent)
    }

    private fun applyShadowClipExpansion() {
        // C17 expands every toolbar ancestor by 120 render pixels so the 16dp elevation and
        // caustic halo are not cut at the 40dp button/AndroidView boundary.
        val oplusViewClass = runCatching { Class.forName("com.oplus.view.OplusView") }.getOrNull()
        val expandMethod = oplusViewClass?.let { clazz ->
            runCatching {
                clazz.getMethod(
                    "setClipExpandSize",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
            }.getOrNull()
        }
        runCatching {
            oplusViewClass?.getMethod(
                "setShadowClippingEnabled",
                View::class.java,
                Boolean::class.javaPrimitiveType,
            )?.invoke(null, this, true)
        }

        var ancestor = parent
        var remainingLevels = 4
        var directParent = true
        while (ancestor is ViewGroup && remainingLevels-- > 0) {
            // AndroidViewHolder defaults to clipChildren=true, unlike C17's toolbar host.
            if (directParent) {
                ancestor.clipChildren = false
                directParent = false
            }
            ancestor.clipToPadding = false
            runCatching {
                expandMethod?.invoke(null, ancestor, 120, 120, 120, 120)
            }
            if (ancestor.id == android.R.id.content) break
            ancestor = ancestor.parent
        }
    }

    private fun applyCausticShadow(): Boolean {
        // COUI caustic level 6. Keep the normal View shadow as the safe fallback.
        val shadowProgress = if (searchCapsule) 0f else 1f
        elevation = (16f * density).roundToInt().toFloat() * shadowProgress
        val standardShadowColor = Color.argb(30, 0, 0, 0)
        outlineAmbientShadowColor = standardShadowColor
        outlineSpotShadowColor = standardShadowColor

        val causticApplied = runCatching {
            val materialUtilClass = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            val causticColor = Color.argb(((if (darkTheme) 51 else 46) * shadowProgress).roundToInt(), 255, 255, 255)
            val colorApplied = materialUtilClass.getMethod(
                "setOutlineCausticShadowColor",
                View::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(null, this, causticColor) as? Boolean ?: false
            val layoutApplied = materialUtilClass.getMethod(
                "setOutlineCausticShadowLayout",
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(
                null,
                this,
                0,
                0,
                0,
                (4.899994f * density).roundToInt(),
            ) as? Boolean ?: false
            val paramsApplied = materialUtilClass.getMethod(
                "setOutlineCausticShadowParams",
                View::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).invoke(
                null,
                this,
                (9.22998f * density).roundToInt().toFloat(),
                0.6f,
                0.6f,
                18f,
                0.45f,
                0.6f,
            ) as? Boolean ?: false
            colorApplied && layoutApplied && paramsApplied
        }.getOrDefault(false)

        runCatching {
            val oplusViewClass = Class.forName("com.oplus.view.OplusView")
            val oplusView = oplusViewClass.getConstructor(View::class.java).newInstance(this)
            oplusViewClass.getMethod(
                "setOverrideLightSourceGeometry",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).invoke(
                oplusView,
                -1f,
                -(160f * density).roundToInt().toFloat(),
                0f,
                (800f * density).roundToInt().toFloat(),
                0f,
            )
        }
        return causticApplied
    }

    private fun clearBackgroundRenderEffect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !nativeBackgroundApplied) return
        runCatching {
            val effectClass = Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect")
            effectClass.getMethod(
                "setBackgroundRenderEffect",
                RenderEffect::class.java,
                View::class.java,
            ).invoke(null, null, this)
        }
        nativeBackgroundApplied = false
    }

    private fun drawFallbackMaterial(canvas: Canvas) {
        val radius = min(width, height) / 2f
        val surfaceColor = Color.argb(
            ColorOsToolbarGlass.FallbackAlpha,
            Color.red(fallbackColor), Color.green(fallbackColor), Color.blue(fallbackColor),
        )
        fallbackPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                blendColor(surfaceColor, Color.WHITE, if (darkTheme) 0.035f else 0.12f),
                surfaceColor,
                blendColor(surfaceColor, Color.BLACK, if (darkTheme) 0.10f else 0.04f),
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, fallbackPaint)
        fallbackPaint.shader = null
    }

    private fun drawFallbackEdge(canvas: Canvas) {
        fallbackEdgePaint.strokeWidth = materialEdgeWidth * density
        fallbackEdgePaint.alpha = if (searchCapsule) (255 * searchProgress).roundToInt() else 255
        val radius = min(width, height) / 2f - fallbackEdgePaint.strokeWidth / 2f
        val strong = Color.argb((255 * materialEdgeAlpha).roundToInt(), 255, 255, 255)
        val soft = Color.argb((255 * materialEdgeAlpha * .15f).roundToInt(), 255, 255, 255)
        fallbackEdgePaint.shader = SweepGradient(
            width / 2f,
            height / 2f,
            intArrayOf(soft, strong, Color.TRANSPARENT, soft, strong, soft),
            floatArrayOf(0f, 0.13f, 0.38f, 0.62f, 0.83f, 1f),
        )
        val inset = fallbackEdgePaint.strokeWidth / 2f
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, fallbackEdgePaint)
        fallbackEdgePaint.shader = null
    }

    private fun blendColor(base: Int, overlay: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(base),
            (Color.red(base) + (Color.red(overlay) - Color.red(base)) * t).roundToInt(),
            (Color.green(base) + (Color.green(overlay) - Color.green(base)) * t).roundToInt(),
            (Color.blue(base) + (Color.blue(overlay) - Color.blue(base)) * t).roundToInt(),
        )
    }
}

/**
 * ColorOS adds toolbar spotlights through ViewOverlay, after the mask and icon have drawn.
 * Keeping this as the last child gives the Compose button the same final overlay layer.
 */
internal class ColorOsTopBarSpotlightView(
    context: Context,
    darkTheme: Boolean,
    style: ColorOsSpotlightRenderer.Style = ColorOsSpotlightRenderer.Style.TopBarButton,
) : View(context) {
    private val spotlight = ColorOsSpotlightRenderer(
        host = this,
        style = style,
        darkTheme = darkTheme,
    )

    init {
        setWillNotDraw(false)
        background = null
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    view.width,
                    view.height,
                    min(view.width, view.height) / 2f,
                )
            }
        }
    }

    fun updateAppearance(darkTheme: Boolean) {
        spotlight.updateAppearance(darkTheme = darkTheme)
    }

    fun spotlightDown(x: Float, y: Float) {
        spotlight.onDown(x, y)
    }

    fun spotlightMove(x: Float, y: Float) {
        spotlight.onMove(x, y)
    }

    fun spotlightUp(x: Float, y: Float) {
        spotlight.onUp(x, y)
    }

    fun cancelSpotlight() {
        spotlight.cancel()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        spotlight.onSizeChanged(width, height)
        invalidateOutline()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        spotlight.draw(canvas)
    }

    override fun onDetachedFromWindow() {
        spotlight.cancel()
        super.onDetachedFromWindow()
    }
}
