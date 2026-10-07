package com.mi.onextbox.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import kotlin.math.min
import kotlin.math.max

// High-API effects are guarded at creation and have a plain fallback.
@SuppressLint("NewApi")
internal class ColorOsDialogMaterialView(
    context: Context,
    private val darkTheme: Boolean,
    private val kind: Kind,
    private val accent: Int = Color.BLUE,
) : View(context) {
    enum class Kind { Panel, Primary, Secondary }

    private val density = resources.displayMetrics.density
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var blurManager: Any? = null
    private var nativeStroke = false
    private var buttonNativeStroke = false
    private val materialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { RuntimeShader(ColorOs17ShadowEdgeShader) }.getOrNull()
    } else null
    private val spotlight = if (kind == Kind.Panel) null else ColorOsSpotlightRenderer(
        this, if (kind == Kind.Primary) ColorOsSpotlightRenderer.Style.DialogPrimary else ColorOsSpotlightRenderer.Style.DialogSecondary, darkTheme,
    )
    private val fallbackColor: Int = when (kind) {
        Kind.Panel -> if (darkTheme) 0xFF1E1E1E.toInt() else Color.WHITE
        Kind.Primary -> accent
        Kind.Secondary -> if (darkTheme) 0xFF4B4B4B.toInt() else 0xFFEBEBEB.toInt()
    }

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = ColorDrawable(fallbackColor)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(::applyMaterial)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateEdgeShader(w.toFloat(), h.toFloat())
        spotlight?.onSizeChanged(w, h)
        post(::applyMaterial)
    }

    override fun onDetachedFromWindow() {
        spotlight?.cancel()
        if (kind != Kind.Panel) {
            setBackgroundEffect(null)
            clearButtonStroke()
        }
        blurManager?.let { manager ->
            runCatching { manager.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType).invoke(manager, 0) }
        }
        blurManager = null
        super.onDetachedFromWindow()
    }

    private fun materialEnabled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@runCatching false
        Settings.System.getInt(context.contentResolver, "system_material_blur_enable", 0) == 1 &&
            context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
    }.getOrDefault(false)

    private fun applyMaterial() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val enabled = materialEnabled()
        var panelBlurApplied = false
        if (kind == Kind.Panel) {
            panelBlurApplied = enabled && applyPanelBlur()
            if (!panelBlurApplied) background = ColorDrawable(fallbackColor)
        } else {
            background = ColorDrawable(Color.TRANSPARENT)
            val blurApplied = enabled && applyButtonBlur()
            if (!blurApplied) {
                setBackgroundEffect(null)
                background = ColorDrawable(fallbackColor)
            }
            buttonNativeStroke = blurApplied && applyButtonStroke()
            if (!buttonNativeStroke) clearButtonStroke()
        }
        nativeStroke = edgeShader != null && if (kind == Kind.Panel) panelBlurApplied else !buttonNativeStroke
        invalidate()
    }

    private fun applyButtonStroke(): Boolean = runCatching {
        val util = Class.forName("com.oplus.view.material.OplusMaterialUtil")
        val edgeClass = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams")
        val shadowClass = Class.forName("com.oplus.view.material.OplusMaterialShadowParams")
        val cornerClass = Class.forName("com.oplus.view.material.OplusMaterialCornerParams")
        val primary = kind == Kind.Primary
        val edgeAlpha = if (primary) { if (darkTheme) .26f else .3f } else .5f
        val fadeIn = if (primary) { if (darkTheme) .08f else .1f } else if (darkTheme) .05f else .1f
        val fadeScale = if (primary && darkTheme) .8f else 1f
        val edge = edgeClass.getConstructor(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).newInstance(1, 2.8f * density, edgeAlpha, 0f)
        val shadow = shadowClass.getConstructor(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).newInstance(1, fadeScale, fadeIn, 1f)
        val corner = cornerClass.getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            .newInstance(min(width, height) / 2f, 1f)
        val cornerApplied = util.getMethod("setCornerParams", View::class.java, cornerClass).invoke(null, this, corner)
        val edgeApplied = util.getMethod("setEdgeParams", View::class.java, edgeClass).invoke(null, this, edge)
        val shadowApplied = util.getMethod("setShadowParams", View::class.java, shadowClass).invoke(null, this, shadow)
        cornerApplied != false && edgeApplied != false && shadowApplied != false
    }.getOrDefault(false)

    private fun clearButtonStroke() {
        runCatching {
            val util = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            for ((method, type) in listOf(
                "setCornerParams" to "OplusMaterialCornerParams",
                "setEdgeParams" to "OplusMaterialEdgeParams",
                "setShadowParams" to "OplusMaterialShadowParams",
            )) {
                util.getMethod(method, View::class.java, Class.forName("com.oplus.view.material.$type"))
                    .invoke(null, this, null)
            }
        }
    }

    private fun applyPanelBlur(): Boolean = runCatching {
        val managerClass = Class.forName("com.oplus.view.ViewRootManager")
        val paramsClass = Class.forName("com.oplus.graphics.OplusBlurParam")
        val manager = blurManager ?: managerClass.getConstructor(View::class.java).newInstance(this).also { blurManager = it }
        val params = paramsClass.getConstructor().newInstance()
        val drawable = managerClass.getMethod("getBackgroundBlurDrawable").invoke(manager) as? Drawable ?: return false
        background = drawable
        paramsClass.getMethod("setBlurType", Int::class.javaPrimitiveType).invoke(params, 2)
        paramsClass.getMethod("setMaterialParams", Int::class.javaPrimitiveType, FloatArray::class.java, FloatArray::class.java)
            .invoke(params, if (darkTheme) 2 else 3,
                colorFloats(if (darkTheme) 0xB2292929.toInt() else 0x509F9F9F),
                colorFloats(if (darkTheme) 0x8C777777.toInt() else 0xDC909090.toInt()))
        paramsClass.getMethod("setSmoothCornerWeight", Float::class.javaPrimitiveType).invoke(params, 3f)
        paramsClass.getMethod("setSmoothCornerType", Int::class.javaPrimitiveType).invoke(params, 1)
        managerClass.getMethod("setBlurParams", paramsClass).invoke(manager, params)
        managerClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType).invoke(manager, 200)
        val radius = min(45f * density, height / 2f)
        managerClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            .invoke(manager, radius, radius, radius, radius)
        managerClass.getMethod("setColor", Int::class.javaPrimitiveType).invoke(manager, Color.TRANSPARENT)
        true
    }.getOrDefault(false)

    private fun applyButtonBlur(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val primary = kind == Kind.Primary
        val firstColor = if (primary) ColorUtils.setAlphaComponent(accent, if (darkTheme) 230 else 229)
            else if (darkTheme) 0x33FFFFFF else 0xCCFFFFFF.toInt()
        val blur = RenderEffect.createBlurEffect(if (primary) 10f else 25f, if (primary) 10f else 25f, Shader.TileMode.MIRROR)
        val first = RenderEffect.createColorFilterEffect(BlendModeColorFilter(firstColor, BlendMode.SRC_OVER), blur)
        val second = RenderEffect.createColorFilterEffect(
            BlendModeColorFilter(if (primary && darkTheme) 0x19FFFFFF else 0x00FFFFFF, if (primary) BlendMode.OVERLAY else BlendMode.SRC_OVER), first,
        )
        return setBackgroundEffect(second)
    }

    private fun setBackgroundEffect(effect: RenderEffect?): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@runCatching false
        Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect")
            .getMethod("setBackgroundRenderEffect", RenderEffect::class.java, View::class.java).invoke(null, effect, this)
        true
    }.getOrDefault(false)

    private fun updateEdgeShader(w: Float, h: Float) {
        val shader = edgeShader ?: return
        if (w <= 0f || h <= 0f) return
        val panel = kind == Kind.Panel
        val primary = kind == Kind.Primary
        val edgeWidth = if (panel) { if (darkTheme) 2f else 4f } else if (primary) 2.5f else 3f
        val edgeAlpha = if (panel) { if (darkTheme) .3f else .6f } else if (primary) { if (darkTheme) .25f else .3f } else if (darkTheme) .1f else .8f
        val fadeIn = if (panel) { if (darkTheme) .03f else .4f } else if (primary) .1f else if (darkTheme) .01f else .05f
        val radius = if (panel) min(45f * density, h / 2f) else min(w, h) / 2f
        shader.setFloatUniform("u_commonArray", floatArrayOf(w, h, 0f, 0f, w, h, radius, radius, radius, radius, if (panel) 3f else 1f, 1f))
        val scale = max(1f, density) * .34f
        val nw = w / scale
        val nh = h / scale
        val perimeter = 2f * (nw + nh)
        val lineScale = edgeWidth * density / if (panel) 10f else 8f
        val near = max(2f * lineScale, 2f)
        val far = max(2f * lineScale, 2f)
        shader.setFloatUniform("u_edgeArray", floatArrayOf(
            1f, 1f, 1f, near, near + max((if (panel) 8f else 6f) * lineScale, 2f),
            far, far + max((if (panel) 2f else 3f) * lineScale, 2f),
            .7f * edgeAlpha, (if (panel) .6f else 1f) * edgeAlpha,
            (nw - if (panel) 312f else 200f) / perimeter, (nw + if (panel) 248f else 200f) / perimeter,
            (nw - if (panel) 200f else 250f) / perimeter, (nw + if (panel) 200f else 100f) / perimeter,
            0f, 4f, .8f,
        ))
        shader.setFloatUniform("u_shadowArray", floatArrayOf(
            1f, 1f, 1f, fadeIn, 0f, 0f, 120f * scale, 0f, 20f * scale,
            1f + 120f / nw, 1f + 15f / nh, 4f, .9f, 20f * scale, 4f, .7f,
        ))
        shader.setFloatUniform("u_edge", 1f)
        shader.setFloatUniform("u_shadow", 1f)
    }

    fun spotlightDown(x: Float, y: Float) { spotlight?.onDown(x, y) }
    fun spotlightMove(x: Float, y: Float) { spotlight?.onMove(x, y) }
    fun spotlightUp(x: Float, y: Float) { spotlight?.onUp(x, y) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (canvas.isHardwareAccelerated && (nativeStroke || buttonNativeStroke)) {
            if (nativeStroke) {
                materialPaint.shader = edgeShader
                canvas.drawPaint(materialPaint)
                materialPaint.shader = null
            }
            spotlight?.draw(canvas)
            return
        }
        if (kind == Kind.Panel) return
        val inset = .5f * density
        val radius = if (kind == Kind.Panel) min(45f * density, height / 2f) else min(width, height) / 2f
        edgePaint.strokeWidth = density
        edgePaint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(if (darkTheme) 0x40FFFFFF else 0x99FFFFFF.toInt(), 0x08FFFFFF, if (darkTheme) 0x20FFFFFF else 0x55FFFFFF),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(inset, inset, width - inset, height - inset), radius, radius, edgePaint)
        edgePaint.shader = null
    }

    private fun colorFloats(color: Int) = floatArrayOf(Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f, Color.alpha(color) / 255f)
}
