package com.mi.onextbox.lsp

/** OEM animation selection and bounded timing, never a source of authentication */
internal object FaceTapFeedbackRules {
    const val COMMIT_DELAY_MS = 80L
    const val BASE = "com.oplus.systemui.keyguard.view.OplusAnimationDrawable"
    const val WATER = "com.oplus.systemui.keyguard.view.OplusWaterRippleAnimationDrawable"
    const val HY_WATER = "com.oplus.systemui.keyguard.view.OplusHyWaterAnimationDrawable"

    fun enabled(parent: Boolean, child: Boolean, systemAnimations: Boolean): Boolean =
        parent && child && systemAnimations

    fun supported(className: String, frames: Int, resourceName: String?): Boolean =
        frames > 0 && !resourceName.isNullOrBlank() && className in setOf(BASE, WATER, HY_WATER)

    // C17 may remove HY's constructor and initialize it through the base constructor.
    fun needsBaseConstructor(className: String, hasOwnConstructor: Boolean): Boolean =
        className == HY_WATER && !hasOwnConstructor

    // Values from C17 OplusAnimationDrawable.Options, not guessed playback speeds
    fun speedForDuration(duration: Int): Int? = when (duration) {
        64 -> 0
        48 -> 1
        33 -> 2
        20 -> 3
        42 -> 4
        else -> null
    }

    fun commitDelay(elapsed: Long): Long = COMMIT_DELAY_MS - elapsed.coerceIn(0L, COMMIT_DELAY_MS)

    fun cleanupDelay(frameDuration: Long, nativeRipple: Boolean): Long =
        (maxOf(frameDuration, if (nativeRipple) 800L else 0L) + 100L).coerceIn(100L, 5000L)

    /** Only the already-authorized, purely visual tail can outlive normal keyguard dismissal */
    fun cancelForHide(committed: Boolean, hard: Boolean): Boolean = hard || !committed

    fun sameSession(expectedUser: Int, expectedAt: Long, currentUser: Int?, currentAt: Long): Boolean =
        expectedAt > 0L && expectedUser == currentUser && expectedAt == currentAt

    fun mayCommit(expectedUser: Int, expectedAt: Long, state: FaceTapUnlockRules.State?, currentAt: Long): Boolean =
        state != null && FaceTapUnlockRules.eligible(state) &&
            sameSession(expectedUser, expectedAt, state.currentUser, currentAt)
}
