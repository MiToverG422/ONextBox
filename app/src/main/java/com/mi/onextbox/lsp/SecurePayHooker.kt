package com.mi.onextbox.lsp

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal object SecurePayHooker {
    private const val TAG = "ONextBox-SecurePay"

    fun hook(loader: ClassLoader, apkPath: String) {
        runCatching { hookRiskCard(loader, apkPath) }
            .onFailure { HookLog.w(TAG, "Risk-card adapter unavailable; stock behavior retained", it) }
        runCatching { hookLiveNotification() }
            .onFailure { HookLog.w(TAG, "Live-notification adapter unavailable; stock behavior retained", it) }
    }

    private fun hookRiskCard(loader: ClassLoader, apkPath: String) {
        val owner = ModernReflect.findClassIfExists(SecurePayReminderRules.CLOUD_OWNER, loader)
            ?: return
        // Resolve by meaning, not the obfuscated "I" name. Do not intercept the shared risk
        // dispatcher: it also notifies the independent high-risk dialog and scan observers.
        ApkDexSession.withSession(apkPath) { open ->
            val dex = open()
            val target = dex.findMethod {
                matcher {
                    declaredClass = SecurePayReminderRules.CLOUD_OWNER
                    usingStrings(SecurePayReminderRules.RISK_CARD_LOG)
                    paramTypes("java.util.List")
                    returnType = "void"
                }
            }.single()
            val method = target.getMethodInstance(loader)
            require(method.declaringClass == owner && !Modifier.isStatic(method.modifiers))
            require(method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(List::class.java)))
            val callers = target.callers.filter { it.isMethod }.mapNotNull {
                runCatching { it.getMethodInstance(loader) }.getOrNull()
            }
            val reached = AtomicBoolean()
            ModernHookRegistry.installFast("$TAG:risk-card:${method.toGenericString()}", method,
                XposedInterface.Hooker { chain ->
                    if (!SecurePaySettings.enabledXposed()) return@Hooker chain.proceed()
                    if (reached.compareAndSet(false, true)) HookLog.i(TAG, "Payment-risk cloud card suppressed")
                    // The caller has already retained the real scan results via super.h(list).
                    // Skip only the visual START request; never mark a risk as safe or fixed.
                    null
                })
            // Small vendor methods can be inlined into their UI callers by system AOT code.
            // Deoptimize only this UI implementation and the identified direct callers.
            deoptimize(owner.declaredMethods.toList() + callers)
            HookLog.i(TAG, "Payment-risk cloud hook installed: ${method.name}")

            // The backup live-notification UI is obfuscated too. Anchor its sender by the
            // native log strings and reuse this short-lived bridge instead of guessing "u.D".
            runCatching {
                val refresh = dex.findMethod {
                    matcher {
                        usingStrings(SecurePayReminderRules.LIVE_LOG_TAG, SecurePayReminderRules.LIVE_REFRESH_LOG)
                        paramTypes()
                        returnType = "void"
                    }
                }.filter { it.className.startsWith("com.oplus.securepay.scan.ui.") }.single()
                    .getMethodInstance(loader)
                require(!Modifier.isStatic(refresh.modifiers))
                deoptimize(refresh.declaringClass.declaredMethods.toList())
                HookLog.i(TAG, "Payment-risk live-notification sender adapted")
            }.onFailure { HookLog.w(TAG, "Live-notification caller adapter unavailable", it) }
        }
    }

    private fun deoptimize(methods: List<Method>) {
        methods.distinct().forEach { method ->
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { HookLog.w(TAG, "Payment UI caller deoptimization unavailable: ${method.name}", it) }
        }
    }

    private fun hookLiveNotification() {
        val reached = AtomicBoolean()
        // These hooks run only in the scoped SecurePay process. The exact native risk channel
        // additionally excludes ordinary notifications and every other app's live alerts.
        val methods = NotificationManager::class.java.declaredMethods.filter { method ->
            method.name == "notify" && method.returnType == Void.TYPE &&
                method.parameterTypes.count { it == Notification::class.java } == 1
        }
        methods.forEach { method ->
            val index = method.parameterTypes.indexOf(Notification::class.java)
            ModernHookRegistry.installCompat("$TAG:live:${method.toGenericString()}", method,
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val original = param.args[index] as? Notification ?: return
                        if (!SecurePayReminderRules.shouldStripLiveAlert(
                                SecurePaySettings.enabledXposed(), original.channelId,
                            )) return
                        // Work on a copy so the vendor's reusable builder, actions, content and
                        // dismissal bookkeeping remain intact. Still post the ordinary warning.
                        val normal = original.clone()
                        normal.extras = Bundle(original.extras ?: Bundle()).apply {
                            SecurePayReminderRules.cloudExtras.forEach(::remove)
                            putBoolean(SecurePayReminderRules.PROMOTION_EXTRA, false)
                        }
                        if (Build.VERSION.SDK_INT >= 36) {
                            normal.flags = SecurePayReminderRules.withoutPromotion(
                                normal.flags, Notification.FLAG_PROMOTED_ONGOING,
                            )
                        }
                        param.args[index] = normal
                        if (reached.compareAndSet(false, true)) HookLog.i(TAG, "Payment-risk notification promotion removed")
                    }
                })
        }
        HookLog.i(TAG, "Payment-risk live-notification hooks installed: ${methods.size}")
    }
}
