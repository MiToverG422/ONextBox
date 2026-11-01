package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field

internal object StatusBarKeepHooker {
    private const val TAG = "ONextBox-StatusBarKeep"
    private const val VISIBILITY_MODEL =
        "com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel"
    private const val DISABLE_FLAGS_MODEL =
        "com.android.systemui.statusbar.pipeline.shared.domain.model.StatusBarDisableFlagsVisibilityModel"
    private const val VIEW_MODEL_PACKAGE =
        "com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModelImpl"
    private const val CLOCK = "$VIEW_MODEL_PACKAGE\$isClockVisible\$1"
    private const val SYSTEM_INFO = "$VIEW_MODEL_PACKAGE\$isSystemInfoVisible\$1"
    private const val OPERATOR_NAME = "$VIEW_MODEL_PACKAGE\$shouldShowOperatorNameView\$1"
    private const val NOTIFICATION_ICONS = "$VIEW_MODEL_PACKAGE\$isNotificationIconContainerVisible\$1"

    fun hook(classLoader: ClassLoader) {
        val key = "$TAG@${System.identityHashCode(classLoader)}:"
        runCatching {
            val visibilityClass = ModernReflect.findClass(VISIBILITY_MODEL, classLoader)
            val visibilityField = visibilityClass.getDeclaredField("visibility").apply { isAccessible = true }
            val animateField = visibilityClass.getDeclaredField("shouldAnimateChange").apply { isAccessible = true }
            val visibilityConstructor =
                visibilityClass.getConstructor(Integer.TYPE, java.lang.Boolean.TYPE)
            val flagsClass = ModernReflect.findClass(DISABLE_FLAGS_MODEL, classLoader)
            val clockAllowed = flagsClass.getDeclaredField("isClockAllowed").apply { isAccessible = true }
            val systemInfoAllowed = flagsClass.getDeclaredField("isSystemInfoAllowed").apply { isAccessible = true }
            val iconsAllowed =
                flagsClass.getDeclaredField("areNotificationIconsAllowed").apply { isAccessible = true }

            install(key, classLoader, CLOCK, 3, visibilityClass, visibilityField, animateField, visibilityConstructor) { args ->
                StatusBarKeepRules.visible(
                    enabled = true,
                    statusBarVisible = args.boolean(0) ?: return@install false,
                    allowed = args.flag(clockAllowed, 1) ?: return@install false,
                )
            }
            install(key, classLoader, SYSTEM_INFO, 3, visibilityClass, visibilityField, animateField, visibilityConstructor) { args ->
                StatusBarKeepRules.visible(
                    enabled = true,
                    statusBarVisible = args.boolean(0) ?: return@install false,
                    allowed = args.flag(systemInfoAllowed, 1) ?: return@install false,
                )
            }
            install(key, classLoader, OPERATOR_NAME, 4, visibilityClass, visibilityField, animateField, visibilityConstructor) { args ->
                val show = args.boolean(2) ?: return@install false
                show && StatusBarKeepRules.visible(
                    enabled = true,
                    statusBarVisible = args.boolean(0) ?: return@install false,
                    allowed = args.flag(systemInfoAllowed, 1) ?: return@install false,
                )
            }
            install(key, classLoader, NOTIFICATION_ICONS, 4, visibilityClass, visibilityField, animateField, visibilityConstructor) { args ->
                StatusBarKeepRules.notificationIconsVisible(
                    enabled = true,
                    anyChipVisible = args.boolean(1) ?: return@install false,
                    statusBarVisible = args.boolean(0) ?: return@install false,
                    allowed = args.flag(iconsAllowed, 2) ?: return@install false,
                )
            }
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Status bar overview persistence unavailable, stock behavior retained", it)
        }
    }

    private fun install(
        key: String,
        classLoader: ClassLoader,
        className: String,
        parameterCount: Int,
        visibilityClass: Class<*>,
        visibilityField: Field,
        animateField: Field,
        visibilityConstructor: Constructor<*>,
        decide: (List<Any?>) -> Boolean,
    ) {
        val lambdaClass = ModernReflect.findClass(className, classLoader)
        val combine = lambdaClass.declaredMethods.firstOrNull {
            it.name == "invoke" && it.parameterCount == parameterCount && !it.isBridge
        } ?: error("$className combiner changed")
        ModernHookRegistry.installFast("$key$className", combine, XposedInterface.Hooker { chain ->
            val original = chain.proceed()
            if (!StatusBarKeepSettings.isEnabledXposed()) return@Hooker original
            val model = original ?: return@Hooker original
            if (model.javaClass != visibilityClass) return@Hooker original
            if (visibilityField.getInt(model) == StatusBarKeepRules.VISIBLE) return@Hooker original
            if (!decide(chain.args)) return@Hooker original
            visibilityConstructor.newInstance(StatusBarKeepRules.VISIBLE, animateField.getBoolean(model))
        })
    }

    private fun List<Any?>.boolean(index: Int): Boolean? = getOrNull(index) as? Boolean

    private fun List<Any?>.flag(field: Field, index: Int): Boolean? {
        val flags = getOrNull(index) ?: return null
        return field.getBoolean(flags)
    }
}
