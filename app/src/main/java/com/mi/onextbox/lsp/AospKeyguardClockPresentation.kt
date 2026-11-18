package com.mi.onextbox.lsp

import android.os.PowerManager
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference

internal class AospKeyguardClockPresentation(loader: ClassLoader) {
    val oemClock = HostClassLookup.load(
        "com.android.systemui.keyguard.ui.view.layout.sections.OplusClockSection", false, loader,
    )
    val oemBottom = HostClassLookup.load(
        "com.oplus.systemui.keyguard.ui.view.sections.OplusDefaultBottomAreaSection", false, loader,
    )
    private val oemClockView = oemClock.getDeclaredField("keyguardStyleClock").apply { isAccessible = true }
    private val wakefulness = HostClassLookup.load("com.android.systemui.keyguard.WakefulnessLifecycle", false, loader)
    private val styleControllerField = oemClockView.type.getDeclaredField("keyguardStyleClockController").apply { isAccessible = true }
    private val innerAlpha = oemClockView.type.getMethod("getInnerAlpha")
    private val styleController = HostClassLookup.load(
        "com.oplus.systemui.keyguard.clockstyle.KeyguardStyleClockControllerImpl", false, loader,
    )
    private val setStyleAlpha = styleController.getMethod("setKeyguardStyleClockAlpha", String::class.java, Float::class.javaPrimitiveType)
    private data class ClockViews(val original: WeakReference<View>, val native: List<WeakReference<View>>)
    private val clockViews = java.util.WeakHashMap<Any, ClockViews>()
    private var activeViews: ClockViews? = null
    private var sleeping: Boolean? = null
    private var ambientAttached = false
    private var coveredController: Any? = null
    private var lastStyleAlpha = 1f

    fun showAmbientClock(show: Boolean) {
        if (show == ambientAttached) return
        ambientAttached = show
        if (sleeping == true) updateVisibility(true)
        if (show) coverOriginalClock() else restoreOriginalClock()
    }

    fun prepare(nativeSection: Any, hostSection: Any, nativeViews: List<View>) {
        val original = oemClockView.get(hostSection) as View
        clockViews[nativeSection] = ClockViews(WeakReference(original), nativeViews.map { WeakReference(it) })
    }

    fun install(nativeClock: Class<*>, root: Class<*>) {
        ModernHookRegistry.installFast("aosp-clock:ambient-style-alpha", setStyleAlpha, XposedInterface.Hooker { chain ->
            if (chain.thisObject !== coveredController || coveredController == null || chain.getArg(0) == AMBIENT_ALPHA_REASON) {
                chain.proceed()
            } else {
                lastStyleAlpha = chain.getArg(1) as Float
                chain.proceed(arrayOf(chain.getArg(0), 0f))
            }
        })
        for ((methodName, enteringSleep) in listOf("dispatchStartedGoingToSleep" to true, "dispatchStartedWakingUp" to false)) {
            val method = wakefulness.getMethod(methodName, Int::class.javaPrimitiveType)
            ModernHookRegistry.installFast("aosp-clock:$methodName", method, XposedInterface.Hooker { chain ->
                sleeping = enteringSleep
                if (!enteringSleep) showAmbientClock(false)
                updateVisibility(enteringSleep)
                chain.proceed()
            })
        }
        val bind = nativeClock.getMethod("bindData", root)
        ModernHookRegistry.installFast("aosp-clock:oem-presentation-bind", bind, XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            val views = clockViews[chain.thisObject] ?: return@Hooker result
            val original = views.original.get() ?: return@Hooker result
            activeViews = views
            val isSleeping = sleeping ?: (original.context.getSystemService(PowerManager::class.java)?.isInteractive == false)
            sleeping = isSleeping
            updateVisibility(isSleeping)
            if (ambientAttached) coverOriginalClock()
            result
        })
        val remove = nativeClock.getMethod("removeViews", root)
        ModernHookRegistry.installFast("aosp-clock:oem-presentation-remove", remove, XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            val views = clockViews[chain.thisObject]
            if (views != null && views === activeViews) {
                views.original.get()?.setTransitionAlpha(1f)
                views.native.forEach { it.get()?.setTransitionAlpha(1f) }
                activeViews = null
                restoreOriginalClock()
            }
            result
        })
    }

    private fun updateVisibility(isSleeping: Boolean) {
        val views = activeViews ?: return
        views.original.get()?.setTransitionAlpha(if (isSleeping && !ambientAttached) 1f else 0f)
        views.native.forEach { it.get()?.setTransitionAlpha(if (isSleeping) 0f else 1f) }
    }

    private fun coverOriginalClock() {
        if (coveredController != null || sleeping != true) return
        val view = activeViews?.original?.get() ?: return
        val owner = styleControllerField.get(view)?.takeIf(styleController::isInstance) ?: return
        runCatching {
            lastStyleAlpha = (innerAlpha.invoke(view) as Number).toFloat()
            setStyleAlpha.invoke(owner, AMBIENT_ALPHA_REASON, 0f)
            coveredController = owner
        }.onFailure { HookLog.w("ONextBox-AospClock", "Could not hide ColorOS ambient clock artwork", it) }
    }

    private fun restoreOriginalClock() {
        val owner = coveredController ?: return
        coveredController = null
        runCatching { setStyleAlpha.invoke(owner, AMBIENT_ALPHA_REASON, lastStyleAlpha) }
            .onFailure { HookLog.w("ONextBox-AospClock", "Could not restore ColorOS ambient clock artwork", it) }
    }

    fun bottomArea(loader: ClassLoader): Any? = runCatching {
        val dependencyType = HostClassLookup.load("com.android.systemui.DependencyEx", false, loader)
        val keyguard = HostClassLookup.load("com.android.keyguard.OplusKeyguardDependencyEx", false, loader)
        val dependency = dependencyType.getField("sDependency").get(null) ?: return null
        val keyguardDependency = dependencyType.getMethod("getDependency", Class::class.java)
            .invoke(dependency, keyguard) ?: return null
        val provider = ModernReflect.callMethod(keyguardDependency, "getOplusKeyguardSection") ?: return null
        ModernReflect.callMethod(provider, "getKeyguardBottomAreaSection")?.takeIf(oemBottom::isInstance)
    }.getOrNull()

    private companion object {
        const val AMBIENT_ALPHA_REASON = "ONextBoxAmbientClock"
    }
}
