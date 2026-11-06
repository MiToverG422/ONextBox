package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicBoolean

internal object FluidCloudRecentsHooker {
    private const val TAG = "ONextBox-FluidRecents"
    private const val VIEW_MODEL =
        "com.oplus.systemui.statusbar.pipeline.shared.ui.viewmodel.OplusHomeStatusBarViewModelImpl\$isSeedlingContainerVisible\$1"

    fun hook(classLoader: ClassLoader) {
        val key = "$TAG@${System.identityHashCode(classLoader)}:"
        runCatching {
            val adapter = StatusBarVisibilityAdapter.load(classLoader)
            val combine = StatusBarVisibilityAdapter.combiner(classLoader, VIEW_MODEL, 6)
            val healthy = AtomicBoolean(true)
            ModernHookRegistry.installFast("${key}capsule", combine, XposedInterface.Hooker { chain ->
                if (!healthy.get() || NativeLiveUpdateSettings.active()) return@Hooker chain.proceed()
                val enabled = FluidCloudRecentsSettings.isEnabledXposed()
                val statusBarKept = StatusBarKeepSettings.isEnabledXposed()
                if (!enabled && !statusBarKept) return@Hooker chain.proceed()
                val args = chain.args
                val replacement = runCatching {
                    val visibility = args.getOrNull(0) ?: return@runCatching null
                    val flags = args.getOrNull(3) ?: return@runCatching null
                    val original = adapter.visibility(visibility)
                    val desired = FluidCloudRecentsRules.keepCapsuleVisible(
                        enabled = enabled,
                        visibility = original,
                        canShowOngoingActivityChips = args.getOrNull(1) as? Boolean ?: return@runCatching null,
                        miniCapsuleShowing = args.getOrNull(2) as? Boolean ?: return@runCatching null,
                        fullscreenPrivacyIcon = args.getOrNull(4) as? Boolean ?: return@runCatching null,
                        notificationIconsAllowed = adapter.notificationIconsAllowed(flags),
                        statusBarKept = statusBarKept,
                    )
                    if (desired == original) null else args.toTypedArray().also {
                        it[0] = adapter.withVisibility(visibility, desired)
                    }
                }.onFailure {
                    if (healthy.compareAndSet(true, false)) {
                        HookLog.w(TAG, "Capsule input changed; stock behavior retained", it)
                    }
                }.getOrNull()
                if (replacement == null) chain.proceed() else chain.proceed(replacement)
            })
            combine.declaringClass.declaredMethods.filter { it.name == "invokeSuspend" }.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
            HookLog.i(TAG, "Independent overview capsule adapter installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Overview capsule persistence unavailable; stock behavior retained", it)
        }
    }
}
