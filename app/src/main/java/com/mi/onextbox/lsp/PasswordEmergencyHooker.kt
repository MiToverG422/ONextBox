package com.mi.onextbox.lsp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.util.WeakHashMap

internal object PasswordEmergencyHooker {
    private const val TAG = "ONextBox-PasswordEmergency"
    private const val BUTTON = "com.android.keyguard.EmergencyButton"
    private val hosts = setOf("com.oplus.keyguard.security.view.OplusKeyguardPinView",
        "com.oplus.keyguard.security.view.OplusKeyguardFixedPinView",
        "com.oplus.keyguard.security.view.OplusKeyguardPasswordView",
        "com.android.keyguard.KeyguardPINView", "com.android.keyguard.KeyguardPasswordView")
    private class State(initial: Int, var inCall: Boolean? = null) {
        val visibility = PasswordEmergencyVisibility(initial)
    }
    private val buttons = WeakHashMap<View, State>()
    private var applying = false

    internal class Targets(loader: ClassLoader) {
        val button = ModernReflect.findClass(BUTTON, loader)
        val visibility = button.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
        val attach = button.getDeclaredMethod("onAttachedToWindow")
        val detach = button.getDeclaredMethod("onDetachedFromWindow")
        val update = button.getDeclaredMethod("updateEmergencyCallButton", *Array(4) { Boolean::class.javaPrimitiveType!! })
    }

    fun hook(loader: ClassLoader) {
        runCatching {
            val targets = Targets(loader)
            ModernHookRegistry.installCompat("$TAG:visibility", targets.visibility, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (applying) return
                    val button = param.thisObject as? View ?: return
                    val requested = param.args.firstOrNull() as? Int ?: return
                    val state = state(button)
                    param.args[0] = state.visibility.nativeRequest(requested, hide(button, state))
                }
            })
            ModernHookRegistry.installCompat("$TAG:update", targets.update, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val button = param.thisObject as? View ?: return
                    state(button).inCall = param.args.firstOrNull() as? Boolean
                }
                override fun afterHookedMethod(param: MethodHookParam) { (param.thisObject as? View)?.let(::reconcile) }
            })
            fun observe(key: String, method: java.lang.reflect.Method, detach: Boolean = false) {
                ModernHookRegistry.installCompat("$TAG:$key", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (applying || !targets.button.isInstance(param.thisObject)) return
                        val button = param.thisObject as View
                        if (detach) restore(button, buttons.remove(button)) else reconcile(button)
                    }
                })
            }
            observe("attach", targets.attach)
            observe("detach", targets.detach, detach = true)
            observe("parent-visibility", View::class.java.getDeclaredMethod("onVisibilityChanged", View::class.java, Int::class.javaPrimitiveType))
            observe("window", View::class.java.getDeclaredMethod("onWindowVisibilityChanged", Int::class.javaPrimitiveType))
            HotReloadLifecycle.register(TAG,
                capture = { buttons.map { (button, state) -> arrayOf(button, state.visibility.requested, state.inCall) }.toTypedArray() },
                canReload = { buttons.keys.none { button -> passwordHost(button)?.let { it.isShown && it.windowVisibility == View.VISIBLE } == true } },
                detach = { buttons.toMap().forEach { (button, state) -> restore(button, state) }; buttons.clear() },
                restore = { saved ->
                    (saved as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach { row ->
                        val button = row.getOrNull(0) as? View ?: return@forEach
                        val visibility = row.getOrNull(1) as? Int ?: return@forEach
                        buttons[button] = State(visibility, row.getOrNull(2) as? Boolean)
                        reconcile(button)
                    }
                })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(TAG)
            buttons.toMap().forEach { (button, state) -> restore(button, state) }
            buttons.clear()
            HookLog.w(TAG, "Password emergency entry unavailable; native visibility retained", it)
        }
    }

    private fun passwordHost(button: View): View? {
        var parent = button.parent
        while (parent is View) {
            if (parent.javaClass.name in hosts) return parent
            parent = parent.parent
        }
        return null
    }

    private fun state(button: View) = buttons.getOrPut(button) { State(button.visibility) }

    // This guarded query runs in SystemUI with the host's phone permission;
    // the module APK intentionally does not request access to phone state.
    @SuppressLint("MissingPermission")
    private fun hide(button: View, state: State): Boolean {
        val enabled = LspConfig.isKeyguardFeatureEnabledXposed(LspConfig.KeyguardFeature.HidePasswordEmergency)
        val passwordPage = passwordHost(button) != null
        if (enabled && passwordPage && state.inCall == null &&
            button.context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            state.inCall = try {
                button.context.getSystemService(TelecomManager::class.java)?.isInCall
            } catch (_: SecurityException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }
        return PasswordEmergencyVisibility.shouldHide(enabled, passwordPage, state.inCall)
    }

    private fun reconcile(button: View) {
        if (applying || HotReloadLifecycle.isRetiring) return
        val state = state(button)
        state.visibility.reconcile(hide(button, state), button.visibility)?.let { setVisibility(button, it) }
    }

    private fun restore(button: View, state: State?) { state?.visibility?.restore()?.let { setVisibility(button, it) } }

    private fun setVisibility(button: View, visibility: Int) {
        if (button.visibility == visibility) return
        applying = true
        try { button.visibility = visibility } finally { applying = false }
    }
}
