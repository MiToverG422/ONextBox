package com.mi.onextbox.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.compositionLocalOf
import kotlin.math.max
import kotlin.math.min

internal val LocalSegmentBackdropEnabled = compositionLocalOf { true }

@SuppressLint("NewApi")
internal class ColorOsSegmentBackdropView(context: Context, private val outlineEnabled: Boolean = true) : View(context) {
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = ColorOsSegmentEdge(density)
    private var dark = false
    private var blurEnabled = false
    private var blurred = false
    private var nativeEdge = false

    init {
        setWillNotDraw(false)
        isForceDarkAllowed = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipToOutline = true
        background = ColorDrawable(Color.TRANSPARENT)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, width, height, min(width, height) / 2f)
            }
        }
    }

    fun updateAppearance(dark: Boolean, blurEnabled: Boolean) {
        if (this.dark == dark && this.blurEnabled == blurEnabled) return
        this.dark = dark
        this.blurEnabled = blurEnabled
        applyMaterial()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyMaterial()
    }

    override fun onDetachedFromWindow() {
        clearMaterial()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!blurred) {
            trackPaint.color = if (dark) 0x0FFFFFFF else 0x0F000000
            val radius = min(width, height) / 2f
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, trackPaint)
        }
        if (outlineEnabled && !nativeEdge) edge.draw(canvas, width.toFloat(), height.toFloat(), dark, selected = false)
    }

    private fun applyMaterial() {
        clearMaterial()
        val setter = backgroundEffectSetter
        if (isAttachedToWindow && isHardwareAccelerated && blurEnabled && setter != null) {
            blurred = runCatching {
                val tint = if (dark) 0x7F7F7F7F else 0x7F333333
                val blend = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE
                val luminosity = if (dark) 0x99262626.toInt() else 0x99EFEFEF.toInt()
                val sampled = RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.MIRROR)
                val tinted = RenderEffect.createColorFilterEffect(BlendModeColorFilter(tint, blend), sampled)
                val material = RenderEffect.createColorFilterEffect(
                    BlendModeColorFilter(luminosity, BlendMode.LUMINOSITY), tinted,
                )
                setter.invoke(null, material, this)
            }.isSuccess
            if (blurred && outlineEnabled) {
                nativeEdge = runCatching {
                    val util = Class.forName("com.oplus.view.material.OplusMaterialUtil")
                    val edgeClass = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams")
                    val shadowClass = Class.forName("com.oplus.view.material.OplusMaterialShadowParams")
                    val edgeParams = edgeClass.getConstructor(
                        Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    ).newInstance(1, 2.5f * density, if (dark) .8f else .6f, 0f)
                    val shadowParams = shadowClass.getConstructor(
                        Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    ).newInstance(1, if (dark) 1f else 1.15f, if (dark) .05f else .15f, 1f)
                    util.getMethod("setShadowParams", View::class.java, shadowClass)
                        .invoke(null, this, shadowParams)
                    util.getMethod("setEdgeParams", View::class.java, edgeClass)
                        .invoke(null, this, edgeParams)
                }.isSuccess
            }
        }
        invalidate()
    }

    private fun clearMaterial() {
        if (blurred) runCatching { backgroundEffectSetter?.invoke(null, null, this) }
        if (nativeEdge) runCatching {
            val util = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            val edgeClass = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams")
            val shadowClass = Class.forName("com.oplus.view.material.OplusMaterialShadowParams")
            util.getMethod("setEdgeParams", View::class.java, edgeClass).invoke(null, this, null)
            util.getMethod("setShadowParams", View::class.java, shadowClass).invoke(null, this, null)
        }
        blurred = false
        nativeEdge = false
    }

    private companion object {
        val backgroundEffectSetter by lazy {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else runCatching {
                Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect").getMethod(
                    "setBackgroundRenderEffect", RenderEffect::class.java, View::class.java,
                )
            }.getOrNull()
        }
    }
}

@SuppressLint("NewApi")
internal class ColorOsSegmentEdge(private val density: Float) {
    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { RuntimeShader(ColorOs17ShadowEdgeShader) }.getOrNull()
    } else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastWidth = 0f
    private var lastHeight = 0f
    private var lastDark = false
    private var lastSelected = false

    fun draw(canvas: Canvas, width: Float, height: Float, dark: Boolean, selected: Boolean) {
        if (width <= 0f || height <= 0f) return
        val edgeShader = shader
        if (edgeShader == null || !canvas.isHardwareAccelerated) {
            val line = .75f * density
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = line
            paint.color = if (dark) 0x22FFFFFF else 0x15000000
            val radius = ((min(width, height) - line) / 2f).coerceAtLeast(0f)
            canvas.drawRoundRect(line / 2f, line / 2f, width - line / 2f, height - line / 2f, radius, radius, paint)
            return
        }
        if (width != lastWidth || height != lastHeight || dark != lastDark || selected != lastSelected) {
            lastWidth = width
            lastHeight = height
            lastDark = dark
            lastSelected = selected
            val radius = min(width, height) / 2f
            val scale = max(1f, density) * .34f
            val w = width / scale
            val h = height / scale
            val near = mapColorOsEdgeFade(.2f, .4f, w, h, 400f, 100f)
            val far = mapColorOsEdgeFade(.15f, .35f, w, h, 400f, 100f)
            val edgeScale = (if (selected) 1.8f else 2.5f) * density / 8f
            val edgeAlpha = if (selected) {
                if (dark) .15f else .8f
            } else if (dark) .1f else .45f
            val shadow = if (dark) {
                if (selected) .02f else .03f
            } else if (selected) .05f else .15f
            val lineStart = max(2f, edgeScale * 2f)
            edgeShader.setFloatUniform("u_commonArray", floatArrayOf(
                width, height, 0f, 0f, width, height, radius, radius, radius, radius, 2f, 1f,
            ))
            edgeShader.setFloatUniform("u_shadow", 1f)
            edgeShader.setFloatUniform("u_shadowArray", floatArrayOf(
                1f, 1f, 1f, shadow, 0f, 0f, 120f * scale, 0f, 20f * scale,
                1f + 120f / w, 1f + 15f / h, 4f, .9f, 20f * scale, 4f, .7f,
            ))
            edgeShader.setFloatUniform("u_edge", 1f)
            edgeShader.setFloatUniform("u_edgeArray", floatArrayOf(
                1f, 1f, 1f, lineStart, lineStart + max(2f, 6f * edgeScale),
                lineStart, lineStart + max(2f, 3f * edgeScale), .7f * edgeAlpha, edgeAlpha,
                near.first, near.second, far.first, far.second, 0f, 4f, .8f,
            ))
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = edgeShader
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }
}
