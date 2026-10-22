package com.mi.onextbox.lsp

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.QsCornerReflect as Reflect
import java.util.WeakHashMap

/** Paint after the native track; leave slider listeners, touch geometry and animations untouched. */
internal object QsSliderPercentageHooker {
    private const val TAG = "ONextBox-QsPercentage"
    private const val LABEL = "com.oplus.systemui.plugins.qs.customize.view.tile.QsLabelView"
    private const val COLORS = "com.oplus.systemui.qs.base.util.QsColorUtil"
    private val styles = WeakHashMap<View, NativeStyle>()
    private var reported = false

    private class NativeStyle(view: View) {
        private val loader = view.javaClass.classLoader
        private val label = Reflect.newInstance(checkNotNull(Reflect.findClassIfExists(LABEL, loader)), view.context)
        val text = Reflect.callMethod(label, "getTextView") as TextView
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val sizeResource = text.resources.getIdentifier("qs_tile_text_size", "dimen", "com.android.systemui")
        val defaultSizePx: Float
            get() = if (sizeResource != 0) text.resources.getDimension(sizeResource)
                else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    QsSliderPercentageRules.DEFAULT_SIZE_SP.toFloat(), text.resources.displayMetrics)
        private val colorType = checkNotNull(Reflect.findClassIfExists(COLORS, loader))
        private val stateMethod = colorType.getDeclaredMethod("getQsColorState")

        fun refresh(): List<Any?> {
            // Delegate wallpaper blur / theme / dark-mode policy to the same class as ordinary titles.
            Reflect.callMethod(label, "updateColor", stateMethod.invoke(null), false, false)
            paint.typeface = text.typeface
            return listOf(text.currentTextColor, defaultSizePx, text.typeface,
                text.resources.configuration.hashCode())
        }
    }

    fun hook(loader: ClassLoader) {
        val type = Reflect.findClassIfExists(QsControlCenterCornerTargets.SEEK_BAR, loader) ?: return
        val observers = QsCornerViewObservers(
            "qs:percentage-views",
            content = { view -> style(view).refresh() },
            configurationToken = QsSliderPercentageSettings::configurationTokenXposed,
        ) { view -> view.invalidate(); true }
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
                        val state = QsSliderPercentageSettings.stateXposed()
                        if (!state.enabled) return
                        val view = param.thisObject as? View ?: return
                        if (!hasSliderOwner(view)) return
                        val canvas = param.args.firstOrNull() as? Canvas ?: return
                        runCatching { draw(view, canvas, state) }.onFailure(::diagnose)
                    }
                })
            }
        hookDetailAnimationFrames(loader)
        hookNativeVolumeTitle(loader)
    }

    private fun hookNativeVolumeTitle(loader: ClassLoader) {
        // This callback only changes the title. Zero selects the native device/volume label branch.
        val name = "com.oplus.systemui.qs.slider.OplusQsDetailVolumeRootViewController\$\$ExternalSyntheticLambda0"
        val type = Reflect.findClassIfExists(name, loader) ?: return
        type.declaredMethods.filter { it.name == "invoke" && it.parameterCount == 1 }.forEach { method ->
            ModernHookRegistry.installCompat("$TAG:volume-title:${method.toGenericString()}", method,
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (QsSliderPercentageSettings.stateXposed().enabled) param.args[0] = 0
                    }
                })
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }.onFailure(::diagnose)
        }
    }

    private fun hookDetailAnimationFrames(loader: ClassLoader) {
        // Parent property animations can reuse the child's RenderNode; force a fresh text recording.
        val manager = QsControlCenterCornerTargets.DETAIL_ANIMATION
        listOf(
            manager to setOf("beginTransition", "setDetailSliderScale"),
            "$manager\$doStartAnimation\$updateListener\$1" to setOf("onAnimationUpdate"),
            "$manager\$doHideAnimation\$updateListener\$1" to setOf("onAnimationUpdate"),
            "$manager\$doStartAnimation\$endListener\$1" to setOf("onAnimationEnd"),
            "$manager\$doHideAnimation\$endListener\$1" to setOf("onAnimationEnd"),
        ).forEach { (name, methods) ->
            val type = Reflect.findClassIfExists(name, loader) ?: return@forEach
            type.declaredMethods.filter { it.name in methods }.forEach { method ->
                ModernHookRegistry.installCompat("$TAG:frame:$name:${method.toGenericString()}", method,
                    object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable != null || !QsSliderPercentageSettings.stateXposed().enabled) return
                            runCatching {
                                val detail = when (param.method.name) {
                                    "setDetailSliderScale" -> param.args.firstOrNull() as? View
                                    "beginTransition" -> param.args.firstOrNull()?.let {
                                        Reflect.callMethod(it, "getDetailSlider") as? View
                                    }
                                    else -> Reflect.getObjectField(param.thisObject!!, "\$detailSlider") as? View
                                }
                                (detail?.let { Reflect.callMethod(it, "getSlider") } as? View)?.invalidate()
                            }.onFailure(::diagnose)
                        }
                    })
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }.onFailure(::diagnose)
            }
        }
    }

    private fun hasSliderOwner(view: View) = generateSequence(view.parent) { it.parent }
        .filterIsInstance<View>().any { QsControlCenterCornerTargets.isSlider(it.javaClass.name) }

    private fun style(view: View): NativeStyle = synchronized(styles) {
        styles.getOrPut(view) { NativeStyle(view).also { it.refresh() } }
    }

    private fun draw(view: View, canvas: Canvas, state: QsSliderPercentageSettings.State) {
        val progress = (Reflect.callMethod(view, "getProgress") as Number).toInt()
        val min = (Reflect.callMethod(view, "getMin") as Number).toInt()
        val max = (Reflect.callMethod(view, "getMax") as Number).toInt()
        val percent = QsSliderPercentageRules.percentage(progress, min, max) ?: return
        val track = Reflect.callMethod(view, "getBackgroundRect") as Rect
        if (track.isEmpty) return
        val native = style(view)
        val paint = native.paint
        paint.color = state.color?.toInt() ?: native.text.currentTextColor
        // The default follows the native resource including font scaling; explicit sizes are sp.
        paint.textSize = if (state.sizeSp == QsSliderPercentageRules.DEFAULT_SIZE_SP) native.defaultSizePx
            else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, state.sizeSp.toFloat(), view.resources.displayMetrics)
        val detail = generateSequence(view.parent) { it.parent }.filterIsInstance<View>()
            .firstOrNull { it.javaClass.name == QsControlCenterCornerTargets.DETAIL_SLIDER }
        val (sizeScale, widthScale) = detail?.let {
            QsSliderPercentageRules.textTransform(it.scaleX, it.scaleY)
        } ?: (1f to 1f)
        paint.textSize *= sizeScale
        val label = "$percent%"
        // Retain the selected sp height while fitting wide numerals into narrow tracks.
        paint.textScaleX = widthScale
        val available = (track.width() - 4 * view.resources.displayMetrics.density).coerceAtLeast(1f)
        val measured = paint.measureText(label)
        if (measured > available) paint.textScaleX *= available / measured
        val metrics = paint.fontMetrics
        val baseline = QsSliderPercentageRules.baseline(track.top.toFloat(), track.bottom.toFloat(),
            metrics.ascent, metrics.descent, state.offset)
        canvas.drawText(label, track.exactCenterX(), baseline, paint)
    }

    private fun diagnose(error: Throwable) {
        if (!reported) {
            reported = true
            HookLog.w(TAG, "Slider percentage adapter unavailable", error)
        }
    }
}
