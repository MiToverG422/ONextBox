package com.mi.onextbox.lsp

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Observes native lockscreen state and accepted results without changing authentication. */
internal object FaceAuthCapsuleHooker {
    private const val TAG = "ONextBox-FaceCapsule"
    private const val OWNER = "face-capsule:views"
    private enum class UnlockSource { Face, Fingerprint, Credential }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var monitor = WeakReference<Any>(null)
    private var failed = false
    private var sessionUser: Int? = null
    private var sessionEpoch = 0L
    private val state = FaceAuthCapsuleState()
    private var prefs: SharedPreferences? = null
    private val native by lazy {
        FaceAuthCapsuleNativeIndicators(::enabled, { locked() || state.phase != FaceAuthCapsuleState.Phase.Hidden }) { owner ->
            onMain { remember(owner); reconcile() }
        }
    }
    private val avoidance = FaceAuthCapsuleAvoidance()
    private val window by lazy {
        FaceAuthCapsuleWindow(native::capsuleLayout, native::protectedContentTop, {
            onMain { state.animationFinished(); reconcile() }
        }, avoidance::reserve, avoidance::clear) { error ->
            main.post { guarded { throw error } }
        }
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == LspConfig.KeyguardFeature.FaceAuthCapsule.key) onMain {
            failed = false
            if (enabled() && locked() && running()) start()
            reconcile()
        }
    }
    private val tick = Runnable { guarded { reconcile() } }

    fun hook(loader: ClassLoader) {
        runCatching {
            val type = Reflect.findClass("com.android.keyguard.KeyguardUpdateMonitor", loader)
            val callback = type.getDeclaredField("mFaceAuthenticationListener").type
            val int = Int::class.javaPrimitiveType!!
            val bool = Boolean::class.javaPrimitiveType!!
            val runChanged = Reflect.findDeclaredMethodExact(callback, "onRunningStateChanged")
            // This native entry is reached after the current user/sleep checks, before keyguard is dismissed.
            val success = Reflect.findDeclaredMethodExact(type, "onFaceAuthenticated", int, bool)
            val failure = Reflect.findDeclaredMethodExact(type, "handleFaceAuthFailed")
            val error = Reflect.findDeclaredMethodExact(type, "handleFaceError", int, String::class.java)
            val fingerprint = Reflect.findDeclaredMethodExact(type, "onFingerprintAuthenticated", int, bool)
            val viewManager = Reflect.findClass("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager", loader)
            val monitorField = viewManager.getDeclaredField("mKeyguardUpdateManager").apply { isAccessible = true }
            val visibility = Reflect.findDeclaredMethodExact(viewManager, "updateStates")
            val waking = Reflect.findDeclaredMethodExact(viewManager, "onStartedWakingUp")
            val security = Reflect.findClass("com.android.keyguard.KeyguardSecurityContainerController", loader)
            val securityMode = Reflect.findClass("com.android.keyguard.KeyguardSecurityModel\$SecurityMode", loader)
            val credential = Reflect.findDeclaredMethodExact(security, "showNextSecurityScreenOrFinish", bool, int, bool, securityMode)
            val securityMonitor = security.getDeclaredField("mUpdateMonitor").apply { isAccessible = true }
            listOf("mKeyguardShowing", "mKeyguardOccluded", "mGoingToSleep", "mSwitchingUser", "mContext")
                .forEach(type::getDeclaredField)
            Reflect.findDeclaredMethodExact(type, "isFaceAuthRunning")
            Reflect.findDeclaredMethodExact(type, "getUserUnlockedWithBiometric", int)
            Reflect.findDeclaredMethodExact(type, "getCurrentUser")
            val outer = callback.declaredFields.single { type.isAssignableFrom(it.type) }.apply { isAccessible = true }
            for (method in listOf(visibility, waking)) after(method) { p -> onMain {
                val owner = p.thisObject ?: return@onMain
                monitorField.get(owner)?.let(::remember)
                reconcile()
                if (method == waking && state.phase == FaceAuthCapsuleState.Phase.Hidden) main.postDelayed(tick, 100L)
            } }
            observeCredential(credential, securityMonitor)
            after(runChanged) { p -> onMain {
                remember(outer.get(p.thisObject) ?: return@onMain)
                if (running()) {
                    if (enabled() && locked()) start()
                } else state.stopped(SystemClock.uptimeMillis())
                reconcile()
            } }
            // An acquired event also catches a listening transition which happened before the view was ready.
            callback.declaredMethods.filter { it.name == "onAuthenticationStatusChanged" }.forEach { method ->
                after(method) { p ->
                    if (p.args.firstOrNull()?.javaClass?.simpleName == "AcquiredFaceAuthenticationStatus") onMain {
                        remember(outer.get(p.thisObject) ?: return@onMain)
                        if (enabled() && locked() && running() && state.phase != FaceAuthCapsuleState.Phase.Success) start()
                        reconcile()
                    }
                }
            }
            before(success) { p -> onMain {
                val owner = p.thisObject ?: return@onMain
                remember(owner)
                val user = p.args.firstOrNull() as? Int
                if (!fingerprintAccepted()) acceptUnlock(user, UnlockSource.Face)
                reconcile()
            } }
            before(fingerprint) { p -> onMain {
                p.thisObject?.let(::remember)
                acceptUnlock(p.args.firstOrNull() as? Int, UnlockSource.Fingerprint)
                reconcile()
            } }
            after(failure) { p -> onMain {
                p.thisObject?.let(::remember)
                if (enabled() && locked()) state.finish(false, SystemClock.uptimeMillis())
                reconcile()
            } }
            after(error) { p -> onMain {
                p.thisObject?.let(::remember)
                val code = p.args[0] as? Int
                if (code == 5 || code == 10) state.stopped(SystemClock.uptimeMillis())
                else if (enabled() && locked()) state.finish(false, SystemClock.uptimeMillis())
                reconcile()
            } }
            for (name in listOf("handleStartedGoingToSleep", "handleUserSwitching", "handleUserSwitchComplete")) {
                type.declaredMethods.filter { it.name == name }.forEach { method ->
                    before(method) { onMain { reset() } }
                }
            }
            type.declaredMethods.filter { it.name == "handleKeyguardReset" }.forEach { method ->
                after(method) { p -> onMain {
                    p.thisObject?.let(::remember)
                    if (locked() && !flag("mKeyguardGoingAway") && !unlockAccepted() &&
                        state.phase == FaceAuthCapsuleState.Phase.Hidden) state.resetSession()
                    if (enabled() && locked() && running()) start()
                    reconcile()
                } }
            }
            runCatching { native.hook(loader) }.onFailure {
                HookLog.w(TAG, "Native face glyph adapter unavailable; other lockscreen tips retained", it)
            }
            avoidance.hook(loader)
            HotReloadLifecycle.register(OWNER,
                // Keep the old two-slot host-state format; authentication results are never transferred.
                capture = { arrayOf(null, monitor.get()) },
                detach = {
                    main.removeCallbacksAndMessages(null)
                    prefs?.unregisterOnSharedPreferenceChangeListener(listener); prefs = null
                    sessionEpoch++; state.resetSession(); window.remove(); native.clear(); avoidance.clear(); monitor.clear()
                    sessionUser = null
                },
                restore = ::restore,
                resumeAfterRejection = { observe(); reconcile() })
            HotReloadLifecycle.afterCommit { restore(HotReloadLifecycle.restoredState(OWNER)) }
            HookLog.i(TAG, "Lockscreen capsule v9: independent locked state and face/fingerprint/credential unlock animation installed")
        }.onFailure {
            failed = true
            main.post { runCatching { window.remove(); native.clear(); avoidance.clear() } }
            HookLog.w(TAG, "Face capsule unavailable; native lockscreen retained", it)
        }
    }

    private fun restore(saved: Any?) {
        monitor = WeakReference((saved as? Array<*>)?.getOrNull(1))
        sessionEpoch++; state.resetSession(); sessionUser = null
        failed = false; observe()
        if (enabled() && locked() && running()) start()
        reconcile()
    }

    private fun observe() {
        if (prefs == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
            prefs = it; it.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun enabled() = !failed && !HotReloadLifecycle.isRetiring &&
        LspConfig.isKeyguardFeatureEnabledXposed(LspConfig.KeyguardFeature.FaceAuthCapsule)
    private fun context() = monitor.get()?.let { Reflect.getObjectField(it, "mContext") as? Context }
    private fun interactive() = context()?.getSystemService(PowerManager::class.java)?.isInteractive == true
    private fun currentUser() = monitor.get()?.let {
        Reflect.callStaticMethod(it.javaClass, "getCurrentUser") as? Int
    }
    private fun flag(name: String) = monitor.get()?.let { Reflect.getObjectField(it, name) == true } == true
    private fun locked(): Boolean {
        val locked = flag("mKeyguardShowing") ||
            context()?.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        return locked && !flag("mKeyguardOccluded")
    }
    private fun running() = monitor.get()?.let {
        Reflect.callMethod(it, "isFaceAuthRunning") == true
    } == true

    private fun fingerprintAccepted() = monitor.get()?.let { owner ->
        val records = Reflect.getObjectField(owner, "mUserFingerprintAuthenticated") as? android.util.SparseArray<*>
        records?.get(currentUser() ?: return@let false)?.let { Reflect.getObjectField(it, "mAuthenticated") == true }
    } == true

    private fun biometricAccepted() = monitor.get()?.let { owner ->
        currentUser()?.let { Reflect.callMethod(owner, "getUserUnlockedWithBiometric", it) == true }
    } == true

    private fun unlockAccepted() = biometricAccepted() || monitor.get()?.let { owner ->
        runCatching { Reflect.callMethod(Reflect.callMethod(owner, "getEx"), "getPasswordMatchSucceed") == true }
            .getOrDefault(false)
    } == true

    private fun acceptUnlock(user: Int?, source: UnlockSource, freshNativeSession: Boolean = false) {
        val freshSession = freshNativeSession || locked() || state.phase == FaceAuthCapsuleState.Phase.Locked ||
            state.phase == FaceAuthCapsuleState.Phase.Scanning
        if (!enabled() || user == null || user != currentUser() || !freshSession ||
            flag("mGoingToSleep") || flag("mSwitchingUser") ||
            (source != UnlockSource.Fingerprint && !interactive())) return
        sessionUser = user
        val duration = if (ValueAnimator.areAnimatorsEnabled()) {
            val scale = ValueAnimator.getDurationScale().takeIf { it.isFinite() } ?: 1f
            (FaceAuthCapsuleMotion.SUCCESS_DURATION * scale.coerceIn(0f, 100f)).toLong()
        } else 600L
        val now = SystemClock.uptimeMillis()
        val accepted = when (source) {
            UnlockSource.Face -> state.finish(true, now, freshNativeSuccess = true, animationDuration = duration)
            UnlockSource.Fingerprint -> state.fingerprintAuthenticated(now, duration)
            UnlockSource.Credential -> state.credentialAuthenticated(now, accepted = true, animationDuration = duration)
        }
        if (accepted) HookLog.d(TAG, "Accepted ${source.name.lowercase()} unlock animation")
    }

    private fun remember(owner: Any) { monitor = WeakReference(owner); locked() }

    private fun start() {
        if (flag("mKeyguardGoingAway") || fingerprintAccepted()) return
        sessionUser = currentUser()
        state.running(SystemClock.uptimeMillis())
    }

    private fun reconcile() {
        main.removeCallbacks(tick)
        val context = context()
        val operational = enabled() && context != null && !flag("mSwitchingUser") &&
            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            (sessionUser == null || sessionUser == currentUser())
        val locked = locked() && !flag("mKeyguardGoingAway")
        state.tick(SystemClock.uptimeMillis(), operational, locked)
        if (operational && locked && interactive() && !flag("mGoingToSleep") && !unlockAccepted()) {
            sessionUser = currentUser()
            state.showLocked()
        }
        native.reconcile()
        if (context != null && interactive() &&
            (!flag("mGoingToSleep") || state.phase == FaceAuthCapsuleState.Phase.Success)) {
            window.show(context, state.phase)
        } else window.remove()
        // The idle lock is static; only authentication transitions need frame-rate reconciliation.
        if (state.phase != FaceAuthCapsuleState.Phase.Hidden) main.postDelayed(tick,
            if (state.phase == FaceAuthCapsuleState.Phase.Locked) 250L else 32L)
    }

    private fun reset() {
        sessionEpoch++; state.resetSession(); sessionUser = null
        main.removeCallbacks(tick)
        window.remove(); native.clear(); avoidance.clear()
    }

    private fun observeCredential(method: Method, monitorField: Field) {
        val attempts = WeakHashMap<ModernMethodHook.MethodHookParam, Long>()
        ModernHookRegistry.installCompat("$TAG:${method.declaringClass.name}:${method.name}", method,
            object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (Looper.myLooper() != Looper.getMainLooper()) return
                    guarded {
                        if (param.args[0] != true ||
                            (param.args[3] as? Enum<*>)?.name !in listOf("PIN", "Password", "Pattern")) return@guarded
                        val owner = param.thisObject ?: return@guarded
                        monitorField.get(owner)?.let(::remember)
                        if (enabled() && locked() && param.args[1] == currentUser() &&
                            !flag("mGoingToSleep") && !flag("mSwitchingUser")) attempts[param] = sessionEpoch
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) { onMain {
                    val epoch = attempts.remove(param) ?: return@onMain
                    // Native finish may already have hidden keyguard; preserve only this call's locked session.
                    if (param.throwable != null || param.result != true || epoch != sessionEpoch) return@onMain
                    acceptUnlock(param.args[1] as? Int, UnlockSource.Credential, freshNativeSession = true)
                    reconcile()
                } }
            })
    }

    private fun after(method: Method, action: (ModernMethodHook.MethodHookParam) -> Unit) {
        install(method, before = false, action)
    }
    private fun before(method: Method, action: (ModernMethodHook.MethodHookParam) -> Unit) {
        install(method, before = true, action)
    }
    private fun install(method: Method, before: Boolean, action: (ModernMethodHook.MethodHookParam) -> Unit) {
        ModernHookRegistry.installCompat("$TAG:${method.declaringClass.name}:${method.name}", method,
            object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) { if (before) guarded { action(param) } }
                override fun afterHookedMethod(param: MethodHookParam) { if (!before) guarded { action(param) } }
            })
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) guarded(action)
        else main.post { guarded(action) }
    }

    private fun guarded(action: () -> Unit) {
        if (HotReloadLifecycle.isRetiring) return
        runCatching(action).onFailure {
            failed = true; state.cancel(); main.removeCallbacks(tick)
            runCatching { window.remove() }; runCatching { native.clear() }; runCatching { avoidance.clear() }
            HookLog.w(TAG, "Face capsule stopped; native indicators restored", it)
        }
    }
}
