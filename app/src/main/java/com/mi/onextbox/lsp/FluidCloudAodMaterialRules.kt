package com.mi.onextbox.lsp

internal object FluidCloudAodMaterialRules {
    fun progress(value: Float?): Float = value?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    fun use(enabled: Boolean, customRow: Boolean, fraction: Float, rendererActive: Boolean): Boolean =
        enabled && customRow && rendererActive && progress(fraction) > 0f

    fun supportsShaderMix(name: String?): Boolean =
        name == "BlurMixSingle" || name == "BlurMixMulti" || name == "BlurMixMultiWithShader"

    fun needsEffectUpdate(refresh: Boolean, previous: Float?, fraction: Float): Boolean =
        (refresh || previous != fraction) && (progress(fraction) > 0f || progress(previous) > 0f)

    fun drawMaterialTint(fraction: Float, handled: Boolean): Boolean =
        progress(fraction) == 0f || handled

}

/** Restricts normal-background color preservation to its native update, including stack copies. */
internal class FluidCloudAodColorScope {
    private val active = ThreadLocal<Any?>()
    fun preservesColor(color: Any?): Boolean = color != null && active.get() === color
    fun preservesBackground(): Boolean = active.get() != null
    fun <T> duringUpdate(color: Any?, update: () -> T): T {
        val previous = active.get()
        if (color == null) active.remove() else active.set(color)
        return try { update() } finally {
            if (previous == null) active.remove() else active.set(previous)
        }
    }
}

/** Masks only the current unified row's legacy AOD layer, never another row or nested draw. */
internal class FluidCloudAodDrawScope {
    private val active = ThreadLocal<Any?>()
    fun hidesMask(extension: Any?): Boolean = extension != null && active.get() === extension
    fun <T> duringDraw(extension: Any?, draw: () -> T): T {
        val previous = active.get()
        if (extension == null) active.remove() else active.set(extension)
        return try { draw() } finally {
            if (previous == null) active.remove() else active.set(previous)
        }
    }
}
