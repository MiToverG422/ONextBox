package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

// Launcher Dock background and system blur.
internal object LauncherDockHooker {
    private const val TAG = "LauncherDock"
    private val creatingBackground = ThreadLocal<Boolean>()

    fun hook(loader: ClassLoader) {
        if (!LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) return
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        runCatching {
            val hotseat = ModernReflect.findClass("com.android.launcher3.OplusHotseat", loader)
            val setBackground = hotseat.getDeclaredMethod("setDockerBackground").apply { isAccessible = true }
            val background = hotseat.getDeclaredField("mDockerBackgroundDrawable").apply { isAccessible = true }
            val screen = ModernReflect.findClass("com.android.common.util.ScreenUtils", loader)
            val blurProperties = ModernReflect.findClass("com.android.launcher3.uioverrides.states.blurdrawable.OplusBlurProperties", loader)
            // Size checks in the background method may be inlined.
            if (!ModernHookRuntime.requireModule().deoptimize(setBackground)) {
                HookLog.w(TAG, "Dock background caller deoptimization unavailable")
            }
            ModernHookRegistry.installFast(
                "${prefix}background", setBackground,
                XposedInterface.Hooker { chain ->
                    if (!LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) return@Hooker chain.proceed()
                    val previous = creatingBackground.get()
                    creatingBackground.set(true)
                    try { chain.proceed() } finally {
                        if (previous == null) creatingBackground.remove() else creatingBackground.set(previous)
                    }
                },
            )
            ModernHookRegistry.installFast(
                "${prefix}blur-size-gate", screen.getDeclaredMethod("hasLargeDisplayFeatures"),
                XposedInterface.Hooker { chain ->
                    if (creatingBackground.get() == true && blurEnabled()) true else chain.proceed()
                },
            )
            ModernHookRegistry.installFast(
                "${prefix}blur-option", blurProperties.getDeclaredMethod("isSupportNewBlur", Context::class.java),
                XposedInterface.Hooker { chain ->
                    if (creatingBackground.get() == true && !blurEnabled()) false else chain.proceed()
                },
            )
            ModernHookRegistry.installFast(
                "${prefix}fold-background", hotseat.getDeclaredMethod("updateDockerBgIfNecessary"),
                XposedInterface.Hooker { chain ->
                    if (!LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) return@Hooker chain.proceed()
                    runCatching {
                        if (background.get(chain.thisObject) == null) setBackground.invoke(chain.thisObject)
                    }.onFailure { HookLog.w(TAG, "Unable to restore Dock background", it) }
                    null
                },
            )
            listOf(
                hotseat.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                hotseat.getDeclaredMethod("onWallpaperBrightnessChanged"),
            ).forEach { method ->
                ModernHookRegistry.installFast(
                    "$prefix${method.name}", method,
                    XposedInterface.Hooker { chain ->
                        val result = chain.proceed()
                        val view = chain.thisObject as? View
                        if (view != null && view.measuredWidth > 0 && view.measuredHeight > 0 &&
                            LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)
                        ) runCatching { setBackground.invoke(view) }
                            .onFailure { HookLog.w(TAG, "Unable to refresh Dock background", it) }
                        result
                    },
                )
            }
            HookLog.i(TAG, "Dock hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Dock hooks unavailable", it)
        }
    }

    private fun blurEnabled() = LauncherFeatureRules.dockBlur(
        LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock),
        LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.DockBlur),
    )
}
