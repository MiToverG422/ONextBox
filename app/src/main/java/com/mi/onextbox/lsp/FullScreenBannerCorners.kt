package com.mi.onextbox.lsp

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal class FullScreenBannerCorners(val type: Class<*>) {
    private class SavedRadius(var original: Float, var applied: Float? = null) {
        fun resolve(current: Float, radius: Float?): Float {
            if (current != applied) original = current
            applied = radius
            return radius ?: original
        }
    }

    private class Shape(val uniform: Float, val corners: FloatArray?, var applied: Float? = null)

    // Both BlurConfig and CornerParams include the mutable radii in equals/hashCode.
    private class MaterialValues<V> {
        private val entries = mutableListOf<Pair<WeakReference<Any>, V>>()
        operator fun get(key: Any): V? = entries.firstOrNull { it.first.get() === key }?.second
        operator fun set(key: Any, value: V) {
            entries.removeAll { it.first.get() == null || it.first.get() === key }
            entries.add(WeakReference(key) to value)
        }
    }

    private class Banner(spot: Int) {
        val spotlight = SavedRadius(spot.toFloat())
        val shadows = WeakHashMap<Any, SavedRadius>()
        val blur = MaterialValues<List<SavedRadius>>()
        val strokes = MaterialValues<SavedRadius>()
        val shapes = WeakHashMap<GradientDrawable, Shape>()
        val transitions = WeakHashMap<View, SavedRadius>()
        var radius: Float? = null
    }

    private val banners = WeakHashMap<View, Banner>()
    private val names = listOf("CornerRadius", "LeftTopCornerRadius", "RightTopCornerRadius",
        "RightBottomCornerRadius", "LeftBottomCornerRadius")

    fun update(view: View, radius: Float?) {
        if (!type.isInstance(view)) return
        val previous = banners[view]
        if (previous == null && radius == null) return
        val current = Reflect.getIntField(view, "mSpotLightRadius")
        val state = previous ?: Banner(current).also { banners[view] = it }
        state.radius = radius
        val previousSpot = state.spotlight.applied
        val spot = state.spotlight.resolve(current.toFloat(), radius?.roundToInt()?.toFloat()).roundToInt()
        if (spot != current) Reflect.setIntField(view, "mSpotLightRadius", spot)
        val shadow = Reflect.getObjectField(view, "mShadowEdgeController")
        if (shadow != null) {
            val native = Reflect.getObjectField(shadow, "cornerRadius") as Float
            val saved = state.shadows.getOrPut(shadow) {
                // A newly attached shadow inherits the then-current spotlight radius.
                if (native == previousSpot) SavedRadius(state.spotlight.original, native) else SavedRadius(native)
            }
            val desired = saved.resolve(native, radius?.roundToInt()?.toFloat())
            if (native != desired) Reflect.callMethod(shadow, "refresh", desired)
        }
        val proxy = Reflect.getObjectField(view, "mViewBlurProxy")
        if (proxy != null) applyBlur(proxy, state, radius)
        val background = Reflect.getObjectField(view, "mFullScreenBannerBg") as? Drawable
        if (proxy == null && (background is LayerDrawable || background is GradientDrawable)) {
            // Isolate the resource ConstantState before modifying its original G2 shapes.
            if (radius != null) background.mutate()
            applyShape(background, state, radius)
        }
        state.transitions.forEach { (transition, saved) ->
            val value = Reflect.callMethod(transition, "getCornerRadius") as Float
            if (value == saved.applied) {
                val desired = saved.resolve(value, radius?.coerceAtLeast(0.001f))
                if (desired != value) {
                    Reflect.callMethod(transition, "setCornerRadius", desired)
                    transition.invalidate()
                }
            }
        }
        if (radius == null) banners.remove(view)
        view.invalidate()
    }

    fun clear() {
        banners.keys.toList().forEach { update(it, null) }
    }

    private fun applyBlur(proxy: Any, state: Banner, radius: Float?) {
        val config = Reflect.callMethod(proxy, "getBlurConfig") ?: return
        val current = names.map { Reflect.callMethod(config, "get$it") as Float }
        val saved = state.blur[config] ?: if (radius != null) current.map { SavedRadius(it) }
            .also { state.blur[config] = it } else return
        val desired = current.mapIndexed { i, value -> saved[i].resolve(value, radius) }
        var changed = current != desired
        if (changed) {
            // This setter writes all four corners. Restore any asymmetric native values afterwards.
            Reflect.callMethod(config, "setCornerRadius", desired[0])
            for (i in 1 until names.size) {
                if (desired[i] != desired[0]) Reflect.callMethod(config, "set${names[i]}", desired[i])
            }
        }
        Reflect.callMethod(config, "getGradientStrokeCornerParam")?.let { stroke ->
            val value = Reflect.callMethod(stroke, "getRadius") as Float
            val baseline = state.strokes[stroke] ?: if (radius != null) SavedRadius(value)
                .also { state.strokes[stroke] = it } else return@let
            val next = baseline.resolve(value, radius)
            if (next != value) {
                Reflect.callMethod(stroke, "setRadius", next)
                changed = true
            }
        }
        if (changed) Reflect.callMethod(proxy, "applyBlurConfig")
    }

    private fun applyShape(drawable: Drawable, state: Banner, radius: Float?) {
        when (drawable) {
            is LayerDrawable -> for (i in 0 until drawable.numberOfLayers) {
                applyShape(drawable.getDrawable(i), state, radius)
            }
            is GradientDrawable -> {
                val current = drawable.cornerRadius
                val corners = drawable.cornerRadii
                var saved = state.shapes[drawable]
                if (radius == null) {
                    if (saved != null && current == saved.applied && corners == null) {
                        drawable.cornerRadius = saved.uniform
                        if (saved.corners != null) drawable.cornerRadii = saved.corners
                    }
                    return
                }
                if (saved == null || current != saved.applied || corners != null) {
                    saved = Shape(current, corners?.clone())
                    state.shapes[drawable] = saved
                }
                if (current != radius || corners != null) drawable.cornerRadius = radius
                saved.applied = radius
            }
        }
    }

    fun installTransitions() {
        // Hook the two native animation initializers. Subsequent morph frames use their own interpolated radii.
        val pkg = type.name.substringBeforeLast('.')
        val loader = type.classLoader
        val controller = Reflect.findClassIfExists("$pkg.FullScreenBannerAnimatorController", loader)
        controller?.declaredMethods?.filter { it.name == "createAnimatorState" && it.parameterCount == 0 && !it.isBridge }?.forEach { method ->
            ModernHookRegistry.installCompat("appearance:fullscreen-banner-start:${method.toGenericString()}", method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (banners.isEmpty() || param.throwable != null) return
                    val owner = param.thisObject ?: return
                    val output = param.result ?: return
                    val helper = Reflect.getObjectField(owner, "container") ?: return
                    val container = Reflect.getObjectField(helper, "mRootView") as? View ?: return
                    val radius = banners[container]?.radius ?: return
                    Reflect.callMethod(output, "setTopCornerRadius", radius)
                    Reflect.callMethod(output, "setBottomCornerRadius", radius)
                }
            })
        }
        val runnable = Reflect.findClassIfExists("${pkg.substringBeforeLast('.')}.FullScreenBannerHelper\$2", loader)
        runnable?.declaredMethods?.firstOrNull { it.name == "run" && it.parameterCount == 0 }?.let { method ->
            ModernHookRegistry.installCompat("appearance:fullscreen-banner-background-start", method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (banners.isEmpty() || param.throwable != null) return
                    val task = param.thisObject ?: return
                    if (Reflect.getIntField(task, "\$r8\$classId") != 0) return
                    val helper = Reflect.getObjectField(task, "this\$0") ?: return
                    val container = Reflect.getObjectField(helper, "mRootView") as? View ?: return
                    val state = banners[container] ?: return
                    val radius = state.radius ?: return
                    if (Reflect.getObjectField(helper, "mBannerCollpseAniming") == true) return
                    val background = Reflect.getObjectField(helper, "mBackgroundNormal") as? View ?: return
                    val id = background.resources.getIdentifier("fullscreen_banner_corner_radius", "dimen", "com.android.systemui")
                    if (id == 0 || background.visibility != View.VISIBLE) return
                    val value = Reflect.callMethod(background, "getCornerRadius") as Float
                    if (value != background.resources.getDimension(id)) return
                    // Native onDraw skips the fill entirely at zero.
                    val saved = state.transitions.getOrPut(background) { SavedRadius(value) }
                    Reflect.callMethod(background, "setCornerRadius", saved.resolve(value, radius.coerceAtLeast(0.001f)))
                    background.invalidate()
                }
            })
        }
    }
}
