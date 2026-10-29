package com.mi.onextbox.lsp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.SurfaceControl
import android.view.animation.Interpolator
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap

internal object BackGesturePrediction {
    private const val TAG = "ONextBox-BackGesture"
    private const val PACKAGE = "com.android.wm.shell.back."
    private val sessions = IdentityHashMap<Any, Session>()
    private val commitScope = ThreadLocal<Session>()

    private class Session(val owner: Any, val native: NativeAnimation) {
        var animator: ValueAnimator? = null
        var finishing = false
    }

    private fun field(type: Class<*>, name: String): Field =
        type.getDeclaredField(name).apply { isAccessible = true }

    private fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method =
        type.getDeclaredMethod(name, *parameters).apply { isAccessible = true }

    private fun install(target: Method, callback: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast("$TAG:predictive:${target.toGenericString()}", target, XposedInterface.Hooker(callback))
        runCatching { ModernHookRuntime.requireModule().deoptimize(target) }
    }

    fun hook(loader: ClassLoader) {
        val ext = loader.loadClass(PACKAGE + "CrossBackAnimationExt")
        val backEvent = loader.loadClass("android.window.BackEvent")
        val motion = loader.loadClass("android.window.BackMotionEvent")
        val surface = SurfaceControl::class.java
        val progress = method(ext, "updateGestureBackProgress", backEvent, surface, surface, surface)
        val committed = method(ext, "onGestureCommitted", surface, surface, Float::class.javaPrimitiveType!!)
        val cancelled = method(ext, "onBackCancelled")
        val invoked = method(ext, "onBackInvoked")
        // Resolve the complete contract before changing any callback for that native animation.
        val adapters = listOf(false, true).mapNotNull { task ->
            runCatching { NativeAnimation(loader, task) }
                .onFailure { HookLog.w(TAG, "Predictive ${if (task) "task" else "activity"} adapter unavailable", it) }
                .getOrNull()
        }
        if (adapters.isEmpty()) return
        install(progress) { chain ->
            val session = synchronized(sessions) { sessions[chain.thisObject] } ?: return@install chain.proceed()
            if (!session.finishing) render(session) { session.native.renderProgress(session.owner) }
            null
        }
        install(committed) { chain ->
            val session = synchronized(sessions) { sessions[chain.thisObject] } ?: return@install chain.proceed()
            if (!session.finishing) render(session) { session.native.renderCommit(session.owner, chain.args[2] as Float) }
            null
        }
        install(cancelled) { chain ->
            val session = synchronized(sessions) { sessions[chain.thisObject] } ?: return@install chain.proceed()
            render(session) {
                session.native.cancel.invoke(session.native.progress.get(session.owner), Runnable { finish(session) })
            }
            null
        }
        install(invoked) { chain ->
            if (synchronized(sessions) { sessions.containsKey(chain.thisObject) }) null else chain.proceed()
        }
        install(method(ValueAnimator::class.java, "start")) { chain ->
            val session = commitScope.get()
            if (session != null && !session.finishing) {
                val animator = chain.thisObject as ValueAnimator
                session.animator = animator
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { finish(session) }
                })
            }
            chain.proceed()
        }
        val edge = method(motion, "getSwipeEdge")
        for (native in adapters) {
            install(method(native.callback, "onBackStarted", motion)) { chain ->
                val owner = native.callbackOwner.get(chain.thisObject) ?: return@install chain.proceed()
                val extension = native.extension.get(owner) ?: return@install chain.proceed()
                // A new runner may already own the host's target fields. Retire callbacks only;
                // finishing an older session here could release the new runner's surfaces.
                synchronized(sessions) { sessions.remove(extension) }?.let { previous ->
                    previous.finishing = true
                    previous.animator?.cancel()
                }
                if (native.supports(owner) && BackGestureSettings.xposed().predictive == 1 &&
                    edge.invoke(chain.args[0]) in listOf(0, 1)) {
                    synchronized(sessions) { sessions[extension] = Session(owner, native) }
                }
                try { chain.proceed() }
                catch (error: Throwable) {
                    synchronized(sessions) { sessions[extension] }?.let(::finish)
                    throw error
                }
            }
            install(native.commit) { chain ->
                val session = synchronized(sessions) { sessions[native.extension.get(chain.thisObject)] }
                    ?: return@install chain.proceed()
                val previous = commitScope.get()
                commitScope.set(session)
                try { chain.proceed() }
                finally { if (previous == null) commitScope.remove() else commitScope.set(previous) }
            }
            install(native.finish) { chain ->
                val extension = native.extension.get(chain.thisObject)
                val session = synchronized(sessions) { sessions[extension] }
                if (session != null) {
                    session.finishing = true
                    // External cancellation also comes through here. Stop queued progress while
                    // the session still suppresses extension rendering, before releasing targets.
                    runCatching { native.reset.invoke(native.progress.get(session.owner)) }
                        .onFailure { HookLog.w(TAG, "Predictive progress reset unavailable", it) }
                    synchronized(sessions) { if (sessions[extension] === session) sessions.remove(extension) }
                }
                session?.animator?.cancel()
                chain.proceed()
            }
            native.deoptimizeCallers()
        }
        BackGesturePredictionRoutes.hook(loader, adapters.any { !it.task }, adapters.any { it.task })
        HotReloadLifecycle.register("$TAG:prediction", canReload = { synchronized(sessions) { sessions.isEmpty() } })
        HookLog.i(TAG, "Predictive style adapters installed; native progress and completion retained")
    }

    private inline fun render(session: Session, action: () -> Unit) {
        try { action() }
        catch (error: Exception) {
            HookLog.w(TAG, "Predictive rendering interrupted; finishing native transition", error)
            finish(session)
        }
    }

    private fun finish(session: Session) {
        val extension = session.native.extension.get(session.owner)
        if (session.finishing || synchronized(sessions) { sessions[extension] !== session }) return
        session.finishing = true
        runCatching { session.native.finish.invoke(session.owner) }
            .onFailure { HookLog.w(TAG, "Predictive transition completion unavailable", it) }
        synchronized(sessions) { if (sessions[extension] === session) sessions.remove(extension) }
    }

    private class NativeAnimation(loader: ClassLoader, val task: Boolean) {
        private var progressLogged = false
        val type = loader.loadClass(PACKAGE + if (task) "CrossTaskBackAnimation" else "CrossActivityBackAnimation")
        val callback = loader.loadClass(type.name + "\$Callback")
        val callbackOwner = field(callback, "this\$0")
        val extension = field(type, if (task) "mBackAnimationExt" else "backAnimationExt")
        val progress = field(type, if (task) "mProgressAnimator" else "progressAnimator")
        val cancel = method(progress.type, "onBackCancelled", Runnable::class.java)
        val reset = method(progress.type, "reset")
        val finish = method(type, "finishAnimation")
        val commit = if (task) method(type, "onGestureCommitted")
            else method(type, "onGestureCommitted", Float::class.javaPrimitiveType!!)
        private fun nativeField(activity: String, tasks: String = "m" + activity.replaceFirstChar { it.uppercase() }) =
            field(type, if (task) tasks else activity)
        private val closing = nativeField("closingTarget")
        private val entering = nativeField("enteringTarget")
        private val leash = closing.type.getField("leash")
        private val currentClosing = nativeField("currentClosingRect", "mClosingCurrentRect")
        private val currentEntering = nativeField("currentEnteringRect", "mEnteringCurrentRect")
        private val transaction = nativeField("transaction")
        private val applyTransaction = method(type, "applyTransaction")
        private val scrim = nativeField("scrimLayer")
        private val maxAlpha = nativeField("maxScrimAlpha")
        private val background = nativeField("background")
        private val backgroundSurface = field(background.type, "mBackgroundSurface")
        private val relativeLayer = method(SurfaceControl.Transaction::class.java, "setRelativeLayer",
            SurfaceControl::class.java, SurfaceControl::class.java, Int::class.javaPrimitiveType!!)
        private val corner = nativeField("cornerRadius")
        private val gestureProgress = nativeField("gestureProgress")
        private val startClosing = if (task) null else field(type, "startClosingRect")
        private val startEntering = if (task) null else field(type, "startEnteringRect")
        private val targetClosing = if (task) null else field(type, "targetClosingRect")
        private val targetEntering = if (task) null else field(type, "targetEnteringRect")
        private val defaultType = if (task) null else loader.loadClass(PACKAGE + "DefaultCrossActivityBackAnimation")
        private val postInterpolator = defaultType?.let { field(it, "postCommitInterpolator") }
        private val flingType = if (task) null else loader.loadClass(type.name + "\$FlingMode")
        private val noFling = flingType?.enumConstants?.single { (it as Enum<*>).name == "NO_FLING" }
        private val bounce = flingType?.enumConstants?.single { (it as Enum<*>).name == "FLING_BOUNCE" }
        private val transform = if (task) method(type, "applyTransform", SurfaceControl::class.java,
            RectF::class.java, Float::class.javaPrimitiveType!!)
        else method(type, "applyTransform", SurfaceControl::class.java, RectF::class.java, Float::class.javaPrimitiveType!!,
            loader.loadClass("android.view.animation.Transformation"), flingType!!)
        private val closeCommit = if (task) method(type, "updatePostCommitClosingAnimation", Float::class.javaPrimitiveType!!) else null
        private val enterCommit = if (task) method(type, "updatePostCommitEnteringAnimation", Float::class.javaPrimitiveType!!) else null

        fun supports(owner: Any) = task || owner.javaClass == defaultType
        fun deoptimizeCallers() {
            listOfNotNull(type, defaultType, callback).flatMap { it.declaredMethods.toList() }.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
        }
        private fun surface(target: Any?) = target?.let { leash.get(it) as? SurfaceControl }?.takeIf { it.isValid }

        private fun orderSurfaces(owner: Any, close: SurfaceControl, enter: SurfaceControl, committed: Boolean = false) {
            if (close === enter) return
            val tx = transaction.get(owner) as SurfaceControl.Transaction
            // Keep the app leashes as independent siblings. A relative link to the other
            // app leash can place this card below that leash's positive-Z app children.
            val front = if (task && committed) enter else close
            val back = if (task && committed) close else enter
            tx.setLayer(back, 0).setLayer(front, 1)
            (backgroundSurface.get(background.get(owner)) as? SurfaceControl)?.takeIf { it.isValid }?.let {
                relativeLayer.invoke(tx, it, back, -2)
            }
            (scrim.get(owner) as? SurfaceControl)?.takeIf { it.isValid }?.let {
                relativeLayer.invoke(tx, it, close, -1)
            }
        }

        fun renderProgress(owner: Any) {
            val closeSurface = surface(closing.get(owner)) ?: return
            val enterSurface = surface(entering.get(owner)) ?: return
            val close = currentClosing.get(owner) as RectF
            val enter = currentEntering.get(owner) as RectF
            if (close.isEmpty) return
            orderSurfaces(owner, closeSurface, enterSurface)
            if (task) {
                transform.invoke(owner, closeSurface, close, corner.getFloat(owner))
                transform.invoke(owner, enterSurface, enter, corner.getFloat(owner))
            } else {
                val fraction = gestureProgress.getFloat(owner)
                interpolate(enter, startEntering!!.get(owner) as RectF, targetEntering!!.get(owner) as RectF, fraction)
                val start = startClosing!!.get(owner) as RectF
                val target = targetClosing!!.get(owner) as RectF
                enter.offset(0f, close.centerY() - (start.centerY() + (target.centerY() - start.centerY()) * fraction))
                transform.invoke(owner, closeSurface, close, 1f, null, noFling)
                transform.invoke(owner, enterSurface, enter, 1f, null, noFling)
            }
            applyTransaction.invoke(owner)
            if (!progressLogged) {
                progressLogged = true
                HookLog.i(TAG, "AOSP predictive ${if (task) "task" else "activity"} frame applied")
            }
        }

        fun renderCommit(owner: Any, fraction: Float) {
            val closeSurface = surface(closing.get(owner)) ?: return
            val enterSurface = surface(entering.get(owner)) ?: return
            val animated = if (task) fraction else (postInterpolator!!.get(owner) as Interpolator).getInterpolation(fraction)
            if (task) {
                closeCommit!!.invoke(owner, animated)
                enterCommit!!.invoke(owner, animated)
            } else {
                val close = currentClosing.get(owner) as RectF
                val enter = currentEntering.get(owner) as RectF
                interpolate(close, startClosing!!.get(owner) as RectF, targetClosing!!.get(owner) as RectF, animated)
                interpolate(enter, startEntering!!.get(owner) as RectF, targetEntering!!.get(owner) as RectF, animated)
                transform.invoke(owner, closeSurface, close, BackGestureRules.closingAlpha(fraction), null, bounce)
                transform.invoke(owner, enterSurface, enter, 1f, null, bounce)
            }
            val overlay = scrim.get(owner) as? SurfaceControl
            if (overlay?.isValid == true) {
                (transaction.get(owner) as SurfaceControl.Transaction).setAlpha(overlay,
                    (maxAlpha.getFloat(owner) * (1f - fraction)).coerceIn(0f, 1f))
            }
            orderSurfaces(owner, closeSurface, enterSurface, committed = true)
            applyTransaction.invoke(owner)
        }

        private fun interpolate(out: RectF, start: RectF, end: RectF, fraction: Float) {
            out.set(start.left + (end.left - start.left) * fraction, start.top + (end.top - start.top) * fraction,
                start.right + (end.right - start.right) * fraction, start.bottom + (end.bottom - start.bottom) * fraction)
        }
    }
}
