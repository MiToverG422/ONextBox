package com.mi.onextbox.lsp

internal object BackGestureRules {
    fun style(legacyAosp: Boolean, configured: Int): Int =
        when (configured) { 0 -> 0; 1, 2 -> 1; else -> if (legacyAosp) 0 else 1 }

    fun haptics(configured: Int, nativeAosp: Boolean): Int =
        if (configured in 0..1) configured else if (nativeAosp) 0 else 1

    fun areaAllows(y: Float, screenHeight: Int, heightPercent: Int, positionPercent: Int): Boolean {
        if (!y.isFinite() || screenHeight <= 0) return true
        val fraction = heightPercent.coerceIn(0, 100) / 100f
        if (fraction == 0f) return false
        val available = 1f - fraction
        val top = available * positionPercent.coerceIn(0, 100) / 100f
        val relative = y / screenHeight
        return relative >= top && relative <= top + fraction
    }

    fun releaseHaptic(enabled: Boolean, action: Int, accepted: Boolean, pointers: Int): Boolean =
        enabled && action == 1 && accepted && pointers == 1

    fun predictiveRoute(style: Int, edge: Int, type: Int, cancelled: Boolean): Boolean =
        style == 1 && edge in 0..1 && type in 2..3 && !cancelled

    fun closingAlpha(progress: Float): Float =
        if (progress.isFinite()) (1f - 5f * progress.coerceIn(0f, 1f)).coerceIn(0f, 1f) else 1f

    data class HomeFrame(val left: Float, val top: Float, val width: Float, val height: Float, val progress: Float)

    fun homePreview(width: Float, height: Float, progress: Float, edge: Int, deltaY: Float, margin: Float): HomeFrame? {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
            !progress.isFinite() || !deltaY.isFinite() || !margin.isFinite() || edge !in 0..1) return null
        val bounded = progress.coerceIn(0f, 1f)
        val eased = 1f - (1f - bounded) * (1f - bounded)
        val scale = 1f - 0.15f * eased
        val w = width * scale
        val h = height * scale
        val inset = margin.coerceIn(0f, minOf(width, height) * 0.075f)
        val y = (kotlin.math.abs(deltaY) / (height * 0.5f)).coerceIn(0f, 1f)
        val yEase = 1f - (1f - y) * (1f - y)
        val shift = maxOf(0f, (height - h) * 0.5f - inset) * yEase * kotlin.math.sign(deltaY)
        val left = if (edge == 1) eased * inset else width - w - eased * inset
        return HomeFrame(left, (height - h) * 0.5f + shift, w, h, eased)
    }
}

internal class BackGestureRouteSlot<T : Any> {
    private var original: T? = null
    private var selected: T? = null
    val active: Boolean get() = selected != null

    fun replace(native: T, replacement: T): T {
        original = native
        selected = replacement
        return replacement
    }

    fun restore(current: T?): T? {
        val restored = if (selected != null && current === selected) original else current
        original = null
        selected = null
        return restored
    }
}
