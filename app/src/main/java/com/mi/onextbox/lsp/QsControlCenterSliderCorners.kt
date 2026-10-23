package com.mi.onextbox.lsp

import android.graphics.Rect
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.QsCornerReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Customize visual corners without changing native vertical padding or seek geometry. */
internal object QsControlCenterSliderCorners {
    private const val TAG = "ONextBox-QsSliderCorner"
    private val applied = WeakHashMap<View, Float>()
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private val nativeRadius = ThreadLocal<Boolean>()
    private val materialUpdating = ThreadLocal<Boolean>()
    private val materialStates = WeakHashMap<View, QsSliderMaterialState>()
    private val detailTransitions = WeakHashMap<View, QsSliderDetailCornerTransition>()

    fun hook(loader: ClassLoader) {
        val observers = QsCornerViewObservers("qs:slider-views", content = ::contentState) { updateSafely(it, force = true) }
        QsControlCenterCornerTargets.sliders.forEach { name ->
            val type = Reflect.findClassIfExists(name, loader) ?: return@forEach
            type.declaredConstructors.forEach { constructor ->
                ModernHookRegistry.installCompat("$TAG:observe:${constructor.toGenericString()}", constructor, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) (param.thisObject as? View)?.let(observers::watch)
                    }
                })
            }
            // getRadius drives native layout padding. Only the visual transition
            // getter may be overridden, after constructors have cached native values.
            type.declaredMethods.filter { it.name == "getViewRadius" && it.parameterCount == 0 }.forEach { method ->
                ModernHookRegistry.installCompat("$TAG:$name:${method.name}", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || HotReloadLifecycle.isRetiring || nativeRadius.get() == true) return
                        val view = param.thisObject as? View ?: return
                        customRadius(view)?.let { param.result = it }
                    }
                })
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { diagnose("Slider radius deoptimization", it) }
            }
            type.declaredMethods.filter { it.name in setOf("onAttachedToWindow", "onSizeChanged", "updateCornerRadius", "setCornerRadius") }.forEach { method ->
                ModernHookRegistry.installCompat("$TAG:$name:${method.name}", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null && nativeRadius.get() != true)
                            (param.thisObject as? View)?.let(observers::invalidate)
                    }
                })
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { diagnose("Slider refresh deoptimization", it) }
            }
        }
        hookDetailTransitions(loader)
        val base = Reflect.findClassIfExists(QsControlCenterCornerTargets.BASE_SLIDER, loader)
        base?.declaredMethods?.filter { it.name == "setCornerRadius" && it.parameterCount == 1 }?.forEach { method ->
            ModernHookRegistry.installCompat("$TAG:after-native-radius", method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || HotReloadLifecycle.isRetiring || nativeRadius.get() == true) return
                    val view = param.thisObject as? View ?: return
                    observers.invalidate(view)
                }
            })
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { diagnose("Native radius setter deoptimization", it) }
        }
        // onDraw on the concrete seekbar is guaranteed to run even when its
        // FrameLayout parent skips its own onDraw. No framework-wide View hooks.
        val seekType = Reflect.findClassIfExists(QsControlCenterCornerTargets.SEEK_BAR, loader)
        seekType?.declaredMethods?.filter { it.name == "updateBaseMixColorDrawableRadius" && it.parameterCount == 1 }
            ?.forEach { method ->
                ModernHookRegistry.installCompat("$TAG:material-native-update", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // A native animation/theme update may overwrite the custom material.
                        // Our own update must not invalidate its newly recorded cache entry.
                        if (materialUpdating.get() != true) (param.thisObject as? View)?.let {
                            synchronized(materialStates) { materialStates.remove(it) }
                        }
                    }
                })
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { diagnose("Material setter deoptimization", it) }
            }
        seekType?.declaredMethods
            ?.filter { it.name == "onDraw" }?.forEach { method ->
                ModernHookRegistry.installFast("$TAG:seek-draw", method, XposedInterface.Hooker { chain ->
                    if (HotReloadLifecycle.isRetiring || nativeRadius.get() == true) return@Hooker chain.proceed()
                    if (!QsTileCornerSettings.enabledXposed() && synchronized(applied) { applied.isEmpty() }) return@Hooker chain.proceed()
                    val seek = chain.thisObject as? View ?: return@Hooker chain.proceed()
                    val owner = generateSequence(seek.parent) { it.parent }.filterIsInstance<View>()
                        .firstOrNull { QsControlCenterCornerTargets.isSlider(it.javaClass.name) }
                    val radius = owner?.let(::paintRadius) ?: return@Hooker chain.proceed()
                    var entered = false
                    try {
                        val progress = (Reflect.getObjectField(seek, QsSliderCornerPaintScope.PROGRESS) as Number).toFloat()
                        val background = (Reflect.getObjectField(seek, QsSliderCornerPaintScope.BACKGROUND) as Number).toFloat()
                        val progressBase = (Reflect.getObjectField(seek, "mProgressRadius") as Number).toFloat()
                        val backgroundBase = (Reflect.getObjectField(seek, "mBackgroundRadius") as Number).toFloat()
                        val paintedProgress = QsSliderCornerPaintScope.scaled(radius, progress, progressBase)
                        val paintedBackground = QsSliderCornerPaintScope.scaled(radius, background, backgroundBase)
                        val scope = QsSliderCornerPaintScope(
                            read = { (Reflect.getObjectField(seek, it) as Number).toFloat() },
                            write = { name, value -> Reflect.setObjectField(seek, name, value) })
                        withSmoothCorners(seek) {
                            scope.paint(paintedProgress, paintedBackground) {
                                entered = true
                                runCatching { updateMaterialIfChanged(seek, paintedProgress) }
                                    .onFailure { diagnose("Slider paint material", it) }
                                chain.proceed()
                            }
                        }
                    } catch (error: Throwable) {
                        if (entered) throw error
                        diagnose("Slider paint preparation", error)
                        chain.proceed()
                    }
                })
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { diagnose("Seekbar drawing deoptimization", it) }
            }
        base?.declaredMethods?.filter { it.name in setOf("setCornerRadius", "updateCornerRadius", "onSizeChanged") }?.forEach { method ->
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { diagnose("Slider setter deoptimization", it) }
        }
        HotReloadLifecycle.register("qs:slider-corners", detach = {
            synchronized(applied) { applied.keys.toList() }.forEach(::restore)
            synchronized(materialStates) { materialStates.clear() }
            synchronized(detailTransitions) { detailTransitions.clear() }
        })
        HookLog.i(TAG, "Control Center brightness/volume corner adapters installed")
    }

    private fun hookDetailTransitions(loader: ClassLoader) {
        val type = Reflect.findClassIfExists(QsControlCenterCornerTargets.DETAIL_ANIMATION, loader) ?: return
        type.declaredMethods.filter { it.name == "beginTransition" && it.parameterCount == 3 }.forEach { method ->
            ModernHookRegistry.installCompat("$TAG:detail-transition", method, object : ModernMethodHook() {
                private val pending = WeakHashMap<MethodHookParam, Pair<View, QsSliderDetailCornerTransition>>()

                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (HotReloadLifecycle.isRetiring) return
                    runCatching {
                        val views = param.args[0] ?: return@runCatching
                        val detail = Reflect.callMethod(views, "getDetailSlider") as? View ?: return@runCatching
                        val from = Reflect.callMethod(views, "getFromSlider") as? View ?: return@runCatching
                        if (detail.javaClass.name != QsControlCenterCornerTargets.DETAIL_SLIDER ||
                            !QsControlCenterCornerTargets.isMainSlider(from.javaClass.name)) return@runCatching
                        val priorRadius = paintRadius(detail)
                        synchronized(detailTransitions) { detailTransitions.remove(detail) }
                        val percent = QsTileCornerSettings.radiusXposed(QsTileCornerSettings.Size.OneXTwo)
                            ?: return@runCatching
                        val detailRadius = customRadius(detail) ?: return@runCatching
                        val fromRadius = customRadius(from) ?: return@runCatching
                        val launching = Reflect.getObjectField(param.thisObject!!, "isLaunching") as Boolean
                        val startScale = detailScale(detail)
                        // Native closing geometry returns to the unpressed tile dimensions.
                        val endScale = if (launching) QsSliderDetailCornerTransition.Scale(1f, 1f) else
                            QsSliderDetailCornerTransition.Scale(
                                from.width.toFloat() / detail.width, from.height.toFloat() / detail.height)
                        val startRadius = if (launching)
                            mainPaintedRadius(from, fromRadius) * detailScale(from).average / startScale.average
                            else priorRadius ?: detailRadius
                        val endRadius = if (launching) detailRadius else fromRadius / endScale.average
                        val transition = QsSliderDetailCornerTransition.create(percent, detail.width, detail.height,
                            detailRadius, startScale, endScale, startRadius, endRadius) ?: return@runCatching
                        synchronized(pending) { pending[param] = detail to transition }
                    }.onFailure { diagnose("Slider detail transition", it) }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val (detail, transition) = synchronized(pending) { pending.remove(param) } ?: return
                    if (param.throwable != null || HotReloadLifecycle.isRetiring) return
                    // beginTransition cancels the old spring first. Publish only after
                    // its cancellation/end callbacks have cleared the previous state.
                    synchronized(detailTransitions) { detailTransitions[detail] = transition }
                }
            })
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { diagnose("Detail transition deoptimization", it) }
        }
        hookDetailTransitionEnd(loader, "\$doStartAnimation\$endListener\$1", "\$animViews")
        hookDetailTransitionEnd(loader, "\$doHideAnimation\$endListener\$1", "\$animaViews")
    }

    private fun hookDetailTransitionEnd(loader: ClassLoader, suffix: String, viewsField: String) {
        val type = Reflect.findClassIfExists(QsControlCenterCornerTargets.DETAIL_ANIMATION + suffix, loader) ?: return
        type.declaredMethods.filter { it.name == "onAnimationEnd" && it.parameterCount == 4 }.forEach { method ->
            ModernHookRegistry.installCompat("$TAG:detail-end:$suffix", method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching {
                        val views = Reflect.getObjectField(param.thisObject!!, viewsField) ?: return@runCatching
                        val detail = Reflect.callMethod(views, "getDetailSlider") as? View ?: return@runCatching
                        synchronized(detailTransitions) { detailTransitions.remove(detail) }
                        detail.invalidate()
                    }.onFailure { diagnose("Slider detail transition cleanup", it) }
                }
            })
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { diagnose("Detail transition end deoptimization", it) }
        }
    }

    private fun detailScale(view: View) = QsSliderDetailCornerTransition.Scale(view.scaleX, view.scaleY)

    private fun mainPaintedRadius(view: View, radius: Float): Float = runCatching {
        val seek = requireNotNull(Reflect.callMethod(view, "getSlider"))
        QsSliderCornerPaintScope.scaled(radius,
            (Reflect.getObjectField(seek, QsSliderCornerPaintScope.BACKGROUND) as Number).toFloat(),
            (Reflect.getObjectField(seek, "mBackgroundRadius") as Number).toFloat())
    }.getOrDefault(radius)

    private fun paintRadius(view: View): Float? {
        val radius = customRadius(view) ?: run {
            synchronized(detailTransitions) { detailTransitions.remove(view) }
            return null
        }
        val transition = synchronized(detailTransitions) { detailTransitions[view] } ?: return radius
        val interpolated = transition.radius(
            QsTileCornerSettings.radiusXposed(QsTileCornerSettings.Size.OneXTwo),
            view.width, view.height, radius, detailScale(view))
        if (interpolated == null) synchronized(detailTransitions) { detailTransitions.remove(view) }
        return interpolated ?: radius
    }

    private fun <T> withSmoothCorners(seek: Any, block: () -> T): T =
        QsSliderSmoothCornerScope(
            read = { requireNotNull(Reflect.getObjectField(seek, it)) },
            write = { name, value -> Reflect.setObjectField(seek, name, value) },
        ).paint(block)

    private fun updateMaterialIfChanged(seek: View, radius: Float) {
        val rect = Reflect.getObjectField(seek, "mClipProgressRect") as Rect
        val state = QsSliderMaterialState(radius,
            (Reflect.getObjectField(seek, "mBackgroundRoundCornerWeight") as Number).toFloat(),
            (Reflect.getObjectField(seek, "mirrorScaleValue") as Number).toFloat(),
            rect.left, rect.top, rect.right, rect.bottom,
            System.identityHashCode(Reflect.getObjectField(seek, "baseMixColorDrawable")),
            System.identityHashCode(Reflect.getObjectField(seek, "activeMixColorDrawable")),
            Reflect.getObjectField(seek, "isSupportStroke") as Boolean,
            Reflect.getObjectField(seek, "isDetailToggle") as Boolean)
        val last = synchronized(materialStates) { materialStates[seek] }
        if (last == state) return
        if (last != null && state.sameMaterial(last)) {
            // Native draw updates bounds. Pure animation radius changes do not need
            // theme/mix/stroke-template reconstruction for both slider layers.
            if (last.radius != radius) {
                try {
                    updateMaterialCorner(seek, "baseMixColorDrawable", radius)
                    updateMaterialCorner(seek, "activeMixColorDrawable", radius)
                } catch (error: Throwable) {
                    diagnose("Slider corner-only update", error)
                    configureMaterial(seek, radius, state)
                    return
                }
            }
            synchronized(materialStates) { materialStates[seek] = state }
            return
        }
        configureMaterial(seek, radius, state)
    }

    private fun updateMaterialCorner(seek: View, field: String, radius: Float) {
        val drawable = Reflect.getObjectField(seek, field) ?: return
        val proxy = requireNotNull(Reflect.callMethod(drawable, "getViewBlurProxy"))
        val config = requireNotNull(Reflect.callMethod(proxy, "getBlurConfig"))
        Reflect.callMethod(config, "setCornerRadius", radius)
        // Preserve native G2 weight, stroke/optics and mix configuration.
        if (Reflect.callMethod(config, "getGradientStrokeLineParam") != null) {
            val corner = requireNotNull(Reflect.callMethod(config, "getGradientStrokeCornerParam"))
            Reflect.callMethod(corner, "setRadius", radius)
        }
        Reflect.callMethod(proxy, "applyBlurConfig")
    }

    private fun configureMaterial(seek: View, radius: Float, state: QsSliderMaterialState) {
        val previous = materialUpdating.get()
        materialUpdating.set(true)
        try {
            Reflect.callMethod(seek, "updateBaseMixColorDrawableRadius", radius)
            synchronized(materialStates) { materialStates[seek] = state }
        } finally {
            if (previous == null) materialUpdating.remove() else materialUpdating.set(previous)
        }
    }

    private fun customRadius(view: View): Float? {
        if (!QsControlCenterCornerTargets.isSlider(view.javaClass.name)) return null
        // Never change values cached by constructors before layout is complete.
        if (view.width <= 0 || view.height <= 0) return null
        val percent = QsTileCornerSettings.radiusXposed(QsTileCornerSettings.Size.OneXTwo) ?: return null
        val seek = runCatching { Reflect.callMethod(view, "getSlider") as? View }.getOrNull()
        val width = seek?.width?.takeIf { it > 0 } ?: view.width
        val height = seek?.height?.takeIf { it > 0 } ?: view.height
        return QsTileCornerRules.pixels(percent, width, height)
    }

    private fun contentState(view: View): List<Any?>? {
        if (QsTileCornerSettings.radiusXposed(QsTileCornerSettings.Size.OneXTwo) == null &&
            synchronized(applied) { !applied.containsKey(view) }) return emptyList()
        if (view.width <= 0 || view.height <= 0) return null
        val seek = Reflect.callMethod(view, "getSlider") as? View ?: return null
        if (seek.width <= 0 || seek.height <= 0) return null
        return listOf(System.identityHashCode(seek), seek.width, seek.height,
            System.identityHashCode(Reflect.getObjectField(seek, "baseMixColorDrawable")),
            System.identityHashCode(Reflect.getObjectField(seek, "activeMixColorDrawable")),
            Reflect.getObjectField(seek, "mBackgroundRoundCornerWeight"),
            System.identityHashCode(Reflect.callMethod(view, "getOnRadiusUpdateListener")))
    }

    private fun updateSafely(view: View?, force: Boolean = false): Boolean {
        if (view == null || HotReloadLifecycle.isRetiring || nativeRadius.get() == true) return false
        if (!QsControlCenterCornerTargets.isSlider(view.javaClass.name)) return false
        return runCatching {
            val radius = customRadius(view)
            val previous = synchronized(applied) { applied[view] }
            if (radius == null) {
                if (QsTileCornerSettings.radiusXposed(QsTileCornerSettings.Size.OneXTwo) != null) return@runCatching false
                if (previous != null) restore(view)
                return@runCatching true
            }
            if (contentState(view) == null) return@runCatching false
            if (!force && radius == previous) return@runCatching true
            synchronized(applied) { applied[view] = radius }
            try { applyVisualRadius(view, radius) }
            catch (error: Throwable) { restore(view); throw error }
            if (reported.add("applied")) HookLog.i(TAG, "Control Center slider background/fill/stroke corners applied")
            true
        }.onFailure { diagnose("Slider corner refresh", it) }.getOrDefault(false)
    }

    private fun applyVisualRadius(view: View, radius: Float) {
        val seek = requireNotNull(Reflect.callMethod(view, "getSlider"))
        val previous = nativeRadius.get()
        nativeRadius.set(true)
        try {
            withSmoothCorners(seek) {
                val original = requireNotNull(Reflect.callMethod(view, "getRoundParams", radius))
                val radiusDp = (Reflect.getObjectField(original, "radiusDp") as Number).toFloat()
                // Use smooth QS parameters for custom radii, keeping shared params untouched.
                val weight = (Reflect.getObjectField(seek, "mBackgroundRoundCornerWeight") as Number).toFloat()
                val params = Reflect.callMethod(original, "copy", radiusDp, radius, weight, false)
                // Never call geometry setters here. COUI uses its radius fields to
                // calculate track height as well as touch/drag ranges in draw().
                // Only onDraw temporarily changes the two paint-radius fields.
                val listener = Reflect.callMethod(view, "getOnRadiusUpdateListener")
                if (listener != null) Reflect.callMethod(listener, "invoke", radius, params)
                (seek as? View)?.let { synchronized(materialStates) { materialStates.remove(it) } }
                Reflect.callMethod(seek, "updateBaseMixColorDrawableRadius", radius)
                (seek as? View)?.invalidate()
            }
        } finally {
            if (previous == null) nativeRadius.remove() else nativeRadius.set(previous)
        }
    }

    private fun restore(view: View) {
        val previous = nativeRadius.get()
        nativeRadius.set(true)
        try {
            synchronized(detailTransitions) { detailTransitions.remove(view) }
            (Reflect.callMethod(view, "getSlider") as? View)?.let {
                synchronized(materialStates) { materialStates.remove(it) }
            }
            // Recompute the current system/theme radius, not an obsolete cached
            // radius from before a theme, density or orientation change.
            val radius = (Reflect.callMethod(view, "getRadius") as Number).toFloat()
            Reflect.callMethod(view, "setCornerRadius", radius)
            synchronized(applied) { applied.remove(view) }
        } finally {
            if (previous == null) nativeRadius.remove() else nativeRadius.set(previous)
        }
    }
    private fun diagnose(stage: String, error: Throwable) {
        if (reported.add(stage)) HookLog.w(TAG, "$stage unavailable; native behavior retained", error)
    }
}
