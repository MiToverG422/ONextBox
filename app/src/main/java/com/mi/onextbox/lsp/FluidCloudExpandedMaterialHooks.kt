package com.mi.onextbox.lsp

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Changes native material inputs; ViewRootManager still owns blur, corners and animation. */
internal class FluidCloudExpandedMaterialHooks(
    private val enabled: () -> Boolean,
    private val logged: (String, String) -> Unit,
    private val report: (String, Throwable) -> Unit,
) {
    private val scope = FluidCloudExpandedBuildScope()
    private val backgrounds = WeakHashMap<View, WeakReference<Drawable>>()

    fun captureReloadState(): Array<Array<Any>> = backgrounds.mapNotNull { (view, drawable) ->
        drawable.get()?.let { arrayOf<Any>(view, it) }
    }.toTypedArray()

    fun restoreReloadState(saved: Any?) {
        (saved as? Array<*>)?.forEach { item ->
            val values = item as? Array<*> ?: return@forEach
            val view = values.getOrNull(0) as? View ?: return@forEach
            val background = values.getOrNull(1) as? Drawable ?: return@forEach
            backgrounds[view] = WeakReference(background)
        }
    }

    fun install(loader: ClassLoader, prefix: String) = runCatching {
        val params = Reflect.findClass("com.oplus.graphics.OplusBlurParam", loader)
            .getDeclaredMethod("setMaterialParams", Int::class.javaPrimitiveType,
                FloatArray::class.java, FloatArray::class.java)
        ModernHookRegistry.installFast("ONextBox-FluidMaterial:expanded:params", params, XposedInterface.Hooker { chain ->
            val style = scope.current
            val blend = chain.getArg(1) as? FloatArray
            val mix = chain.getArg(2) as? FloatArray
            if (!FluidCloudExpandedMaterialRules.shouldReplaceParams(style != null, chain.getArg(0) as Int,
                    blend?.size, mix?.size)) return@Hooker chain.proceed()
            val args = chain.args.toTypedArray()
            // The overlay does not change card_background_blend_color_old. Retain it on
            // legacy blur devices while replacing the shared mix color in both branches.
            if (style?.modernBlur == true) args[1] = FluidCloudExpandedMaterialRules.rgba(
                FluidCloudExpandedMaterialRules.BLEND_COLOR)
            args[2] = FluidCloudExpandedMaterialRules.rgba(FluidCloudExpandedMaterialRules.MIX_COLOR)
            val result = chain.proceed(args)
            logged("expanded:params:${style?.modernBlur}",
                "Expanded native material uses Pui color inputs; modernBlur=${style?.modernBlur}")
            result
        })
        ModernHookRuntime.requireModule().deoptimize(params)

        val plugin = FluidCloudMaterialRules.PLUGIN_PACKAGE
        val target = Reflect.findClass("com.heytap.log.nx.obus.a", loader)
        val root = Reflect.findClass("com.oplus.view.ViewRootManager", loader)
        val overlay = Reflect.findClassIfExists("$plugin.seedling.card.ui.view.CardGradientOverlayView", loader)
        val builder = overlay?.let {
            runCatching { target.getDeclaredMethod("b", View::class.java, root, it) }.getOrNull()
        } ?: target.getDeclaredMethod("b", View::class.java, root)
        val separateOverlay = builder.parameterCount == 3
        val gradientType = if (separateOverlay) "$plugin.seedling.card.ui.view.f" else "$plugin.seedling.util.d"
        val gradient = Reflect.findClass(gradientType, loader).declaredConstructors.single {
            it.parameterCount == (if (separateOverlay) 5 else 4) &&
                it.parameterTypes.take(3).all { type -> type == Int::class.javaPrimitiveType } &&
                (!separateOverlay || it.parameterTypes[3] == Float::class.javaPrimitiveType)
        }
        val blurModeOwner = if (separateOverlay) "$plugin.seedling.util.d" else "$plugin.seedling.util.e"
        if (separateOverlay) {
            val draw = gradient.declaringClass.getDeclaredMethod("draw", android.graphics.Canvas::class.java)
            ModernHookRegistry.installFast("$prefix:expanded:overlayDraw", draw, XposedInterface.Hooker { chain ->
                var owner = (chain.thisObject as Drawable).callback
                while (owner is Drawable) owner = owner.callback
                if (owner is View && (overlay?.isInstance(owner) == true || isBackgroundHost(owner)) && enabled()) {
                    logged("expanded:overlay", "Expanded card gradient omitted over notification material")
                    null
                } else chain.proceed()
            })
        } else ModernHookRegistry.installFast("$prefix:expanded:gradient", gradient, XposedInterface.Hooker { chain ->
            if (scope.current == null) return@Hooker chain.proceed()
            val args = chain.args.toTypedArray()
            repeat(3) { args[it] = FluidCloudExpandedMaterialRules.GRADIENT_COLOR }
            chain.proceed(args)
        })

        ModernHookRegistry.installFast("$prefix:background", builder, XposedInterface.Hooker { chain ->
            val view = chain.getArg(0) as? View
            val use = enabled() && view != null && isBackgroundHost(view)
            val style = if (use) runCatching {
                val blurMode = Reflect.findClass(blurModeOwner, loader)
                    .getDeclaredField("a").apply { isAccessible = true }.get(null)!!
                FluidCloudExpandedBuildScope.Style(Reflect.callMethod(blurMode, "getValue") == true)
            }.onFailure { report("Cannot select native expanded blur branch", it) }.getOrNull() else null
            // A nested, non-target initialization shadows the outer scope. Always restore
            // the scope in finally, including when the native builder throws.
            val result = scope.duringBuild(style) { chain.proceed() }
            if (view != null) {
                if (style == null) backgrounds.remove(view) else runCatching {
                    val background = view.background ?: return@runCatching
                    // Native no-blur drawables retain their shape, outline and smooth weight.
                    if (background is GradientDrawable) {
                        val owned = background.mutate() as GradientDrawable
                        owned.setColor(FluidCloudExpandedMaterialRules.NO_BLUR_COLOR)
                        view.background = owned
                    }
                    backgrounds[view] = WeakReference(view.background!!)
                    logged("expanded:background:${view.javaClass.name}",
                        "Expanded background remains native: ${view.background?.javaClass?.simpleName}; capsule excluded")
                }.onFailure { report("Cannot retain expanded native background", it) }
            }
            result
        })
        ModernHookRuntime.requireModule().deoptimize(builder)
        for ((className, methods) in listOf(
            "${FluidCloudMaterialRules.PLUGIN_PACKAGE}.seedling.card.ui.view.CardBackgroundView" to
                listOf("onAttachedToWindow", "onConfigurationChanged"),
            "${FluidCloudMaterialRules.PLUGIN_PACKAGE}.seedling.card.ui.view.InstantContainer" to listOf("u"),
            "${FluidCloudMaterialRules.PLUGIN_PACKAGE}.seedling.capsule.g" to listOf("onLayoutChange"),
        )) runCatching {
            Reflect.findClass(className, loader).declaredMethods.filter { it.name in methods }
                .forEach { ModernHookRuntime.requireModule().deoptimize(it) }
        }.onFailure { report("Expanded native caller unavailable: $className", it) }
        logged("expanded:installed:$prefix", "Native expanded Pui material hooks installed; no resource overlay")
    }.onFailure { report("Expanded native material hook unavailable", it) }

    /** InstantContainer's template root can itself carry the native outer blur background. */
    fun retainBackground(view: View): Boolean {
        if (!enabled()) return false
        val native = backgrounds[view]?.get() ?: return false
        if (view.background !== native) view.background = native
        return true
    }

    private fun isBackgroundHost(view: View): Boolean {
        if (FluidCloudExpandedMaterialRules.isBackgroundHost(view.javaClass.name, false)) return true
        var parent = view.parent as? View
        while (parent != null) {
            if (parent.javaClass.name == "${FluidCloudMaterialRules.PLUGIN_PACKAGE}.seedling.card.ui.view.InstantContainer") {
                return runCatching { Reflect.getObjectField(parent, "g") === view }.getOrDefault(false)
            }
            parent = parent.parent as? View
        }
        return false
    }
}
