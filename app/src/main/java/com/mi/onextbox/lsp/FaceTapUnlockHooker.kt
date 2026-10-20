package com.mi.onextbox.lsp

import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import com.mi.onextbox.lsp.LspConfig.KeyguardFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Lockscreen entry after live face authentication, unsupported interfaces remain untouched. */
internal object FaceTapUnlockHooker {
    private const val TAG = "ONextBox-FaceTap"
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val failed = AtomicBoolean(false)
    @Volatile private var sessionUser: Int? = null
    @Volatile private var authenticatedAt = 0L
    @Volatile private var mech = WeakReference<Any>(null)
    private var panel = WeakReference<FrameLayout>(null)
    private var button: ImageView? = null
    private var snapshot: IconSnapshot? = null
    private var tap: FaceTapUnlockRules.Tap? = null
    private val nativePress = FaceTapUnlockRules.NativePress()
    private val nativeHandoff = FaceTapNativeHandoff()
    private val pointerHookMethods = mutableSetOf<Method>()
    private val pointerHookUnavailable = AtomicBoolean(false)
    private var gracePolls = 0
    private var faceSource: Any? = null
    private var fingerprintUtils: Any? = null
    private var lastStateDiagnostic: String? = null
    private var lastCaptureDiagnostic: String? = null
    private val feedback = NativeFingerprintFeedback()
    private val haptics = NativeFingerprintHaptics()
    private var wake = FaceTapWakeHandoff()
    private var pendingDismiss: Runnable? = null
    private var pendingHold: Runnable? = null
    private val pendingOpticalEvents = AtomicInteger()

    private data class IconSnapshot(
        val drawable: Drawable,
        val bounds: Rect,
        val displayId: Int,
        val screenWidth: Int,
        val screenHeight: Int,
    )

    private val refresh = Runnable { guarded { reconcile() } }
    private fun enabled() = !HotReloadLifecycle.isRetiring && !failed.get() &&
        LspConfig.isKeyguardFeatureEnabledXposed(KeyguardFeature.FaceTapUnlock)
    private fun feedbackEnabled() = FaceTapFeedbackRules.enabled(
        enabled(), LspConfig.isKeyguardFeatureEnabledXposed(KeyguardFeature.FaceTapAnimation),
        ValueAnimator.areAnimatorsEnabled(),
    )

    fun hook(loader: ClassLoader) {
        HotReloadLifecycle.register("face-tap:session",
            canReload = {
                // Never migrate an authorization or a press which might later dismiss keyguard.
                sessionUser == null && pendingDismiss == null && pendingHold == null &&
                    pendingOpticalEvents.get() == 0 && !nativePress.active && !nativeHandoff.pending &&
                    !feedback.isActive && !wake.running && !wake.busy
            },
            capture = { arrayOf(panel.get(), mech.get()) },
            detach = {
                sessionUser = null
                cancelFeedbackAndDismiss()
                feedback.clear()
                wake.dispose()
                main.removeCallbacksAndMessages(null)
                tap?.invalidate()
                tap = null
                nativePress.reset()
                nativeHandoff.reset()
                snapshot = null
                removeButton()
                panel.clear()
                mech.clear()
            },
            restore = { saved ->
                val values = saved as? Array<*>
                val host = values?.getOrNull(0) as? FrameLayout
                panel = WeakReference(host)
                mech = WeakReference(values?.getOrNull(1))
                wake = FaceTapWakeHandoff()
                if (host != null) tap = FaceTapUnlockRules.Tap(ViewConfiguration.get(host.context).scaledTouchSlop.toFloat())
                // A fresh native authentication event is required after the handover.
                sessionUser = null
                authenticatedAt = 0L
                nativeHandoff.reset()
            },
            resumeAfterRejection = ::resumeAfterRejectedReload)
        if (!enabled()) return
        guarded {
            val monitor = Reflect.findClass("com.android.keyguard.KeyguardUpdateMonitor", loader)
            val target = Reflect.findClass("com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintUiMech", loader)
            val icon = Reflect.findClass("com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintIcon", loader)
            val host = Reflect.findClass("com.android.systemui.shade.NotificationPanelView", loader)
            val state = Reflect.findClass("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", loader)
            val auth = Reflect.findClass("com.oplus.systemui.biometrics.OplusBiometricAuthController", loader)
            val mediator = Reflect.findClass("com.android.systemui.keyguard.KeyguardViewMediator", loader)
            val biometricSource = Reflect.findClass("android.hardware.biometrics.BiometricSourceType", loader)
            faceSource = biometricSource.getField("FACE").get(null)
            val utils = Reflect.findClass("com.oplus.systemui.biometrics.finger.KeyguardFingerprintUtils", loader)
            Reflect.findDeclaredMethodExact(utils, "getZoomOut")
            fingerprintUtils = utils.getField("INSTANCE").get(null)
            val integer = Int::class.javaPrimitiveType!!
            val boolean = Boolean::class.javaPrimitiveType!!
            // Resolve the whole contract before touching a critical system process.
            listOf("keyguardUpdateMonitor", "keyguardStateController", "oplusBiometricAuthController",
                "fpIcon", "imMobileDrawable", "fpShowReason", "hasInitFp", "hasHideIconByQs",
                "isGoingToSleep", "onDozeState", "onDreamingStart", "screenTurnedOff",
                "showForEnroll", "bouncerShowing").forEach(target::getDeclaredField)
            listOf("mShowing", "mOccluded", "mKeyguardGoingAway", "mKeyguardFadingAway",
                "mPrimaryBouncerShowing").forEach(state::getDeclaredField)
            listOf("mDeviceInteractive", "mGoingToSleep", "mSwitchingUser").forEach(monitor::getDeclaredField)
            auth.getDeclaredField("keyguardViewMediatorLazy")
            icon.getDeclaredField("onScreenFingerprintUiMech")
            host.getDeclaredField("mDozing")
            Reflect.findDeclaredMethodExact(monitor, "getCurrentUser")
            Reflect.findDeclaredMethodExact(monitor, "getIsFaceAuthenticated")
            Reflect.findDeclaredMethodExact(monitor, "isUnlockingWithBiometricAllowed", biometricSource)
            Reflect.findDeclaredMethodExact(monitor, "getUserCanSkipBouncer", integer)
            Reflect.findDeclaredMethodExact(monitor, "isEncryptedOrLockdown", integer)
            Reflect.findDeclaredMethodExact(monitor, "isSimPinSecure")
            Reflect.findDeclaredMethodExact(monitor, "isBouncerShowing")
            Reflect.findDeclaredMethodExact(auth, "isBiometricPromptShowing")
            Reflect.findDeclaredMethodExact(mediator, "dismiss", Reflect.findClass("com.android.internal.policy.IKeyguardDismissCallback", loader), CharSequence::class.java)
            val face = Reflect.findDeclaredMethodExact(monitor, "handleFaceAuthenticated", integer, boolean)
            val clears = listOf(
                Reflect.findDeclaredMethodExact(monitor, "handleStartedGoingToSleep", integer),
                Reflect.findDeclaredMethodExact(monitor, "handleUserSwitching", integer, Runnable::class.java),
                Reflect.findDeclaredMethodExact(monitor, "handleUserSwitchComplete", integer),
            )
            val reset = Reflect.findDeclaredMethodExact(monitor, "handleKeyguardReset")
            val show = Reflect.findDeclaredMethodExact(target, "fpIconShow", integer, boolean)
            val hide = Reflect.findDeclaredMethodExact(target, "fpIconHide", integer)
            val stopReveal = Reflect.findDeclaredMethodExact(target, "stopOpticalAnimation")
            val visibility = Reflect.findDeclaredMethodExact(icon, "onVisibilityChanged", View::class.java, integer)
            val iconTouch = Reflect.findDeclaredMethodExact(icon, "onTouchEvent", MotionEvent::class.java)
            val attach = Reflect.findDeclaredMethodExact(host, "onAttachedToWindow")
            val detach = Reflect.findDeclaredMethodExact(host, "onDetachedFromWindow")
            val dispatch = Reflect.findDeclaredMethodExact(host, "dispatchTouchEvent", MotionEvent::class.java)
            haptics.prepare(loader)
            val callbackBase = Reflect.findClass("android.hardware.fingerprint.FingerprintManager\$AuthenticationCallback", loader)
            fun replayPointers(saved: Any?) {
                val methods = (saved as? Array<*>)?.filterIsInstance<Method>().orEmpty() +
                    ModernHotReloadSession.current?.previousTargets.orEmpty()
                NativePointerCallbackReplay.owners(methods, callbackBase).forEach(::installNativePointerCallbacks)
            }
            HotReloadLifecycle.registerReplay("face-tap:pointer-hooks",
                capture = { pointerHookMethods.toTypedArray() }, replay = ::replayPointers)
            replayPointers(null)

            after(face) { param ->
                if (!enabled()) return@after
                cancelFeedbackAndDismiss()
                wake.cancelPlayback()
                tap?.invalidate()
                authenticatedAt = SystemClock.uptimeMillis()
                sessionUser = param.args[0] as? Int
                schedule()
            }
            clears.forEach { method -> before(method) { clearSession() } }
            // This method refreshes fingerprint listening, it does NOT clear face authentication.
            // Re-read live gates instead of discarding a still-valid face session on a UI refresh.
            after(reset) { schedule() }
            before(hide) { param -> observeMech(param.thisObject) }
            after(show) { param -> observeMech(param.thisObject) }
            before(stopReveal) { param ->
                if (!enabled() || !ValueAnimator.areAnimatorsEnabled()) return@before
                val source = param.thisObject ?: return@before
                val captured = wake.capture(source) ?: return@before
                onMain {
                    mech = WeakReference(source)
                    captureIcon(source)
                    wake.offer(captured)
                    schedule()
                }
            }
            after(visibility) { param ->
                param.thisObject?.let { observeMech(Reflect.getObjectField(it, "onScreenFingerprintUiMech")) }
            }
            after(attach) { param ->
                onMain {
                    cancelFeedbackAndDismiss(hard = false)
                    nativePress.reset()
                    nativeHandoff.reset()
                    wake.clear()
                    removeButton()
                    val view = param.thisObject as? FrameLayout ?: return@onMain
                    panel = WeakReference(view)
                    tap = FaceTapUnlockRules.Tap(ViewConfiguration.get(view.context).scaledTouchSlop.toFloat())
                    schedule()
                }
            }
            before(detach) { param ->
                onMain {
                    if (panel.get() === param.thisObject) {
                        cancelFeedbackAndDismiss(hard = false)
                        nativePress.reset()
                        nativeHandoff.reset()
                        wake.clear()
                        removeButton()
                        panel.clear()
                        tap?.reset()
                    }
                }
            }
            before(dispatch) { param ->
                if (panel.get() === param.thisObject && handleTouch(param.args[0] as MotionEvent)) param.result = true
            }
            // Some firmwares deliver the native icon's separate window touch before the panel.
            before(iconTouch) { param ->
                if (!enabled() ||
                    Reflect.getObjectField(mech.get() ?: return@before, "fpIcon") !== param.thisObject) return@before
                val event = param.args[0] as MotionEvent
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    if (handleTouch(event)) param.result = true
                } else {
                    // The optical UI has its own Looper, keep all auth/UI actions on main.
                    // Stock onTouchEvent already returns true without an authentication action.
                    val copy = MotionEvent.obtain(event)
                    pendingOpticalEvents.incrementAndGet()
                    if (!main.post {
                        try {
                            if (!HotReloadLifecycle.isRetiring) guarded { handleTouch(copy) }
                        } finally {
                            copy.recycle()
                            pendingOpticalEvents.decrementAndGet()
                        }
                    }) {
                        copy.recycle()
                        pendingOpticalEvents.decrementAndGet()
                    }
                }
            }
            HookLog.i(TAG, "Face tap interaction installed, native authentication unchanged")
        }
    }

    private fun before(method: Method, action: (ModernMethodHook.MethodHookParam) -> Unit) {
        ModernHookRegistry.installCompat("face-tap:${method.declaringClass.name}:${method.name}:before", method, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { guarded { action(param) } }
        })
    }

    private fun after(method: Method, action: (ModernMethodHook.MethodHookParam) -> Unit) {
        ModernHookRegistry.installCompat("face-tap:${method.declaringClass.name}:${method.name}:after", method, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable == null) guarded { action(param) }
            }
        })
    }

    private fun bool(target: Any, field: String) = Reflect.getObjectField(target, field) as Boolean
    private fun callBool(target: Any, name: String, vararg args: Any?) = Reflect.callMethod(target, name, *args) as Boolean

    private fun liveState(forNativeContact: Boolean = false): FaceTapUnlockRules.State? {
        if (!enabled()) return null
        val source = mech.get() ?: return null
        val host = panel.get() ?: return null
        if (!host.isAttachedToWindow || (!forNativeContact && !host.isShown) || host.display?.displayId != 0) return null
        val monitor = Reflect.getObjectField(source, "keyguardUpdateMonitor") ?: return null
        val state = Reflect.getObjectField(source, "keyguardStateController") ?: return null
        val auth = Reflect.getObjectField(source, "oplusBiometricAuthController") ?: return null
        val user = Reflect.callStaticMethod(monitor.javaClass, "getCurrentUser") as Int
        val reason = Reflect.getObjectField(source, "fpShowReason") as Int
        val hardBlocks = linkedMapOf(
            "monitorSleeping" to bool(monitor, "mGoingToSleep"),
            "switchingUser" to bool(monitor, "mSwitchingUser"),
            "occluded" to bool(state, "mOccluded"),
            "goingAway" to bool(state, "mKeyguardGoingAway"),
            "fadingAway" to bool(state, "mKeyguardFadingAway"),
            "primaryBouncer" to bool(state, "mPrimaryBouncerShowing"),
            "bouncer" to callBool(monitor, "isBouncerShowing"),
            "simPin" to callBool(monitor, "isSimPinSecure"),
            "encryptedOrLockdown" to callBool(monitor, "isEncryptedOrLockdown", user),
            "biometricPrompt" to callBool(auth, "isBiometricPromptShowing"),
            "qsHidingIcon" to bool(source, "hasHideIconByQs"),
            "sourceSleeping" to bool(source, "isGoingToSleep"),
            "enrolling" to bool(source, "showForEnroll"),
            "sourceBouncer" to bool(source, "bouncerShowing"),
            "nonKeyguardReason" to (reason != 4 && reason != -1),
        )
        val displayBlocks = linkedMapOf(
            "hostHidden" to !host.isShown,
            "fpNotInitialized" to !bool(source, "hasInitFp"),
            "screenOff" to bool(source, "screenTurnedOff"),
            "doze" to bool(source, "onDozeState"),
            "dreaming" to bool(source, "onDreamingStart"),
            "hostDozing" to bool(host, "mDozing"),
        )
        val hardBlocked = hardBlocks.values.any { it }
        return FaceTapUnlockRules.State(
            sessionUser = sessionUser,
            currentUser = user,
            faceAuthenticated = callBool(monitor, "getIsFaceAuthenticated"),
            faceAllowed = callBool(monitor, "isUnlockingWithBiometricAllowed", faceSource ?: return null),
            canSkipBouncer = callBool(monitor, "getUserCanSkipBouncer", user),
            showing = bool(state, "mShowing"),
            interactive = bool(monitor, "mDeviceInteractive"),
            blocked = hardBlocked || displayBlocks.values.any { it },
            handoffBlocked = hardBlocked,
            blockReasons = (hardBlocks + displayBlocks).filterValues { it }.keys.joinToString(","),
        )
    }

    private fun observeMech(value: Any?) {
        if (value == null || !enabled()) return
        onMain {
            mech = WeakReference(value)
            installNativePointerHooks(value)
            captureIcon(value)
            schedule()
        }
    }

    private fun installNativePointerHooks(source: Any) {
        // Resolve the actual callback instance, not firmware-specific anonymous class names.
        // onFpTouch(false) also occurs on auth failures, so it is NOT evidence of a real UP.
        runCatching {
            val monitor = Reflect.getObjectField(source, "keyguardUpdateMonitor") ?: return
            val callback = Reflect.getObjectField(monitor, "mFingerprintAuthenticationCallback") ?: return
            installNativePointerCallbacks(callback.javaClass)
        }.onFailure {
            if (pointerHookUnavailable.compareAndSet(false, true))
                HookLog.w(TAG, "Native pointer handoff unavailable; window touch retained", it)
        }
    }

    private fun installNativePointerCallbacks(callbackType: Class<*>) {
        val down = Reflect.findDeclaredMethodExact(callbackType, "onUdfpsPointerDown", Int::class.javaPrimitiveType!!)
        val up = Reflect.findDeclaredMethodExact(callbackType, "onUdfpsPointerUp", Int::class.javaPrimitiveType!!)
        check(down.returnType == Void.TYPE && up.returnType == Void.TYPE)
        val requiredMethods = setOf(down, up)
        var installed = false
        listOf(down to true, up to false).forEach { (method, isDown) ->
            if (method in pointerHookMethods) return@forEach
            before(method) { param ->
                // A partially installed pair must not arm contacts without a sensor UP hook.
                if (!pointerHookMethods.containsAll(requiredMethods)) return@before
                val at = SystemClock.uptimeMillis()
                val eventCallback = param.thisObject
                onMain {
                    val current = mech.get() ?: return@onMain
                    val currentMonitor = Reflect.getObjectField(current, "keyguardUpdateMonitor") ?: return@onMain
                    if (Reflect.getObjectField(currentMonitor, "mFingerprintAuthenticationCallback") !== eventCallback) return@onMain
                    val state = liveState(forNativeContact = true)
                    val wasPending = nativeHandoff.pending
                    if (isDown) nativeHandoff.pointerDown(at, state)
                    else nativeHandoff.pointerUp(at, state)
                    if (nativeHandoff.pending || wasPending) {
                        HookLog.d(TAG, "Native sensor ${if (isDown) "DOWN" else "UP"}; pending=${nativeHandoff.pending}")
                    }
                    schedule()
                }
            }
            pointerHookMethods += method
            installed = true
        }
        if (installed) HookLog.i(TAG, "Native UDFPS pointer callbacks installed")
    }

    private fun captureIcon(source: Any) {
        val icon = Reflect.getObjectField(source, "fpIcon") as? ImageView ?: return
        val reason = Reflect.getObjectField(source, "fpShowReason") as Int
        if (reason != 4 && reason != -1 || !icon.isAttachedToWindow || icon.width <= 0 || icon.height <= 0) return
        val metrics = icon.resources.displayMetrics
        if (ValueAnimator.areAnimatorsEnabled()) wake.prepare(source)
        // Copy the idle drawable, not a face-success fade-out or fingerprint HBM frame.
        val idle = Reflect.getObjectField(source, "imMobileDrawable") as? Drawable ?: return
        val copy = idle.constantState?.newDrawable(icon.resources)?.mutate() ?: return
        val location = IntArray(2).also(icon::getLocationOnScreen)
        val zoom = Reflect.callMethod(fingerprintUtils ?: return, "getZoomOut") as Float
        val content = FaceTapUnlockRules.nativeIconBounds(
            location[0], location[1], icon.measuredWidth, icon.measuredHeight,
            idle.intrinsicWidth, idle.intrinsicHeight, zoom, metrics.widthPixels, metrics.heightPixels,
        )
        val captureDiagnostic = "Icon geometry container=${icon.measuredWidth}x${icon.measuredHeight}, content=" +
            if (content == null) "unavailable" else "${content.right - content.left}x${content.bottom - content.top}"
        if (captureDiagnostic != lastCaptureDiagnostic) {
            lastCaptureDiagnostic = captureDiagnostic
            HookLog.d(TAG, captureDiagnostic)
        }
        if (content == null) return
        val bounds = Rect(content.left, content.top, content.right, content.bottom)
        copy.state = idle.state.clone()
        copy.alpha = 255
        icon.imageTintList?.let(copy::setTintList)
        snapshot = IconSnapshot(copy, bounds, icon.display?.displayId ?: -1, metrics.widthPixels,
            metrics.heightPixels)
        // Decode the first frame and prepare the owned renderer before an authorized press.
        if (feedbackEnabled() && !feedback.isActive) feedback.prepare(source)
    }

    private fun schedule() {
        onMain {
            main.removeCallbacks(refresh)
            gracePolls = 4
            main.post(refresh)
        }
    }

    private fun resumeAfterRejectedReload() {
        if (panel.get() == null && sessionUser == null && pendingHold == null && pendingDismiss == null) return
        guarded {
            // Never replay a press or delayed dismissal across a pause. Keep the native session,
            // re-check its live eligibility, and require a fresh press for any dismissal action.
            tap?.invalidate()
            nativePress.invalidate()
            nativeHandoff.invalidate()
            cancelFeedbackAndDismiss(hard = false)
            main.removeCallbacks(refresh)
            gracePolls = 4
            reconcile()
        }
    }

    private fun reconcile() {
        main.removeCallbacks(refresh)
        wake.expire()
        val state = liveState()
        val now = SystemClock.uptimeMillis()
        val contactState = state ?: liveState(forNativeContact = true)
        if (nativeHandoff.claim(now, authenticatedAt, contactState)) {
            HookLog.d(TAG, "Native sensor press handed off after live face authentication")
            requestDismiss(immediate = true, animate = false)
            return
        }
        if (!FaceTapUnlockRules.contactAllowed(state)) nativePress.invalidate()
        val eligible = state != null && FaceTapUnlockRules.eligible(state)
        val host = panel.get()
        val source = mech.get()
        val diagnostic = "State session=${sessionUser != null}, host=${host?.isAttachedToWindow == true}, mech=${source != null}, " +
            "face=${state?.faceAuthenticated}, allowed=${state?.faceAllowed}, skip=${state?.canSkipBouncer}, " +
            "showing=${state?.showing}, interactive=${state?.interactive}, blocked=${state?.blocked}, " +
            "eligible=$eligible, icon=${snapshot != null}, handoff=${nativeHandoff.pending}, reasons=${contactState?.blockReasons}"
        if (diagnostic != lastStateDiagnostic) {
            lastStateDiagnostic = diagnostic
            HookLog.d(TAG, diagnostic)
        }
        if (eligible && host != null && source != null) {
            if (snapshot == null) captureIcon(source)
            if (feedbackEnabled() && !feedback.isActive) feedback.prepare(source)
            val saved = snapshot
            val metrics = host.resources.displayMetrics
            val native = Reflect.getObjectField(source, "fpIcon") as? ImageView
            if (saved != null && saved.displayId == host.display?.displayId &&
                saved.screenWidth == metrics.widthPixels && saved.screenHeight == metrics.heightPixels) {
                // C17 hides by clearing the drawable while its animation view stays VISIBLE.
                if (native != null && FaceTapUnlockRules.nativeIconVisible(native.isShown, native.alpha, native.drawable != null)) {
                    if (wake.running) wake.clear()
                    removeButton()
                } else {
                    showButton(host, saved)
                    if (ValueAnimator.areAnimatorsEnabled()) wake.show(host, saved.bounds, source,
                        eligible = { liveState()?.let(FaceTapUnlockRules::eligible) == true },
                        onEnd = { button?.alpha = if (wake.running) 0f else 1f })
                }
            } else {
                wake.clear()
                removeButton()
            }
            gracePolls = 4
        } else {
            tap?.invalidate()
            cancelFeedbackAndDismiss(hard = false)
            wake.cancelPlayback()
            removeButton()
        }
        if (!feedbackEnabled()) feedback.clear()
        if (!enabled() || !ValueAnimator.areAnimatorsEnabled()) wake.clear()
        // Don't exhaust the ordinary grace polls at frame cadence while AOD is still exiting
        val handoffActive = wake.awaiting || wake.running
        if (enabled() && (handoffActive || nativeHandoff.pending || sessionUser != null && (eligible || gracePolls-- > 0)))
            main.postDelayed(refresh, if (handoffActive) 16L else if (nativeHandoff.pending) FaceTapNativeHandoff.POLL_MS else 500L)
    }

    private fun showButton(host: FrameLayout, saved: IconSnapshot) {
        val location = IntArray(2).also(host::getLocationOnScreen)
        val left = saved.bounds.left - location[0]
        val top = saved.bounds.top - location[1]
        if (left < 0 || top < 0 || left + saved.bounds.width() > host.width || top + saved.bounds.height() > host.height) {
            removeButton()
            return
        }
        val view = button ?: ImageView(host.context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = runCatching {
                ModuleStrings.get(host.context, "keyguard_face_tap_action")
            }.getOrDefault("Unlock")
            isClickable = true
            // Do not depend solely on the panel/native window forwarding ACTION_DOWN.
            // The owned icon uses the same press stream as those two routes, including holds.
            setOnTouchListener { _, event ->
                var consumed = false
                guarded { consumed = handleTouch(event) }
                if (consumed && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                consumed
            }
            setOnClickListener { guarded { requestDismiss() } }
            host.addView(this)
            button = this
        }
        if (view.drawable !== saved.drawable) view.setImageDrawable(saved.drawable)
        // The compact child represents the scaled drawable, not the full animation container.
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        view.setPadding(0, 0, 0, 0)
        val old = view.layoutParams as? FrameLayout.LayoutParams
        if (old == null || old.width != saved.bounds.width() || old.height != saved.bounds.height() || old.leftMargin != left || old.topMargin != top) {
            view.layoutParams = FrameLayout.LayoutParams(saved.bounds.width(), saved.bounds.height(), Gravity.TOP or Gravity.LEFT).apply {
                leftMargin = left
                topMargin = top
            }
        }
        view.bringToFront()
        view.alpha = if (wake.running) 0f else 1f
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        observeNativeHandoffTouch(event)
        if (observeNativePress(event)) return false
        val gesture = tap ?: return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // A native-window/panel handoff can forward the same press twice, do not restart
            // its hold deadline, nor re-arm a press already canceled by movement/revocation.
            if (gesture.matchesPress(event.downTime)) {
                if (event.pointerCount != 1 || touchObscured(event)) {
                    gesture.invalidate()
                    cancelHold()
                    feedback.cancelUncommitted()
                }
                return true
            }
            gesture.reset()
            cancelHold()
            val state = liveState() ?: return false
            val saved = snapshot ?: return false
            val host = panel.get() ?: return false
            val metrics = host.resources.displayMetrics
            if (!FaceTapUnlockRules.eligible(state) || saved.screenWidth != metrics.widthPixels ||
                saved.screenHeight != metrics.heightPixels || saved.displayId != host.display?.displayId ||
                !FaceTapUnlockRules.mayBegin(event.downTime, authenticatedAt) ||
                event.pointerCount != 1 || touchObscured(event)) return false
            val area = FaceTapUnlockRules.IconBounds(saved.bounds.left, saved.bounds.top,
                saved.bounds.right, saved.bounds.bottom)
            if (!area.contains(event.rawX, event.rawY)) return false
            gesture.begin(event.rawX, event.rawY, event.downTime, state.currentUser, area)
            HookLog.d(TAG, "Authorized fingerprint-area press began")
            feedback.cancelUncommitted()
            wake.clear()
            button?.alpha = 1f
            startFeedback()
            val expectedAt = authenticatedAt
            val expectedUser = state.currentUser
            val expectedPress = event.downTime
            val task = Runnable {
                if (!gesture.matchesPress(expectedPress)) return@Runnable
                pendingHold = null
                guarded {
                    val current = liveState()
                    if (FaceTapFeedbackRules.mayCommit(expectedUser, expectedAt, current, authenticatedAt) &&
                        gesture.finishHold(SystemClock.uptimeMillis(), current?.currentUser ?: -1, true)) {
                        HookLog.d(TAG, "Authorized fingerprint press committed at ${FaceTapUnlockRules.HOLD_TO_ENTER_MS} ms")
                        requestDismiss(immediate = true)
                    } else {
                        gesture.invalidate()
                        feedback.cancelUncommitted()
                        HookLog.d(TAG, "Fingerprint hold canceled, live state or contact no longer eligible")
                    }
                }
            }
            pendingHold = task
            val elapsed = (SystemClock.uptimeMillis() - event.downTime).coerceAtLeast(0L)
            if (!main.postDelayed(task, (FaceTapUnlockRules.HOLD_TO_ENTER_MS - elapsed).coerceAtLeast(0L))) pendingHold = null
            return true
        }
        if (!gesture.matchesPress(event.downTime)) return false
        if (event.pointerCount != 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
            touchObscured(event)) gesture.invalidate()
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> gesture.move(event.rawX, event.rawY)
            MotionEvent.ACTION_CANCEL -> {
                gesture.reset()
                cancelHold()
                feedback.cancelUncommitted()
                HookLog.d(TAG, "Fingerprint press canceled by its touch window")
            }
            MotionEvent.ACTION_UP -> {
                cancelHold()
                val state = liveState()
                if (gesture.finish(event.rawX, event.rawY, event.eventTime, state?.currentUser ?: -1,
                        state != null && FaceTapUnlockRules.eligible(state))) requestDismiss()
                else feedback.cancelUncommitted()
            }
        }
        if (event.actionMasked != MotionEvent.ACTION_UP && !gesture.validForFeedback) {
            cancelHold()
            feedback.cancelUncommitted()
        }
        return true
    }

    private fun touchObscured(event: MotionEvent): Boolean =
        event.flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0

    private fun observeNativeHandoffTouch(event: MotionEvent) {
        if (!nativeHandoff.pending) return
        val saved = snapshot
        val outside = saved != null && !FaceTapUnlockRules.IconBounds(
            saved.bounds.left, saved.bounds.top, saved.bounds.right, saved.bounds.bottom,
        ).contains(event.rawX, event.rawY)
        if (event.pointerCount != 1 || touchObscured(event) ||
            !event.rawX.isFinite() || !event.rawY.isFinite() ||
            (outside && event.actionMasked != MotionEvent.ACTION_CANCEL)) {
            nativeHandoff.invalidate()
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> nativeHandoff.touchReleased(event.eventTime)
            MotionEvent.ACTION_CANCEL -> nativeHandoff.windowCanceled(liveState(forNativeContact = true))
        }
    }

    private fun observeNativePress(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !nativePress.matches(event.downTime)) {
            nativePress.reset()
            val state = liveState()
            val saved = snapshot
            val host = panel.get()
            if (state != null && !state.faceAuthenticated && saved != null && host != null &&
                saved.displayId == host.display?.displayId &&
                saved.screenWidth == host.resources.displayMetrics.widthPixels &&
                saved.screenHeight == host.resources.displayMetrics.heightPixels &&
                event.pointerCount == 1 && !touchObscured(event)) {
                nativePress.begin(event.rawX, event.rawY, event.downTime, state,
                    FaceTapUnlockRules.IconBounds(saved.bounds.left, saved.bounds.top,
                        saved.bounds.right, saved.bounds.bottom))
            }
        }
        if (!nativePress.matches(event.downTime)) return false
        val state = liveState()
        if (!FaceTapUnlockRules.contactAllowed(state) || event.pointerCount != 1 || touchObscured(event))
            nativePress.invalidate()
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> nativePress.move(event.rawX, event.rawY)
            MotionEvent.ACTION_POINTER_DOWN -> nativePress.invalidate()
            MotionEvent.ACTION_CANCEL -> nativePress.reset()
            MotionEvent.ACTION_UP -> {
                if (nativePress.finish(event.rawX, event.rawY, event.eventTime, authenticatedAt, state)) {
                    HookLog.d(TAG, "Native fingerprint press released after live face authentication")
                    requestDismiss(immediate = true, animate = false)
                }
            }
        }
        return true
    }

    private fun startFeedback(): Boolean {
        if (!feedbackEnabled()) return false
        val host = panel.get() ?: return false
        val saved = snapshot ?: return false
        val source = mech.get() ?: return false
        return feedback.release(host, saved.bounds, source)
    }

    private fun requestDismiss(immediate: Boolean = false, animate: Boolean = true) {
        // Always re-read after the gesture/accessibility click, never use the display-time result.
        val state = liveState() ?: return
        if (!FaceTapUnlockRules.eligible(state)) return
        if (pendingDismiss != null) return
        cancelHold()
        wake.clear()
        val expectedAt = authenticatedAt
        // A touch already started the effect on DOWN; release must not restart its first frame.
        // Accessibility clicks still get the same bounded feedback without a touch stream.
        if (animate && feedbackEnabled() && (feedback.isActive || startFeedback())) {
            if (immediate) {
                commitDismiss(state.currentUser, expectedAt)
                return
            }
            // Give the selected OEM animation a few frames, never wait for the entire effect.
            // Credentials and user/session are re-checked after this bounded visual delay.
            val task = Runnable {
                pendingDismiss = null
                guarded { commitDismiss(state.currentUser, expectedAt) }
            }
            pendingDismiss = task
            if (!main.postDelayed(task, feedback.commitDelay())) {
                pendingDismiss = null
                commitDismiss(state.currentUser, expectedAt)
            }
        } else {
            feedback.cancel()
            commitDismiss(state.currentUser, expectedAt)
        }
    }

    private fun commitDismiss(expectedUser: Int, expectedAt: Long) {
        val state = liveState()
        if (!FaceTapFeedbackRules.mayCommit(expectedUser, expectedAt, state, authenticatedAt)) {
            feedback.cancel()
            return
        }
        val source = mech.get() ?: return
        val auth = Reflect.getObjectField(source, "oplusBiometricAuthController") ?: return
        val lazy = Reflect.getObjectField(auth, "keyguardViewMediatorLazy") ?: return
        val mediator = Reflect.callMethod(lazy, "get") ?: return
        // Public request path leaves final auth/bouncer decisions to the stock keyguard.
        // Keep only the authorized visual tail, not any authentication/session capability
        feedback.allowFinish()
        haptics.playSuccess(auth)
        try {
            Reflect.callMethod(mediator, "dismiss", null, null)
        } catch (error: Throwable) {
            feedback.cancel()
            throw error
        }
        sessionUser = null
        nativePress.reset()
        nativeHandoff.reset()
        cancelHold()
        wake.clear()
        tap?.invalidate()
        removeButton()
        main.removeCallbacks(refresh)
    }

    private fun clearSession() {
        sessionUser = null
        authenticatedAt = 0L
        onMain {
            nativePress.reset()
            nativeHandoff.reset()
            cancelFeedbackAndDismiss()
            feedback.clear()
            wake.clear()
            main.removeCallbacks(refresh)
            tap?.invalidate()
            snapshot = null
            removeButton()
        }
    }

    private fun removeButton() {
        button?.let { (it.parent as? FrameLayout)?.removeView(it) }
        button = null
    }

    private fun cancelHold() {
        pendingHold?.let(main::removeCallbacks)
        pendingHold = null
    }

    private fun cancelFeedbackAndDismiss(hard: Boolean = true) {
        cancelHold()
        pendingDismiss?.let(main::removeCallbacks)
        pendingDismiss = null
        if (hard) feedback.cancel() else feedback.cancelUncommitted()
    }

    private fun onMain(action: () -> Unit) {
        if (HotReloadLifecycle.isRetiring) return
        if (Looper.myLooper() == Looper.getMainLooper()) guarded(action)
        else main.post { guarded(action) }
    }

    private fun guarded(action: () -> Unit) {
        if (failed.get() || HotReloadLifecycle.isRetiring) return
        runCatching(action).onFailure {
            if (failed.compareAndSet(false, true)) {
                sessionUser = null
                main.post {
                    nativePress.reset()
                    nativeHandoff.reset()
                    runCatching { cancelFeedbackAndDismiss() }
                    runCatching { feedback.clear() }
                    runCatching { wake.clear() }
                    main.removeCallbacks(refresh)
                    tap?.invalidate()
                    runCatching { removeButton() }
                }
                HookLog.w(TAG, "Face tap disabled, native lockscreen kept", it)
            }
        }
    }
}
