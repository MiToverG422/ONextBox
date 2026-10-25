package com.mi.onextbox.lsp

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Only the independently verified C16 SystemUI contracts enter the shared feature hooks. */
internal object ColorOs16SystemUiHooks {
    private const val TAG = "ONextBox-C16SystemUI"
    internal const val CLEAR_CONTROLLER = "com.oplus.systemui.notification.clearall.ClearAllController"
    internal const val CLEAR_BUTTON = "com.oplus.systemui.notification.clearall.OplusClearAllButton"
    internal const val ROTATION_CONTROLLER = "com.android.systemui.shared.rotation.RotationButtonController"
    internal const val BLUETOOTH_POLICY = "com.oplus.systemui.statusbar.phone.OplusPhoneStatusBarPolicyExImpl"
    internal const val ICON_CONTROLLER = "com.android.systemui.statusbar.phone.ui.StatusBarIconController"
    internal const val ICON_CONTROLLER_IMPL = "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl"

    internal enum class Feature {
        HideNotificationClear,
        HideRotationSuggestion,
        HideDisconnectedBluetooth,
    }

    fun hook(loader: ClassLoader) {
        dispatch(
            findClass = { Class.forName(it, false, loader) },
            install = { feature ->
                when (feature) {
                    Feature.HideNotificationClear -> HideNotificationClearHooker.hook(loader)
                    Feature.HideRotationSuggestion -> RotationSuggestionHooker.hook(loader)
                    Feature.HideDisconnectedBluetooth -> DisconnectedBluetoothIconHooker.hook(loader)
                }
            },
            onUnavailable = { feature, error ->
                HookLog.w(TAG, "$feature contract unavailable; stock behavior retained", error)
            },
        )
        ColorOs16SystemUiFeatureHooks.hook(loader)
    }

    /** A missing contract or failed installer must not prevent another feature from being tried. */
    internal fun dispatch(
        findClass: (String) -> Class<*>,
        install: (Feature) -> Unit,
        onUnavailable: (Feature, Throwable) -> Unit,
    ) {
        Feature.entries.forEach { feature ->
            runCatching {
                verify(feature, findClass)
                install(feature)
            }.onFailure { error ->
                runCatching { onUnavailable(feature, error) }
            }
        }
    }

    /** Inspect metadata only: do not construct OEM objects, initialize classes, or invoke methods. */
    internal fun verify(feature: Feature, findClass: (String) -> Class<*>) {
        when (feature) {
            Feature.HideNotificationClear -> {
                val controller = findClass(CLEAR_CONTROLLER)
                val button = findClass(CLEAR_BUTTON)
                requireInstanceVoid(controller.getDeclaredMethod(
                    "setVisible", button, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                ))
            }
            Feature.HideRotationSuggestion -> requireInstanceVoid(
                findClass(ROTATION_CONTROLLER).getDeclaredMethod(
                    "setRotateSuggestionButtonState", Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ),
            )
            Feature.HideDisconnectedBluetooth -> {
                val policy = findClass(BLUETOOTH_POLICY)
                // Require C16's observed entry point before entering the shared Bluetooth hook.
                requireInstanceVoid(policy.getDeclaredMethod("updateBluetooth\$1"))
                requireInstanceField(policy, "bluetoothConnected", Boolean::class.javaPrimitiveType!!)
                requireInstanceField(policy, "slotBluetooth", String::class.java)
                val iconController = findClass(ICON_CONTROLLER)
                requireInstanceField(policy, "statusBarIconController", iconController)
                val implementation = findClass(ICON_CONTROLLER_IMPL)
                require(iconController.isAssignableFrom(implementation)) {
                    "${implementation.name} does not implement ${iconController.name}"
                }
                requireInstanceVoid(implementation.getMethod(
                    "setIconVisibility", String::class.java, Boolean::class.javaPrimitiveType,
                ))
            }
        }
    }

    private fun requireInstanceVoid(method: Method) {
        require(!Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE) {
            "Unexpected instance void method contract: ${method.toGenericString()}"
        }
    }

    private fun requireInstanceField(owner: Class<*>, name: String, type: Class<*>) {
        val field = owner.getDeclaredField(name)
        require(!Modifier.isStatic(field.modifiers) && field.type == type) {
            "Unexpected instance field contract: ${field.toGenericString()}"
        }
    }
}
