package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Keeps the live unified material during AOD; the native transition still owns content/effects. */
internal class FluidCloudAodMaterial(private val report: (String, Throwable) -> Unit) {
    private class State(val drawable: WeakReference<Any>, val shader: Boolean)
    private val states = WeakHashMap<View, State>()

    fun capture(): Array<View> = states.keys.toTypedArray()
    fun active(view: View): Boolean = states.containsKey(view) && progress(view) > 0f

    fun progress(view: View): Float = runCatching {
        val extension = Reflect.callMethod(view, "getExt") ?: return@runCatching 0f
        val repository = Reflect.getObjectField(extension, "oplusPanoramicAodRepository") ?: return@runCatching 0f
        FluidCloudAodMaterialRules.progress((Reflect.callMethod(repository, "getPanoramicAodProgress") as? Number)?.toFloat())
    }.getOrDefault(0f)

    fun prepare(view: View, proxy: Any?, rendererActive: Boolean, fraction: Float = progress(view)): Boolean {
        return runCatching {
            if (!FluidCloudAodMaterialRules.use(true,
                    view.parent?.javaClass?.name == "com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow",
                    fraction, rendererActive)) {
                release(view)
                return@runCatching false
            }
            val actual = proxy?.let { Reflect.callMethod(it, "getBlurDrawable", null) }
            if (actual == null) {
                release(view)
                return@runCatching false
            }
            val mix = if (actual.javaClass.simpleName == "PlatformBlurDrawable") {
                Reflect.callMethod(actual, "getCurrentBlurMixConfig")
            } else null
            val shader = FluidCloudAodMaterialRules.supportsShaderMix(mix?.javaClass?.simpleName)
            val previous = states[view]
            val state = if (previous != null && previous.drawable.get() === actual && previous.shader == shader) previous else {
                previous?.let(::restoreShader)
                State(WeakReference(actual), shader)
            }
            // Acquire ownership before writing so a failed native call can be rolled back.
            states[view] = state
            if (shader) {
                // getCurrentBlurMixConfig is the live base recipe: native AOD writes with
                // changeBlurColor(..., false) do not replace it. Read it on every update,
                // including resource/renderer changes, rather than freezing a stale snapshot.
                Reflect.callMethod(actual, "setAodState", true)
                Reflect.callMethod(actual, "changeBlurColor", mix, false)
            }
            // Wallpaper renderers also retain their live recipe. No opaque dark fallback
            // is added over an active renderer; the draw scope hides its legacy AOD mask.
            true
        }.onFailure {
            release(view)
            report("Cannot preserve unified notification AOD material", it)
        }.getOrDefault(false)
    }

    fun release(view: View) {
        val state = states.remove(view) ?: return
        runCatching { restoreShader(state) }
            .onFailure { report("Cannot restore native AOD background", it) }
    }

    fun releaseAll() { states.keys.toList().forEach(::release) }

    private fun restoreShader(state: State) {
        if (!state.shader) return
        val drawable = state.drawable.get() ?: return
        Reflect.callMethod(drawable, "setAodState", false)
        val origin = Reflect.callMethod(drawable, "getCurrentBlurMixConfig") ?: return
        Reflect.callMethod(drawable, "changeBlurColor", origin, true)
    }
}
