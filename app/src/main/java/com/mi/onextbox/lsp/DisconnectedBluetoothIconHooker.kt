package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

/** Follow the OEM connection state without changing Bluetooth or quick-settings behavior. */
internal object DisconnectedBluetoothIconHooker {
    private const val TAG = "ONextBox-BluetoothIcon"

    fun hook(loader: ClassLoader) {
        val key = "$TAG@${System.identityHashCode(loader)}:"
        runCatching {
            val policy = ModernReflect.findClass(
                "com.oplus.systemui.statusbar.phone.OplusPhoneStatusBarPolicyExImpl", loader,
            )
            val updates = policy.declaredMethods.filter {
                (it.name == "updateBluetooth" || it.name.startsWith("updateBluetooth\$")) &&
                    it.parameterCount == 0 && it.returnType == Void.TYPE
            }
            require(updates.isNotEmpty()) { "OEM Bluetooth icon update is unavailable" }
            val connected = policy.getDeclaredField("bluetoothConnected").apply { isAccessible = true }
            require(connected.type == Boolean::class.javaPrimitiveType)
            val slot = policy.getDeclaredField("slotBluetooth").apply { isAccessible = true }
            val controller = policy.getDeclaredField("statusBarIconController").apply { isAccessible = true }
            val iconController = ModernReflect.findClassIfExists(
                "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl", loader,
            ) ?: ModernReflect.findClass("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", loader)
            // The OEM interface is stripped; visibility is declared on the concrete controller.
            val visibility = iconController.getMethod("setIconVisibility", String::class.java, Boolean::class.javaPrimitiveType)

            // Let the OEM recompute connection, battery, transfer, and accessibility state first.
            updates.forEach { update ->
                ModernHookRegistry.installFast("$key${update.name}", update, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (!LspConfig.isHideDisconnectedBluetoothEnabledXposed()) return@Hooker result
                    val owner = chain.thisObject ?: return@Hooker result
                    runCatching {
                        if (DisconnectedBluetoothIconRules.shouldHide(true, connected.getBoolean(owner))) {
                            visibility.invoke(controller.get(owner), slot.get(owner), false)
                        }
                    }.onFailure { HookLog.w(TAG, "Unable to apply Bluetooth icon visibility; stock state retained", it) }
                    result
                })
            }
            // These callbacks are also used on connection, disconnection, and adapter changes.
            val callbacks = policy.declaredMethods.filter {
                it.name in setOf("handleBroadcast", "init", "access\$applyBluetoothBatterySlot")
            } + ModernReflect.findClassIfExists("com.android.systemui.statusbar.phone.PhoneStatusBarPolicy", loader)
                ?.declaredMethods.orEmpty().filter {
                    it.name in setOf("onBluetoothDevicesChanged", "onBluetoothStateChange")
                }
            callbacks.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    .onFailure { error -> HookLog.w(TAG, "Unable to deoptimize ${it.name}", error) }
            }
            HookLog.i(TAG, "Disconnected Bluetooth icon hook installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Bluetooth icon hooks unavailable; stock behavior retained", it)
        }
    }
}
