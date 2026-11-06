package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface

internal object RetainLockscreenNotificationsSettings {
    const val KEY = "systemui_retain_lockscreen_notifications"
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(KEY, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(KEY, false) == true
    fun set(context: Context, value: Boolean) =
        ModernXposedPreferenceSync.setBooleanAndSync(context, KEY, value)
}

internal object RetainLockscreenNotificationsHooker {
    private const val TAG = "ONextBox-LockscreenNotifications"
    private val checkingLockscreen = ThreadLocal<Boolean>()

    fun hook(loader: ClassLoader) {
        runCatching {
            val entry = loader.loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")
            val provider = loader.loadClass("com.oplus.systemui.statusbar.notification.interruption.OplusKeyguardNotificationVisibilityProviderExImpl")
            val visibility = provider.getDeclaredMethod("shouldHideNotification", entry)
            val debug = loader.loadClass("com.oplus.systemui.notification.debug.OplusNotificationDebug")
                .getDeclaredMethod("getSkipCheckPostedBeforeLock")
            require(visibility.returnType == Boolean::class.javaPrimitiveType &&
                debug.returnType == Boolean::class.javaPrimitiveType)
            ModernHookRegistry.installFast("$TAG:age-check", debug, XposedInterface.Hooker { chain ->
                if (checkingLockscreen.get() == true) true else chain.proceed()
            })
            ModernHookRegistry.installFast("$TAG:visibility", visibility, XposedInterface.Hooker { chain ->
                val previous = checkingLockscreen.get()
                checkingLockscreen.set(RetainLockscreenNotificationsSettings.enabledXposed())
                try { chain.proceed() }
                finally {
                    if (previous == null) checkingLockscreen.remove() else checkingLockscreen.set(previous)
                }
            })
            runCatching { ModernHookRuntime.requireModule().deoptimize(visibility) }
                .onFailure { HookLog.w(TAG, "Visibility deoptimization unavailable", it) }
            HookLog.i(TAG, "Lockscreen notification age check adapter installed")
        }.onFailure { HookLog.w(TAG, "Lockscreen notification age check unavailable; stock behavior retained", it) }
    }
}
