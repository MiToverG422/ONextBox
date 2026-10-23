package com.mi.onextbox.lsp

import android.graphics.Canvas
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.QsCornerReflect as Reflect
import java.util.WeakHashMap

/** Both collapsed and expanded volume rows use the same native vertical seekbar. */
internal object SideVolumePercentageHooker {
    private const val SEEK = "com.oplus.systemui.volume.OplusVolumeSeekBar"
    private const val TAG = "ONextBox-SideVolumePercentage"
    private class Drawing(view: View) {
        val style = QsSliderPercentageHooker.NativeStyle(view)
        val track = Rect()
    }
    private val drawings = WeakHashMap<View, Drawing>()
    private var reported = false
    private fun drawing(view: View) = synchronized(drawings) {
        drawings.getOrPut(view) { Drawing(view).also { it.style.refresh() } }
    }

    fun hook(loader: ClassLoader) {
        val type = Reflect.findClassIfExists(SEEK, loader) ?: return
        val observers = QsCornerViewObservers("volume:percentage-views",
            content = { drawing(it).style.refresh() },
            configurationToken = SideVolumePercentageSettings::configurationTokenXposed,
        ) { it.invalidate(); true }
        type.declaredConstructors.forEach { constructor ->
            ModernHookRegistry.installCompat("$TAG:observe:${constructor.toGenericString()}", constructor,
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) (param.thisObject as? View)?.let(observers::watch)
                    }
                })
        }
        type.declaredMethods.filter { it.name == "onDraw" && it.parameterTypes.contentEquals(arrayOf(Canvas::class.java)) }
            .forEach { method ->
                ModernHookRegistry.installCompat("$TAG:draw", method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || HotReloadLifecycle.isRetiring) return
                        val state = SideVolumePercentageSettings.stateXposed()
                        if (!state.enabled) return
                        val view = param.thisObject as? View ?: return
                        observers.watch(view)
                        val canvas = param.args.firstOrNull() as? Canvas ?: return
                        runCatching { draw(view, canvas, state) }.onFailure {
                            if (!reported) { reported = true; HookLog.w(TAG, "Volume popup percentage unavailable", it) }
                        }
                    }
                })
            }
    }

    private fun draw(view: View, canvas: Canvas, state: QsSliderPercentageSettings.State) {
        if (!SideVolumePercentageVisibility.shouldDraw(state.enabled,
                (Reflect.callMethod(view, "getThickType") as Number).toInt(),
                Reflect.callMethod(view, "getHasEnlarge") == true,
                (Reflect.getObjectField(view, "mCurBackgroundWidth") as Number).toFloat() /
                    (Reflect.getObjectField(view, "mBackgroundWidth") as Number).toFloat())) return
        val percent = QsSliderPercentageRules.percentage(
            (Reflect.callMethod(view, "getProgress") as Number).toInt(),
            (Reflect.callMethod(view, "getMin") as Number).toInt(),
            (Reflect.callMethod(view, "getMax") as Number).toInt()) ?: return
        val drawing = drawing(view)
        Reflect.callMethod(view, "copyBackgroundRect", drawing.track)
        val track = drawing.track
        if (track.isEmpty) return
        val paint = drawing.style.paint
        paint.color = state.color?.toInt() ?: drawing.style.text.currentTextColor
        paint.textSize = if (state.sizeSp == QsSliderPercentageRules.DEFAULT_SIZE_SP) drawing.style.defaultSizePx
            else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, state.sizeSp.toFloat(), view.resources.displayMetrics)
        paint.textScaleX = 1f
        val label = "$percent%"
        val available = (track.width() - 4 * view.resources.displayMetrics.density).coerceAtLeast(1f)
        val measured = paint.measureText(label)
        if (measured > available) paint.textScaleX = available / measured
        val metrics = paint.fontMetrics
        canvas.drawText(label, track.exactCenterX(),
            QsSliderPercentageRules.baseline(track.top.toFloat(), track.bottom.toFloat(),
                metrics.ascent, metrics.descent, state.offset), paint)
    }
}
