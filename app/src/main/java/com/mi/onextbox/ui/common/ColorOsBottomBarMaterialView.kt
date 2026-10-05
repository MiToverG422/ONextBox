package com.mi.onextbox.ui.common

import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
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

/**
 * ColorOS 17's floating bottom-bar material (FRAMEWORK_BOTTOM_BAR + CAPSULE_3).
 *
 * The constants here are mirrored from the ColorOS 17 MMS APK. The vendor RenderNode APIs
 * provide the real background blur and caustic shadow on ColorOS; the Canvas path is only a
 * fallback for devices where those hidden APIs are unavailable.
 */
internal class ColorOsBottomBarMaterialView(
    context: Context,
    darkTheme: Boolean,
    fallbackColor: Int,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val colorOsEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = BlendMode.SRC_OVER
        }
    }
    private val colorOsEdgeShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { RuntimeShader(ColorOs17ShadowEdgeShader) }.getOrNull()
    } else {
        null
    }

    private var darkTheme = darkTheme
    private var fallbackColor = fallbackColor
    private var nativeBackgroundApplied = false
    private var nativeStrokeApplied = false

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
        val edgeShader = colorOsEdgeShader
        if (!nativeStrokeApplied && canvas.isHardwareAccelerated && edgeShader != null) {
            colorOsEdgePaint.shader = edgeShader
            canvas.drawPaint(colorOsEdgePaint)
            colorOsEdgePaint.shader = null
        } else if (!nativeStrokeApplied) {
            drawFallbackEdge(canvas)
        }
    }

    private fun applyColorOsMaterial() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return

        // ColorOS installs the transparent state background before setting the background
        // RenderEffect. Replacing it afterwards can discard the vendor RenderNode state.
        background = ColorDrawable(Color.TRANSPARENT)
        nativeBackgroundApplied = applyBackgroundRenderEffect()
        if (!nativeBackgroundApplied) {
            background = ColorDrawable(fallbackColor)
        }
        // ColorOS's RenderNode stroke is substantially dimmer than drawing its source alpha
        // directly into a Canvas shader. Prefer the same native path used by MMS; the custom
        // shader remains only as a fallback for non-ColorOS builds.
        nativeStrokeApplied = applyMaterialStrokeAndCorner()
        clipToOutline = !nativeStrokeApplied
        applyBottomBarShadow()
        invalidateOutline()
        invalidate()
    }

    private fun applyBackgroundRenderEffect(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            // COUIMaterialBlurEffect.TYPE_FRAMEWORK_BOTTOM_BAR. Radius is deliberately in raw
            // render pixels in the ColorOS implementation, rather than density-scaled dp.
            val colorLayer1: Int
            val blendModeLayer1: BlendMode
            val colorLayer2: Int
            val blendModeLayer2: BlendMode
            if (darkTheme) {
                colorLayer1 = Color.parseColor("#80666666")
                blendModeLayer1 = BlendMode.OVERLAY
                colorLayer2 = Color.parseColor("#B3262626")
                blendModeLayer2 = BlendMode.LUMINOSITY
            } else {
                colorLayer1 = Color.parseColor("#B3666666")
                blendModeLayer1 = BlendMode.COLOR_DODGE
                colorLayer2 = Color.parseColor("#99F5F5F5")
                blendModeLayer2 = BlendMode.LUMINOSITY
            }

            val blur = RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.MIRROR)
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

    private fun applyMaterialStrokeAndCorner(): Boolean = runCatching {
        val materialUtilClass = Class.forName("com.oplus.view.material.OplusMaterialUtil")
        val edgeParamsClass = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams")
        val shadowParamsClass = Class.forName("com.oplus.view.material.OplusMaterialShadowParams")
        val cornerParamsClass = Class.forName("com.oplus.view.material.OplusMaterialCornerParams")

        // TYPE_FRAMEWORK_CAPSULE_3, exactly as BaseBottomFloatingMenuView.V() applies it in MMS.
        val shadowFadeIn = if (darkTheme) 0.03f else 0.1f
        val edgeAlpha = if (darkTheme) 0.8f else 0.25f
        val edgeParams = edgeParamsClass.getConstructor(
            Int::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).newInstance(1, 2.5f * density, edgeAlpha, 0f)
        val shadowParams = shadowParamsClass.getConstructor(
            Int::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).newInstance(1, 1f, shadowFadeIn, 1f)
        val cornerParams = cornerParamsClass.getConstructor(
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        ).newInstance(min(width, height) / 2f, 1f)

        materialUtilClass.getMethod(
            "setCornerParams",
            View::class.java,
            cornerParamsClass,
        ).invoke(null, this, cornerParams)
        materialUtilClass.getMethod(
            "setEdgeParams",
            View::class.java,
            edgeParamsClass,
        ).invoke(null, this, edgeParams)
        materialUtilClass.getMethod(
            "setShadowParams",
            View::class.java,
            shadowParamsClass,
        ).invoke(null, this, shadowParams)
        true
    }.getOrDefault(false)

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
                1f,
                1f,
            ),
        )

        // BaseBottomFloatingMenuView's drawable ShadowEdge fallback uses CAPSULE_12.
        val densityScale = max(1f, density) * 0.34f
        val normalizedWidth = max(1f, width) / densityScale
        val normalizedHeight = max(1f, height) / densityScale
        val lineScale = (2f * density) / 8f
        val nearStart = max(2f * lineScale, 2f)
        val nearEnd = nearStart + max(6f * lineScale, 2f)
        val farStart = max(2f * lineScale, 2f)
        val farEnd = farStart + max(3f * lineScale, 2f)
        val nearFade = mapColorOsEdgeFade(
            start = 0.2f,
            extent = 0.4f,
            normalizedWidth = normalizedWidth,
            normalizedHeight = normalizedHeight,
        )
        val farFade = mapColorOsEdgeFade(
            start = 0.15f,
            extent = 0.35f,
            normalizedWidth = normalizedWidth,
            normalizedHeight = normalizedHeight,
        )
        val edgeAlpha = if (darkTheme) 0.15f else 0.8f
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
    ): Pair<Float, Float> {
        val styleWidth = 400f
        val styleHeight = 100f
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
            if (directParent) {
                ancestor.clipChildren = false
                directParent = false
            }
            ancestor.clipToPadding = false
            runCatching {
                // COUIBottomFloatingNavigationBar expands the upper clip by 120 render pixels.
                expandMethod?.invoke(null, ancestor, 0, 120, 0, 0)
            }
            if (ancestor.id == android.R.id.content) break
            ancestor = ancestor.parent
        }
    }

    private fun applyBottomBarShadow() {
        // ShadowUtils level 7 (caustic-down), used by BaseBottomFloatingMenuView.L().
        elevation = 24f * density
        val standardShadowColor = Color.argb(45, 0, 0, 0)
        outlineAmbientShadowColor = standardShadowColor
        outlineSpotShadowColor = standardShadowColor

        runCatching {
            val materialUtilClass = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            materialUtilClass.getMethod(
                "setOutlineCausticShadowColor",
                View::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(null, this, 0x42FFFFFF)
            materialUtilClass.getMethod(
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
            )
            materialUtilClass.getMethod(
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
                10.399994f * density,
                0.6f,
                0.6f,
                18f,
                0.45f,
                0.6f,
            )
        }

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
                -320f * density,
                0f,
                1600f * density,
                0f,
            )
        }
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
        fallbackPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                blendColor(fallbackColor, Color.WHITE, if (darkTheme) 0.035f else 0.12f),
                fallbackColor,
                blendColor(fallbackColor, Color.BLACK, if (darkTheme) 0.10f else 0.04f),
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            radius,
            radius,
            fallbackPaint,
        )
        fallbackPaint.shader = null
    }

    private fun drawFallbackEdge(canvas: Canvas) {
        val inset = fallbackEdgePaint.strokeWidth / 2f
        val radius = min(width, height) / 2f - inset
        val strong = if (darkTheme) 0x26FFFFFF else 0xCCFFFFFF.toInt()
        val soft = if (darkTheme) 0x0AFFFFFF else 0x24000000
        fallbackEdgePaint.shader = SweepGradient(
            width / 2f,
            height / 2f,
            intArrayOf(soft, strong, Color.TRANSPARENT, soft, strong, soft),
            floatArrayOf(0f, 0.13f, 0.38f, 0.62f, 0.83f, 1f),
        )
        canvas.drawRoundRect(
            inset,
            inset,
            width - inset,
            height - inset,
            radius,
            radius,
            fallbackEdgePaint,
        )
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
