package com.mi.onextbox.lsp

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.mi.onextbox.lsp.LauncherBlurRules.Effect
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Reuse the launcher's blur controllers, transitions, observers and cleanup. */
internal object LauncherBlurHooker {
    private const val TAG = "LauncherBlur"

    fun hook(loader: ClassLoader, apkPath: String) {
        // Native views cache capabilities in constructors; switching requires a
        // launcher restart rather than rewriting already-live rendering state.
        if (!enabled()) return
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        val session = Session()
        runCatching {
            LauncherBlurTargets.withDex(apkPath) { dex ->
                install("${prefix}platform:") {
                    val native = LauncherBlurTargets.platform(dex, loader)
                    session.hook("${prefix}platform:unavailable", native.unavailable) { chain ->
                        if (session.allowed(Effect.Animation, chain.getArg(0) as? Context)) false else chain.proceed()
                    }
                    listOf(native.dynamic, native.support).forEach { method ->
                        session.hook("${prefix}platform:$method", method) { chain ->
                            val context = if (method.parameterCount == 1) chain.getArg(0) as? Context else null
                            if (session.allowed(Effect.Animation, context)) true
                            else chain.proceed()
                        }
                    }
                    val scope = LauncherBlurScope()
                    session.hook("${prefix}platform:tier", native.tier) { chain ->
                        if (enabled() && scope.isActive()) chain.getArg(0) == 1 else chain.proceed()
                    }
                    native.blurQueries.forEach { method ->
                        session.hook("${prefix}platform:query:$method", method) { chain ->
                            if (session.allowed(Effect.Material)) scope.within { chain.proceed() } else chain.proceed()
                        }
                    }
                    native.animationFlags.forEach { (method, forced) ->
                        session.hook("${prefix}platform:flag:$method", method) { chain ->
                            if (session.allowed(Effect.Animation)) forced else chain.proceed()
                        }
                    }
                    session.deoptimize(native.callers)
                    HookLog.i(TAG, "Native desktop blur/animation tier hooks installed (${native.dynamic.declaringClass.name})")
                }
                install("${prefix}menu:") {
                    val native = LauncherBlurTargets.menu(dex, loader)
                    val scope = LauncherBlurScope()
                    session.hook("${prefix}menu:builder", native.builder) { chain ->
                        if (enabled()) scope.within { chain.proceed() } else chain.proceed()
                    }
                    session.hook("${prefix}menu:tier", native.tier) { chain ->
                        if (enabled() && scope.isActive()) true else chain.proceed()
                    }
                    session.hook("${prefix}menu:material-tier", native.materialTier) { chain ->
                        // Registration must also run while blur/power settings
                        // are off, so native observers can disable AND re-enable
                        // the effect. Never force the material state itself.
                        if (enabled()) false else chain.proceed()
                    }
                    session.deoptimize(native.callers)
                    HookLog.i(TAG, "Native launcher menu blur tier hooks installed (${native.builder.declaringClass.name})")
                }
            }
        }.onFailure { HookLog.w(TAG, "Native blur discovery unavailable; stock behavior retained", it) }
    }

    private fun install(prefix: String, block: () -> Unit) {
        runCatching(block).onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "${prefix.substringAfterLast('@')} unavailable; stock behavior retained", it)
        }
    }

    private fun enabled() = !HotReloadLifecycle.isRetiring &&
        LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.ForceBlur)

    private class Session {
        private data class Snapshot(val time: Long, val state: LauncherBlurRules.State)
        @Volatile private var app: Context? = null
        @Volatile private var snapshot: Snapshot? = null
        private val reported = ConcurrentHashMap.newKeySet<String>()

        fun allowed(effect: Effect, context: Context? = null): Boolean {
            if (!enabled()) return false
            return runCatching {
                val current = context?.applicationContext ?: app ?: run {
                    val type = HostClassLookup.load("android.app.ActivityThread", false, Context::class.java.classLoader)
                    ModernReflect.callStaticMethod(type, "currentApplication") as? Context
                } ?: return@runCatching false
                app = current
                val now = SystemClock.uptimeMillis()
                val state = snapshot?.takeIf { now - it.time in 0L..100L }?.state ?: run {
                    val resolver = current.contentResolver
                    LauncherBlurRules.State(
                        animation = LauncherBlurRules.animationEnabled(Settings.System.getInt(resolver, "animationBlurrySwitch", -1)),
                        material = Settings.System.getInt(resolver, "system_material_blur_enable", 0) == 1,
                        lowPower = Settings.Global.getInt(resolver, "low_power", 0) != 0,
                        superPowerSave = Settings.System.getInt(resolver, "super_powersave_mode_state", 0) != 0,
                        windowBlursDisabled = Settings.Global.getInt(resolver, "disable_window_blurs", 0) != 0,
                    ).also { snapshot = Snapshot(now, it) }
                }
                state.allows(effect)
            }.onFailure { report("System blur policy unavailable", it) }.getOrDefault(false)
        }

        fun hook(key: String, method: Method, block: (XposedInterface.Chain) -> Any?) {
            ModernHookRegistry.installFast(key, method, XposedInterface.Hooker(block))
        }

        fun deoptimize(callers: List<Executable>) {
            callers.forEach { method ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onSuccess { if (!it) report("Caller deoptimization unavailable", IllegalStateException(method.toString())) }
                    .onFailure { report("Caller deoptimization unavailable", it) }
            }
        }

        private fun report(message: String, error: Throwable) {
            if (reported.add(message)) HookLog.w(TAG, message, error)
        }
    }
}
