package com.mi.onextbox.lsp

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.QsCornerReflect as Reflect
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Device-plugin cards use PluginDrawable, not the ordinary QS tile delegate. */
internal object QsDeviceCardCorners {
    private const val PREFIX = "com.oplus.deviceplugin.sdk.ui.view.separatecardview."
    private const val TAG = "ONextBox-QsDeviceCorner"
    private val targets = mapOf(
        "SquareDeviceCardView" to "square_device_constraintlayout",
        "SquareEntranceCardView" to "square_entrance_constraintLayout",
        "RectangleDeviceCardView" to "rectangleCoLayout",
        "RectangleEntranceCardView" to "rectangle_entrance_constraintLayout",
        "NoDeviceEntranceCardView" to "no_device_constraintLayout",
    )
    private val classNames = targets.keys.map { PREFIX + it }.toSet()
    private data class Clip(val original: ViewOutlineProvider?, val clipped: Boolean, val custom: ViewOutlineProvider, val radius: Float, val weight: Float?)
    private val clips = WeakHashMap<View, Clip>()
    private val applied = WeakHashMap<View, Boolean>()
    private val painted = WeakHashMap<Drawable, Float>()
    private val nativeShape = ThreadLocal<Boolean>()
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private var hierarchy: QsCornerHierarchyObservers? = null
    private val innerIds = ConcurrentHashMap<Class<*>, Int>()

    fun hook(loader: ClassLoader) {
        val observers = QsCornerViewObservers("qs:device-card-views",
            onLayout = { update(it, refreshPaint = true) },
            content = ::contentState) { update(it, refreshPaint = true) }
        hierarchy = QsCornerHierarchyObservers("qs:device-card-discovery") { view ->
            if (view.javaClass.name in classNames) {
                observers.watch(view)
                update(view, refreshPaint = true)
            }
        }
        (classNames + "${PREFIX}d").forEach { name ->
            val type = Reflect.findClassIfExists(name, loader) ?: return@forEach
            if (name != "${PREFIX}d") type.declaredConstructors.forEach { constructor ->
                ModernHookRegistry.installCompat("$TAG:observe:${constructor.toGenericString()}", constructor, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) (param.thisObject as? View)?.let(observers::watch)
                    }
                })
            }
            type.declaredMethods.filter { it.name in setOf("g", "h", "onAttachedToWindow", "setBlurDrawable", "setCornerRadiusToDrawable") }
                .forEach { method ->
                    ModernHookRegistry.installCompat("$TAG:${method.toGenericString()}", method, object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable == null && nativeShape.get() != true)
                                (param.thisObject as? View)?.let(observers::invalidate)
                        }
                    })
                }
        }
        HotReloadLifecycle.register("qs:device-card-corners", detach = {
            synchronized(applied) { applied.keys.toList() }.forEach(::restore)
            synchronized(painted) { painted.clear() }
        })
    }

    fun discover(view: View) {
        val root = generateSequence(view as View?) { it.parent as? View }
            .firstOrNull { it.javaClass.name == "com.oplus.systemui.plugins.qs.OplusQSRootView" }
        (root as? android.view.ViewGroup)?.let { hierarchy?.watch(it) }
    }

    private fun innerView(view: View): View? {
        val id = innerIds.getOrPut(view.javaClass) {
            view.resources.getIdentifier(targets.getValue(view.javaClass.simpleName), "id", "com.android.systemui")
        }
        return if (id == 0) null else view.findViewById(id)
    }

    private fun contentState(view: View): List<Any?>? {
        val percent = size(view)?.let { QsTileCornerSettings.radiusXposed(it) }
        if (percent == null && synchronized(applied) { !applied.containsKey(view) }) return emptyList()
        if (view.width <= 0 || view.height <= 0) return null
        val drawable = Reflect.callMethod(view, "getBlurDrawable") as? Drawable ?: return null
        val path = Reflect.callMethod(drawable, "getPathProvider") ?: return null
        val inner = innerView(view) ?: return null
        if (inner.width <= 0 || inner.height <= 0) return null
        return listOf(System.identityHashCode(drawable), System.identityHashCode(path),
            Reflect.callMethod(path, "getCornerRadius", drawable), Reflect.callMethod(path, "getCornerWeight", drawable),
            System.identityHashCode(inner), inner.width, inner.height,
            System.identityHashCode(view.outlineProvider), view.clipToOutline,
            System.identityHashCode(inner.outlineProvider), inner.clipToOutline,
            percent)
    }

    private fun size(view: View): QsTileCornerSettings.Size? =
        generateSequence(view as View?) { it.parent as? View }
            .take(4).mapNotNull(QsCornerSizeResolver::resolve).firstOrNull()

    private fun update(view: View, refreshPaint: Boolean = false): Boolean {
        if (HotReloadLifecycle.isRetiring || nativeShape.get() == true || view.javaClass.name !in classNames) return false
        return runCatching {
            val percent = size(view)?.let { QsTileCornerSettings.radiusXposed(it) }
            if (percent == null) {
                if (synchronized(applied) { applied.containsKey(view) }) restore(view)
                return@runCatching true
            }
            if (view.width <= 0 || view.height <= 0) return@runCatching false
            val radius = QsTileCornerRules.pixels(percent, view.width, view.height) ?: return@runCatching false
            val drawable = Reflect.callMethod(view, "getBlurDrawable") as? Drawable ?: return@runCatching false
            val inner = innerView(view) ?: return@runCatching false
            if (inner.width <= 0 || inner.height <= 0) return@runCatching false
            synchronized(applied) { applied[view] = true }
            val path = requireNotNull(Reflect.callMethod(drawable, "getPathProvider"))
            val currentWeight = (Reflect.callMethod(path, "getCornerWeight", drawable) as? Number)?.toFloat()
            val weight = QsCornerSmoothness.weight(currentWeight)
            val current = (Reflect.callMethod(path, "getCornerRadius", drawable) as Number).toFloat()
            if (current != radius || currentWeight != weight) {
                Reflect.callMethod(drawable, "setCornerRadius", radius, weight)
            }
            if (refreshPaint || current != radius || currentWeight != weight || synchronized(painted) { painted[drawable] != radius }) {
                // A new PluginDrawable starts with radius=0 even when its
                // underlying blur configuration was built with a circle.
                // The native equal-value setter otherwise skips propagation.
                Reflect.callMethod(drawable, "invalidatePath")
                drawable.invalidateSelf()
                synchronized(painted) { painted[drawable] = radius }
            }
            clip(view, drawable, radius, weight)
            // The inner layout can also retain its own native circular clip.
            val idName = targets.getValue(view.javaClass.simpleName)
            val id = view.resources.getIdentifier(idName, "id", "com.android.systemui")
            if (id != 0) view.findViewById<View>(id)?.let { clip(it, drawable, radius, weight) }
            if (reported.add("applied:${view.javaClass.name}")) HookLog.i(TAG,
                "Device-plugin corners applied: ${view.javaClass.simpleName}, requested=${percent}%/${radius}px, drawable=${drawable.javaClass.simpleName}")
            true
        }.onFailure {
            if (reported.add("update")) HookLog.w(TAG, "Device card corner update unavailable", it)
        }.getOrDefault(false)
    }

    private fun clip(view: View, drawable: Drawable, radius: Float, weight: Float?) {
        if (!view.clipToOutline) return
        val previous = synchronized(clips) { clips[view] }
        if (previous?.radius == radius && previous.weight == weight && view.outlineProvider === previous.custom &&
            (previous.custom as? QsCornerDrawableOutline)?.usesDrawable(drawable) == true) return
        val owned = previous != null && view.outlineProvider === previous.custom
        val original = if (owned) previous.original else view.outlineProvider
        val clipped = if (owned) previous.clipped else view.clipToOutline
        val custom = QsCornerDrawableOutline(drawable)
        synchronized(clips) { clips[view] = Clip(original, clipped, custom, radius, weight) }
        view.outlineProvider = custom
        view.invalidateOutline()
    }

    private fun restore(view: View) {
        nativeShape.set(true)
        try {
            (Reflect.callMethod(view, "getBlurDrawable") as? Drawable)?.let { drawable ->
                synchronized(painted) { painted.remove(drawable) }
            }
            // Let the native card recompute theme/blur/shape, rather than restoring
            // values captured before a configuration or device-state change.
            Reflect.callMethod(view, "g")
            val id = view.resources.getIdentifier(targets.getValue(view.javaClass.simpleName), "id", "com.android.systemui")
            listOfNotNull(view, if (id != 0) view.findViewById<View>(id) else null).forEach { target ->
                val state = synchronized(clips) { clips.remove(target) }
                if (state != null && target.outlineProvider === state.custom) {
                    target.outlineProvider = state.original
                    target.clipToOutline = state.clipped
                    target.invalidateOutline()
                }
            }
            synchronized(applied) { applied.remove(view) }
        } finally { nativeShape.remove() }
    }
}
