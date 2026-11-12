package com.mi.onextbox.lsp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Checkable
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

/** Identifies the control's state before deciding whether its original fill should be kept. */
internal class SoftLightSelection(private val enabled: () -> Boolean) {
    private val drawing = ThreadLocal<Int>()
    private val applied = WeakHashMap<RuntimeShader, Int>()
    private var activeAttr = 0

    companion object {
        fun white(color: Int) = Color.alpha(color) >= 160 &&
            minOf(Color.red(color), Color.green(color), Color.blue(color)) >= 180 &&
            maxOf(Color.red(color), Color.green(color), Color.blue(color)) -
                minOf(Color.red(color), Color.green(color), Color.blue(color)) <= 42

        fun selected(view: View) = view.isSelected || view.isActivated || (view as? Checkable)?.isChecked == true
    }

    private fun selected(states: IntArray) = states.any {
        it == android.R.attr.state_selected || it == android.R.attr.state_checked ||
            it == android.R.attr.state_activated || (activeAttr != 0 && it == activeAttr)
    }

    fun installTiles(loader: ClassLoader, preserveControlWhite: () -> Boolean, report: (String) -> Unit) {
        val flag = ModernReflect.findClassIfExists("com.oplus.systemui.qs.base.res.model.TileViewFlag", loader) ?: return
        activeAttr = flag.getMethod("getAttr").invoke(flag.getField("Active").get(null)) as Int
        val tile = ModernReflect.findClassIfExists("com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable", loader) ?: return
        val mask = tile.getDeclaredField("maskColor").apply { isAccessible = true }
        val path = tile.getMethod("getPath")
        val config = tile.getDeclaredField("currentConfig").apply { isAccessible = true }
        val first = config.type.getMethod("getFirst")
        val provider = ModernReflect.findClassIfExists("com.oplusos.systemui.common.util.QSBlurConfigProvider", loader)
        val activeMixes = listOf("QS_ACTIVE_LIGHT_MIX_COLOR", "QS_ACTIVE_NIGHT_MIX_COLOR").mapNotNull { name ->
            runCatching { provider?.getDeclaredField(name)?.apply { isAccessible = true }?.get(null) }.getOrNull()
        }
        val mix = ModernReflect.findClassIfExists("com.oplusos.systemui.common.blurability.BlurMixConfig\$BlurMixMultiWithShader", loader)
        val layers = mix?.methods.orEmpty().filter { it.name in setOf("getForegroundShaderParam", "getBackgroundShaderParam") }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val draw = tile.getDeclaredMethod("draw", Canvas::class.java)
        ModernHookRegistry.installFast("soft-light:tile-selection", draw, XposedInterface.Hooker { chain ->
            if (!enabled()) return@Hooker chain.proceed()
            val owner = chain.thisObject as Drawable
            val previous = drawing.get()
            val color = mask.getInt(owner)
            if (preserveControlWhite()) {
                val recipe = first.invoke(config.get(owner))
                val activeWhite = Color.alpha(color) < 160 && mix?.isInstance(recipe) == true &&
                    layers.any { it.invoke(recipe) in activeMixes }
                if (white(color) || activeWhite) {
                    val shape = path.invoke(owner) as Path
                    if (!shape.isEmpty) {
                        paint.color = if (activeWhite) Color.WHITE else color
                        paint.alpha = paint.alpha * owner.alpha / 255
                        (chain.getArg(0) as Canvas).drawPath(shape, paint)
                        report("solid white control tile")
                        return@Hooker null
                    }
                }
            }
            val state = when {
                white(color) -> 2
                selected(owner.state) -> classify(color)
                // This tile can use a native blur mix instead of an opaque mask on keyguard.
                Color.alpha(color) < 160 -> 1
                else -> 0
            }
            drawing.set(state)
            try { chain.proceed() } finally { if (previous == null) drawing.remove() else drawing.set(previous) }
        })
    }

    // A translucent mask needs the host shader's pre-glass fill to decide its final color.
    private fun classify(color: Int?) = when {
        color == null || Color.alpha(color) < 160 -> 1
        white(color) -> 2
        else -> 0
    }

    private fun state(drawable: Drawable): Int {
        drawing.get()?.let { return it }
        var marked = selected(drawable.state)
        var owner = drawable.callback
        var depth = 0
        while (owner is Drawable && depth++ < 12) {
            marked = marked || selected(owner.state)
            owner = owner.callback
        }
        var view = owner as? View
        depth = 0
        while (view != null && depth++ < 8) {
            if (marked || selected(view) || selected(view.drawableState)) {
                val tint = view.backgroundTintList
                return classify(tint?.getColorForState(view.drawableState, tint.defaultColor)
                    ?: (view.background as? ColorDrawable)?.color)
            }
            view = view.parent as? View
        }
        return if (marked) 1 else 0
    }

    fun adapter(type: Class<*>, owns: (RuntimeShader) -> Boolean, changed: (RuntimeShader) -> Unit): ((Drawable) -> Unit)? {
        val shaderField = type.declaredFields.singleOrNull { it.type.name == "com.oplus.posteffect.agsl.DrawableShader" } ?: return null
        val programField = shaderField.type.declaredFields.singleOrNull { it.type == RuntimeShader::class.java } ?: return null
        shaderField.isAccessible = true
        programField.isAccessible = true
        return update@{ drawable ->
            if (!enabled()) return@update
            val material = shaderField.get(drawable) ?: return@update
            val shader = programField.get(material) as? RuntimeShader ?: return@update
            if (!owns(shader)) return@update
            val value = state(drawable)
            val dirty = synchronized(applied) {
                if (applied[shader] == value) false else {
                    shader.setFloatUniform("onbSelectionState", value.toFloat())
                    applied[shader] = value
                    true
                }
            }
            if (dirty) changed(shader)
        }
    }
}
