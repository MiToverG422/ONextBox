package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

// Battery percentage outside the icon while Fluid Cloud is visible.
internal object FluidCloudBatteryHooker {
    private const val TAG = "ONextBox-FluidBattery"
    private const val BATTERY_PACKAGE = "com.oplus.systemui.statusbar.pipeline.battery"

    fun hook(classLoader: ClassLoader) {
        if (!LspConfig.isFluidCloudBatteryEnabledXposed()) return
        val key = "$TAG@${System.identityHashCode(classLoader)}:"
        runCatching {
            val modelClass = ModernReflect.findClass("$BATTERY_PACKAGE.data.model.BatteryStyleModel", classLoader)
            val viewModelClass = ModernReflect.findClass(
                "$BATTERY_PACKAGE.ui.viewmodel.LocationBasedBatteryViewModel", classLoader,
            )
            val capsuleShowing = modelClass.getDeclaredMethod("getCapsuleShowing")
            val percentStyle = modelClass.getDeclaredMethod("getPercentStyle")
            val iconStyle = modelClass.getDeclaredMethod("getIconStyle")
            val forceStyle = viewModelClass.getDeclaredMethod("setForceShowStyle", Integer.TYPE)
            val interactorField = viewModelClass.getDeclaredField("interactor").apply { isAccessible = true }

            ModernHookRegistry.installFast(
                "${key}forceStyle", forceStyle,
                XposedInterface.Hooker { chain ->
                    val requested = chain.getArg(0) as? Int ?: return@Hooker chain.proceed()
                    if (requested != 1 || !LspConfig.isFluidCloudBatteryEnabledXposed()) {
                        return@Hooker chain.proceed()
                    }
                    val replacement = runCatching {
                        val interactor = interactorField.get(chain.thisObject)
                        val styleFlow = ModernReflect.callMethod(interactor, "getBatteryStyle")
                        val style = ModernReflect.callMethod(styleFlow, "getValue")
                        FluidCloudBatteryRules.forceShowStyle(
                            requested, true,
                            percentStyle.invoke(style) as? Int,
                            iconStyle.invoke(style) as? Int,
                        )
                    }.getOrDefault(requested)
                    if (replacement == requested) chain.proceed() else chain.proceed(arrayOf(replacement))
                },
            )
            ModernHookRegistry.installFast(
                "${key}capsuleShowing", capsuleShowing,
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val original = result as? Boolean ?: return@Hooker result
                    if (!original || !LspConfig.isFluidCloudBatteryEnabledXposed()) return@Hooker original
                    runCatching {
                        FluidCloudBatteryRules.capsuleShowing(
                            original, true,
                            percentStyle.invoke(chain.thisObject) as? Int,
                            iconStyle.invoke(chain.thisObject) as? Int,
                        )
                    }.getOrDefault(original)
                },
            )
            HookLog.i(TAG, "Outside battery percentage hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Outside battery percentage hooks unavailable", it)
        }
    }
}
