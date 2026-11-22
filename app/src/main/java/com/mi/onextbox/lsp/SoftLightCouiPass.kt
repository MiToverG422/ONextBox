package com.mi.onextbox.lsp

import android.graphics.BlendMode
import android.graphics.Color
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** COUI controls that expose native blur and tint parameters but no readable texture. */
internal class SoftLightCouiPass(private val active: () -> Boolean, private val profile: () -> SoftLightGlassSettings.Profile, private val used: () -> Unit) {
    private class Binding(val owner: Any?, val setter: Method, val original: Array<Any?>)
    private val materials = WeakHashMap<View, Binding>()

    companion object {
        fun findSetter(loader: ClassLoader): Method? {
            val host = ModernReflect.findClassIfExists("com.coui.appcompat.COUIMaterialBlurEffect", loader) ?: return null
            val params = arrayOf(View::class.java, java.lang.Float.TYPE, Integer.TYPE, BlendMode::class.java,
                Integer.TYPE, BlendMode::class.java)
            val owners = host.declaredClasses.asSequence() + host
            return owners.flatMap { it.declaredMethods.asSequence() }.singleOrNull {
                it.returnType == Void.TYPE && it.parameterTypes.contentEquals(params)
            }
        }
    }

    fun install(setter: Method) {
        val type = setter.declaringClass
        ModernHookRegistry.installFast("soft-light:coui:${System.identityHashCode(type)}", setter, XposedInterface.Hooker { chain ->
            val view = chain.getArg(0) as View
            val radius = chain.getArg(1) as Float
            if (radius <= 0f) {
                synchronized(materials) { materials.remove(view) }
                return@Hooker chain.proceed()
            }
            val args = chain.args.toTypedArray()
            synchronized(materials) { materials[view] = Binding(chain.thisObject, setter, args.copyOfRange(1, args.size)) }
            if (!active()) return@Hooker chain.proceed()
            val p = profile()
            if (p.keepWhite && SoftLightSelection.selected(view) &&
                (SoftLightSelection.white(args[2] as Int) || SoftLightSelection.white(args[4] as Int))) return@Hooker chain.proceed()
            val strength = p[SoftLightGlassSettings.Parameter.STRENGTH]
            if (strength == 0f) return@Hooker chain.proceed()
            val density = view.resources.displayMetrics.density
            val baseRadius = if (p.liquid) minOf(radius, 8f * density) else radius*.86f
            val targetRadius = p.plugin?.let { it.blur(p.fallbackSurface) * density }
                ?: (radius + strength*(baseRadius*p[SoftLightGlassSettings.Parameter.BLUR]-radius))
            args[1] = p.blurRadius(targetRadius, density)
            for (index in listOf(2,4)) {
                val color = args[index] as Int
                val alpha = Color.alpha(color)
                val attenuation = if (p.liquid) .60f else .28f
                if (alpha in 1..254) args[index] = (color and 0x00ffffff) or ((alpha*(1f-attenuation*strength)).roundToInt() shl 24)
            }
            if (p.plugin != null || p.dimmingOverlay != 0f) {
                val index = when (BlendMode.SRC_OVER) {
                    args[5] -> 4
                    args[3] -> 2
                    else -> null
                }
                if (index != null) {
                    val config = android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    val dark = view.resources.configuration.uiMode and config == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    args[index] = p.plugin?.nativeTint(p.fallbackSurface, dark) ?: p.adjustMixColor(args[index] as Int)
                }
            }
            used()
            chain.proceed(args)
        })
        // A native clear must also clear the saved binding, including the OEM's "no material" transition.
        type.declaredMethods.filter {
            it.parameterTypes.contentEquals(arrayOf(View::class.java, android.graphics.RenderEffect::class.java))
        }.forEach { clear ->
            ModernHookRegistry.installFast("soft-light:coui:clear:${System.identityHashCode(type)}", clear, XposedInterface.Hooker { chain ->
                if (chain.getArg(1) == null) synchronized(materials) { materials.remove(chain.getArg(0) as View) }
                chain.proceed()
            })
        }
    }

    fun refresh() {
        val snapshot = synchronized(materials) { materials.map { it.key to it.value } }
        snapshot.forEach { (view, b) ->
            if (view.isAttachedToWindow) runCatching { b.setter.invoke(b.owner, view, *b.original); view.invalidate() }
        }
    }

    fun capture(): Any = synchronized(materials) {
        materials.map { (view, b) -> arrayOf(view, b.owner, b.setter, b.original) }.toTypedArray()
    }

    fun restore(saved: Any?) {
        synchronized(materials) {
            (saved as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach { row ->
                val values = row[3] as Array<*>
                materials[row[0] as View] = Binding(row[1], row[2] as Method, values.map { it }.toTypedArray())
            }
        }
    }
}
