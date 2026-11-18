package com.mi.onextbox.lsp

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.graphics.Point
import android.graphics.Color
import android.text.format.DateFormat
import android.widget.LinearLayout
import android.widget.TextClock
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface

internal class AospAmbientClock(
    private val loader: ClassLoader,
    private val bridge: PixelClockBridge,
    private val onAttachmentChanged: (Boolean) -> Unit,
) {
    private fun type(name: String) = HostClassLookup.load(name, false, loader)

    private val layout = type("com.oplus.systemui.aod.aodclock.off.AodClockLayout")
    private val hostArtwork = layout.getDeclaredField("mAodViewFromApk").apply { isAccessible = true }
    private val controller = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockController")
    private val face = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController")
    private val events = type("com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents")
    private val getLarge = controller.getMethod("getLargeClock")
    private val getView = face.getMethod("getView")
    private val getFaceLayout = face.getMethod("getLayout")
    private val getFaceViews = getFaceLayout.returnType.getMethod("getViews")
    private val constraintLayout = type("androidx.constraintlayout.widget.ConstraintLayout")
    private val constraintSet = type("androidx.constraintlayout.widget.ConstraintSet")
    private val applyFaceConstraints = getFaceLayout.returnType.getMethod("applyConstraints", constraintSet)
    private val getEvents = face.getMethod("getEvents")
    private val tick = events.getMethod("onTimeTick")
    private val fontSize = events.getMethod("onFontSettingChanged", Float::class.javaPrimitiveType)
    private val currentTimeFormat = type("com.android.keyguard.ClockEventController")
        .getMethod("getTimeFormatKind", Int::class.javaObjectType)
    private val getControllerEvents = controller.getMethod("getEvents")
    private val setTimeFormat = getControllerEvents.returnType.getMethod("onTimeFormatChanged", currentTimeFormat.returnType)
    private val initialize = controller.getMethod("initialize", Boolean::class.javaPrimitiveType,
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
    private val burnInOffset = type("com.android.systemui.doze.util.BurnInHelperKt")
        .getMethod("getBurnInOffset", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    private val authType = type("com.android.systemui.biometrics.AuthController")
    private val sensorLocation = authType.getMethod("getUdfpsLocation")
    private val sensorRadius = authType.getMethod("getUdfpsRadius")

    private data class ClockSource(val buffers: Any, val settings: Any, val eventController: Any)
    private data class InfoRow(val view: View, val parent: ViewGroup, val index: Int,
        val params: ViewGroup.LayoutParams, val x: Float = view.translationX, val y: Float = view.translationY)
    private data class Binding(
        val root: RelativeLayout,
        val viewport: FrameLayout,
        val frame: FrameLayout,
        val clockEvents: Any,
        val originalArtwork: List<View>,
        val layoutListener: View.OnLayoutChangeListener,
        val infoRows: List<InfoRow>,
        val placeInfoRows: () -> Unit,
        val burnInX: Int,
        val burnInY: Int,
    )

    private var source: ClockSource? = null
    private var binding: Binding? = null
    private var prepareFailed = false
    private var authController: Any? = null

    fun useClock(buffers: Any, settings: Any, eventController: Any) { source = ClockSource(buffers, settings, eventController) }

    fun useDeviceEntry(sections: List<*>) {
        val entry = sections.firstOrNull { it?.javaClass?.simpleName == "DefaultDeviceEntrySection" } ?: return
        authController = runCatching { ModernReflect.getObjectField(entry, "authController") }
            .getOrNull()?.takeIf(authType::isInstance)
    }

    fun install() {
        val show = layout.getDeclaredMethod("performLayoutUpdate", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val update = layout.getMethod("performAodUpdate", Boolean::class.javaPrimitiveType)
        val visibility = layout.getMethod("setVisibility", Int::class.javaPrimitiveType)
        val attached = layout.getMethod("onAttachedToWindow")
        val detach = layout.getMethod("onDetachedFromWindow")
        val destroy = layout.getMethod("onAodClockLayoutDestroy")
        for (method in listOf(show, update, visibility, attached)) {
            ModernHookRegistry.installFast("aosp-clock:ambient:${method.name}", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val root = chain.thisObject as? RelativeLayout ?: return@Hooker result
                if (method == visibility && root.visibility != View.VISIBLE) {
                    if (binding?.root === root) clear()
                } else if (root.isAttachedToWindow && root.visibility == View.VISIBLE && !prepareFailed) {
                    runCatching { refresh(root) }.onFailure {
                        prepareFailed = true
                        clear()
                        HookLog.w("ONextBox-AospClock", "Ambient clock could not be attached; keeping ColorOS AOD", it)
                    }
                }
                result
            })
            ModernHookRuntime.requireModule().deoptimize(method)
        }
        for (method in listOf(detach, destroy)) {
            ModernHookRegistry.installFast("aosp-clock:ambient:${method.name}", method, XposedInterface.Hooker { chain ->
                if (binding?.root === chain.thisObject) clear()
                chain.proceed()
            })
        }
        HookLog.i("ONextBox-AospClock", "Native ambient layout hooked into ColorOS AOD visibility")
    }

    private fun refresh(root: RelativeLayout) {
        binding?.takeIf { it.root === root }?.let {
            tick.invoke(it.clockEvents)
            moveForBurnIn(it)
            it.placeInfoRows()
            return
        }
        val definition = source ?: return
        val viewport = root.parent as? FrameLayout ?: return
        val clock = bridge.create(root.context, definition.buffers, definition.settings) ?: return
        val large = getLarge.invoke(clock)
        val nativeView = getView.invoke(large) as? View ?: return
        val clockEvents = requireNotNull(getEvents.invoke(large))
        val faceLayout = requireNotNull(getFaceLayout.invoke(large))
        val views = (getFaceViews.invoke(faceLayout) as List<*>).filterIsInstance<View>()
        require(views.isNotEmpty())
        val reference = bridge.referenceResources(root.context)
        val density = root.resources.displayMetrics.density
        fun pixelDimen(name: String, fallbackDp: Int): Int {
            val id = reference.getIdentifier(name, "dimen", "com.android.systemui")
            val dp = if (id == 0) fallbackDp.toFloat() else reference.getDimension(id) / reference.displayMetrics.density
            return (dp * density + .5f).toInt()
        }
        initialize.invoke(clock, true, 1f, 0f)
        setTimeFormat.invoke(getControllerEvents.invoke(clock), currentTimeFormat.invoke(definition.eventController, null))
        fontSize.invoke(clockEvents, pixelDimen("large_clock_text_size", 150).toFloat())
        tick.invoke(clockEvents)
        val originalArtwork = mutableListOf<View>()
        val infoRows = mutableListOf<InfoRow>()
        (hostArtwork.get(root) as? View)?.let { collectArtwork(it, originalArtwork, infoRows) }
        clear()
        val date = TextClock(root.context).apply {
            val pattern = DateFormat.getBestDateTimePattern(resources.configuration.locales[0], "EEEMMMd")
            format12Hour = pattern
            format24Hour = pattern
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val clockBlock = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            if (views.size == 1 && views[0] === nativeView) {
                (nativeView.parent as? ViewGroup)?.removeView(nativeView)
                addView(nativeView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            } else {
                val pane = createFacePane(root, views, faceLayout)
                addView(pane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            addView(date, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val frame = FrameLayout(root.context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(clockBlock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
        val topPadding = pixelDimen("enhanced_smartspace_height", 104) + pixelDimen("keyguard_smartspace_top_offset", 6)
        val dateGap = pixelDimen("smartspace_padding_vertical", 6)
        (date.layoutParams as LinearLayout.LayoutParams).topMargin = dateGap
        val iconsGap = pixelDimen("keyguard_status_view_bottom_margin", 20)
        val batteryMargin = pixelDimen("keyguard_indication_margin_bottom", 32)
        fun placeInfoRows() {
            if (clockBlock.height == 0 || viewport.height == 0) return
            for (row in infoRows) {
                val view = row.view
                if (view.parent !== viewport) continue
                val placement = view.layoutParams as FrameLayout.LayoutParams
                val top = if (view.javaClass.simpleName == "BatteryView") {
                    viewport.height - batteryMargin - view.measuredHeight
                } else {
                    (frame.top + frame.translationY + clockBlock.bottom + iconsGap).toInt()
                }
                val safeTop = top.coerceIn(0, (viewport.height - view.measuredHeight).coerceAtLeast(0))
                if (placement.topMargin != safeTop) {
                    placement.topMargin = safeTop
                    view.layoutParams = placement
                }
            }
        }
        fun position(viewportHeight: Int) {
            if (viewportHeight <= 0) return
            val insets = viewport.rootWindowInsets
            val status = insets?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())?.top ?: 0
            val navigation = insets?.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())?.bottom ?: 0
            val top = topPadding + status / 2
            val sensorTop = authController?.let { auth ->
                val point = sensorLocation.invoke(auth) as? Point
                val radius = (sensorRadius.invoke(auth) as Number).toFloat()
                point?.let { (it.y - radius).toInt() }
            }
            val entryTop = sensorTop?.takeIf { it in (top + 1)..viewportHeight }
                ?: (viewportHeight - navigation - pixelDimen("keyguard_affordance_vertical_offset", 32) - (72 * density).toInt())
            val height = (entryTop - top).coerceAtLeast(0)
            if (params.height != height || params.topMargin != top) {
                params.height = height
                params.topMargin = top
                frame.layoutParams = params
            }
        }
        val listener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            position(bottom - top)
            placeInfoRows()
        }
        val current = Binding(root, viewport, frame, clockEvents, originalArtwork, listener, infoRows, ::placeInfoRows,
            pixelDimen("burn_in_prevention_offset_x", 8), pixelDimen("burn_in_prevention_offset_y", 50))
        binding = current
        viewport.addView(frame, params)
        viewport.addOnLayoutChangeListener(listener)
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeInfoRows() }
        for (row in infoRows) {
            val view = row.view
            row.parent.removeView(view)
            view.translationX = 0f
            view.translationY = 0f
            viewport.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        position(viewport.height)
        moveForBurnIn(current)
        originalArtwork.forEach { it.setTransitionAlpha(0f) }
        onAttachmentChanged(true)
        HookLog.i("ONextBox-AospClock", "Native ambient clock attached to ColorOS AOD view")
    }

    private fun createFacePane(root: View, views: List<View>, faceLayout: Any): ViewGroup {
        val pane = constraintLayout.getConstructor(android.content.Context::class.java).newInstance(root.context) as ViewGroup
        for (view in views) {
            if (view.id == View.NO_ID) view.id = View.generateViewId()
            (view.parent as? ViewGroup)?.removeView(view)
            pane.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val constraints = constraintSet.getDeclaredConstructor().newInstance()
        constraintSet.getMethod("clone", constraintLayout).invoke(constraints, pane)
        val connect = constraintSet.getMethod("connect", *Array(4) { Int::class.javaPrimitiveType })
        for (view in views) {
            for (edge in intArrayOf(3, 4, 6, 7)) connect.invoke(constraints, view.id, edge, 0, edge)
        }
        applyFaceConstraints.invoke(faceLayout, constraints)
        constraintSet.getMethod("applyTo", constraintLayout).invoke(constraints, pane)
        return pane
    }

    private fun moveForBurnIn(binding: Binding) {
        fun position(max: Int, horizontal: Boolean) = (burnInOffset.invoke(null, max * 2, horizontal) as Int) - max
        binding.frame.translationX = position(binding.burnInX, true).toFloat()
        binding.frame.translationY = position(binding.burnInY, false).toFloat()
    }

    private fun collectArtwork(view: View, clocks: MutableList<View>, infoRows: MutableList<InfoRow>) {
        when {
            view.javaClass.name in OEM_CLOCK_ARTWORK -> clocks.add(view)
            view.javaClass.name in OEM_INFO_ROWS -> {
                val parent = view.parent as? ViewGroup ?: return
                infoRows.add(InfoRow(view, parent, parent.indexOfChild(view), view.layoutParams))
            }
            view is ViewGroup -> for (index in 0 until view.childCount) collectArtwork(view.getChildAt(index), clocks, infoRows)
        }
    }

    private fun clear() {
        val current = binding ?: return
        binding = null
        current.viewport.removeOnLayoutChangeListener(current.layoutListener)
        current.viewport.removeView(current.frame)
        current.originalArtwork.forEach { it.setTransitionAlpha(1f) }
        current.infoRows.forEach { row ->
            if (row.view.parent === current.viewport) current.viewport.removeView(row.view)
            row.view.translationX = row.x
            row.view.translationY = row.y
            if (row.view.parent == null) row.parent.addView(row.view, row.index.coerceIn(0, row.parent.childCount), row.params)
        }
        onAttachmentChanged(false)
    }

    private companion object {
        val OEM_CLOCK_ARTWORK = setOf(
            "com.oplus.egview.widget.TimeView", "com.oplus.egview.widget.ImageClockView",
            "com.oplus.egview.widget.ImageClockWithDateView", "com.oplus.egview.widget.NumberClockView",
            "com.oplus.egview.widget.EdgeNumberClockView", "com.oplus.egview.widget.OpKeyguardClockInfoView",
            "com.oplus.egview.widget.DateView", "com.oplus.aodimpl.view.DetailDateView",
        )
        val OEM_INFO_ROWS = setOf("com.oplus.egview.widget.NotificationView", "com.oplus.egview.widget.BatteryView")
    }
}
