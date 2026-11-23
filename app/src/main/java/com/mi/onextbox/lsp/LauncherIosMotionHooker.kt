package com.mi.onextbox.lsp

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.view.SurfaceControl
import android.view.View
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal object LauncherIosMotionHooker {
    private const val TAG = "LauncherIosMotion"

    fun config() = LauncherIosMotionProfile.Config(
        openSpeed = LauncherIosMotionSettings.value(LauncherIosMotionSettings.openSpeed),
        closeSpeed = LauncherIosMotionSettings.value(LauncherIosMotionSettings.closeSpeed),
        slowMotion = LauncherIosMotionSettings.value(LauncherIosMotionSettings.slow),
        bounceStrength = if (LauncherIosMotionSettings.active(LauncherIosMotionSettings.BOUNCE))
            LauncherIosMotionSettings.value(LauncherIosMotionSettings.bounce) else 0,
    )

    fun allowed(kind: LauncherIosMotionProfile.RouteKind): Boolean = LauncherIosMotionSettings.active(
        if (kind == LauncherIosMotionProfile.RouteKind.Opening) LauncherIosMotionSettings.OPENING_APPS else LauncherIosMotionSettings.CLOSING_APPS,
    )

    fun hook(classLoader: ClassLoader) {
        val scope = "$TAG@${System.identityHashCode(classLoader)}"
        runCatching {
            val base = LauncherIosMotionTargets(classLoader)
            val bounce = installSprings(base, scope)
            val renderScope = "$scope:perspective"
            var renderer: LauncherIosPerspectiveRenderer? = null
            runCatching {
                renderer = LauncherIosPerspectiveRenderer(LauncherIosMorphTargets(base), renderScope, bounce).also { it.install() }
            }.onFailure {
                renderer?.dispose()
                ModernHookRegistry.unhookPrefix(renderScope)
                HookLog.w(TAG, "Reference perspective unavailable; spring profile retained", it)
            }
        }.onFailure {
            ModernHookRegistry.unhookPrefix(scope)
            HookLog.w(TAG, "Reference animation unavailable; stock launcher retained", it)
        }
    }

    private fun installSprings(base: LauncherIosMotionTargets, scope: String): LauncherIosBounceGuard? {
        val tuning = ThreadLocal<LauncherIosMotionProfile.SpringSpec?>()
        val directWrites = ThreadLocal<Boolean>()
        val logged = ConcurrentHashMap.newKeySet<String>()
        val bounce = runCatching { LauncherIosBounceGuard(base, "$scope:bounce", directWrites).also { it.install() } }.onFailure {
            ModernHookRegistry.unhookPrefix("$scope:bounce")
            HookLog.w(TAG, "Bounce recovery unavailable; existing spring profile retained", it)
        }.getOrNull()
        base.setupMethods.forEachIndexed { index, method ->
            ModernHookRegistry.installFast("$scope:setup:$index", method, XposedInterface.Hooker { chain ->
                if (!LauncherIosMotionSettings.active() || !ValueAnimator.areAnimatorsEnabled()) return@Hooker chain.proceed()
                val previous = tuning.get()
                val kindName = (base.springKind.get(chain.thisObject) as? Enum<*>)?.name.orEmpty()
                val kind = bounce?.route(chain.thisObject) ?: LauncherIosMotionProfile.classify(kindName)
                val config = config().let {
                    if (kind == LauncherIosMotionProfile.RouteKind.SwipeToHome &&
                        bounce?.allowsReturnBounce(chain.thisObject) != true) it.copy(bounceStrength = 0) else it
                }
                val spec = bounce?.openingSpec(chain.thisObject) ?:
                    if (kind != null && allowed(kind)) LauncherIosMotionProfile.spring(kindName, config, kind) else null
                tuning.set(spec)
                try {
                    if (index == 0) bounce?.beforeInitialized(chain.thisObject)
                    if (spec != null && logged.add(kindName)) HookLog.i(TAG,
                        "Reference spring: $kindName stiffness=${spec.stiffness} damping=${spec.damping}")
                    val result = chain.proceed()
                    if (index == 0) bounce?.onInitialized(chain.thisObject)
                    result
                } finally { if (previous == null) tuning.remove() else tuning.set(previous) }
            })
            ModernHookRuntime.requireModule().deoptimize(method)
        }
        base.forceSetters.forEachIndexed { index, method ->
            ModernHookRegistry.installFast("$scope:force:$index", method, XposedInterface.Hooker { chain ->
                if (directWrites.get() == true) return@Hooker chain.proceed()
                val spec = tuning.get() ?: return@Hooker chain.proceed()
                chain.proceed(chain.args.toTypedArray().also { it[0] = if (index == 0) spec.damping else spec.stiffness })
            })
            ModernHookRuntime.requireModule().deoptimize(method)
        }
        HookLog.i(TAG, "Reference seven-channel spring profile installed: ${base.spring.name}")
        return bounce
    }
}

internal class LauncherIosPerspectiveRenderer(
    private val targets: LauncherIosMorphTargets,
    private val scope: String,
    private val bounce: LauncherIosBounceGuard? = null,
) {
    private class Binding(
        val lease: LauncherIosSurfaceOwners.Lease,
        val route: LauncherIosMorphTargets.Route,
        val spring: WeakReference<Any>,
        val listener: WeakReference<Any>,
    )
    private data class Source(val x: Float, val y: Float, val width: Float)
    private data class Input(
        val minimized: Double, val iconLeft: Double, val iconWidth: Double, val row: Double,
        val intensity: Double, val opening: Boolean, val openResponse: Double, val closeResponse: Double,
        val damping: Double, val droplet: Float, val width: Float, val height: Float, val time: Long,
        val taskId: Int, val windowAlpha: Float,
    )
    private class Motion(var surface: WeakReference<SurfaceControl>, val taskId: Int,
        var lease: LauncherIosSurfaceOwners.Lease, val model: LauncherIosGenieMotion, var time: Long = Long.MIN_VALUE) {
        var shape: LauncherIosDeformation? = null
        var input: Input? = null
    }
    private data class Icon(
        val surface: SurfaceControl, val transaction: SurfaceControl.Transaction,
        val crop: Rect, val affine: Matrix, val alpha: Float, val needsContainment: Boolean,
    )
    private class Batch(val params: WeakReference<Any>, val rect: RectF, val time: Long) {
        val handoff = LauncherIosIconHandoff<Icon>()
        var lease: LauncherIosSurfaceOwners.Lease? = null
    }
    private class IconPass(
        val batch: Batch, val owner: Any,
        val surface: SurfaceControl, val transaction: SurfaceControl.Transaction, val matrix: Matrix,
        val windowAlpha: Float, val opening: Boolean, val genie: Boolean,
    ) {
        var affine: Matrix? = null
        var alpha = 1f
        var captured = false
    }
    private data class IconFade(val windowAlpha: Float, val opening: Boolean)
    private class Frame(
        val helper: Any, val params: Any, val record: Any?, val binding: Binding?,
        val surface: SurfaceControl, val transaction: SurfaceControl.Transaction, val input: Input?,
        val batch: Batch?, val source: String,
    ) {
        var saved: Saved? = null
        var radiusBuilder: Any? = null
    }
    private data class Saved(
        val lease: LauncherIosSurfaceOwners.Lease, val surface: SurfaceControl,
        val transaction: SurfaceControl.Transaction, val crop: Rect, val affine: Matrix,
        val shape: LauncherIosDeformation?, val input: Input, val radius: Float, val batch: Batch?, val motion: Motion,
    )
    private class OriginalAlpha(val surface: SurfaceControl, val batch: Batch, var alpha: Float)
    private data class IconPresentation(val saved: Saved, val icon: Icon)

    private val lock = Any()
    private val bindings = WeakHashMap<Any, Binding>()
    private val sources = WeakHashMap<Any, Source>()
    private val records = WeakHashMap<Any, Saved>()
    private val pendingBatches = WeakHashMap<Any, Batch>()
    private val motions = ArrayList<Motion>(4)
    private val alphas = ArrayList<OriginalAlpha>(2)
    private val presentations = ArrayList<IconPresentation>(2)
    private val retirements = LinkedHashMap<LauncherIosSurfaceOwners.Lease, Long>()
    private val cleanup = Handler(Looper.getMainLooper())
    private val owners = LauncherIosSurfaceOwners<SurfaceControl>(targets::sameSurface)
    private val currentFrame = ThreadLocal<Frame?>()
    private val currentBatch = ThreadLocal<Batch?>()
    private val currentIcon = ThreadLocal<IconPass?>()
    private val currentFade = ThreadLocal<IconFade?>()
    private val pendingSource = ThreadLocal<Source?>()
    private val matrixScratch = ThreadLocal.withInitial { FloatArray(16) }
    private val fitter = ThreadLocal.withInitial { LauncherIosFlyingIconBounds() }
    private val iconBoundsMethods = ConcurrentHashMap<Class<*>, List<Method>>()
    private val identity = FloatArray(16).apply { for (i in 0..3) this[i * 5] = 1f }
    private val logged = HashSet<String>()
    private var generation = 0L
    private var failed = false
    private var disposed = false

    private fun enabled() = !failed && !disposed && ValueAnimator.areAnimatorsEnabled() && LauncherIosMotionSettings.active()

    fun install() {
        installSources()
        hook(targets.registerUpdateMethod, "binding") { chain ->
            safe {
                val listener = chain.getArg(0) ?: return@safe
                val spring = chain.thisObject ?: return@safe
                val route = targets.routes.firstOrNull { it.method.declaringClass.isInstance(listener) } ?: return@safe
                val helper = route.transformField.get(listener) ?: return@safe
                val kind = (targets.transformKindField.get(helper) as? Enum<*>)?.name.orEmpty()
                LauncherIosMotionProfile.classify(kind)?.let { bounce?.bind(spring, it) }
                synchronized(lock) {
                    bindings[helper]?.let { retire(listOf(it.lease)) }
                    bindings[helper] = Binding(LauncherIosSurfaceOwners.Lease(++generation), route, WeakReference(spring), WeakReference(listener))
                    (pendingSource.get() ?: listenerSource(route, listener))?.let { sources[helper] = it }
                    if (listener.javaClass.name.contains("SwipeBreakLaunchAnimCreator") && logged.add("continuation-binding"))
                        HookLog.i("LauncherIosMotion", "Interrupted launch continuation bound to the shared deformation timeline")
                }
            }
            chain.proceed()
        }
        hook(targets.dispatchMethod, "batch") { chain ->
            if (!enabled()) return@hook chain.proceed()
            val previous = currentBatch.get()
            val batch = guarded {
                val params = chain.getArg(2) ?: return@guarded null
                val rect = chain.getArg(3) as? RectF ?: return@guarded null
                Batch(WeakReference(params), RectF(rect), System.nanoTime()).also { synchronized(lock) { pendingBatches[params] = it } }
            }
            currentBatch.set(batch)
            try { chain.proceed() } finally {
                restore(currentBatch, previous)
                safe { synchronized(lock) {
                    if (batch != null && !batch.handoff.deferred) abandonBatch(batch, true)
                } }
            }
        }
        hook(targets.frameMethod, "window") { chain ->
            if (!enabled()) return@hook chain.proceed()
            val previous = currentFrame.get()
            currentFrame.set(guarded { makeFrame(chain) })
            try { chain.proceed() } finally { restore(currentFrame, previous) }
        }
        installProperties()
        installIcons()
        installFloatingIcon()
        installRecords()
        hook(targets.cancelMethod, "cancel") { chain ->
            val leases = leases(chain.thisObject)
            val result = chain.proceed()
            safe { retire(leases) }
            result
        }
        targets.endMethods.forEachIndexed { index, method ->
            hook(method, "end:$index") { chain ->
                val leases = leases(chain.thisObject)
                val result = chain.proceed()
                safe { if (targets.springRunningMethod.invoke(chain.thisObject) != true) {
                    if (targets.springCancelledField.getBoolean(chain.thisObject)) retire(leases) else release(leases)
                } }
                result
            }
        }
        HotReloadLifecycle.register(scope, detach = ::dispose)
        HookLog.i("LauncherIosMotion", "Reference perspective ready: transform=${targets.transformClass.name} icon=${targets.iconUpdateMethod.declaringClass.name} record=${targets.frameRecordClass.name} fallback=${targets.floatingIcon?.run?.declaringClass?.name}; routes=${targets.routes.size}; immediateAlpha=true; warp=false")
    }

    private fun listenerSource(route: LauncherIosMorphTargets.Route, listener: Any): Source? {
        val screen = route.screenRectField.get(listener) as? RectF ?: return null
        val rect = route.iconRectFields.asSequence().mapNotNull { it.get(listener) as? RectF }.firstOrNull {
            !it.isEmpty && it.width().isFinite() && it.height().isFinite() && it.width() <= screen.width() * .5f &&
                it.width() / it.height() in .5f..2f
        } ?: return null
        return Source(rect.centerX(), rect.centerY(), rect.width())
    }

    private fun installSources() {
        targets.sourceMethods.forEachIndexed { index, method ->
            val viewIndex = method.parameterTypes.indexOfFirst { View::class.java.isAssignableFrom(it) }
            hook(method, "source:$index") { chain ->
                if (!enabled()) return@hook chain.proceed()
                val previous = pendingSource.get()
                val source = runCatching {
                    val view = chain.getArg(viewIndex) as? View ?: return@runCatching null
                    if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return@runCatching null
                    val rect = Rect()
                    iconBoundsMethods.getOrPut(view.javaClass) {
                        view.javaClass.methods.filter { it.name == "getIconBounds" && it.parameterTypes.contentEquals(arrayOf(Rect::class.java)) }
                            .onEach { it.isAccessible = true }
                    }.firstOrNull()?.invoke(view, rect)
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    Source(location[0] + if (rect.isEmpty) view.width * .5f else rect.exactCenterX(),
                        location[1] + if (rect.isEmpty) view.height * .5f else rect.exactCenterY(),
                        if (rect.isEmpty) view.width.toFloat() else rect.width().toFloat())
                }.getOrNull()
                if (source != null) synchronized(lock) {
                    if (logged.add("source:$index")) HookLog.i("LauncherIosMotion", "Reference icon bounds captured: ${method.name} width=${source.width}")
                }
                pendingSource.set(source)
                try { chain.proceed() } finally { restore(pendingSource, previous) }
            }
        }
        targets.transformClass.declaredConstructors.forEachIndexed { index, constructor ->
            hook(constructor, "source-owner:$index") { chain ->
                val result = chain.proceed()
                val source = pendingSource.get()
                if (source != null) synchronized(lock) { (chain.thisObject ?: result)?.let { sources[it] = source } }
                result
            }
        }
    }

    private fun makeFrame(chain: XposedInterface.Chain): Frame? = synchronized(lock) {
        val helper = chain.thisObject ?: return@synchronized null
        val params = chain.getArg(0) ?: return@synchronized null
        val holder = chain.getArg(3) ?: return@synchronized null
        val transaction = targets.transactionField.get(holder) as? SurfaceControl.Transaction ?: return@synchronized null
        val raw = chain.getArg(1) as? Array<*> ?: return@synchronized null
        val matching = raw.filterNotNull().filter { targets.matchesTargetMethod.invoke(helper, chain.getArg(2), it) == true }
        val target = matching.singleOrNull() ?: return@synchronized null
        val surface = targets.leashField.get(target) as? SurfaceControl ?: return@synchronized null
        if (!surface.isValid) return@synchronized null
        val rect = chain.getArg(5) as? RectF ?: return@synchronized null
        val binding = bindings[helper]
        val now = System.nanoTime()
        val batch = currentBatch.get() ?: pendingBatches[params]?.takeIf {
            now >= it.time && now - it.time < 50_000_000L && sameRect(it.rect, rect)
        }
        if (binding?.lease?.active == true) batch?.lease = binding.lease
        val name = (targets.transformKindField.get(helper) as? Enum<*>)?.name.orEmpty()
        val kind = LauncherIosMotionProfile.classify(name)
        val fullscreen = targets.getWindowingModeMethod.invoke(targets.windowConfigurationField.get(target)) == 1
        val listener = binding?.listener?.get()
        val widget = listener != null && binding.route.isWidgetField?.getBoolean(listener) == true
        val window = targets.fullWindowMethod.invoke(helper) as? Rect
        val taskId = (targets.taskInfoField.get(target) as? ActivityManager.RunningTaskInfo)?.taskId ?: -1
        val input = if (binding?.lease?.active == true && kind != null && LauncherIosMotionHooker.allowed(kind) &&
            window != null && !rect.isEmpty && LauncherIosMotionProfile.supportsFullscreenFrame(
                window.width(), window.height(), fullscreen, widget, targets.needsRotationField.getBoolean(params),
            )
        ) {
            val rawAnchor = targets.originHeightField.getFloat(params) / window.height()
            val anchor = rawAnchor.takeIf { it in .015f.. .30f } ?: .065f
            val scale = rect.height() / window.height()
            val inherited = if (name == "SWIPE_BREAK_LAUNCH") motions.firstOrNull {
                taskId >= 0 && it.taskId == taskId && it.lease.active && it.input != null
            }?.input else null
            val source = sources[helper] ?: inherited?.let {
                Source(window.left + (it.iconLeft + it.iconWidth * .5).toFloat() * window.width(),
                    window.exactCenterY() + it.row.toFloat() * window.height() * .5f,
                    it.iconWidth.toFloat() * window.width()).also { source -> sources[helper] = source }
            }
            val remaining = (1f - scale).coerceAtLeast(.20f)
            val row = source?.let { (it.y - window.exactCenterY()) * 2f / window.height() }
                ?: (rect.centerY() - window.exactCenterY()) * 2f / window.height() / remaining
            val column = source?.let { (it.x - window.exactCenterX()) * 2f / window.width() }
                ?: (rect.centerX() - window.exactCenterX()) * 2f / window.width() / remaining
            val progress = ((scale - anchor) / (1f - anchor)).coerceIn(0f, 1f)
            val width = (source?.width ?: anchor * window.height()).toDouble() / window.width()
            val opening = kind == LauncherIosMotionProfile.RouteKind.Opening
            val config = LauncherIosMotionHooker.config()
            if (scale.isFinite() && row.isFinite() && column.isFinite() && width.isFinite()) Input(
                1.0 - progress, (1.0 + column - width) * .5, width, row.toDouble(),
                if (LauncherIosMotionSettings.active(LauncherIosMotionSettings.GENIE)) LauncherIosMotionSettings.value(LauncherIosMotionSettings.bend) * .01 else 0.0,
                opening, LauncherIosMotionProfile.response(true, config), LauncherIosMotionProfile.response(false, config),
                if (kind == LauncherIosMotionProfile.RouteKind.SwipeToHome) 1.1 else 1.0,
                if (LauncherIosMotionSettings.active(LauncherIosMotionSettings.DROPLET_CORNERS)) LauncherIosDeformation.droplet(progress, opening) else 0f,
                rect.width(), rect.height(), batch?.time ?: now,
                taskId,
                targets.appAlphaField.getFloat(params),
            ) else null
        } else null
        Frame(helper, params, chain.getArg(4), binding, surface, transaction, input, batch, "window:$name")
    }

    private fun installProperties() {
        hook(targets.setMatrixMethod, "matrix") { chain ->
            val result = chain.proceed()
            if (currentIcon.get() == null && currentFrame.get() == null && owners.isEmpty) return@hook result
            safe {
                val surface = targets.propertiesSurfaceField.get(chain.thisObject) as? SurfaceControl ?: return@safe
                val matrix = chain.getArg(0) as? Matrix ?: return@safe
                val icon = currentIcon.get()
                if (icon != null && targets.sameSurface(icon.surface, surface) && matrix === icon.matrix) {
                    icon.affine = Matrix(matrix)
                    synchronized(lock) { captureIcon(icon) }
                }
                else {
                    val frame = currentFrame.get()
                    if (frame != null && targets.sameSurface(frame.surface, surface) && targets.transformMatrixField.get(frame.helper) === matrix) {
                        applyFrame(frame, Matrix(matrix))
                        frame.radiusBuilder = chain.thisObject
                    } else synchronized(lock) {
                        val presentation = presentation(surface)
                        if (presentation != null) {
                            val holder = targets.propertiesTransactionField.get(chain.thisObject)
                            val transaction = targets.transactionField.get(holder) as SurfaceControl.Transaction
                            applyIcon(presentation.saved.copy(transaction = transaction), presentation.icon.copy(
                                transaction = transaction, affine = Matrix(matrix)), "native-matrix")
                        } else if (owners.releaseIfOwned(surface, null)) {
                            val holder = targets.propertiesTransactionField.get(chain.thisObject)
                            val transaction = targets.transactionField.get(holder) as SurfaceControl.Transaction
                            targets.applyPerspective(transaction, surface, identity)
                            alphas.removeAll { targets.sameSurface(it.surface, surface) }
                        }
                    }
                }
            }
            result
        }
        hook(targets.setAlphaMethod, "icon-alpha") { chain ->
            val icon = currentIcon.get()
            if (icon == null) {
                val fade = currentFade.get()
                if (fade == null && owners.isEmpty) return@hook chain.proceed()
                val native = chain.getArg(0) as? Float ?: return@hook chain.proceed()
                val alpha = if (fade != null) LauncherIosMotionProfile.iconAlphaBeforeFit(native, fade.windowAlpha, fade.opening, false)
                else guarded { synchronized(lock) {
                    val surface = targets.propertiesSurfaceField.get(chain.thisObject) as? SurfaceControl ?: return@synchronized null
                    val state = presentation(surface) ?: return@synchronized null
                    alphas.firstOrNull { targets.sameSurface(it.surface, surface) }?.alpha = native
                    if (logged.add("native-alpha")) HookLog.i("LauncherIosMotion", "Native icon alpha synchronized with window phase")
                    LauncherIosMotionProfile.iconAlpha(native, state.saved.input.windowAlpha, state.saved.input.opening)
                } }
                return@hook if (alpha == null) chain.proceed() else chain.proceed(chain.args.toTypedArray().also { it[0] = alpha })
            }
            val alpha = guarded {
                val surface = targets.propertiesSurfaceField.get(chain.thisObject) as? SurfaceControl ?: return@guarded null
                val native = chain.getArg(0) as? Float ?: return@guarded null
                if (!targets.sameSurface(icon.surface, surface) || !native.isFinite() || !icon.windowAlpha.isFinite()) return@guarded null
                icon.alpha = if (icon.genie) LauncherIosMotionProfile.iconAlpha(native, icon.windowAlpha, icon.opening) else native
                synchronized(lock) {
                    alphas.removeAll { targets.sameSurface(it.surface, surface) }
                    if (icon.genie) alphas += OriginalAlpha(surface, icon.batch, native)
                    if (icon.genie && icon.windowAlpha > .005f && logged.add("alpha:${icon.opening}"))
                        HookLog.i("LauncherIosMotion", "Reference icon hidden before fitting: opening=${icon.opening}")
                }
                if (icon.genie) LauncherIosMotionProfile.iconAlphaBeforeFit(native, icon.windowAlpha, icon.opening, true) else native
            }
            if (alpha == null) chain.proceed() else chain.proceed(chain.args.toTypedArray().also { it[0] = alpha })
        }
        targets.radiusMethods.forEachIndexed { index, method ->
            hook(method, "droplet:$index") { chain ->
                val frame = currentFrame.get()
                val saved = frame?.saved
                if (frame == null || saved == null || frame.radiusBuilder !== chain.thisObject || saved.input.droplet <= 0f) return@hook chain.proceed()
                val native = chain.getArg(0) as? Float ?: return@hook chain.proceed()
                val radius = LauncherIosMotionProfile.cornerRadius(native, saved.crop.width(), saved.crop.height(), saved.input.droplet)
                frame.radiusBuilder = null
                chain.proceed(chain.args.toTypedArray().also { it[0] = radius })
            }
        }
    }

    private fun installIcons() {
        hook(targets.iconUpdateMethod, "icon-frame") { chain ->
            if (!enabled()) return@hook chain.proceed()
            val batch = currentBatch.get()?.takeIf { it.params.get() === chain.getArg(0) }
            val previous = currentIcon.get()
            val previousFade = currentFade.get()
            val fade = guarded {
                val opening = targets.iconOpeningField.getBoolean(chain.thisObject)
                val kind = if (opening) LauncherIosMotionProfile.RouteKind.Opening else LauncherIosMotionProfile.RouteKind.Closing
                if (!LauncherIosMotionHooker.allowed(kind) || !LauncherIosMotionSettings.active(LauncherIosMotionSettings.GENIE) ||
                    LauncherIosMotionSettings.value(LauncherIosMotionSettings.bend) == 0) return@guarded null
                targets.appAlphaField.getFloat(chain.getArg(0)).takeIf(Float::isFinite)?.let { IconFade(it, opening) }
            }
            currentFade.set(fade)
            val pass = guarded {
                if (batch == null || fade == null) return@guarded null
                val surface = chain.getArg(3) as? SurfaceControl ?: return@guarded null
                val transaction = targets.transactionField.get(chain.getArg(4)) as? SurfaceControl.Transaction ?: return@guarded null
                val matrix = targets.iconMatrixField.get(chain.thisObject) as? Matrix ?: return@guarded null
                synchronized(lock) { (chain.getArg(2) as? RectF)?.let(batch.rect::set) }
                IconPass(batch, chain.thisObject!!, surface, transaction, matrix, fade.windowAlpha, fade.opening, true)
            }
            currentIcon.set(pass)
            try {
                chain.proceed()
            } finally {
                restore(currentIcon, previous)
                restore(currentFade, previousFade)
                if (pass != null && !pass.captured) safe {
                    synchronized(lock) { restoreAlpha(pass.transaction, pass.surface, batch = pass.batch) }
                }
            }
        }
    }

    private fun captureIcon(pass: IconPass) {
        val affine = pass.affine ?: return
        val nativeCrop = targets.iconCropField.get(pass.owner) as Rect
        val crop = if (!nativeCrop.isEmpty) Rect(nativeCrop) else Rect(0, 0,
            targets.surfaceWidthMethod.invoke(pass.surface) as Int, targets.surfaceHeightMethod.invoke(pass.surface) as Int)
        if (crop.isEmpty || !pass.alpha.isFinite()) return
        val previous = pass.batch.handoff.capture(pass.transaction, Icon(pass.surface, pass.transaction, crop,
            affine, pass.alpha, pass.genie))
        pass.captured = true
        if (previous != null && !targets.sameSurface(previous.surface, pass.surface))
            restoreAlpha(previous.transaction, previous.surface, batch = pass.batch)
    }

    private fun installFloatingIcon() {
        val fallback = targets.floatingIcon ?: return
        hook(fallback.run, "floating-icon-scope") { chain ->
            val previous = currentFade.get()
            currentFade.set(guarded {
                if (!enabled() || !LauncherIosMotionSettings.active(LauncherIosMotionSettings.GENIE) ||
                    !LauncherIosMotionSettings.active(LauncherIosMotionSettings.OPENING_APPS) ||
                    LauncherIosMotionSettings.value(LauncherIosMotionSettings.bend) == 0) return@guarded null
                fallback.alpha.getFloat(chain.thisObject).takeIf(Float::isFinite)?.let { IconFade(it, true) }
            })
            try { chain.proceed() } finally { restore(currentFade, previous) }
        }
        hook(fallback.update, "floating-icon-alpha") { chain ->
            val fade = currentFade.get() ?: return@hook chain.proceed()
            val native = chain.getArg(0) as? Float ?: return@hook chain.proceed()
            if (!native.isFinite()) return@hook chain.proceed()
            val alpha = LauncherIosMotionProfile.iconAlphaBeforeFit(native, fade.windowAlpha, fade.opening, false)
            synchronized(lock) {
                if (logged.add("floating-icon")) HookLog.i("LauncherIosMotion", "Reference FloatingIconView handoff active")
            }
            chain.proceed(chain.args.toTypedArray().also { it[0] = alpha })
        }
    }

    private fun applyFrame(frame: Frame, affine: Matrix) = synchronized(lock) {
        val lease = frame.binding?.lease ?: return@synchronized
        if (!owners.canAcquire(frame.surface, lease)) return@synchronized
        val input = frame.input
        val crop = Rect(targets.transformCropField.get(frame.helper) as Rect)
        if (!enabled() || input == null || crop.isEmpty || !affine.isAffine) {
            clearOwned(frame.transaction, frame.surface, lease)
            frame.batch?.let(::abandonBatch)
            return@synchronized
        }
        val now = System.nanoTime()
        motions.removeAll { !it.lease.active && now - it.time > 1_000_000_000L }
        val motion = motions.firstOrNull { it.surface.get()?.let { surface -> targets.sameSurface(surface, frame.surface) } == true }
            ?: motions.firstOrNull { input.taskId >= 0 && it.taskId == input.taskId && retirements.containsKey(it.lease) }
            ?: Motion(WeakReference(frame.surface), input.taskId, lease, LauncherIosGenieMotion()).also { motions += it }
        if (motion.lease.generation > lease.generation) return@synchronized
        val predecessor = motion.lease.takeIf { it !== lease && retirements.containsKey(it) }
        if (predecessor != null) motion.model.rebaseClock(input.time)
        if (input.time > motion.time) {
            motion.shape = motion.model.update(input.minimized, input.iconLeft, input.iconWidth, input.row, input.intensity, input.opening,
                input.openResponse, input.closeResponse, input.damping, input.time, preserveState = motion.lease.active)
                ?.takeIf { it.isVisible(input.width, input.height) }
            motion.time = input.time
            motion.input = input
        }
        motion.surface = WeakReference(frame.surface)
        motion.lease = lease
        val pose = motion.input ?: input
        val shape = renderedShape(motion, pose)
        val radius = LauncherIosMotionProfile.cornerRadius(targets.cornerRadiusField.getFloat(frame.params), crop.width(), crop.height(), pose.droplet)
        val saved = Saved(lease, frame.surface, frame.transaction, crop, affine, shape, pose, radius, frame.batch, motion)
        if (applySaved(saved, frame.source)) {
            frame.saved = saved
            frame.batch?.lease = lease
            if (predecessor != null) completeRetarget(predecessor, saved)
        }
    }

    private fun renderedShape(motion: Motion, pose: Input): LauncherIosDeformation? =
        motion.shape?.settleAtFullscreen(pose.minimized)

    private fun applySaved(saved: Saved, source: String): Boolean {
        if (!owners.canAcquire(saved.surface, saved.lease)) return false
        val shape = saved.shape
        if (shape == null) clearOwned(saved.transaction, saved.surface, saved.lease)
        else if (owners.acquire(saved.surface, saved.lease)) {
            val matrix = matrixScratch.get()!!
            shape.writeMatrix(matrix, saved.crop.left.toFloat(), saved.crop.top.toFloat(), saved.crop.width().toFloat(), saved.crop.height().toFloat())
            if (matrix.any { !it.isFinite() }) {
                clearOwned(saved.transaction, saved.surface, saved.lease)
                saved.batch?.let(::abandonBatch)
                return false
            }
            targets.applyPerspective(saved.transaction, saved.surface, matrix)
        }
        val paired = finishIcon(saved, source)
        if (logged.add("$source:$paired")) HookLog.i("LauncherIosMotion", "Reference perspective frame: source=$source paired=$paired shape=${shape != null} droplet=${saved.input.droplet}")
        return true
    }

    private fun finishIcon(saved: Saved, source: String): Boolean {
        val batch = saved.batch?.takeIf { it.time == saved.input.time && it.lease === saved.lease }
        val captured = batch?.handoff?.take(saved.transaction)?.takeIf { icon ->
            alphas.any { it.batch === batch && targets.sameSurface(it.surface, icon.surface) }
        }
        val icon = captured
            ?: presentations.firstOrNull { it.saved.lease === saved.lease && it.saved.input.time <= saved.input.time }
                ?.icon?.copy(transaction = saved.transaction) ?: return false
        return applyIcon(saved, icon, source)
    }

    private fun presentation(surface: SurfaceControl): IconPresentation? = if (!enabled() ||
        !LauncherIosMotionSettings.active(LauncherIosMotionSettings.GENIE)) null else presentations.firstOrNull {
        it.saved.lease.active && targets.sameSurface(it.icon.surface, surface) && owners.isOwned(surface, it.saved.lease)
    }

    private fun applyIcon(saved: Saved, icon: Icon, source: String): Boolean {
        if (!icon.surface.isValid || !owners.canAcquire(icon.surface, saved.lease)) return false
        val previous = presentations.firstOrNull { targets.sameSurface(it.icon.surface, icon.surface) }
        if (previous != null && (previous.saved.lease.generation > saved.lease.generation ||
            previous.saved.lease === saved.lease && previous.saved.input.time > saved.input.time)) return false
        val original = alphas.firstOrNull { it.batch.lease === saved.lease && targets.sameSurface(it.surface, icon.surface) } ?: return false
        if (saved.input.intensity <= 0.0 || !icon.needsContainment) {
            clearOwned(saved.transaction, icon.surface, saved.lease)
            restoreAlpha(saved.transaction, icon.surface, lease = saved.lease)
            return false
        }
        if (!owners.acquire(icon.surface, saved.lease)) return false
        val fit = fitter.get()!!
        val attachment = LauncherIosMotionProfile.iconAttachment(saved.input.minimized)
        if (!fit.fit(icon.crop, icon.affine, saved.crop, saved.affine, saved.radius, saved.shape, attachment)) {
            clearOwned(saved.transaction, icon.surface, saved.lease)
            if (logged.add("icon-fit:$source")) HookLog.w("LauncherIosMotion", "Reference icon fit skipped; native visibility restored: $source")
            return false
        }
        targets.applyPerspective(saved.transaction, icon.surface, fit.matrix)
        val alpha = LauncherIosMotionProfile.iconAlpha(original.alpha, saved.input.windowAlpha, saved.input.opening).coerceIn(0f, 1f)
        saved.transaction.setAlpha(icon.surface, alpha)
        if (previous != null) presentations.remove(previous)
        presentations += IconPresentation(saved, icon.copy(alpha = alpha))
        if (logged.add("fit:$source")) HookLog.i("LauncherIosMotion", "Synchronized icon deformation: $source alpha=$alpha attachment=$attachment")
        if (alpha > .015f && saved.shape != null && logged.add("visible:${saved.input.opening}"))
            HookLog.i("LauncherIosMotion", "Visible icon follows window deformation: opening=${saved.input.opening} top=${saved.shape.topWidth} bottom=${saved.shape.bottomWidth}")
        return true
    }

    private fun abandonBatch(batch: Batch) = abandonBatch(batch, false)

    private fun abandonBatch(batch: Batch, flush: Boolean) {
        val icon = batch.handoff.abandon() ?: return
        val lease = batch.lease
        if (lease != null && retirements.containsKey(lease)) return
        val original = alphas.firstOrNull { it.batch === batch && targets.sameSurface(it.surface, icon.surface) }
        val alpha = original?.alpha?.takeIf { it.isFinite() && icon.surface.isValid &&
            (lease == null || !owners.hasNewerOwner(icon.surface, lease)) }
        if (lease != null && owners.canAcquire(icon.surface, lease)) clearOwned(icon.transaction, icon.surface, lease)
        restoreAlpha(icon.transaction, icon.surface, batch = batch)
        if (flush && alpha != null) {
            val rollback = SurfaceControl.Transaction()
            try {
                targets.applyPerspective(rollback, icon.surface, identity)
                rollback.setAlpha(icon.surface, alpha.coerceIn(0f, 1f)).apply()
            } finally { rollback.close() }
        }
    }

    private fun installRecords() {
        hook(targets.recordStoreMethod, "record-store") { chain ->
            val result = chain.proceed()
            safe {
                val record = chain.thisObject ?: return@safe
                synchronized(lock) {
                    val saved = currentFrame.get()?.takeIf { it.record === record }?.saved
                    if (saved == null) records.remove(record) else {
                        saved.batch?.handoff?.deferred = true
                        records[record] = saved
                    }
                }
            }
            result
        }
        hook(targets.recordReplayMethod, "record-replay") { chain ->
            val result = chain.proceed()
            safe {
                val record = chain.thisObject ?: return@safe
                val transaction = chain.getArg(0) as? SurfaceControl.Transaction ?: return@safe
                val surface = chain.getArg(1) as? SurfaceControl ?: return@safe
                synchronized(lock) {
                    val saved = records[record] ?: return@synchronized
                    if (!enabled() || !saved.lease.active || !surface.isValid || !targets.recordValidField.getBoolean(record)) return@synchronized
                    if (!owners.canAcquire(surface, saved.lease)) return@synchronized
                    if (saved.motion.lease === saved.lease) {
                        val priorSurface = saved.motion.surface.get()
                        if (priorSurface != null && !targets.sameSurface(priorSurface, surface) &&
                            owners.releaseIfOwned(priorSurface, saved.lease)) targets.applyPerspective(transaction, priorSurface, identity)
                        saved.motion.surface = WeakReference(surface)
                    }
                    val values = targets.recordAffineFields.map { it.getFloat(record) }
                    if (values.any { !it.isFinite() } || abs(values[3]) > .001f) return@synchronized
                    val crop = Rect(targets.recordCropField.get(record) as Rect)
                    if (crop.isEmpty) return@synchronized
                    val affine = Matrix().apply { setScale(values[2], values[2]); postTranslate(values[0], values[1]); postRotate(values[3], values[4], values[5]) }
                    val radius = targets.recordRadiusField.getFloat(record)
                    val pose = saved.motion.input?.takeIf { saved.motion.lease === saved.lease && it.time >= saved.input.time } ?: saved.input
                    val shape = if (pose === saved.input) saved.shape else renderedShape(saved.motion, pose)
                    val adjusted = LauncherIosMotionProfile.cornerRadius(radius, crop.width(), crop.height(), pose.droplet)
                    if (applySaved(saved.copy(surface = surface, transaction = transaction, crop = crop, affine = affine,
                        radius = adjusted, input = pose, shape = shape), "replay")) {
                        if (pose.droplet > 0f) targets.applyRecordRadius(transaction, surface, record, adjusted)
                    }
                }
            }
            result
        }
        hook(targets.recordClearMethod, "record-clear") { chain ->
            val result = chain.proceed()
            safe { synchronized(lock) { records.remove(chain.thisObject)?.batch?.let { abandonBatch(it, true) } } }
            result
        }
    }

    private fun clearOwned(transaction: SurfaceControl.Transaction, surface: SurfaceControl, lease: LauncherIosSurfaceOwners.Lease) {
        if (!owners.releaseIfOwned(surface, lease)) return
        targets.applyPerspective(transaction, surface, identity)
        restoreAlpha(transaction, surface, lease)
        presentations.removeAll { it.saved.lease === lease && targets.sameSurface(it.icon.surface, surface) }
    }
    private fun restoreAlpha(transaction: SurfaceControl.Transaction, surface: SurfaceControl,
        lease: LauncherIosSurfaceOwners.Lease? = null, batch: Batch? = null) {
        val original = alphas.firstOrNull { targets.sameSurface(it.surface, surface) &&
            (lease == null || it.batch.lease === lease) && (batch == null || it.batch === batch) } ?: return
        val originalLease = original.batch.lease
        if (surface.isValid && original.alpha.isFinite() &&
            (originalLease == null || !owners.hasNewerOwner(surface, originalLease)))
            transaction.setAlpha(surface, original.alpha.coerceIn(0f, 1f))
        alphas.remove(original)
    }
    private fun leases(spring: Any?): List<LauncherIosSurfaceOwners.Lease> = synchronized(lock) {
        if (spring == null) emptyList() else bindings.values.filter { it.spring.get() === spring }.map { it.lease }
    }
    private fun retire(leases: List<LauncherIosSurfaceOwners.Lease>) = synchronized(lock) {
        val added = leases.filter { it.active && !retirements.containsKey(it) }
        val now = System.nanoTime()
        added.forEach { retirements[it] = now + 160_000_000L }
        if (added.isNotEmpty()) cleanup.postDelayed({ if (!disposed && !failed) safe { synchronized(lock) {
            val time = System.nanoTime()
            release(retirements.filterValues { it <= time }.keys.toList())
        } } }, 170)
    }

    private fun completeRetarget(previous: LauncherIosSurfaceOwners.Lease, saved: Saved) {
        retirements.remove(previous)
        val surfaces = owners.detachLease(previous)
        for (surface in surfaces) {
            targets.applyPerspective(saved.transaction, surface, identity)
            val original = alphas.firstOrNull { it.batch.lease === previous && targets.sameSurface(it.surface, surface) }
            if (original != null) {
                if (surface.isValid) saved.transaction.setAlpha(surface, 0f)
                alphas.remove(original)
            }
        }
        val pending = alphas.filter { it.batch.lease === previous }
        pending.forEach { original ->
            if (original.surface.isValid && !owners.hasNewerOwner(original.surface, previous))
                saved.transaction.setAlpha(original.surface, 0f)
        }
        alphas.removeAll(pending.toSet())
        presentations.removeAll { it.saved.lease === previous }
        records.entries.removeAll { !it.value.lease.active }
        pendingBatches.entries.removeAll { it.value.lease?.active != true }
        if (logged.add("retarget")) HookLog.i("LauncherIosMotion", "Continuous retarget inherited shape and velocity: task=${saved.input.taskId}")
    }

    private fun release(leases: List<LauncherIosSurfaceOwners.Lease>) = synchronized(lock) {
        leases.forEach { lease ->
            retirements.remove(lease)
            val pending = alphas.filter { it.batch.lease === lease && owners.canAcquire(it.surface, lease) }.map { it.surface }
            clear((owners.detachLease(lease) + pending).distinct())
        }
        records.entries.removeAll { !it.value.lease.active }
        pendingBatches.entries.removeAll { it.value.lease?.active != true }
        presentations.removeAll { !it.saved.lease.active }
    }
    private fun clear(surfaces: List<SurfaceControl>) {
        if (surfaces.isEmpty()) return
        val transaction = SurfaceControl.Transaction()
        try {
            surfaces.forEach { surface -> runCatching { targets.applyPerspective(transaction, surface, identity); restoreAlpha(transaction, surface) } }
            transaction.apply()
        } finally { transaction.close() }
    }
    private fun disable(error: Throwable) = synchronized(lock) {
        if (!failed) {
            failed = true
            clear((owners.detachAll() + alphas.map { it.surface }).distinct())
            records.clear(); pendingBatches.clear(); motions.clear(); alphas.clear(); presentations.clear(); retirements.clear()
            HookLog.w("LauncherIosMotion", "Reference perspective disabled after error; stock visibility restored", error)
        }
    }
    fun dispose() = synchronized(lock) {
        disposed = true
        clear((owners.detachAll() + alphas.map { it.surface }).distinct())
        bindings.clear(); sources.clear(); records.clear(); pendingBatches.clear(); motions.clear(); alphas.clear(); presentations.clear(); retirements.clear()
    }
    private fun sameRect(a: RectF, b: RectF) = abs(a.left - b.left) < .05f && abs(a.top - b.top) < .05f &&
        abs(a.right - b.right) < .05f && abs(a.bottom - b.bottom) < .05f
    private inline fun safe(block: () -> Unit) { try { block() } catch (error: Throwable) { disable(error) } }
    private inline fun <T> guarded(block: () -> T?): T? = try { block() } catch (error: Throwable) { disable(error); null }
    private fun <T> restore(local: ThreadLocal<T?>, previous: T?) { if (previous == null) local.remove() else local.set(previous) }
    private fun hook(executable: Executable, slot: String, callback: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast("$scope:$slot", executable, XposedInterface.Hooker(callback))
        ModernHookRuntime.requireModule().deoptimize(executable)
    }
}
