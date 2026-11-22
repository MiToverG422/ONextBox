package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

// Launcher Dock background and system blur.
internal object LauncherDockHooker {
    private const val TAG = "LauncherDock"
    private val creatingBackground = ThreadLocal<Boolean>()
    private data class AppliedCorners(val radius: Float, val mode: Int)

    fun hook(loader: ClassLoader) {
        val prefix = "$TAG@${System.identityHashCode(loader)}:"
        val iconChanges = LauncherDockCorners.IconChanges()
        HotReloadLifecycle.register("${prefix}icon-setting",
            capture = iconChanges::attached, detach = iconChanges::close,
            restore = { saved -> (saved as? List<*>)?.filterIsInstance<View>()?.forEach { it.requestLayout() } },
        )
        if (!LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) return
        runCatching {
            val c16 = SystemAdaptationDetector.current() == SystemAdaptationVersion.ColorOs16
            val hotseat = ModernReflect.findClass("com.android.launcher3.OplusHotseat", loader)
            val setBackground = hotseat.getDeclaredMethod("setDockerBackground").apply { isAccessible = true }
            val background = if (c16) null else hotseat.getDeclaredField("mDockerBackgroundDrawable").apply { isAccessible = true }
            val shortcuts = hotseat.getMethod("getShortcutsAndWidgets")
            val appliedCorners = WeakHashMap<View, AppliedCorners>()
            val folderRadius = runCatching { LauncherDockCorners.nativeFolderFractionReader(hotseat, loader) }
                .onFailure { HookLog.w(TAG, "Dock folder radius lookup unavailable", it) }.getOrNull()
            fun wanted(view: View): Float? {
                if (LauncherDockCorners.mode() == LauncherDockCorners.SYSTEM) return null
                val height = (shortcuts.invoke(view) as? View)?.measuredHeight?.takeIf { it > 0 } ?: view.measuredHeight
                return LauncherDockCorners.radius(view, height, iconChanges, folderRadius)
            }
            fun record(view: View, radius: Float?) {
                if (radius == null) {
                    appliedCorners.remove(view)
                } else {
                    val mode = LauncherDockCorners.mode()
                    val previous = appliedCorners[view]
                    if (previous == null || previous.radius != radius || previous.mode != mode) {
                        appliedCorners[view] = AppliedCorners(radius, mode)
                    }
                }
            }
            val largeDisplay = LauncherDynamicTargets.largeDisplay(loader)
            require(largeDisplay.returnType == Boolean::class.javaPrimitiveType &&
                java.lang.reflect.Modifier.isStatic(largeDisplay.modifiers))
            val blurProperties = ModernReflect.findClass("com.android.launcher3.uioverrides.states.blurdrawable.OplusBlurProperties", loader)
            runCatching { hookDockMaterialCorners(loader, prefix, c16, blurProperties) }
                .onFailure { HookLog.w(TAG, "Dock material corners unavailable", it) }
            val blurOption = if (c16) blurProperties.getDeclaredMethod("isSupportNewBlur", Context::class.java,
                Boolean::class.javaPrimitiveType)
            else try {
                blurProperties.getDeclaredMethod("isSupportNewBlur", Context::class.java)
            } catch (_: NoSuchMethodException) {
                ModernReflect.findClass("${blurProperties.name}\$Companion", loader)
                    .getDeclaredMethod("isSupportNewBlur", Context::class.java)
            }
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
                    try {
                        val result = chain.proceed()
                        val view = chain.thisObject as View
                        if (LauncherDockCorners.mode() == LauncherDockCorners.SYSTEM) {
                            appliedCorners.remove(view)
                        } else {
                            val drawable = if (c16) (shortcuts.invoke(view) as? View)?.background else background?.get(view)
                            if (drawable is GradientDrawable) {
                                val radius = runCatching { wanted(view) }.getOrNull()
                                if (radius != null) (drawable.mutate() as GradientDrawable).cornerRadius = radius
                                record(view, radius)
                            }
                        }
                        result
                    } finally {
                        if (previous == null) creatingBackground.remove() else creatingBackground.set(previous)
                    }
                },
            )
            runCatching {
                val update = hotseat.getDeclaredMethod("updateBlurParams", Float::class.javaPrimitiveType)
                if (c16) hotseat.declaredMethods.firstOrNull { it.name == "createBlurDrawable" }?.let {
                    ModernHookRuntime.requireModule().deoptimize(it)
                }
                ModernHookRegistry.installFast("${prefix}blur-radius", update) { chain ->
                    if (!LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) return@installFast chain.proceed()
                    val view = chain.thisObject as View
                    val radius = runCatching { wanted(view) }.getOrNull()
                    record(view, radius)
                    if (radius == null) chain.proceed() else chain.proceed(arrayOf(radius))
                }
                val launcher = loader.loadClass("com.android.launcher.Launcher")
                val getHotseat = launcher.getMethod("getHotseat")
                ModernHookRegistry.installFast("${prefix}refresh-radius", launcher.getDeclaredMethod("onResume")) { chain ->
                    val result = chain.proceed()
                    if (LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) {
                        val dock = getHotseat.invoke(chain.thisObject) as? View
                        if (dock != null) {
                            val applied = appliedCorners[dock]
                            val radius = runCatching { wanted(dock) }.getOrNull()
                            if (radius != applied?.radius || (radius != null && applied?.mode != LauncherDockCorners.mode())) {
                                dock.requestLayout()
                            }
                        }
                    }
                    result
                }
            }.onFailure { HookLog.w(TAG, "Dock corner hooks unavailable", it) }
            ModernHookRegistry.installFast(
                "${prefix}blur-size-gate", largeDisplay,
                XposedInterface.Hooker { chain ->
                    if (creatingBackground.get() == true && blurEnabled()) true else chain.proceed()
                },
            )
            ModernHookRegistry.installFast(
                "${prefix}blur-option", blurOption,
                XposedInterface.Hooker { chain ->
                    if (creatingBackground.get() == true && !blurEnabled()) false else chain.proceed()
                },
            )
            ModernHookRegistry.installFast(
                "${prefix}fold-background", hotseat.getDeclaredMethod("updateDockerBgIfNecessary"),
                XposedInterface.Hooker { chain ->
                    if (!LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) return@Hooker chain.proceed()
                    runCatching {
                        val drawable = if (c16) (shortcuts.invoke(chain.thisObject) as? View)?.background
                            else background?.get(chain.thisObject)
                        if (drawable == null) setBackground.invoke(chain.thisObject)
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

    private fun hookDockMaterialCorners(loader: ClassLoader, prefix: String, c16: Boolean, blurProperties: Class<*>) {
        val packageName = blurProperties.packageName
        val info = loader.loadClass("com.android.launcher3.model.data.ItemInfo")
        val folder = info.getConstructor().newInstance()
        info.getMethod("setSpanXY", Integer.TYPE, Integer.TYPE).invoke(folder,
            LauncherDockCorners.FOLDER_COLUMNS, LauncherDockCorners.FOLDER_ROWS)
        val layer = loader.loadClass("$packageName.LayerBlurDrawable")
        if (c16) {
            val corners = layer.getDeclaredMethod("setCornerRadius", java.lang.Float.TYPE, java.lang.Boolean.TYPE,
                info, java.lang.Boolean.TYPE)
            blurProperties.declaredMethods.filter { it.name == "setBlurCornerRadius" }.forEach {
                ModernHookRuntime.requireModule().deoptimize(it)
            }
            ModernHookRegistry.installFast("${prefix}folder-material", corners) { chain ->
                if (chain.getArg(3) == true && LauncherDockCorners.mode() == LauncherDockCorners.ICON &&
                    LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) chain.proceed(arrayOf(chain.getArg(0), true, folder, false))
                else chain.proceed()
            }
            return
        }

        val geometry = loader.loadClass("$packageName.BlurTransitionDrawable").declaredMethods.single {
            it.name == "resolveCornerTypeAndWeight" && it.parameterCount == 2 && it.parameterTypes.contains(info) &&
                it.parameterTypes.contains(Integer.TYPE)
        }
        val sceneIndex = geometry.parameterTypes.indexOf(Integer.TYPE)
        val folderScene = 16 // 2 x 1 folder preview
        val dockScene = 4
        val circularScene = 9 // Native circular arc for both the blur mask and its stroke.
        listOf(layer, loader.loadClass("$packageName.RenderEffectDrawable")).forEach {
            ModernHookRuntime.requireModule().deoptimize(it.getDeclaredMethod("setCornerRadius",
                java.lang.Float.TYPE, java.lang.Boolean.TYPE, info))
        }
        ModernHookRegistry.installFast("${prefix}folder-material", geometry) { chain ->
            if (chain.getArg(sceneIndex) != dockScene || !LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock)) {
                return@installFast chain.proceed()
            }
            val scene = when (LauncherDockCorners.mode()) {
                LauncherDockCorners.CUSTOM -> circularScene
                LauncherDockCorners.ICON -> folderScene
                else -> return@installFast chain.proceed()
            }
            val item = if (scene == folderScene) folder else null
            if (sceneIndex == 0) chain.proceed(arrayOf(scene, item))
            else chain.proceed(arrayOf(item, scene))
        }
    }

    private fun blurEnabled() = LauncherFeatureRules.dockBlur(
        LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.Dock),
        LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.DockBlur),
    )
}
