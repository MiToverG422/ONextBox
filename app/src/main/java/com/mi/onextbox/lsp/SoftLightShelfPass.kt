package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.mi.onextbox.lsp.SoftLightGlassSettings.Parameter
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Shelf draws its translucent surfaces into individual paths, above a separate window blur. */
internal class SoftLightShelfPass(
    private val active: () -> Boolean,
    private val profile: () -> SoftLightGlassSettings.Profile,
    private val report: (String, Throwable?) -> Unit,
) : View.OnAttachStateChangeListener {
    companion object {
        const val PACKAGE = "com.coloros.assistantscreen"
    }

    private class BlurApi {
        val root = View::class.java.getMethod("getViewRootImpl")
        val create = root.returnType.getMethod("createBackgroundBlurDrawable")
        val radius = create.returnType.getMethod("setBlurRadius", Integer.TYPE)
        val corner = create.returnType.getMethod("setCornerRadius", Float::class.javaPrimitiveType)
    }

    private class Card(val readCorner: Method) {
        var blur: Drawable? = null
        var blurRadius = -1
        var cornerRadius = -1f
        var reported = false
    }

    private val owners = WeakHashMap<View, Card>()
    private val fillPaths = WeakHashMap<Path, Boolean>()
    private val drawing = ThreadLocal<View>()
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val api by lazy { runCatching { BlurApi() }.onFailure { report("shelf regional blur unavailable", it) }.getOrNull() }
    private var regionFailed = false

    fun install(loader: ClassLoader) {
        retireWindowHooks()
        val delegate = ModernReflect.findClassIfExists("com.coloros.common.ui.HighlightDelegate", loader) ?: return
        val fill = delegate.declaredMethods.singleOrNull { it.returnType == Void.TYPE &&
            it.parameterTypes.contentEquals(arrayOf(Canvas::class.java, Path::class.java, Float::class.javaPrimitiveType)) } ?: return
        val factory = ModernReflect.findClassIfExists("com.oplus.assistantscreen.cardcontainer.ui.render.e", loader)
        val cardFrame = factory?.declaredMethods?.singleOrNull {
            it.parameterTypes.contentEquals(arrayOf(Context::class.java, View::class.java)) &&
                FrameLayout::class.java.isAssignableFrom(it.returnType) &&
                it.returnType.methods.any { getter -> getter.name == "getHighlightRadius" && getter.parameterCount == 0 }
        }?.returnType
        val hosts = listOfNotNull(
            ModernReflect.findClassIfExists("com.coloros.common.widget.HighlightShadowImageView", loader),
            ModernReflect.findClassIfExists("com.coloros.common.widget.HighlightShadowConstraintLayout", loader),
            cardFrame,
        )
        for (host in hosts) {
            val radius = host.methods.firstOrNull { it.name == "getHighlightRadius" && it.parameterCount == 0 &&
                it.returnType == Float::class.javaPrimitiveType } ?: continue
            val method = host.getDeclaredMethod("onDraw", Canvas::class.java)
            ModernHookRegistry.installFast("soft-light:shelf:${host.name}", method, XposedInterface.Hooker { chain ->
                val view = chain.thisObject as View
                if (Looper.myLooper() != Looper.getMainLooper()) return@Hooker chain.proceed()
                if (!owners.containsKey(view)) {
                    owners[view] = Card(radius)
                    view.addOnAttachStateChangeListener(this)
                }
                val previous = drawing.get()
                drawing.set(view)
                try { chain.proceed() } finally { if (previous == null) drawing.remove() else drawing.set(previous) }
            })
        }
        ModernHookRegistry.installFast("soft-light:shelf:fill", fill, XposedInterface.Hooker { chain ->
            val view = drawing.get() ?: return@Hooker chain.proceed()
            val p = profile()
            if (!enabled(p)) return@Hooker chain.proceed()
            val canvas = chain.getArg(0) as Canvas
            val path = chain.getArg(1) as Path
            fillPaths[path] = true
            val fraction = (chain.getArg(2) as Float).coerceIn(0f, 1f)
            if (path.isEmpty || fraction <= 0f || view.width <= 0 || view.height <= 0) return@Hooker chain.proceed()
            val card = owners[view] ?: return@Hooker chain.proceed()
            if (canvas.isHardwareAccelerated && !regionFailed) {
                runCatching { drawBlur(view, card, canvas, path, fraction, p) }.onFailure {
                    regionFailed = true
                    owners.values.forEach(::release)
                    report("shelf regional blur unavailable", it)
                }
            }
            if (!card.reported) {
                card.reported = true
                report("shelf component ${view.javaClass.simpleName} ${view.width}x${view.height} regionalBlur=${card.blur != null}", null)
            }
            val dark = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val plugin = p.plugin
            if (plugin != null) {
                val color = plugin.nativeTint(p.fallbackSurface, dark)
                tint.color = color
                tint.alpha = (Color.alpha(color) * fraction).roundToInt()
                canvas.drawPath(path, tint)
                return@Hooker null
            }
            val weight = if (p.liquid) .60f else .28f
            val result = chain.proceed(arrayOf(canvas, path, fraction * (1f - weight * p[Parameter.STRENGTH])))
            val overlay = p.dimmingOverlay
            if (overlay != 0f) {
                tint.color = if (overlay < 0f) Color.WHITE else Color.BLACK
                tint.alpha = (255f * kotlin.math.abs(overlay) * fraction).roundToInt()
                canvas.drawPath(path, tint)
            }
            result
        })
        delegate.declaredMethods.singleOrNull { it.returnType == Void.TYPE && it.parameterCount == 3 &&
            it.parameterTypes[0] == Canvas::class.java && it.parameterTypes[1] == Path::class.java &&
            it.parameterTypes[2].isInterface }?.let { stroke ->
            ModernHookRegistry.installFast("soft-light:shelf:stroke", stroke, XposedInterface.Hooker { chain ->
                val p = profile()
                if (p[Parameter.SHINE] == 0f && enabled(p) && Looper.myLooper() == Looper.getMainLooper() &&
                    (drawing.get() != null || fillPaths.containsKey(chain.getArg(1) as Path))) null else chain.proceed()
            })
        }
    }

    private fun enabled(p: SoftLightGlassSettings.Profile) = active() &&
        p.selected(SoftLightGlassSettings.Scene.ASSISTANT) && p[Parameter.STRENGTH] > 0f

    private fun drawBlur(view: View, card: Card, canvas: Canvas, path: Path, fraction: Float, p: SoftLightGlassSettings.Profile) {
        val blurApi = api ?: return
        val density = view.resources.displayMetrics.density
        val spread = p.plugin?.blur(p.fallbackSurface) ?: (if (p.liquid) 8f else 26f) * p[Parameter.BLUR] * p[Parameter.STRENGTH]
        val radius = p.blurRadius(density * spread, density).roundToInt()
        if (radius <= 0 || !view.context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled) {
            release(card)
            return
        }
        val drawable = card.blur ?: run {
            val root = blurApi.root.invoke(view) ?: return
            (blurApi.create.invoke(root) as Drawable).also { card.blur = it }
        }
        if (radius != card.blurRadius) {
            blurApi.radius.invoke(drawable, radius)
            card.blurRadius = radius
        }
        val corner = card.readCorner.invoke(view) as Float
        if (corner != card.cornerRadius) {
            blurApi.corner.invoke(drawable, corner)
            card.cornerRadius = corner
        }
        if (drawable.bounds.width() != view.width || drawable.bounds.height() != view.height)
            drawable.setBounds(0, 0, view.width, view.height)
        drawable.alpha = (255f * fraction).roundToInt()
        val save = canvas.save()
        try {
            canvas.clipPath(path)
            drawable.draw(canvas)
        } finally { canvas.restoreToCount(save) }
    }

    fun refresh() {
        owners.forEach { (view, card) ->
            release(card)
            view.invalidate()
        }
    }

    fun detach() {
        owners.keys.toList().forEach(::onViewDetachedFromWindow)
    }

    private fun release(card: Card) {
        card.blur?.setVisible(false, false)
        card.blur = null
        card.blurRadius = -1
        card.cornerRadius = -1f
    }

    override fun onViewAttachedToWindow(view: View) = Unit

    override fun onViewDetachedFromWindow(view: View) {
        owners.remove(view)?.let(::release)
        view.removeOnAttachStateChangeListener(this)
        view.invalidate()
    }

    private fun retireWindowHooks() {
        val session = ModernHotReloadSession.current ?: return
        for (target in session.previousTargets) {
            val retired = when (target.declaringClass.name) {
                "com.oplus.graphics.OplusRenderEffect" -> target.name == "createGradientBlurEffect" && target.parameterCount == 8
                "com.oplus.graphics.OplusBlurManager" -> target.name == "setBackgroundBlurRadius" && target.parameterCount == 4
                "com.android.internal.graphics.drawable.BackgroundBlurDrawable" -> target.name == "setBlurRadius"
                "com.oplus.assistantscreen.common.helper.g" -> target.parameterTypes.contentEquals(arrayOf(
                    android.view.SurfaceControl.Transaction::class.java, android.view.SurfaceControl::class.java, Integer.TYPE))
                "com.coui.appcompat.COUIMaterialBlurEffect", "com.coui.appcompat.COUIMaterialBlurEffect\$Companion" ->
                    target.parameterTypes.firstOrNull() == View::class.java && (target.parameterCount == 6 ||
                        target.parameterTypes.contentEquals(arrayOf(View::class.java, android.graphics.RenderEffect::class.java)))
                else -> false
            }
            if (retired) session.retireLegacyFirstSlot(target)
        }
    }
}
