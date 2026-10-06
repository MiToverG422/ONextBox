package com.mi.onextbox.lsp

/** No cached face result alone can authorize a dismiss request. All live gates must agree. */
internal object FaceTapUnlockRules {
    const val HOLD_TO_ENTER_MS = 600L
    data class State(
        val sessionUser: Int?,
        val currentUser: Int,
        val faceAuthenticated: Boolean,
        val faceAllowed: Boolean,
        val canSkipBouncer: Boolean,
        val showing: Boolean,
        val interactive: Boolean,
        val blocked: Boolean,
    )

    fun eligible(state: State): Boolean = with(state) {
        sessionUser != null && sessionUser == currentUser && faceAuthenticated && faceAllowed &&
            canSkipBouncer && showing && interactive && !blocked
    }

    fun mayBegin(eventTime: Long, authenticatedAt: Long): Boolean =
        authenticatedAt > 0L && eventTime >= authenticatedAt

    fun nativeIconVisible(viewShown: Boolean, alpha: Float, hasDrawable: Boolean): Boolean =
        viewShown && alpha.isFinite() && alpha > 0.05f && hasDrawable

    data class IconBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Float, y: Float): Boolean = x.isFinite() && y.isFinite() &&
            x >= left && x < right && y >= top && y < bottom
    }

    /** Matches C17 isInFingerViewArea, the animation container may be screen-width sized. */
    fun nativeIconBounds(
        locationX: Int, locationY: Int, containerWidth: Int, containerHeight: Int,
        drawableWidth: Int, drawableHeight: Int, zoom: Float, screenWidth: Int, screenHeight: Int,
    ): IconBounds? {
        if (screenWidth <= 0 || screenHeight <= 0 || containerWidth <= 0 || containerHeight <= 0 ||
            drawableWidth <= 0 || drawableHeight <= 0 || !zoom.isFinite() || zoom <= 0f) return null
        val width = drawableWidth * zoom
        val height = drawableHeight * zoom
        if (!width.isFinite() || !height.isFinite() || width < 1f || height < 1f ||
            width > screenWidth / 3f || height > screenHeight / 3f) return null
        val iconWidth = width.toInt()
        val iconHeight = height.toInt()
        // Native CENTER image view is scaled about its pivot, C17 adjusts its negative origin.
        val x = locationX.toLong().coerceAtLeast(0L)
        val y = locationY.toLong() + if (locationX < 0) -locationX.toLong() else 0L
        val left = x + containerWidth / 2 - iconWidth / 2
        val top = y + containerHeight / 2 - iconHeight / 2
        val right = left + iconWidth
        val bottom = top + iconHeight
        if (left < 0 || top < 0 || right > screenWidth || bottom > screenHeight) return null
        return IconBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    /** A consumed gesture stays consumed, but movement/cancel/revocation never triggers unlock. */
    class Tap(private val slop: Float) {
        private var x = 0f
        private var y = 0f
        private var started = 0L
        private var user: Int? = null
        private var valid = false
        private var bounds: IconBounds? = null
        var active: Boolean = false
            private set
        val validForFeedback: Boolean get() = active && valid

        fun matchesPress(downTime: Long): Boolean = active && started == downTime

        fun begin(x: Float, y: Float, time: Long, user: Int, bounds: IconBounds? = null) {
            this.x = x
            this.y = y
            started = time
            this.user = user
            this.bounds = bounds
            active = true
            valid = x.isFinite() && y.isFinite() && (bounds == null || bounds.contains(x, y))
        }

        fun move(x: Float, y: Float) {
            val dx = x - this.x
            val dy = y - this.y
            val area = bounds
            // A fingerprint contact may drift within its sensor area, it is not a click gesture.
            // Once it leaves the stock icon area it cannot re-arm by moving back into it.
            val outside = if (area != null) !area.contains(x, y) else dx * dx + dy * dy > slop * slop
            if (!x.isFinite() || !y.isFinite() || outside) valid = false
        }

        fun invalidate() { valid = false }

        /** A stationary hold completes once, authorization is supplied from a fresh live read */
        fun finishHold(time: Long, currentUser: Int, eligible: Boolean): Boolean {
            if (!active || !valid || time < started ||
                time - started < HOLD_TO_ENTER_MS) return false
            val result = eligible && user == currentUser
            valid = false
            return result
        }

        fun finish(x: Float, y: Float, time: Long, currentUser: Int, eligible: Boolean): Boolean {
            move(x, y)
            val result = active && valid && eligible && user == currentUser &&
                time >= started
            reset()
            return result
        }

        fun reset() {
            active = false
            valid = false
            user = null
            bounds = null
        }
    }
}
