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
    private var nativeBackgroundApplied = false
    private var nativeCausticApplied = false
    private var searchProgress = .4f
    private var nativeSearchStrokeApplied = false

    init {
        setWillNotDraw(false)
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
        background = ColorDrawable(fallbackColor)
    }

    fun updateAppearance(darkTheme: Boolean, fallbackColor: Int) {
        if (this.darkTheme == darkTheme && this.fallbackColor == fallbackColor) return
        this.darkTheme = darkTheme
        this.fallbackColor = fallbackColor
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
        post {
            applyShadowClipExpansion()
            applyColorOsMaterial()
        }
    }

    override fun onDetachedFromWindow() {
        clearBackgroundRenderEffect()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateColorOsEdgeShader(width.toFloat(), height.toFloat())
        invalidateOutline()
        post(::applyColorOsMaterial)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        if (!nativeBackgroundApplied) {
            drawFallbackMaterial(canvas)
        }
        // Search uses the native capsule material channel (CAPSULE_7 for glass preset 1).
        // Do not reuse the toolbar circle's constantly visible AGSL edge preset.
        val edgeShader = colorOsEdgeShader
        if (canvas.isHardwareAccelerated && edgeShader != null) {
            colorOsEdgePaint.shader = edgeShader
            canvas.drawPaint(colorOsEdgePaint)
            colorOsEdgePaint.shader = null
        } else {
            drawFallbackEdge(canvas)
        }
    }


    private fun applyColorOsMaterial() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return

        // C17 installs the transparent state background before applying the RenderEffect.
        // Replacing the background afterwards can rebuild the RenderNode and drop vendor state.
        background = ColorDrawable(Color.TRANSPARENT)
        nativeBackgroundApplied = applyBackgroundRenderEffect()
        if (!nativeBackgroundApplied) {
            background = ColorDrawable(if (searchCapsule) Color.TRANSPARENT else fallbackColor)
        }
        applyMaterialCornerAndShadow()
        nativeCausticApplied = applyCausticShadow()
        invalidateOutline()
        invalidate()
    }

    private fun applyBackgroundRenderEffect(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            // COUIMaterialBlurEffect.TYPE_FRAMEWORK_TOP_BAR (materialType = 0).
            val colorLayer1: Int
            val blendModeLayer1: BlendMode
            val colorLayer2: Int
            val blendModeLayer2: BlendMode
            if (searchCapsule) {
                // ToolbarMaterialEffectResolver materialType=1: C17's translucent glass
                // preset, shared across light/dark themes, rather than the opaque TOP_BAR.
                colorLayer1 = Color.parseColor("#1A6E6E6E")
                blendModeLayer1 = BlendMode.COLOR_DODGE
                colorLayer2 = Color.parseColor("#24F1F1F1")
                blendModeLayer2 = BlendMode.LUMINOSITY
            } else if (darkTheme) {
                colorLayer1 = Color.parseColor("#CC262626")
                blendModeLayer1 = BlendMode.SRC_OVER
                colorLayer2 = Color.parseColor("#B37F7F7F")
                blendModeLayer2 = BlendMode.OVERLAY
            } else {
                colorLayer1 = Color.parseColor("#66333333")
                blendModeLayer1 = BlendMode.COLOR_DODGE
                colorLayer2 = Color.parseColor("#99F5F5F5")
                blendModeLayer2 = BlendMode.LUMINOSITY
            }

            val blurRadius = if (searchCapsule) 25f else 0f
            val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.MIRROR)
            val firstLayer = RenderEffect.createColorFilterEffect(
                BlendModeColorFilter(colorLayer1, blendModeLayer1),
                blur,
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
        val applied = runCatching {
            val materialUtilClass = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            val edgeParamsClass = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams")
            val shadowParamsClass = Class.forName("com.oplus.view.material.OplusMaterialShadowParams")
            val cornerParamsClass = Class.forName("com.oplus.view.material.OplusMaterialCornerParams")

            // Search: TYPE_FRAMEWORK_CAPSULE_7; toolbar: TYPE_FRAMEWORK_CIRCLE_1.
            val shadowFadeIn = if (searchCapsule) .05f else { if (darkTheme) 0.1f else 0.15f }
            val edgeType = if (searchCapsule) 1 else 2
            val edgeWidth = if (searchCapsule) 2.5f else 1.9f
            // Toolbar keeps its Canvas edge; search delegates its edge to the native channel.
            val edgeParams = edgeParamsClass.getConstructor(
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).newInstance(edgeType, edgeWidth * density, 0f, 0f)
            val shadowParams = shadowParamsClass.getConstructor(
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).newInstance(edgeType, 1f, shadowFadeIn * (if (searchCapsule) searchProgress else 1f), 1f)
            val cornerParams = cornerParamsClass.getConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ).newInstance(min(width, height) / 2f, if (searchCapsule) 1f else 2f)

            // Do not add a second Canvas edge when the native capsule channel succeeds.
            materialUtilClass.getMethod(
                "setCornerParams",
                View::class.java,
                cornerParamsClass,
            ).invoke(null, this, cornerParams)
            val edgeApplied = materialUtilClass.getMethod(
                "setEdgeParams",
                View::class.java,
                edgeParamsClass,
            ).invoke(null, this, edgeParams)
            materialUtilClass.getMethod(
                "setShadowParams",
                View::class.java,
                shadowParamsClass,
            ).invoke(null, this, shadowParams)
            edgeApplied != false
        }
        nativeSearchStrokeApplied = searchCapsule && applied.getOrDefault(false)
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
                2f,
                1f,
            ),
        )

        // GradientStrokeStylePresets.EDGE_STYLE_1 and GradientStrokeStyleMapper.toEdgeArray16,
        // with the exact TYPE_FRAMEWORK_CIRCLE_1 width/alpha/angle from ColorOS 17.
        val densityScale = max(1f, density) * 0.34f
        val normalizedWidth = max(1f, width) / densityScale
        val normalizedHeight = max(1f, height) / densityScale
        val lineScale = ((if (searchCapsule) 2.5f else 1.9f) * density) / 8f
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
        val edgeAlpha = if (searchCapsule) .5f * searchProgress else if (darkTheme) .6f else .2f
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
        shader.setFloatUniform("u_shadow", 0f)
        shader.setFloatUniform("u_shadowArray", FloatArray(16))
        shader.setFloatUniform("u_edge", 1f)
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
        val shadowProgress = if (searchCapsule) searchProgress else 1f
        elevation = 16f * density * shadowProgress
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
                9.22998f * density,
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
                -160f * density,
                0f,
                800f * density,
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
        val surfaceColor = if (searchCapsule) {
            Color.argb(if (darkTheme) 72 else 115, Color.red(fallbackColor), Color.green(fallbackColor), Color.blue(fallbackColor))
        } else fallbackColor
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
        fallbackEdgePaint.alpha = if (searchCapsule) (255 * searchProgress).roundToInt() else 255
        val radius = min(width, height) / 2f - fallbackEdgePaint.strokeWidth / 2f
        val strong = if (darkTheme) 0x8FFFFFFF.toInt() else 0x3DFFFFFF
        val soft = if (darkTheme) 0x18FFFFFF else 0x12000000
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
