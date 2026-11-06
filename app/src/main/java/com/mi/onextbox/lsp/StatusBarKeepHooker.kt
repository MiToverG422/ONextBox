package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicBoolean

internal object StatusBarKeepHooker {
    private const val TAG = "ONextBox-StatusBarKeep"
    private const val VM = "com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModelImpl"

    fun hook(classLoader: ClassLoader) {
        val adapter = runCatching { StatusBarVisibilityAdapter.load(classLoader) }.getOrElse {
            HookLog.w(TAG, "Status bar visibility contract unavailable; stock behavior retained", it)
            return
        }
        var installed = 0
        // The operator combiner returns Boolean; the other three return VisibilityModel.
        // Modify only a copied disable flag and let each native combiner produce its own result.
        for ((suffix, count, flagIndex, flagName) in listOf(
            Target("isClockVisible", 3, 1, "isClockAllowed"),
            Target("isSystemInfoVisible", 3, 1, "isSystemInfoAllowed"),
            Target("shouldShowOperatorNameView", 4, 1, "isSystemInfoAllowed"),
            Target("isNotificationIconContainerVisible", 4, 2, "areNotificationIconsAllowed"),
        )) runCatching {
            val method = StatusBarVisibilityAdapter.combiner(classLoader, "$VM\$${suffix}\$1", count)
            val healthy = AtomicBoolean(true)
            ModernHookRegistry.installFast("$TAG@${System.identityHashCode(classLoader)}:$suffix", method,
                XposedInterface.Hooker { chain ->
                    val args = chain.args
                    if (!healthy.get() || !StatusBarKeepRules.visible(
                            StatusBarKeepSettings.isEnabledXposed(), args.firstOrNull() == true, false,
                        )) return@Hooker chain.proceed()
                    val replacement = runCatching {
                        val original = args.getOrNull(flagIndex) ?: return@runCatching null
                        val flags = adapter.allow(original, flagName)
                        if (flags === original) null else args.toTypedArray().also { it[flagIndex] = flags }
                    }.onFailure {
                        if (healthy.compareAndSet(true, false)) {
                            HookLog.w(TAG, "$suffix input changed; stock behavior retained", it)
                        }
                    }.getOrNull()
                    if (replacement == null) chain.proceed() else chain.proceed(replacement)
                })
            method.declaringClass.declaredMethods.filter { it.name == "invokeSuspend" }.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
            installed++
        }.onFailure { HookLog.w(TAG, "$suffix adapter unavailable; other status bar adapters retained", it) }
        HookLog.i(TAG, "Overview status bar adapters installed: $installed/4")
    }

    private data class Target(val suffix: String, val count: Int, val flagIndex: Int, val flag: String)
}
