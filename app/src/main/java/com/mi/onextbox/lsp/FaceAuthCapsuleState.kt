package com.mi.onextbox.lsp

internal class FaceAuthCapsuleState {
    enum class Phase { Hidden, Locked, Scanning, Success, Failed }
    var phase = Phase.Hidden
        private set
    private var deadline = 0L
    private var stopDeadline: Long? = null
    private var authenticated = false

    fun showLocked() {
        if (authenticated || phase != Phase.Hidden) return
        phase = Phase.Locked
        deadline = 0L
        stopDeadline = null
    }

    fun start(now: Long) {
        if (authenticated || phase == Phase.Scanning || phase == Phase.Success) return
        phase = Phase.Scanning
        deadline = now + 10_000L
        stopDeadline = null
    }

    fun stopped(now: Long) {
        if (phase == Phase.Scanning && stopDeadline == null) stopDeadline = now + 400L
    }

    fun running(now: Long) {
        start(now)
        stopDeadline = null
    }

    // A fresh accepted native success may arrive without a running callback on fast unlocks.
    fun finish(success: Boolean, now: Long, freshNativeSuccess: Boolean = false,
        animationDuration: Long = FaceAuthCapsuleMotion.SUCCESS_DURATION): Boolean {
        if (authenticated || phase == Phase.Success) return false
        if (phase != Phase.Scanning && !(success && freshNativeSuccess)) return false
        phase = if (success) Phase.Success else Phase.Failed
        if (success) authenticated = true
        // Completion comes from the renderer; this is only a bounded orphan watchdog.
        deadline = now + if (success) animationDuration + 500L else 750L
        stopDeadline = null
        return true
    }

    fun animationFinished() { if (phase == Phase.Success) cancel() }

    fun fingerprintAuthenticated(now: Long, animationDuration: Long = FaceAuthCapsuleMotion.SUCCESS_DURATION) =
        finish(true, now, freshNativeSuccess = true, animationDuration = animationDuration)

    fun credentialAuthenticated(now: Long, accepted: Boolean,
        animationDuration: Long = FaceAuthCapsuleMotion.SUCCESS_DURATION) =
        accepted && finish(true, now, freshNativeSuccess = true, animationDuration = animationDuration)

    fun resetSession() { cancel(); authenticated = false }

    fun cancel() { phase = Phase.Hidden; deadline = 0L; stopDeadline = null }

    fun tick(now: Long, allowed: Boolean, keyguardShowing: Boolean = true) {
        if (!allowed || (!keyguardShowing && phase != Phase.Success) ||
            (phase != Phase.Locked && now >= deadline) ||
            (phase == Phase.Scanning && stopDeadline?.let { now >= it } == true)) cancel()
    }
}
