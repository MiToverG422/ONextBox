package com.mi.onextbox.lsp

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
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
    private val controlBackgrounds = WeakHashMap<Drawable, Boolean>()
    private var activeAttr = 0

    fun currentControlState() = drawing.get() ?: 0
    fun copyState(from: RuntimeShader, to: RuntimeShader) {
        synchronized(applied) {
            val value = applied[from] ?: return
            to.setFloatUniform("onbSelectionState", value.toFloat())
            applied[to] = value
        }
    }
    private inline fun withControlState(white: Boolean, active: Boolean = white, draw: () -> Any?): Any? {
        val previous = drawing.get()
        drawing.set(if (white) 3 else if (active) -1 else 0)
        return try { draw() } finally { if (previous == null) drawing.remove() else drawing.set(previous) }
    }

    fun refresh() {
        synchronized(controlBackgrounds) { controlBackgrounds.keys.toList() }.forEach { it.invalidateSelf() }
    }

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

    private class WhiteRecipes(loader: ClassLoader) {
        private val provider = ModernReflect.findClassIfExists("com.oplusos.systemui.common.util.QSBlurConfigProvider", loader)
        private val activeMixes = listOf("QS_ACTIVE_LIGHT_MIX_COLOR", "QS_ACTIVE_NIGHT_MIX_COLOR").mapNotNull { name ->
            runCatching { provider?.getDeclaredField(name)?.apply { isAccessible = true }?.get(null) }.getOrNull()
        }
        private val mix = ModernReflect.findClassIfExists("com.oplusos.systemui.common.blurability.BlurMixConfig\$BlurMixMultiWithShader", loader)
        private val layers = mix?.methods.orEmpty().filter { it.name in setOf("getForegroundShaderParam", "getBackgroundShaderParam") }

        private class Entry(val recipe: Any?, val active: Boolean)
        private val entries = WeakHashMap<Drawable, Entry>()

        fun active(owner: Drawable, recipe: Any?): Boolean = synchronized(entries) {
            val cached = entries[owner]
            if (cached != null && cached.recipe === recipe) return@synchronized cached.active
            val active = mix?.isInstance(recipe) == true && layers.any { layer ->
                layer.invoke(recipe)?.let { it in activeMixes } == true
            }
            entries[owner] = Entry(recipe, active)
            active
        }
    }

    fun installDeviceCards(loader: ClassLoader, preserve: () -> Boolean, report: (String) -> Unit) {
        val plugin = ModernReflect.findClassIfExists("com.oplus.systemui.plugins.drawable.MixColorPluginDrawable", loader) ?: return
        val recipes = WhiteRecipes(loader)
        val config = plugin.getDeclaredField("config").apply { isAccessible = true }
        val recipe = config.type.getMethod("getBlurMixConfig")
        val outline = plugin.getMethod("getPathProvider")
        val outlineType = ModernReflect.findClassIfExists("com.oplusos.systemui.common.outline.RoundRectOutlineProvider", loader) ?: return
        val radius = outlineType.getMethod("getCornerRadius", Drawable::class.java)
        val weight = outlineType.getMethod("getCornerWeight", Drawable::class.java)
        val auto = plugin.getDeclaredField("autoBlurDrawable").type
        val proxy = auto.getMethod("getViewBlurProxy")
        val amount = proxy.returnType.getMethod("getBlurAmount")
        var flatColorId: Int? = null
        val shapes = QsSliderProgressShape()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val draw = plugin.getDeclaredMethod("draw", Canvas::class.java)
        ModernHookRegistry.installFast("soft-light:device-background", draw, XposedInterface.Hooker { chain ->
            val owner = chain.thisObject as DrawableWrapper
            if (!recipes.active(owner, recipe.invoke(config.get(owner)))) return@Hooker chain.proceed()
            val view = owner.callback as? View ?: return@Hooker chain.proceed()
            val card = generateSequence(view as View?) { it.parent as? View }.take(4)
                .firstOrNull { QsDeviceCardCorners.isDeviceView(it) } ?: return@Hooker chain.proceed()
            synchronized(controlBackgrounds) { controlBackgrounds[owner] = true }
            if (!preserve()) return@Hooker withControlState(true) { chain.proceed() }
            val drawn = runCatching {
                val id = flatColorId ?: card.resources.getIdentifier("light_os_low_separate_usable_color", "color", "com.android.systemui").also { flatColorId = it }
                val color = if (id != 0) card.context.getColor(id) else Color.WHITE
                if (!white(color)) return@runCatching false
                val provider = outline.invoke(owner)
                val shape = shapes.path(owner, radius.invoke(provider, owner) as Float, weight.invoke(provider, owner) as? Float)
                if (shape.isEmpty) return@runCatching false
                val opacity = amount.invoke(proxy.invoke(owner.drawable)) as Float
                paint.color = color
                paint.alpha = (Color.alpha(color) * opacity.coerceIn(0f, 1f)).toInt()
                (chain.getArg(0) as Canvas).drawPath(shape, paint)
                true
            }.onFailure { report("device selection unavailable: ${it.javaClass.simpleName}") }.getOrDefault(false)
            if (!drawn) return@Hooker chain.proceed()
            report("native flat selected device background")
            null
        })
    }

    fun installTiles(loader: ClassLoader, preserveControlWhite: () -> Boolean, report: (String) -> Unit) {
        val flag = ModernReflect.findClassIfExists("com.oplus.systemui.qs.base.res.model.TileViewFlag", loader) ?: return
        activeAttr = flag.getMethod("getAttr").invoke(flag.getField("Active").get(null)) as Int
        val tile = ModernReflect.findClassIfExists("com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable", loader) ?: return
        val mask = tile.getDeclaredField("maskColor").apply { isAccessible = true }
        val path = tile.getMethod("getPath")
        val config = tile.getDeclaredField("currentConfig").apply { isAccessible = true }
        val first = config.type.getMethod("getFirst")
        val animator = tile.getDeclaredField("animator").apply { isAccessible = true }
        val deforming = tile.getMethod("isDeforming")
        val recipes = WhiteRecipes(loader)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val draw = tile.getDeclaredMethod("draw", Canvas::class.java)
        ModernHookRegistry.installFast("soft-light:tile-selection", draw, XposedInterface.Hooker { chain ->
            if (!enabled()) return@Hooker chain.proceed()
            val owner = chain.thisObject as Drawable
            val color = mask.getInt(owner)
            val whiteRecipe = recipes.active(owner, first.invoke(config.get(owner)))
            val active = selected(owner.state)
            val isWhite = whiteRecipe || (active && white(color))
            if (isWhite) synchronized(controlBackgrounds) { controlBackgrounds[owner] = true }
            // The native drawable composites the animated mask and deformation. Only replace
            // a settled, maskless white blur recipe; an opaque native mask already draws itself.
            if (isWhite && whiteRecipe && Color.alpha(color) == 0 && preserveControlWhite() &&
                !(animator.get(owner) as ValueAnimator).isRunning && deforming.invoke(owner) != true) {
                val shape = path.invoke(owner) as Path
                if (!shape.isEmpty) {
                    paint.color = Color.WHITE
                    paint.alpha = owner.alpha
                    (chain.getArg(0) as Canvas).drawPath(shape, paint)
                    report("solid white control tile")
                    return@Hooker null
                }
            }
            withControlState(isWhite, active) { chain.proceed() }
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
