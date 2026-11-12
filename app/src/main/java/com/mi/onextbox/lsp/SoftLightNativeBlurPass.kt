package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.drawable.Drawable
import android.view.SurfaceControl
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.min
import kotlin.math.roundToInt

/** Adapts native blur where SurfaceFlinger owns the background pixels. Never draws over a window. */
internal class SoftLightNativeBlurPass(
    private val packageName: String,
    private val active: () -> Boolean,
    private val profile: () -> SoftLightGlassSettings.Profile,
    private val app: () -> Context?,
    private val report: (String) -> Unit,
) {
    private val radii = WeakHashMap<Drawable, Int>()
    private var setRadius: Method? = null
    private val keyguardTransition = ThreadLocal<Boolean>()

    private fun enabled(): Boolean {
        val p = profile()
        if (!active() || p[SoftLightGlassSettings.Parameter.STRENGTH] == 0f) return false
        return if (keyguardTransition.get() == true) p.selected(SoftLightGlassSettings.Scene.LOCK) else p.wholeProcess
    }

    private fun spread(value: Float, density: Float): Float {
        if (value <= 0f) return value
        val p = profile()
        val target = (value + min(value*.16f,12f*density))*p[SoftLightGlassSettings.Parameter.BLUR]
        return value + (target-value)*p[SoftLightGlassSettings.Parameter.STRENGTH]
    }

    fun install(loader: ClassLoader) {
        val systemUi = packageName == "com.android.systemui"
        val shelf = packageName == "com.coloros.assistantscreen"
        if (shelf) installShelfWindowFallback(loader)
        if (systemUi) {
            val lock = ModernReflect.findClassIfExists("com.oplus.systemui.keyguard.anim.transition.OplusMultiLayerBlurManager", loader)
            val method = lock?.declaredMethods?.singleOrNull { it.name == "applyBlur" &&
                it.returnType == SurfaceControl.Transaction::class.java && it.parameterTypes.contains(SurfaceControl::class.java) }
            if (method != null) ModernHookRegistry.installFast("soft-light:keyguard-transition", method, XposedInterface.Hooker { chain ->
                val previous = keyguardTransition.get()
                keyguardTransition.set(true)
                try { chain.proceed() } finally {
                    if (previous == null) keyguardTransition.remove() else keyguardTransition.set(previous)
                }
            })
        }
        val helper = ModernReflect.findClassIfExists("com.oplus.graphics.OplusRenderEffect",loader)
        helper?.declaredMethods?.singleOrNull { it.name == "createGradientBlurEffect" && it.parameterCount == 8 &&
            it.parameterTypes.last() == RenderEffect::class.java }?.let { method ->
            ModernHookRegistry.installFast("soft-light:gradient-blur", method, XposedInterface.Hooker { chain ->
                if (!enabled()) return@Hooker chain.proceed()
                val context = app() ?: return@Hooker chain.proceed()
                val first = chain.getArg(0) as Float
                val last = chain.getArg(1) as Float
                if (first <= 0f && last <= 0f) return@Hooker chain.proceed()
                val args = chain.args.toTypedArray()
                val density = context.resources.displayMetrics.density
                args[0] = spread(first,density)
                args[1] = spread(last,density)
                val mix = args[6] as? Int
                if (mix != null) {
                    val p = profile()
                    val base = if (shelf) {
                        val alpha = Color.alpha(mix) * (1f - .52f * p[SoftLightGlassSettings.Parameter.STRENGTH])
                        (mix and 0xffffff) or (alpha.roundToInt() shl 24)
                    } else mix
                    args[6] = p.adjustMixColor(base)
                }
                report("native gradient background")
                chain.proceed(args)
            })
        }

        if (packageName !in SoftLightGlassSettings.mainPackages) return
        val window = ModernReflect.findClassIfExists("com.android.internal.graphics.drawable.BackgroundBlurDrawable",loader)
        window?.getDeclaredMethod("setBlurRadius",Integer.TYPE)?.let { setter ->
            setRadius = setter
            ModernHookRegistry.installFast("soft-light:window-radius",setter,XposedInterface.Hooker { chain ->
                if (systemUi && keyguardTransition.get() != true) return@Hooker chain.proceed()
                val drawable = chain.thisObject as Drawable
                val radius = chain.getArg(0) as Int
                synchronized(radii) { if (radius > 0) radii[drawable] = radius else radii.remove(drawable) }
                if (radius <= 0 || !enabled()) return@Hooker chain.proceed()
                val density = app()?.resources?.displayMetrics?.density ?: return@Hooker chain.proceed()
                report("native window blur")
                chain.proceed(arrayOf(spread(radius.toFloat(),density).roundToInt()))
            })
        }

        val blur = ModernReflect.findClassIfExists("com.oplus.graphics.OplusBlurManager",loader) ?: return
        val param = ModernReflect.findClassIfExists("com.oplus.graphics.OplusBlurParam",loader) ?: return
        val method = blur.declaredMethods.singleOrNull { it.name == "setBackgroundBlurRadius" &&
            it.parameterCount == 4 && it.parameterTypes[2] == Integer.TYPE && it.parameterTypes[3] == param } ?: return
        val read = param.getMethod("toFloatArray")
        val write = param.getMethod("fromFloatArray",FloatArray::class.java)
        val constructor = param.getDeclaredConstructor()
        ModernHookRegistry.installFast("soft-light:surface-radius",method,XposedInterface.Hooker { chain ->
            if (systemUi && keyguardTransition.get() != true) return@Hooker chain.proceed()
            val radius = chain.getArg(2) as Int
            if (!enabled() || radius <= 0) return@Hooker chain.proceed()
            val original = chain.getArg(3) ?: return@Hooker chain.proceed()
            val context = app() ?: return@Hooker chain.proceed()
            val args = runCatching {
                val data = read.invoke(original) as FloatArray
                if (data.size != 18) return@runCatching null
                if (data[0] == 2f) data[0] = 3f
                // Entries 8..11 are the OEM mix color. Preserve progress, mirror, zoom, crop and corner parameters.
                if (data[3] == 1f && data[11] > 0f && data[11] < 1f) {
                    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    val neutral = if (dark) .30f else .62f
                    val amount = .30f*profile()[SoftLightGlassSettings.Parameter.STRENGTH]
                    for (channel in 8..10) data[channel] += amount*(neutral-data[channel])
                    if (shelf) data[11] *= 1f - .52f * profile()[SoftLightGlassSettings.Parameter.STRENGTH]
                }
                val p = profile()
                if (data[3] == 1f && p.dimmingOverlay != 0f && (8..11).all { data[it] in 0f..1f }) {
                    val tint = p.adjustMixColor(Color.argb(data[11], data[8], data[9], data[10]))
                    data[8] = Color.red(tint) / 255f
                    data[9] = Color.green(tint) / 255f
                    data[10] = Color.blue(tint) / 255f
                    data[11] = Color.alpha(tint) / 255f
                }
                val next = constructor.newInstance()
                write.invoke(next,data)
                chain.args.toTypedArray().also {
                    it[2] = spread(radius.toFloat(),context.resources.displayMetrics.density).roundToInt()
                    it[3] = next
                }
            }.getOrNull() ?: return@Hooker chain.proceed()
            report("native surface blur and mix")
            chain.proceed(args)
        })
    }

    private fun installShelfWindowFallback(loader: ClassLoader) {
        val helper = ModernReflect.findClassIfExists("com.oplus.assistantscreen.common.helper.g", loader) ?: return
        val signature = arrayOf(SurfaceControl.Transaction::class.java, SurfaceControl::class.java, Integer.TYPE)
        val method = helper.declaredMethods.singleOrNull {
            it.returnType == Void.TYPE && it.parameterTypes.contentEquals(signature)
        } ?: return
        ModernHookRegistry.installFast("soft-light:shelf-surface", method, XposedInterface.Hooker { chain ->
            val radius = chain.getArg(2) as Int
            if (radius <= 0 || !enabled()) return@Hooker chain.proceed()
            val density = app()?.resources?.displayMetrics?.density ?: return@Hooker chain.proceed()
            report("shelf window backdrop")
            chain.proceed(chain.args.toTypedArray().also { it[2] = spread(radius.toFloat(), density).roundToInt() })
        })
    }

    fun refresh() {
        val setter = setRadius ?: return
        val activeDrawables = synchronized(radii) { radii.map { it.key to it.value } }
        val previous = keyguardTransition.get()
        if (packageName == "com.android.systemui") keyguardTransition.set(true)
        try {
            activeDrawables.forEach { (d,r) -> runCatching { setter.invoke(d,r); d.invalidateSelf() } }
        } finally {
            if (previous == null) keyguardTransition.remove() else keyguardTransition.set(previous)
        }
    }
}
