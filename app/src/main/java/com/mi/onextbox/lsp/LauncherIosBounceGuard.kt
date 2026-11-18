package com.mi.onextbox.lsp

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.RectF
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

internal class LauncherIosBounceGuard(private val base: LauncherIosMotionTargets, private val scope: String,
    private val directWrites: ThreadLocal<Boolean>) {
    private val lifecycle = LauncherIosBounceLifecycle<Any>()
    private val logged = ConcurrentHashMap.newKeySet<String>()
    private val creatingHome = ThreadLocal<Long?>()
    @Volatile private var failed = false
    private val startRect = field(base.spring, "d", RectF::class.java)
    private val targetRect = field(base.spring, "e", RectF::class.java)
    private val inherited = field(base.spring, "c1", Boolean::class.javaPrimitiveType!!)
    private val widthMode = field(base.spring, "H", Boolean::class.javaPrimitiveType!!)
    private val forceClass = base.forceSetters.first().declaringClass
    private val forceTarget = field(forceClass, "i", Double::class.javaPrimitiveType!!)
    private val animationFields = base.spring.declaredFields.groupBy { it.type }.entries.single { (type, fields) ->
        fields.size == 7 && type.declaredFields.any { it.type == forceClass }
    }.value.onEach { it.isAccessible = true }
    private val animationClass = animationFields.first().type
    private val forceLink = animationClass.declaredFields.single { it.type == forceClass }.apply { isAccessible = true }
    private val channelName = animationClass.declaredFields.single { it.type == String::class.java }.apply { isAccessible = true }
    private val position = field(animationClass, "a", Float::class.javaPrimitiveType!!)
    private val velocity = field(animationClass, "b", Float::class.javaPrimitiveType!!)
    private val startVelocities = listOf("k", "s", "A", "K", "S", "a0", "j0").map {
        field(base.spring, it, Float::class.javaPrimitiveType!!)
    }
    private val copyMethod = base.spring.declaredMethods.single { method ->
        method.returnType == base.spring && method.parameterCount == 8 &&
            method.parameterTypes.take(3) == listOf(RectF::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType) &&
            method.parameterTypes.takeLast(4).all { it == Boolean::class.javaPrimitiveType }
    }.apply { isAccessible = true }
    private val retargetMethod = base.spring.declaredMethods.single { method ->
        method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(RectF::class.java, Float::class.javaPrimitiveType))
    }.apply { isAccessible = true }

    private fun active() = !failed && ValueAnimator.areAnimatorsEnabled() && LauncherIosMotionSettings.active() &&
        LauncherIosMotionSettings.active(LauncherIosMotionSettings.BOUNCE) &&
        LauncherIosMotionSettings.value(LauncherIosMotionSettings.bounce) > 0 &&
        LauncherIosMotionHooker.allowed(LauncherIosMotionProfile.RouteKind.SwipeToHome)

    private fun kind(owner: Any) = (base.springKind.get(owner) as? Enum<*>)?.name.orEmpty()
    private fun area(rect: RectF) = rect.width().toDouble() * rect.height()
    private fun startArea(owner: Any) = area(startRect.get(owner) as RectF)
    private fun isHomeSpring(owner: Any) = lifecycle.wasHome(owner) ||
        LauncherIosMotionProfile.classify(kind(owner)) == LauncherIosMotionProfile.RouteKind.SwipeToHome

    fun install() {
        ModernHookRegistry.installFast("$scope:copy", copyMethod, XposedInterface.Hooker { chain ->
            if (!active()) return@Hooker chain.proceed()
            val owner = chain.thisObject ?: return@Hooker chain.proceed()
            val next = chain.getArg(0) as? RectF ?: return@Hooker chain.proceed()
            val shouldRecover = guarded {
                isHomeSpring(owner) &&
                    LauncherIosBounceRecovery.opensWindow(startArea(owner), area(targetRect.get(owner) as RectF), area(next))
            } ?: false
            val result = chain.proceed()
            if (shouldRecover && result != null) guarded {
                lifecycle.open(result, seedFromBounce = true)
                if (logged.add("copy")) HookLog.i("LauncherIosMotion", "Return-home bounce handed off to a non-bouncing opening continuation")
            }
            result
        })
        ModernHookRuntime.requireModule().deoptimize(copyMethod)
        ModernHookRegistry.installFast("$scope:retarget", retargetMethod, XposedInterface.Hooker { chain ->
            if (!active()) return@Hooker chain.proceed()
            val owner = chain.thisObject ?: return@Hooker chain.proceed()
            val next = chain.getArg(0) as? RectF ?: return@Hooker chain.proceed()
            val action = guarded {
                val oldArea = targetArea(owner)
                val nextArea = area(next)
                when {
                    isHomeSpring(owner) && lifecycle.opening(owner) != true &&
                        LauncherIosBounceRecovery.opensWindow(startArea(owner), oldArea, nextArea) -> 1
                    lifecycle.opening(owner) == true && nextArea < oldArea * .5 -> -1
                    else -> 0
                }
            } ?: 0
            val result = chain.proceed()
            if (action != 0) guarded {
                if (action > 0) {
                    reverseHomeSpring(owner)
                } else {
                    lifecycle.home(owner)
                    val config = LauncherIosMotionHooker.config().let {
                        if (lifecycle.allows(owner)) it else it.copy(bounceStrength = 0)
                    }
                    LauncherIosMotionProfile.spring(kind(owner), config, lifecycle.route(owner))?.let { tune(owner, it, false) }
                }
            }
            result
        })
        ModernHookRuntime.requireModule().deoptimize(retargetMethod)
        installLaunchBoundary()
        installInPlaceReverse()
        installDesktopLifecycle()
        installHomeRequestBoundaries()
        HotReloadLifecycle.register(scope, detach = {
            guarded { stopPending(lifecycle.leaveDesktop()) }
            failed = true; lifecycle.clear()
        })
        HookLog.i("LauncherIosMotion", "Return-home bounce recovery installed: ${copyMethod.name}/${retargetMethod.name}")
    }

    fun bind(owner: Any, route: LauncherIosMotionProfile.RouteKind) = guarded {
        val pending = lifecycle.bind(owner, route, creatingHome.get())
        if (active()) stopPending(pending)
        if (active() && route == LauncherIosMotionProfile.RouteKind.SwipeToHome && lifecycle.superseded(owner))
            stopPending(listOf(owner))
        if (pending.isNotEmpty() && active() && logged.add("pending"))
            HookLog.i("LauncherIosMotion", "Pending return-home bounce stopped at app entry: springs=${pending.size}")
        if (route == LauncherIosMotionProfile.RouteKind.Opening && logged.add("opening-route"))
            HookLog.i("LauncherIosMotion", "Opening listener excludes pending return-home bounce")
    }

    fun allowsReturnBounce(owner: Any?): Boolean = owner != null && !failed && lifecycle.allows(owner)
    fun route(owner: Any?): LauncherIosMotionProfile.RouteKind? = if (owner == null || failed) null else lifecycle.route(owner)

    fun openingSpec(owner: Any?): LauncherIosMotionProfile.SpringSpec? {
        if (owner == null || !active() || !LauncherIosMotionHooker.allowed(LauncherIosMotionProfile.RouteKind.Opening)) return null
        return guarded {
            val opening = lifecycle.opening(owner) ?:
                (inherited.getBoolean(owner) && LauncherIosMotionProfile.classify(kind(owner)) == LauncherIosMotionProfile.RouteKind.Opening)
            if (opening) LauncherIosMotionProfile.spring("SWIPE_BREAK_LAUNCH", LauncherIosMotionHooker.config()) else null
        }
    }

    fun beforeInitialized(owner: Any?) {
        if (owner == null || !active() || !lifecycle.takeBounceSeed(owner)) return
        guarded { startVelocities.forEach { it.setFloat(owner, 0f) } }
    }

    fun onInitialized(owner: Any?) {
        if (owner != null && active() && lifecycle.superseded(owner) && lifecycle.opening(owner) != true) {
            guarded {
                LauncherIosMotionProfile.spring("SWIPE_TO_HOME", LauncherIosMotionHooker.config().copy(bounceStrength = 0))
                    ?.let { tune(owner, it, true, true) }
            }
            return
        }
        val spec = openingSpec(owner) ?: return
        guarded {
            lifecycle.open(owner!!)
            tune(owner!!, spec, true)
        }
    }

    private fun tune(owner: Any, spec: LauncherIosMotionProfile.SpringSpec, removeBounce: Boolean, stopVelocity: Boolean = false) {
        var changed = 0
        val response = 2.0 * kotlin.math.PI / kotlin.math.sqrt(spec.stiffness.toDouble())
        val previous = directWrites.get()
        directWrites.set(true)
        try { for (entry in animationFields) {
            val animation = entry.get(owner) ?: continue
            val force = forceLink.get(animation) ?: continue
            val end = forceTarget.getDouble(force).toFloat()
            val value = position.getFloat(animation)
            if (!end.isFinite() || !value.isFinite()) continue
            base.forceSetters[0].invoke(force, spec.damping)
            base.forceSetters[1].invoke(force, spec.stiffness)
            if (removeBounce) {
                val before = velocity.getFloat(animation)
                val after = if (stopVelocity && before.isFinite()) 0f else LauncherIosBounceRecovery.velocity(value, end, before, response)
                if (before != after) { velocity.setFloat(animation, after); changed++ }
            }
        } } finally { if (previous == null) directWrites.remove() else directWrites.set(previous) }
        if (changed > 0 && logged.add("velocity")) HookLog.i("LauncherIosMotion", "Pending return-home bounce removed from opening velocity: channels=$changed")
    }

    private fun stopPending(owners: List<Any>) {
        if (owners.isEmpty()) return
        val config = LauncherIosMotionHooker.config().copy(bounceStrength = 0)
        val spec = LauncherIosMotionProfile.spring("SWIPE_TO_HOME", config) ?: return
        owners.forEach { tune(it, spec, true, true) }
    }

    private fun installLaunchBoundary() {
        val launcher = HostClassLookup.load("com.android.launcher.Launcher", base.loader)
        val start = launcher.declaredMethods.single { method ->
            method.name == "startActivitySafely" && method.parameterTypes.take(2) ==
                listOf(View::class.java, Intent::class.java) && method.parameterCount == 3 &&
                method.returnType == Boolean::class.javaPrimitiveType
        }.apply { isAccessible = true }
        ModernHookRegistry.installFast("$scope:launch-request", start, XposedInterface.Hooker { chain ->
            if (active()) guarded {
                val pending = lifecycle.revokeHomeBounces()
                stopPending(pending)
                if (pending.isNotEmpty() && logged.add("launch-request"))
                    HookLog.i("LauncherIosMotion", "Home bounce revoked before launcher start request")
            }
            chain.proceed()
        })
        ModernHookRuntime.requireModule().deoptimize(start)
    }

    private fun installInPlaceReverse() {
        val reverse = LauncherDexResolver.method(base.loader, "ios-bounce-in-place-reverse-v1") { dex ->
            dex.findMethod {
                matcher {
                    name = "run"
                    paramCount = 0
                    returnType = "void"
                    usingStrings("reverseToOpen, curRectF: ")
                }
            }.single()
        }
        val spring = reverse.declaringClass.declaredFields.single { it.type == base.spring }
            .apply { isAccessible = true }
        ModernHookRegistry.installFast("$scope:in-place-reverse", reverse, XposedInterface.Hooker { chain ->
            if (active()) guarded {
                val owner = spring.get(chain.thisObject) ?: return@guarded
                if (isHomeSpring(owner) && lifecycle.opening(owner) != true) {
                    reverseHomeSpring(owner)
                    if (logged.add("in-place-reverse"))
                        HookLog.i("LauncherIosMotion", "Home bounce isolated before native in-place opening")
                }
            }
            chain.proceed()
        })
        ModernHookRuntime.requireModule().deoptimize(reverse)
    }

    private fun installHomeRequestBoundaries() {
        listOf("SwipeToHomeAnimCreator", "SwipeToClientHomeAnimCreator").forEach { name ->
            runCatching {
                val creator = HostClassLookup.load("com.android.launcher3.anim.engine.creator.$name", base.loader)
                val create = creator.declaredMethods.single { method ->
                    method.name == "create" && !method.isBridge && method.parameterCount == 2 &&
                        method.parameterTypes[1] == View::class.java
                }
                ModernHookRegistry.installFast("$scope:home-request:$name", create, XposedInterface.Hooker { chain ->
                    if (!active()) return@Hooker chain.proceed()
                    val previous = creatingHome.get()
                    creatingHome.set(lifecycle.homeRequestGeneration())
                    try {
                        chain.proceed()
                    } finally {
                        if (previous == null) creatingHome.remove() else creatingHome.set(previous)
                    }
                })
                ModernHookRuntime.requireModule().deoptimize(create)
            }.onFailure {
                ModernHookRegistry.unhook("$scope:home-request:$name")
                HookLog.w("LauncherIosMotion", "Native return-home request boundary unavailable: $name", it)
            }
        }
    }

    private fun reverseHomeSpring(owner: Any) {
        stopPending(lifecycle.leaveDesktop())
        lifecycle.open(owner)
        val spec = openingSpec(owner) ?: LauncherIosMotionProfile.spring(
            "SWIPE_TO_HOME", LauncherIosMotionHooker.config().copy(bounceStrength = 0),
        ) ?: return
        tune(owner, spec, true, true)
    }

    private fun installDesktopLifecycle() {
        val launcher = HostClassLookup.load("com.android.launcher.Launcher", base.loader)
        for (name in listOf("onStart", "onResume", "onPause", "onStop", "onWindowFocusChanged")) {
            val params = if (name == "onWindowFocusChanged") arrayOf(Boolean::class.javaPrimitiveType!!) else emptyArray()
            val method = launcher.getDeclaredMethod(name, *params).apply { isAccessible = true }
            ModernHookRegistry.installFast("$scope:desktop:$name", method, XposedInterface.Hooker { chain ->
                val starts = name == "onStart" || name == "onResume" ||
                    name == "onWindowFocusChanged" && chain.getArg(0) == true
                guarded {
                    if (starts) lifecycle.startDesktop()
                    else {
                        val pending = lifecycle.leaveDesktop()
                        if (active()) stopPending(pending)
                        if (pending.isNotEmpty() && logged.add("desktop-left"))
                            HookLog.i("LauncherIosMotion", "Desktop left foreground; queued return-home bounces expired")
                    }
                }
                val result = chain.proceed()
                if (starts && name != "onStart") guarded {
                    val pending = lifecycle.resumeDesktop()
                    if (active()) {
                        val spec = LauncherIosMotionProfile.spring("SWIPE_TO_HOME", LauncherIosMotionHooker.config())
                        if (spec != null) pending.forEach { tune(it, spec, false) }
                    }
                }
                result
            })
            ModernHookRuntime.requireModule().deoptimize(method)
        }
    }

    private fun targetArea(owner: Any): Double {
        var size = Double.NaN
        var ratio = Double.NaN
        for (entry in animationFields) {
            val animation = entry.get(owner) ?: continue
            val force = forceLink.get(animation) ?: continue
            when (channelName.get(animation)) {
                "width" -> size = forceTarget.getDouble(force)
                "radio" -> ratio = forceTarget.getDouble(force)
            }
        }
        return if (widthMode.getBoolean(owner)) size * size * ratio else size * size / ratio
    }

    private fun <T> guarded(block: () -> T): T? = try { block() } catch (error: Throwable) {
        if (!failed) { failed = true; lifecycle.clear()
            HookLog.w("LauncherIosMotion", "Bounce recovery unavailable; existing launcher animation retained", error) }
        null
    }

    private fun field(owner: Class<*>, name: String, type: Class<*>): Field = owner.getDeclaredField(name).apply {
        require(this.type == type); isAccessible = true
    }
}
