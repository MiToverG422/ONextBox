package com.mi.onextbox.lsp

import kotlin.math.PI

internal object LauncherIosMotionProfile {
    enum class RouteKind { Opening, Closing, SwipeToHome }
    data class SpringSpec(val stiffness: Float, val damping: Float)
    data class Config(
        val openSpeed: Int = LauncherIosMotionSettings.openSpeed.default,
        val closeSpeed: Int = LauncherIosMotionSettings.closeSpeed.default,
        val slowMotion: Int = LauncherIosMotionSettings.slow.default,
        val bounceStrength: Int = 0,
    )
    fun classify(typeName: String): RouteKind? = when (typeName) {
        "LAUNCH_FROM_HOME", "PRE_START3_LAUNCH", "SWIPE_BREAK_LAUNCH" -> RouteKind.Opening
        "REMOTE_CLOSE_TO_HOME", "PREDICTIVE_BACK_TO_HOME" -> RouteKind.Closing
        "SWIPE_TO_HOME", "SWIPE_TO_CLIENT_HOME" -> RouteKind.SwipeToHome
        else -> null
    }
    fun response(opening: Boolean, config: Config): Double {
        val speed = (if (opening) config.openSpeed else config.closeSpeed).coerceIn(50, 200)
        return (if (opening) .24 else .336) * config.slowMotion.coerceIn(100, 500) / speed
    }
    fun spring(typeName: String, config: Config, route: RouteKind? = null): SpringSpec? {
        val kind = route ?: classify(typeName) ?: return null
        val frequency = 2.0 * PI / response(kind == RouteKind.Opening, config)
        val damping = if (kind == RouteKind.SwipeToHome) 1f - .20f * config.bounceStrength.coerceIn(0, 100) / 100f else 1f
        return SpringSpec((frequency * frequency).toFloat(), damping)
    }
    fun iconAlpha(nativeAlpha: Float, windowAlpha: Float, opening: Boolean): Float {
        if (!nativeAlpha.isFinite() || !windowAlpha.isFinite()) return nativeAlpha
        val t = ((windowAlpha - .08f) / .45f).coerceIn(0f, 1f)
        return nativeAlpha * (1f - t * t * (3f - 2f * t))
    }
    fun iconAlphaBeforeFit(nativeAlpha: Float, windowAlpha: Float, opening: Boolean, paired: Boolean): Float {
        if (!nativeAlpha.isFinite() || !windowAlpha.isFinite()) return nativeAlpha
        return if (paired || (windowAlpha > .005f && opening)) 0f else iconAlpha(nativeAlpha, windowAlpha, opening)
    }
    fun iconAttachment(minimized: Double): Float {
        val t = ((1.0 - minimized) / .12).coerceIn(0.0, 1.0).toFloat()
        return t * t * (3f - 2f * t)
    }
    fun cornerRadius(nativeRadius: Float, width: Int, height: Int, weight: Float): Float {
        val limit = minOf(width, height).coerceAtLeast(0) * .5f
        val radius = nativeRadius.takeIf(Float::isFinite)?.coerceIn(0f, limit) ?: limit
        return radius + (limit - radius) * weight.coerceIn(0f, 1f)
    }
}
