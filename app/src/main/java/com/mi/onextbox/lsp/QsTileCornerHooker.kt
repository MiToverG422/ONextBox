package com.mi.onextbox.lsp

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.QsCornerReflect as Reflect
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Per-tile providers only: never mutate the shared QS resource pool or global drawables. */
internal object QsTileCornerHooker {
    private const val TAG = "ONextBox-QsTileCorner"
    private const val RESIZEABLE = "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView"
    private const val STANDARD = "com.oplus.systemui.qs.base.tile.OplusQSTileBaseView"
    private const val HIGHLIGHT = "com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView"
    private const val MEDIA = "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView"
    private const val MEDIA_DRAWABLE = "com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable"
    private const val SCENARIO = "com.oplus.systemui.qs.scenario.ui.template.ScenarioTileViewBase"
    private data class MediaClip(val original: ViewOutlineProvider?, val clipped: Boolean, val custom: ViewOutlineProvider, val radius: Float, val weight: Float?)
    private val owned = WeakHashMap<Drawable, QsTileCornerOwnership<Any>>()
    private val mediaClips = WeakHashMap<View, MediaClip>()
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private var hierarchy: QsCornerHierarchyObservers? = null
    private val animationCapture = ThreadLocal<Boolean>()

    fun hook(loader: ClassLoader) {
        // Class lookup must not initialize launcher/SystemUI configuration during package loading.
        val provider = Reflect.findClassIfExists("com.oplusos.systemui.common.outline.RoundRectOutlineProvider", loader) ?: return
        val observers = QsCornerViewObservers("qs:corner-views",
            onLayout = { updateSafely(it, provider, refreshChildren = true) },
            content = ::contentState) { updateSafely(it, provider, refreshChildren = true) }
        val types = listOf(RESIZEABLE, "${RESIZEABLE}OneXOne", "${RESIZEABLE}TwoXOne", "${RESIZEABLE}TwoXTwo",
            "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableThreeStageView",
            STANDARD, HIGHLIGHT, MEDIA, SCENARIO,
            "com.oplus.systemui.qs.scenario.ui.template.ScenarioTileViewOneXOne",
            "com.oplus.systemui.qs.scenario.ui.template.ScenarioTileViewTwoXOne",
            "com.oplus.systemui.qs.scenario.ui.template.ScenarioTileViewTwoXTwo",
            "com.oplus.systemui.qs.scenario.ui.template.ScenarioTileViewFourXOne")
        hierarchy = QsCornerHierarchyObservers("qs:tile-discovery") { view ->
            // Constructor calls can be inlined into native factories. Discover
            // actual subclasses (including the normal media panel) after layout.
            if (generateSequence(view.javaClass as Class<*>?) { it.superclass }.any { it.name in types }) {
                observers.watch(view)
                updateSafely(view, provider, refreshChildren = true)
            }
        }
        var installed = 0
        types.forEach { name ->
            val type = Reflect.findClassIfExists(name, loader) ?: return@forEach
            type.declaredConstructors.forEach { constructor ->
                runCatching {
                    ModernHookRegistry.installCompat("$TAG:observe:${constructor.toGenericString()}", constructor, object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable == null) (param.thisObject as? View)?.let(observers::watch)
                        }
                    })
                }.onFailure { diagnose("Observe ${type.simpleName}", it) }
            }
            type.declaredMethods.filter { it.name == "dispatchDraw" }.forEach { method ->
                runCatching {
                    ModernHookRegistry.installFast("$TAG:${type.name}:dispatchDraw", method) { chain ->
                        // Keep discovery for inlined constructors, without compatibility params
                        // and argument-array copies on every rendered frame.
                        runCatching { (chain.thisObject as? View)?.let(observers::watch) }
                            .onFailure { diagnose("Observe tile drawing", it) }
                        chain.proceed()
                    }
                    ModernHookRuntime.requireModule().deoptimize(method)
                    installed++
                }.onFailure { diagnose("Hook ${type.simpleName}.dispatchDraw", it) }
            }
            type.declaredMethods.filter { it.name in setOf("onAttachedToWindow", "onOutlineUpdate", "onDrawableUpdate") }.forEach { method ->
                runCatching {
                    ModernHookRegistry.installCompat("$TAG:${type.name}:${method.name}", method, object : ModernMethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            (param.thisObject as? View)?.let(observers::watch)
                            // Drawing only discovers missed/inlined constructors. The observer
                            // performs expensive work when configuration or geometry changes.
                        }
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable == null && method.name != "dispatchDraw") {
                                (param.thisObject as? View)?.let(observers::invalidate)
                            }
                        }
                    })
                    ModernHookRuntime.requireModule().deoptimize(method)
                    installed++
                }.onFailure { diagnose("Hook ${type.simpleName}.${method.name}", it) }
            }
            if (name.startsWith(RESIZEABLE) || name.endsWith("OplusQSResizeableThreeStageView") || name == MEDIA) {
                val radiusMethods = if (name == MEDIA) setOf("getViewRadius", "getContainerCornerRadius") else setOf("getCornerRadius", "getTransitionRadius", "getCornerWeight")
                type.declaredMethods.filter { it.name in radiusMethods && it.parameterCount == 0 }.forEach { method ->
                    runCatching {
                        ModernHookRegistry.installCompat("$TAG:${type.name}:${method.name}", method, object : ModernMethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (param.throwable != null || HotReloadLifecycle.isRetiring) return
                                val view = param.thisObject as? View ?: return
                                runCatching {
                                    // Change the animation endpoints, never geometry getters used by layout.
                                    if (method.name in setOf("getCornerRadius", "getCornerWeight") && animationCapture.get() != true) return@runCatching
                                    val size = QsCornerSizeResolver.resolve(view) ?: return@runCatching
                                    val percent = QsTileCornerSettings.radiusXposed(size) ?: return@runCatching
                                    if (method.name == "getCornerWeight") {
                                        param.result = QsCornerSmoothness.weight((param.result as? Number)?.toFloat())
                                        return@runCatching
                                    }
                                    if (animationCapture.get() != true && target(view)?.let { isAnimating(it.drawable) } == true) return@runCatching
                                    val bg = runCatching { Reflect.callMethod(view, "getBg") as? View }.getOrNull()
                                    // Constructors cache getCornerRadius() in viewRadius. Keep that
                                    // native cache untouched so disabling also restores animations.
                                    if (bg == null || bg.width <= 0 || bg.height <= 0) return@runCatching
                                    QsTileCornerRules.pixels(percent, bg.width, bg.height)
                                        ?.let { param.result = it }
                                }.onFailure { diagnose("Tile animation radius", it) }
                            }
                        })
                        ModernHookRuntime.requireModule().deoptimize(method)
                    }.onFailure { diagnose("Hook ${type.simpleName}.${method.name}", it) }
                }
            }
        }
        listOf(
            "com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed.TileFixedRadiusShareTransitionProperty",
            "com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.threestage.ThreeStageRadiusShareProperty",
        ).forEach { name ->
            Reflect.findClassIfExists(name, loader)?.declaredMethods?.forEach { method ->
                if (method.name == "captureValues") {
                    ModernHookRegistry.installFast("$TAG:capture:${method.toGenericString()}", method) { chain ->
                        val previous = animationCapture.get()
                        animationCapture.set(true)
                        try { chain.proceed() } finally {
                            if (previous == null) animationCapture.remove() else animationCapture.set(previous)
                        }
                    }
                }
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { diagnose("Tile animation caller deoptimization", it) }
            }
        }
        // Standard-layout small tiles draw through this native icon frame, not the tile root.
        Reflect.findClassIfExists("$STANDARD\$SpotLightIconFrame", loader)?.declaredMethods
            ?.filter { it.name == "dispatchDraw" }?.forEach { method ->
                runCatching {
                    ModernHookRegistry.installCompat("$TAG:standard-draw", method, object : ModernMethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            runCatching { Reflect.getObjectField(requireNotNull(param.thisObject), "this\$0") as? View }
                                .onSuccess { it?.let(observers::watch) }.onFailure { diagnose("Standard tile owner", it) }
                        }
                    })
                    ModernHookRuntime.requireModule().deoptimize(method)
                }.onFailure { diagnose("Standard tile drawing", it) }
            }
        // Media has no dispatchDraw override. Update its native background before it
        // draws, including the independent multi-light/stroke corner cache.
        Reflect.findClassIfExists(MEDIA_DRAWABLE, loader)?.declaredMethods
            ?.filter { it.name == "draw" }?.forEach { method ->
                runCatching {
                    ModernHookRegistry.installCompat("$TAG:media-draw", method, object : ModernMethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (HotReloadLifecycle.isRetiring) return
                            if (!QsTileCornerSettings.enabledXposed() && synchronized(owned) { owned.isEmpty() }) return
                            val drawable = param.thisObject as? Drawable ?: return
                            runCatching {
                                val state = synchronized(owned) { owned[drawable] } ?: return@runCatching
                                // Only the animated light border needs draw-time synchronization.
                                syncMediaLight(drawable, state.custom)
                            }.onFailure { diagnose("Media background corners", it) }
                        }
                    })
                    ModernHookRuntime.requireModule().deoptimize(method)
                }.onFailure { diagnose("Media drawing", it) }
            }
        runCatching { QsControlCenterSliderCorners.hook(loader) }
            .onFailure { diagnose("Control Center slider corners", it) }
        runCatching { QsDeviceCardCorners.hook(loader) }
            .onFailure { diagnose("Device card corners", it) }
        HotReloadLifecycle.register("qs:tile-corners", detach = {
            synchronized(owned) { owned.toList() }.forEach { (drawable, state) -> restore(drawable, state) }
            synchronized(mediaClips) { mediaClips.keys.toList() }.forEach(::restoreMediaClip)
        })
        HookLog.i(TAG, "Per-tile corner adapters installed: $installed")
        if (installed > 0) HookEvidence.report("qs_corner", "installed")
    }

    private fun updateSafely(view: View?, provider: Class<*>, refreshChildren: Boolean = false): Boolean {
        if (view == null || HotReloadLifecycle.isRetiring) return false
        val root = generateSequence(view as View?) { it.parent as? View }
            .firstOrNull { it.javaClass.name == "com.oplus.systemui.plugins.qs.OplusQSRootView" }
        (root as? android.view.ViewGroup)?.let { hierarchy?.watch(it) }
        QsDeviceCardCorners.discover(view)
        if (!QsTileCornerSettings.enabledXposed() && synchronized(owned) { owned.isEmpty() } &&
            synchronized(mediaClips) { mediaClips.isEmpty() }) return true
        return runCatching { update(view, provider, refreshChildren) }
            .onFailure { diagnose("Native tile corner update", it) }.getOrDefault(false)
    }

    private data class Target(val drawable: Drawable, val bg: View, val media: Boolean)

    private fun target(view: View): Target? {
        val hierarchy = generateSequence(view.javaClass as Class<*>?) { it.superclass }.map { it.name }.toSet()
        val scenario = SCENARIO in hierarchy
        val nativeBackground = RESIZEABLE in hierarchy || MEDIA in hierarchy
        val scenarioWide = scenario && hierarchy.any { it.endsWith("ScenarioTileViewTwoXOne") }
        val drawable = (when {
            scenarioWide -> Reflect.callMethod(view, "getHighLightDrawableBg")
            scenario -> Reflect.callMethod(view, "getBgDrawable")
            nativeBackground -> Reflect.callMethod(view, "getTransitionDrawable")
            else -> Reflect.getObjectField(view, "mBgDrawable")
        }) as? Drawable ?: return null
        val bg = (when {
            scenarioWide -> Reflect.callMethod(view, "getTileBg")
            scenario -> Reflect.callMethod(view, "getIvBg")
            nativeBackground -> Reflect.callMethod(view, "getBg")
            else -> Reflect.getObjectField(view, "mBg")
        }) as? View ?: return null
        return Target(drawable, bg, MEDIA in hierarchy)
    }

    private fun contentState(view: View): List<Any?>? {
        val target = target(view) ?: return null
        val bg = target.bg
        val percent = QsCornerSizeResolver.resolve(view)?.let { QsTileCornerSettings.radiusXposed(it) }
        if (percent == null && synchronized(owned) { !owned.containsKey(target.drawable) } &&
            synchronized(mediaClips) { !mediaClips.containsKey(view) && !mediaClips.containsKey(bg) }) return emptyList()
        if (bg.width <= 0 || bg.height <= 0) return null
        val delegate = Reflect.getObjectField(target.drawable, "delegate") ?: return null
        // Ignore temporary press/deformation overrides; only observe the persistent provider.
        val path = Reflect.getObjectField(delegate, "pathProvider") ?: return null
        return listOf(System.identityHashCode(target.drawable), System.identityHashCode(bg),
            bg.width, bg.height, System.identityHashCode(path),
            Reflect.callMethod(path, "getCornerRadius", target.drawable),
            Reflect.callMethod(path, "getCornerWeight", target.drawable),
            System.identityHashCode(view.outlineProvider), view.clipToOutline,
            System.identityHashCode(bg.outlineProvider),
            percent,
            isAnimating(target.drawable))
    }

    private fun update(view: View, provider: Class<*>, refreshChildren: Boolean): Boolean {
        val target = target(view) ?: return false
        val drawable = target.drawable
        val bg = target.bg
        val size = QsCornerSizeResolver.resolve(view)
        val percent = size?.let { QsTileCornerSettings.radiusXposed(it) }
        if (percent == null) {
            // Restore only our own changes, then leave this size entirely to the system.
            val state = synchronized(owned) { owned[drawable] }
            if (state != null) {
                if (isAnimating(drawable)) return false
                restore(drawable, state)
                synchronized(owned) { owned.remove(drawable) }
                bg.invalidateOutline()
                bg.invalidate()
            }
            restoreMediaClip(bg)
            restoreMediaClip(view)
            return true
        }
        if (bg.width <= 0 || bg.height <= 0) return false
        // The block provider owns interpolation until the native animation releases it.
        if (isAnimating(drawable)) return false
        if (updateDrawable(drawable, provider, bg.width, bg.height, percent)) {
            bg.invalidateOutline()
            bg.invalidate()
        }
        // Theme/attachment callbacks can rebuild children while retaining the
        // same outer provider. Re-propagate it once, not on every rendered frame.
        if (refreshChildren) {
            Reflect.callMethod(drawable, "invalidatePath")
            bg.invalidate()
        }
        updateMediaClip(bg, drawable)
        if (target.media) updateMediaClip(view, drawable)
        return true
    }

    private fun updateMediaClip(view: View, drawable: Drawable) {
        val state = synchronized(owned) { owned[drawable] }
        if (state == null) { restoreMediaClip(view); return }
        val previous = synchronized(mediaClips) { mediaClips[view] }
        if (previous?.radius == state.radius && previous.weight == state.weight &&
            view.outlineProvider === previous.custom && view.clipToOutline &&
            (previous.custom as? QsCornerDrawableOutline)?.usesDrawable(drawable) == true) return
        // Keep native clipping flags and reuse the drawable's smooth/animated outline.
        if (!view.clipToOutline) return
        val custom = QsCornerDrawableOutline(drawable)
        val original = if (previous != null && view.outlineProvider === previous.custom) previous.original else view.outlineProvider
        val clipped = if (previous != null && view.outlineProvider === previous.custom) previous.clipped else view.clipToOutline
        synchronized(mediaClips) { mediaClips[view] = MediaClip(original, clipped, custom, state.radius, state.weight) }
        view.outlineProvider = custom
        view.invalidateOutline()
    }
    private fun restoreMediaClip(view: View) {
        val previous = synchronized(mediaClips) { mediaClips.remove(view) } ?: return
        if (view.outlineProvider === previous.custom) {
            view.outlineProvider = previous.original
            view.clipToOutline = previous.clipped
            view.invalidateOutline()
        }
    }

    private fun isAnimating(drawable: Drawable): Boolean {
        val delegate = requireNotNull(Reflect.getObjectField(drawable, "delegate"))
        val persistent = requireNotNull(Reflect.getObjectField(delegate, "pathProvider"))
        return Reflect.callMethod(drawable, "getPathProvider") !== persistent
    }

    private fun updateDrawable(drawable: Drawable, provider: Class<*>, width: Int, height: Int, percent: Int): Boolean {
        // getPathProvider() may return a temporary press-animation override. Read only
        // the underlying provider so deformation can neither be captured nor discarded.
        val delegate = requireNotNull(Reflect.getObjectField(drawable, "delegate"))
        val current = requireNotNull(Reflect.getObjectField(delegate, "pathProvider"))
        val state = synchronized(owned) { owned[drawable] }
        val radius = QsTileCornerRules.pixels(percent, width, height) ?: return false
        val original = state?.originalFor(current) ?: current
        // Full/circular native corners can use 1/null; custom radii need the smooth QS weight.
        val weight = QsCornerSmoothness.weight((Reflect.callMethod(original, "getCornerWeight", drawable) as? Number)?.toFloat())
        if (state?.matches(current, radius, weight) == true) {
            syncMediaLight(drawable, current)
            return false
        }
        // Create a dedicated native provider. Background, blur and gradient stroke
        // consume the same path, while the normal temporary deformation stays intact.
        val custom = Reflect.newInstance(provider, radius, weight)
        val next = QsTileCornerOwnership(original, custom, radius, weight)
        synchronized(owned) { owned[drawable] = next }
        try {
            Reflect.callMethod(drawable, "setPathProvider", custom)
            // Native setters skip structurally equal providers. Remember the actual
            // accepted object so an unchanged tile does not allocate every frame.
            val accepted = requireNotNull(Reflect.getObjectField(delegate, "pathProvider"))
            synchronized(owned) { owned[drawable] = next.accepted(accepted) }
            syncMediaLight(drawable, accepted)
        }
        catch (error: Throwable) {
            restore(drawable, next)
            synchronized(owned) { owned.remove(drawable) }
            throw error
        }
        if (reported.add("applied:${drawable.javaClass.name}")) {
            val effective = Reflect.callMethod(drawable, "getPathProvider")
            val actual = effective?.let { Reflect.callMethod(it, "getCornerRadius", drawable) }
            HookLog.i(TAG, "Native tile corners applied: ${drawable.javaClass.simpleName}, requested=${percent}%/${radius}px, effective=${actual}px")
            if (reported.add("evidence:observed")) HookEvidence.report("qs_corner", "observed")
        }
        return true
    }

    private fun restore(drawable: Drawable, state: QsTileCornerOwnership<Any>) {
        val delegate = requireNotNull(Reflect.getObjectField(drawable, "delegate"))
        val current = Reflect.getObjectField(delegate, "pathProvider") ?: return
        if (state.shouldRestore(current)) {
            Reflect.callMethod(drawable, "setPathProvider", state.original)
            syncMediaLight(drawable, state.original)
        }
    }
    private fun syncMediaLight(drawable: Drawable, provider: Any) {
        if (drawable.javaClass.name != MEDIA_DRAWABLE) return
        // Light follows the current animated path during transitions, while only
        // the non-animated base provider is recorded for ownership/restoration.
        val effective = Reflect.callMethod(drawable, "getPathProvider") ?: provider
        val radius = (Reflect.callMethod(effective, "getCornerRadius", drawable) as Number).toFloat()
        val weight = Reflect.callMethod(effective, "getCornerWeight", drawable) as? Float
        // The native setter invalidates on changes; avoid logging/updating every draw.
        if (Reflect.getObjectField(drawable, "cornerRadius") != radius ||
            Reflect.getObjectField(drawable, "cornerWeight") != (weight ?: 3f)) {
            Reflect.callMethod(drawable, "setCornerParams", radius, weight)
        }
    }
    private fun diagnose(stage: String, error: Throwable) {
        if (reported.add(stage)) HookLog.w(TAG, "$stage unavailable; native behavior retained", error)
    }
}
