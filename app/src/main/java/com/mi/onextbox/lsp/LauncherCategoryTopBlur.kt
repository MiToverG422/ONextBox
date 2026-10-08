package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.mi.onextbox.lsp.compat.ModernReflect

// Top background blur for the standalone category page.
internal class LauncherCategoryTopBlur(context: Context) : View(context) {
    private var disabled = false
    private var fadeStart = 0
    private var appliedStart = -1
    private var appliedWidth = -1
    private var appliedHeight = -1

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false)
    }

    fun updateFade(start: Int) {
        fadeStart = start.coerceAtLeast(0)
        applyEffect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyEffect()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT)
    }

    private fun applyEffect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (disabled || width <= 0 || height <= 0 ||
            appliedStart == fadeStart && appliedWidth == width && appliedHeight == height
        ) return
        runCatching {
            val bridge = ModernReflect.findClass("com.oplus.view.OplusViewBackgroundRenderEffect", context.classLoader)
            val mask = LinearGradient(0f, fadeStart.coerceAtMost(height - 1).toFloat(), 0f, height.toFloat(),
                Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            val radius = (24f * resources.displayMetrics.density).coerceAtLeast(1f)
            val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            val effect = RenderEffect.createBlendModeEffect(blur, RenderEffect.createShaderEffect(mask), BlendMode.DST_IN)
            ModernReflect.callStaticMethod(bridge, "setBackgroundRenderEffect", effect, this)
            appliedStart = fadeStart
            appliedWidth = width
            appliedHeight = height
        }.onFailure {
            disabled = true
            HookLog.w("LauncherCategoryPage", "Top background blur unavailable", it)
        }
    }
}
