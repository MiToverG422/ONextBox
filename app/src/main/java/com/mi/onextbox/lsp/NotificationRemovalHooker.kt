package com.mi.onextbox.lsp

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.mi.onextbox.lsp.LspConfig.NotificationRemovalFeature
import com.mi.onextbox.lsp.compat.ModernHookBridge
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Exact notification identities, never notification text or a whole system channel. */
internal object NotificationRemovalRules {
    fun match(pkg: String, channel: String?, id: Int): NotificationRemovalFeature? = when {
        pkg == "com.android.systemui" -> when {
            channel == "INS" && id == 10002 -> NotificationRemovalFeature.DeveloperMode
            channel == "FLA" && (id == 10011 || id == 10012) -> NotificationRemovalFeature.Flashlight
            channel == "channel_dnd_notice" && id == 10001 -> NotificationRemovalFeature.DoNotDisturb
            channel == "BLOCK_BANNER" && id == 10008 -> NotificationRemovalFeature.MuteNotifications
            (channel == "gt_mode_channel_id" || channel == "gt_mode_channel_id_2.0") && id == 6 ->
                NotificationRemovalFeature.GtMode
            else -> null
        }
        pkg == "com.oplus.battery" || pkg == "com.coloros.phonemanager" -> when {
            channel == "high_performance_channel_id" && id == 5 -> NotificationRemovalFeature.HighPerformance
            (channel == "PowerConsumptionOptimizationChannel" || channel == "PowerConsumptionOptimizationChannelLow") && id == 17 ->
                NotificationRemovalFeature.HighBatteryConsumption
            else -> null
        }
        pkg == "android" && channel == "DurationNotification" && id == 4 ->
            NotificationRemovalFeature.HotspotPowerConsumption
        else -> null
    }
}

internal object NotificationRemovalHooker {
    private const val TAG = "ONextBox-NotificationRemoval"
    private val installed = ConcurrentHashMap.newKeySet<String>()

    private fun enabled(feature: NotificationRemovalFeature) =
        LspConfig.isNotificationRemovalEnabledXposed(feature)

    fun hookSystemServer(loader: ClassLoader) {
        if (!installed.add("system@${System.identityHashCode(loader)}")) return
        hookVoid(loader, listOf("com.android.server.wm.AlertWindowNotification"),
            setOf("onPostNotification"), NotificationRemovalFeature.Overlay)
        hookVoid(loader, listOf("com.android.server.connectivity.VpnExtImpl",
            "com.android.server.connectivity.OplusVpnHelper"), setOf("showNotification"),
            NotificationRemovalFeature.Vpn)

        // Battery utility classes are obfuscated and change with every APK update. Filter
        // their exact channel + id at the server instead of guessing the obfuscated name.
        runCatching {
            val type = Class.forName("com.android.server.notification.NotificationManagerService", false, loader)
            val methods = type.declaredMethods.filter {
                it.name == "enqueueNotificationInternal" && it.returnType == Void.TYPE &&
                    it.parameterTypes.firstOrNull() == String::class.java &&
                    it.parameterTypes.getOrNull(5) == Int::class.javaPrimitiveType &&
                    it.parameterTypes.getOrNull(6) == Notification::class.java
            }
            check(methods.isNotEmpty()) { "No supported notification enqueue signature" }
            methods.forEach { method ->
                ModernHookBridge.hookMethodFast(method) { chain ->
                    val n = chain.args[6] as? Notification
                    val feature = NotificationRemovalRules.match(
                        chain.args[0] as? String ?: "", n?.channelId, chain.args[5] as? Int ?: -1,
                    )
                    if (feature != null && enabled(feature)) null else chain.proceed()
                }
            }
            HookLog.i(TAG, "system notification filter installed (${methods.size} signatures)")
        }.onFailure { HookLog.w(TAG, "System notification filter unavailable", it) }
    }

    fun hookSystemUi(loader: ClassLoader) {
        if (!installed.add("systemui@${System.identityHashCode(loader)}")) return
        // Blocking only the posting call keeps cancellation and actual mode changes intact.
        runCatching {
            val method = NotificationManager::class.java.getDeclaredMethod(
                "notifyAsUser", String::class.java, Int::class.javaPrimitiveType,
                Notification::class.java, android.os.UserHandle::class.java,
            )
            val contextField = NotificationManager::class.java.getDeclaredField("mContext").apply {
                isAccessible = true
            }
            ModernHookBridge.hookMethodFast(method) { chain ->
                val notification = chain.args[2] as? Notification
                val pkg = (contextField.get(chain.thisObject) as? Context)?.packageName ?: ""
                val feature = NotificationRemovalRules.match(pkg,
                    notification?.channelId, chain.args[1] as? Int ?: -1)
                if (feature != null && enabled(feature)) null else chain.proceed()
            }
            HookLog.i(TAG, "SystemUI notification filter installed")
        }.onFailure { HookLog.w(TAG, "SystemUI notification filter unavailable", it) }

        // The BATTERY channel/id is shared with temperature and voltage warnings. Inspect
        // the producer's type, not that shared notification identity. 7/8/9 mean full charge.
        val powerTypes = listOf(
            "com.oplus.systemui.statusbar.notification.power.OplusPowerNotificationWarnings",
            "com.oplusos.systemui.notification.power.OplusPowerNotificationWarnings",
            "com.coloros.systemui.notification.power.ColorosPowerNotificationWarnings",
        )
        powerTypes.forEach { name ->
            val type = runCatching { Class.forName(name, false, loader) }.getOrNull() ?: return@forEach
            type.declaredMethods.filter { it.name == "showChargeErrorDialog" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                it.returnType == Void.TYPE
            }.forEach { method ->
                install(method) { chain ->
                    if (enabled(NotificationRemovalFeature.ChargingCompleted) &&
                        (chain.args[0] as? Int) in 7..9) null else chain.proceed()
                }
            }
        }

        // Older ColorOS releases used different notification channels. Retain the source
        // hooks as version fallbacks while accepting every void overload (C17 has three).
        hookVoid(loader, listOf(
            "com.oplus.systemui.notification.flashlight.FlashlightNotification",
            "com.oplus.systemui.statusbar.notification.flashlight.FlashlightNotification",
            "com.oplusos.systemui.flashlight.FlashlightNotification",
        ), setOf("sendNotification", "sendNotification\$1"), NotificationRemovalFeature.Flashlight)
        hookVoid(loader, listOf("com.oplus.systemui.statusbar.util.GTUtils",
            "com.oplusos.systemui.statusbar.util.GTUtils"),
            setOf("notifyOpenGtMode", "showOpenGtModeNotify"), NotificationRemovalFeature.GtMode)
    }

    private fun hookVoid(loader: ClassLoader, classes: List<String>, names: Set<String>,
        feature: NotificationRemovalFeature,
    ) {
        var count = 0
        classes.forEach { name ->
            val type = runCatching { Class.forName(name, false, loader) }.getOrNull() ?: return@forEach
            type.declaredMethods.filter { it.name in names && it.returnType == Void.TYPE }.forEach { method ->
                if (install(method) { chain -> if (enabled(feature)) null else chain.proceed() }) count++
            }
        }
        HookLog.i(TAG, "${feature.name}: $count producer hooks")
    }

    private fun install(method: Method, callback: io.github.libxposed.api.XposedInterface.Hooker): Boolean =
        runCatching { ModernHookBridge.hookMethodFast(method, callback); true }
            .onFailure { HookLog.w(TAG, "Hook unavailable: $method", it) }.getOrDefault(false)
}
