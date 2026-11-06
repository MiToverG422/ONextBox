package com.mi.onextbox.lsp

internal object FaceAuthCapsuleMotion {
    const val SUCCESS_DURATION = 1520L
    const val HIDE_DURATION = 260L

    class Curves(val geometry: (Float) -> Float, val content: (Float) -> Float)

    data class Frame(
        val opacity: Float,
        val expansion: Float,
        val retraction: Float,
        val unlock: Float,
        val contentOpacity: Float,
        val contentScale: Float,
        val shapeStretch: Float,
    )

    fun success(elapsed: Long, curves: Curves, initialOpacity: Float = 0f): Frame {
        val expand = curves.geometry(fraction(elapsed, 40, 340))
        val settle = curves.geometry(fraction(elapsed, 340, 300))
        val retract = curves.geometry(fraction(elapsed, 1120, 400))
        val enter = curves.content(fraction(elapsed, 80, 180))
        val exit = curves.content(fraction(elapsed, 1080, 160))
        val unlock = curves.geometry(fraction(elapsed, 340, 400))
        return Frame(
            opacity = (initialOpacity.coerceIn(0f, 1f) +
                (1f - initialOpacity.coerceIn(0f, 1f)) * curves.content(fraction(elapsed, 0, 100))) *
                (1f - curves.content(fraction(elapsed, 1190, 330))),
            expansion = expand,
            retraction = retract,
            unlock = unlock,
            contentOpacity = 1f - exit,
            contentScale = (0.96f + 0.04f * enter) * (1f - 0.08f * exit),
            shapeStretch = 1f + 0.025f * maxOf(0f, expand - settle) * (1f - retract),
        )
    }

    private fun fraction(time: Long, start: Long, duration: Long) =
        ((time - start).toFloat() / duration).coerceIn(0f, 1f)
}
