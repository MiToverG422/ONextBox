package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Reuses SystemUI's notification shader pipeline, rather than approximating it with colors. */
internal class FluidCloudNotificationMaterial(
    private val loader: ClassLoader,
    private val report: (String, Throwable) -> Unit,
) {
    private var nativeApi: NativeApi? = null
    private val backgrounds = WeakHashMap<View, WeakReference<MaterialDrawable>>()
    private val panels = WeakHashMap<View, PanelState>()
    @Volatile private var blurManager: WeakReference<Any>? = null
    private var revision = 0

    private class PanelState(var key: FluidCloudMaterialRules.StyleKey? = null, var mix: Any? = null)

    fun invalidateStyle() {
        revision++
        invalidateColors()
    }

    fun invalidateColors() {
        backgrounds.keys.toList().forEach { it.postInvalidateOnAnimation() }
        panels.keys.toList().forEach { it.postInvalidateOnAnimation() }
    }

    /** CUSTOM rows skip normal notification size updates and the BP material-color layer. */
    fun preparePanel(view: View, proxy: Any) {
        val native = api()
        val config = Reflect.callMethod(proxy, "getBlurConfig") ?: return
        val width = (Reflect.callMethod(view, "getActualWidth") as Number).toInt()
        val height = (Reflect.callMethod(view, "getActualHeight") as Number).toInt()
        val key = native.key(view.context, width, height, revision)
        val state = panels.getOrPut(view) { PanelState() }
        val mix = Reflect.callMethod(config, "getPlatformMixConfig")
        if (state.key != key || state.mix != mix) {
            native.update(config, view.context,
                (Reflect.callMethod(config, "getCornerRadius") as Number).toFloat(),
                (Reflect.callMethod(config, "getRadiusWeight") as? Number)?.toFloat() ?: 0f, key)
            Reflect.callMethod(proxy, "applyBlurConfig")
            state.key = key
            state.mix = Reflect.callMethod(config, "getPlatformMixConfig")
        }
        // Keep native row classification/stacking intact. Only its shader geometry is shared.
        native.ensureNotificationMetaBall(proxy, key.mode)
    }

    fun drawPanelColor(canvas: Canvas, extension: Any, proxy: Any) {
        val native = api()
        if (!native.isBP()) return
        val color = native.materialColor()
        if (Color.alpha(color) == 0) return
        val drawable = Reflect.callMethod(extension, "getMaterialColorDrawable") as? Drawable ?: return
        val actual = Reflect.callMethod(proxy, "getBlurDrawable", null) as? Drawable ?: return
        drawable.setTint(color)
        drawable.bounds = actual.bounds
        drawable.draw(canvas)
    }

    fun observeManager(manager: Any) {
        if (blurManager?.get() !== manager) blurManager = WeakReference(manager)
    }

    fun ready(): Boolean = runCatching {
        api()
        !blurDisabled()
    }.onFailure { report("Notification material API unavailable", it) }.getOrDefault(false)

    fun panelMix(manager: Any): Any? {
        observeManager(manager)
        if (!ready()) return null
        val selection = Reflect.callMethod(manager, "globalBlurMode") ?: return null
        // Preserve NO_BLUR and uninitialized states rather than changing their rendering policy.
        if (!FluidCloudMaterialRules.isNotificationMode((selection as? Enum<*>)?.name)) return null
        return api().mix(manager, selection)
    }

    fun panelMix(): Any? = blurManager?.get()?.let(::panelMix)

    fun isPanelMaterialActive(proxy: Any): Boolean {
        if (Reflect.callMethod(proxy, "getBlurEnabled") != true) return false
        val type = Reflect.callMethod(proxy, "getBlurType")
        // Low-Gaussian devices use the ordinary wallpaper-blending renderer, not the shader.
        if (type !== api().platformType && type !== api().wallpaperType) return false
        val config = Reflect.callMethod(proxy, "getBlurConfig") ?: return false
        return Reflect.callMethod(config, "getPlatformMixConfig") != null
    }

    fun available(view: View): Boolean = runCatching {
        api()
        FluidCloudMaterialRules.canClearTemplate(true, true, blurDisabled())
    }.onFailure { report("Notification material API unavailable for ${view.javaClass.simpleName}", it) }
        .getOrDefault(false)

    fun clearTemplate(view: View) {
        // InstantContainer can use the template root itself as its outer background.
        view.background = backgrounds[view]?.get()?.takeUnless { it.failed }
    }

    fun release(view: View) {
        backgrounds.remove(view)?.get()?.let {
            view.removeOnAttachStateChangeListener(it)
            it.onViewDetachedFromWindow(view)
        }
    }

    fun background(view: View, radius: Float, weight: Float, fallback: Drawable?): Drawable? {
        if (!available(view)) return null
        return runCatching {
            backgrounds[view]?.get()?.takeUnless { it.failed }?.let { existing ->
                existing.update(radius, weight)
                return@runCatching existing
            }
            val native = api()
            val config = native.newConfig(view.context, radius, weight)
            val proxy = Reflect.newInstance(native.proxy, view, config, null, null, null)
            var material: MaterialDrawable? = null
            try {
                Reflect.callMethod(proxy, "setBlurType", native.platformType)
                val drawable = Reflect.newInstance(native.autoDrawable, proxy, fallback) as Drawable
                MaterialDrawable(view, native, config, proxy, drawable, fallback).also {
                    material = it
                    it.update(radius, weight)
                    view.addOnAttachStateChangeListener(it)
                    backgrounds[view] = WeakReference(it)
                }
            } catch (error: Throwable) {
                material?.let(view::removeOnAttachStateChangeListener)
                runCatching { Reflect.callMethod(proxy, "setBlurEnabled", false) }
                throw error
            }
        }.onFailure { report("Cannot create native notification material", it) }.getOrNull()
    }

    private fun api(): NativeApi = nativeApi ?: NativeApi(loader) { blurManager?.get() }.also { nativeApi = it }

    private fun blurDisabled(): Boolean = Reflect.callStaticMethod(
        Reflect.findClass("com.oplus.systemui.blur.GaussBlurUtils", loader), "isGaussBlurDisabled",
    ) == true

    private class NativeApi(loader: ClassLoader, private val manager: () -> Any?) {
        val proxy = Reflect.findClass("com.oplusos.systemui.common.blurability.ViewBlurProxy", loader)
        val autoDrawable = Reflect.findClass("com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable", loader)
        private val config = Reflect.findClass("com.oplusos.systemui.common.blurability.BlurConfig", loader)
        private val params = singleton("com.oplus.systemui.notification.blur.NotificationPlatFormBlurParamsManager", loader)
        private val round = singleton("com.oplus.systemui.notification.base.radius.SmoothRoundEx", loader)
        private val blurRadius = Reflect.findClass("com.oplusos.systemui.common.util.QSBlurConfigProvider", loader)
        private val mode = Reflect.findClass("com.oplus.systemui.notification.row.material.NotificationBlurMode", loader)
        private val dependency = Reflect.findClass("com.android.systemui.DependencyEx", loader)
        private val colorManager = Reflect.findClass("com.oplus.systemui.notification.color.MaterialColorManager", loader)
        private val policy = Reflect.findClass("com.oplusos.systemui.common.util.ScrimUtil", loader)
        private val feature = Reflect.findClass("com.oplusos.systemui.common.feature.FeatureOption", loader)
        private val shape = Reflect.findClass("com.oplus.systemui.notification.base.radius.SmoothRoundExKt", loader)
        private val metaDrawables = WeakHashMap<Any, WeakReference<Any>>()
        val notificationCard = Reflect.findClass("com.oplus.systemui.notification.blur.ViewBlurManager\$CardType", loader)
            .getField(FluidCloudMaterialRules.NOTIFICATION_CARD).get(null)!!
        val platformType = singleton(
            "com.oplusos.systemui.common.blurability.ViewBlurProxy\$BlurType\$BlurTypePlatformStatic", loader,
        )
        val wallpaperType = singleton(
            "com.oplusos.systemui.common.blurability.ViewBlurProxy\$BlurType\$BlurTypeBlendWallpaper", loader,
        )

        fun newConfig(context: Context, radius: Float, weight: Float): Any =
            Reflect.newInstance(config, 0, 0, null, null, null, null, false, null, null, 0, null, 33554431)
                .also { update(it, context, radius, weight, key(context, 0, 0, 0)) }

        fun key(context: Context, width: Int, height: Int, revision: Int): FluidCloudMaterialRules.StyleKey {
            // Remote template contexts can be forced dark even when the system is light.
            val resources = (context.applicationContext ?: context).resources
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            // Notification material also has wallpaper A/B variants. A forced SHADE_DARK
            // recipe is not equivalent to the notification cards visible on this device.
            val currentManager = manager()
            val nativeMode = currentManager?.let { Reflect.callMethod(it, "globalBlurMode") as? Enum<*> }
            return FluidCloudMaterialRules.StyleKey(
                FluidCloudMaterialRules.notificationMode(night, nativeMode?.name), night,
                currentManager?.let { Reflect.callMethod(it, "useMotionBlur") == true } ?: false,
                width, height, resources.configuration.hashCode(), revision,
            )
        }

        private fun referenceProxy(selection: Any): Any? {
            // Read a regular, non-colorized, non-stacked row's actual config. Never reuse its
            // Drawable, callbacks, SurfaceControl, bounds or notification contents.
            val proxies = manager()?.let { Reflect.getObjectField(it, "viewBlurProxyes") as? Map<*, *> } ?: return null
            for ((host, proxy) in proxies.toMap()) {
                val view = host as? View ?: continue
                if (view.visibility != View.VISIBLE || view.parent?.javaClass?.simpleName != "ExpandableNotificationRow") continue
                if ((view.parent as? View)?.translationX != 0f) continue
                val ext = Reflect.callMethod(view, "getExt") ?: continue
                val nativeMode = Reflect.getObjectField(ext, "notificationBlurMode") as? Enum<*>
                if (FluidCloudMaterialRules.canUseReference((selection as Enum<*>).name, nativeMode?.name,
                        Reflect.callMethod(ext, "noColorized") != true,
                        (Reflect.callMethod(ext, "getStackedProgress") as Number).toFloat() > 0f)) {
                    return proxy
                }
            }
            return null
        }

        private fun reference(selection: Any): Any? = referenceProxy(selection)?.let { Reflect.callMethod(it, "getBlurConfig") }

        fun mix(currentManager: Any?, selection: Any): Any? {
            val source = reference(selection)?.let { Reflect.callMethod(it, "getPlatformMixConfig") }
            if (source?.javaClass?.simpleName == "BlurMixMultiWithShader") {
                // The shader parameter list is mutable during alpha updates. Give each card
                // its own copy; sharing it makes one card's animation recolor another.
                return Reflect.callMethod(source, "copy",
                    Reflect.callMethod(source, "getForegroundShaderParam"),
                    Reflect.callMethod(source, "getBackgroundShaderParam"),
                    Reflect.callMethod(source, "getMirrorScale"),
                    Reflect.callMethod(source, "getAlphaWithBlurAmount"),
                    Reflect.callMethod(source, "getPlaceHolderColor"))
            }
            return currentManager?.let { Reflect.callMethod(it, "getMixConfig", notificationCard, selection) }
                ?: Reflect.callMethod(params, "normalCardPlatformMixConfig", selection,
                    currentManager?.let { Reflect.callMethod(it, "useMotionBlur") == true } ?: false, "ONextBox Fluid Cloud")
        }

        fun update(config: Any, context: Context, radius: Float, weight: Float, key: FluidCloudMaterialRules.StyleKey) {
            val selection = mode.getField(key.mode).get(null)!!
            val currentManager = manager()
            val source = reference(selection)
            val mix = mix(currentManager, selection)
            val corner = Reflect.callMethod(round, "createCornerParams", radius, weight > 0f)!!
            if (weight > 0f) Reflect.callMethod(corner, "setWeight", weight)
            if (weight >= 3f) {
                val type = Reflect.findClass("com.oplus.posteffect.params.CornerType", proxy.classLoader)
                    .getField("G2").get(null)
                Reflect.callMethod(corner, "setType", type)
            }
            Reflect.callMethod(config, "setBlurRadius", source?.let { Reflect.callMethod(it, "getBlurRadius") }
                ?: Reflect.callStaticMethod(blurRadius, "getDefaultBlurRadius"))
            Reflect.callMethod(config, "setBlurColor", source?.let { Reflect.callMethod(it, "getBlurColor") }
                ?: currentManager?.let { Reflect.getObjectField(it, "cardBlurColor") } ?: 0)
            Reflect.callMethod(config, "setCornerRadius", radius)
            Reflect.callMethod(config, "setRadiusWeight", weight)
            Reflect.callMethod(config, "setPlatformMixConfig", mix)
            Reflect.callMethod(config, "setGradientStrokeCornerParam", corner)
            val stroke = Reflect.callMethod(params, "getGradientStrokeLineParams", notificationCard, selection)
            val shadow = Reflect.callMethod(params, "getInnerShadowParams", notificationCard, selection)
            // These mutable objects belong to this card, not to the reference notification.
            source?.let {
                val fromStroke = Reflect.callMethod(it, "getGradientStrokeLineParam")
                val fromShadow = Reflect.callMethod(it, "getInnerShadowParams")
                // Some cached rows are still faded for AOD/overlay transitions. Their zero
                // alpha must not erase the normal notification recipe on a visible card.
                val visibleStroke = fromStroke != null &&
                    ((Reflect.callMethod(fromStroke, "getStrokeLineAlphaNear") as Number).toFloat() > 0f ||
                        (Reflect.callMethod(fromStroke, "getStrokeLineAlphaFar") as Number).toFloat() > 0f)
                if (stroke != null && visibleStroke) Reflect.callMethod(stroke, "copyFrom", fromStroke)
                if (shadow != null && fromShadow != null && visibleStroke) Reflect.callMethod(shadow, "copyFrom", fromShadow)
            }
            if (key.width > 0 && key.height > 0) {
                Reflect.callMethod(params, "updateGradientStrokeLineParamsForNotificationSize", context, stroke,
                    key.width.toFloat(), key.height.toFloat(), selection)
                Reflect.callMethod(params, "updateInnerShadowParamsForNotificationSize", context, shadow,
                    key.width.toFloat(), key.height.toFloat(), selection)
            }
            Reflect.callMethod(config, "setGradientStrokeLineParam", stroke)
            Reflect.callMethod(config, "setInnerShadowParams", shadow)
            Reflect.callMethod(config, "setSupportMetaBall", source?.let { Reflect.getObjectField(it, "isSupportMetaBall") }
                ?: (Reflect.callStaticMethod(feature, "isExpRegion") != true))
            if (currentManager != null) {
                Reflect.callMethod(config, "setMotionBlurMixConfig",
                    Reflect.callMethod(currentManager, "headsupCardMotionMixConfig", notificationCard, selection))
            }
            source?.let {
                Reflect.callMethod(config, "setMotionBlurRadius", Reflect.callMethod(it, "getMotionBlurRadius"))
            }
        }

        fun isBP(): Boolean = Reflect.callStaticMethod(policy, "isBPAnimLevel") == true

        fun materialColor(): Int {
            val dependencies = dependency.getDeclaredField("sDependency").apply { isAccessible = true }.get(null) ?: return 0
            val colors = Reflect.callMethod(dependencies, "getDependency", colorManager) ?: return 0
            return Reflect.callMethod(colors, "getCurrentMaterialColor") as Int
        }

        @Suppress("DiscouragedApi")
        fun colorLayer(context: Context, radius: Float): Drawable? {
            val host = context.applicationContext ?: context
            // Release SystemUI APKs can inline/remove R classes while retaining resources.
            // Resolve the optional color by name; it must not disable the native blur API.
            val colorId = host.resources.getIdentifier(
                "notification_material_background_color_compass", "color", "com.android.systemui",
            )
            if (colorId == 0) return null
            return (Reflect.callStaticMethod(shape, "getSmoothDrawable", host,
                colorId) as? Drawable)?.also {
                (it as? GradientDrawable)?.cornerRadius = radius
            }
        }

        fun ensureNotificationMetaBall(proxy: Any, modeName: String) {
            val actual = Reflect.callMethod(proxy, "getBlurDrawable", null) ?: return
            if (actual.javaClass.simpleName != "PlatformBlurDrawable") return
            if (metaDrawables[proxy]?.get() === actual) return
            val blur = Reflect.callMethod(actual, "getBlurDrawable") ?: return
            if (blur.javaClass.simpleName != "MetaBallBlurDrawable") return
            val selection = mode.getField(modeName).get(null)!!
            val source = referenceProxy(selection)?.let { Reflect.callMethod(it, "getBlurDrawable", null) }
                ?.let { Reflect.callMethod(it, "getBlurDrawable") } ?: return
            // Copy the ordinary card's active main-ball shader, but not menu/gesture shapes
            // or brand colors. Native reset-to-zero takes the opposite path when inactive.
            if (source.javaClass.simpleName == "MetaBallBlurDrawable" &&
                Reflect.callMethod(source, "isMetaBallEffective") == true &&
                Reflect.callMethod(blur, "isMetaBallEffective") != true) {
                Reflect.callMethod(blur, "setBlendRange", 1)
            }
            metaDrawables[proxy] = WeakReference(actual)
        }

        private fun singleton(name: String, loader: ClassLoader): Any =
            Reflect.findClass(name, loader).getField("INSTANCE").get(null)!!
    }

    /** AutoBlurDrawable.setAlpha is empty; expanded-card animations need this forwarding layer. */
    private inner class MaterialDrawable(
        view: View,
        private val native: NativeApi,
        private val config: Any,
        private val proxy: Any,
        private val delegate: Drawable,
        private val fallback: Drawable?,
    ) : Drawable(), Drawable.Callback, View.OnAttachStateChangeListener {
        private val owner = WeakReference(view)
        private var currentAlpha = 255
        private var radius = 0f
        private var weight = 0f
        private var key: FluidCloudMaterialRules.StyleKey? = null
        private var colorLayer: Drawable? = null
        var failed = false
            private set

        init { delegate.callback = this }

        fun update(radius: Float, weight: Float) {
            this.radius = radius
            this.weight = weight
            key = null
            refresh()
        }

        private fun refresh() {
            val view = owner.get() ?: return
            val next = native.key(view.context, bounds.width(), bounds.height(), revision)
            if (next == key) return
            native.update(config, view.context, radius, weight, next)
            Reflect.callMethod(config, "setCardType", 0)
            Reflect.callMethod(proxy, "applyBlurConfig")
            colorLayer = runCatching { native.colorLayer(view.context, radius) }
                .onFailure { report("Optional notification color layer unavailable", it) }.getOrNull()
            key = next
        }

        override fun draw(canvas: Canvas) {
            if (!LspConfig.isFluidCloudMaterialEnabledXposed() || blurDisabled()) {
                runCatching { Reflect.callMethod(proxy, "setBlurEnabled", false) }
                fallback?.draw(canvas)
                return
            }
            if (!failed && canvas.isHardwareAccelerated) {
                try {
                    if (Reflect.callMethod(proxy, "getBlurEnabled") != true) {
                        Reflect.callMethod(proxy, "setBlurEnabled", true)
                    }
                    refresh()
                    key?.let { native.ensureNotificationMetaBall(proxy, it.mode) }
                    delegate.draw(canvas)
                    if (native.isBP()) {
                        val color = native.materialColor()
                        if (Color.alpha(color) != 0) colorLayer?.let {
                            it.bounds = bounds
                            it.setTint(color)
                            it.alpha = currentAlpha
                            it.draw(canvas)
                        }
                    }
                    return
                } catch (error: Throwable) {
                    failed = true
                    runCatching { Reflect.callMethod(proxy, "setBlurEnabled", false) }
                    report("Native material draw failed; original background restored", error)
                }
            }
            fallback?.draw(canvas)
        }

        override fun onBoundsChange(bounds: Rect) {
            delegate.bounds = bounds
            fallback?.bounds = bounds
        }

        override fun setAlpha(alpha: Int) {
            currentAlpha = alpha.coerceIn(0, 255)
            fallback?.alpha = currentAlpha
            if (!failed) runCatching {
                Reflect.callMethod(proxy, "setBlurAmount", FluidCloudMaterialRules.blurAmount(currentAlpha))
            }.onFailure {
                failed = true
                runCatching { Reflect.callMethod(proxy, "setBlurEnabled", false) }
                report("Cannot forward material animation alpha", it)
            }
            invalidateSelf()
        }

        override fun getAlpha(): Int = currentAlpha
        override fun setColorFilter(filter: ColorFilter?) { fallback?.colorFilter = filter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            delegate.setVisible(visible, restart)
            return super.setVisible(visible, restart)
        }

        override fun onViewAttachedToWindow(view: View) {
            if (failed) return
            runCatching {
                update(radius, weight)
                Reflect.callMethod(proxy, "setBlurEnabled", true)
                Reflect.callMethod(proxy, "setBlurAmount", FluidCloudMaterialRules.blurAmount(currentAlpha))
                Reflect.callMethod(proxy, "applyBlurConfig")
            }.onFailure {
                failed = true
                runCatching { Reflect.callMethod(proxy, "setBlurEnabled", false) }
                report("Cannot reattach notification material", it)
            }
        }

        override fun onViewDetachedFromWindow(view: View) {
            // Native helpers otherwise keep a drawable bound to an obsolete SurfaceControl.
            runCatching { Reflect.callMethod(proxy, "setBlurEnabled", false) }
        }

        override fun invalidateDrawable(who: Drawable) = invalidateSelf()
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
        override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
    }
}
