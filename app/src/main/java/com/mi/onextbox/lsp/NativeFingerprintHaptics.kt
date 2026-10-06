package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Reuse OPPO's verification-success effect and its system haptic setting, never synthesize one. */
internal class NativeFingerprintHaptics {
    private var helperLazy: Field? = null
    private var verifySuccess: Method? = null
    private var reportedFailure = false

    fun prepare(loader: ClassLoader) {
        runCatching {
            val auth = Reflect.findClass("com.oplus.systemui.biometrics.OplusBiometricAuthController", loader)
            val field = auth.getDeclaredField("oplusKeyguardVibratorHelperLazy").apply { isAccessible = true }
            val helper = Reflect.findClass("com.oplus.keyguard.OplusKeyguardVibratorHelper", loader)
            val method = helper.getDeclaredMethod("vibrateShortWhenVerify").apply { isAccessible = true }
            check(method.returnType == Void.TYPE) { "Unknown fingerprint success haptic contract" }
            helperLazy = field
            verifySuccess = method
        }.onFailure { report(it) }
    }

    /** Called only after a fresh authorized commit, regardless of the optional animation switch. */
    fun playSuccess(auth: Any) {
        val field = helperLazy ?: return
        val method = verifySuccess ?: return
        runCatching {
            val lazy = field.get(auth) ?: return@runCatching
            val helper = Reflect.callMethod(lazy, "get") ?: return@runCatching
            method.invoke(helper)
        }.onFailure { report(it) }
    }

    private fun report(error: Throwable) {
        if (reportedFailure) return
        reportedFailure = true
        // A haptic-only compatibility failure must not disable the authorized unlock interaction.
        HookLog.w("ONextBox-FaceTap", "Native fingerprint success vibration unavailable, unlock retained", error)
    }
}
