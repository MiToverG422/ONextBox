package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

internal object FluidCloudRecentsHooker {
    private const val TAG = "ONextBox-FluidRecents"
    private const val VIEW_MODEL =
        "com.oplus.systemui.statusbar.pipeline.shared.ui.viewmodel.OplusHomeStatusBarViewModelImpl\$isSeedlingContainerVisible\$1"
    private const val VISIBILITY_MODEL =
        "com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel"
    private const val DISABLE_FLAGS_MODEL =
        "com.android.systemui.statusbar.pipeline.shared.domain.model.StatusBarDisableFlagsVisibilityModel"

    fun hook(classLoader: ClassLoader) {
        val key = "$TAG@${System.identityHashCode(classLoader)}:"
        runCatching {
            val lambdaClass = ModernReflect.findClass(VIEW_MODEL, classLoader)
            val combine = lambdaClass.declaredMethods.firstOrNull {
                it.name == "invoke" && it.parameterCount == 6 && !it.isBridge
            } ?: error("capsule visibility combiner changed")
            val visibilityClass = ModernReflect.findClass(VISIBILITY_MODEL, classLoader)
            val visibilityField = visibilityClass.getDeclaredField("visibility").apply { isAccessible = true }
            val animateField = visibilityClass.getDeclaredField("shouldAnimateChange").apply { isAccessible = true }
            val visibilityConstructor =
                visibilityClass.getConstructor(Integer.TYPE, java.lang.Boolean.TYPE)
            val flagsClass = ModernReflect.findClass(DISABLE_FLAGS_MODEL, classLoader)
            val iconsAllowedField =
                flagsClass.getDeclaredField("areNotificationIconsAllowed").apply { isAccessible = true }

            ModernHookRegistry.installFast("${key}capsule", combine, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val model = original ?: return@Hooker original
                if (model.javaClass != visibilityClass) return@Hooker original
                val visibility = visibilityField.getInt(model)
                val enabled = FluidCloudRecentsSettings.isEnabledXposed()
                if (!enabled && visibility != FluidCloudRecentsRules.VISIBLE) return@Hooker original
                val args = chain.args
                if (args.size < 5) return@Hooker original
                val flags = args[3] ?: return@Hooker original
                val desired = FluidCloudRecentsRules.keepCapsuleVisible(
                    enabled = enabled,
                    visibility = visibility,
                    canShowOngoingActivityChips = args[1] as? Boolean ?: return@Hooker original,
                    miniCapsuleShowing = args[2] as? Boolean ?: return@Hooker original,
                    fullscreenPrivacyIcon = args[4] as? Boolean ?: return@Hooker original,
                    notificationIconsAllowed = iconsAllowedField.getBoolean(flags),
                )
                if (desired == visibility) return@Hooker original
                visibilityConstructor.newInstance(desired, animateField.getBoolean(model))
            })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Overview capsule persistence unavailable, stock behavior retained", it)
        }
    }
}
