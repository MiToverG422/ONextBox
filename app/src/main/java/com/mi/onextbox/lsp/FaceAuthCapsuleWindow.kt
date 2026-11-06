package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

/** A visual-only System UI layer, independent of the shade's unlock fade and clipping. */
internal class FaceAuthCapsuleWindow(
    private val layout: () -> FaceAuthCapsuleNativeLayout?,
    private val protectedContentTop: () -> Float?,
    private val onSuccessFinished: () -> Unit,
    private val reserveIcons: (FaceAuthCapsuleBounds) -> Boolean,
    private val releaseIcons: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private var manager: WindowManager? = null
    private var view: FaceAuthCapsuleView? = null

    fun show(context: Context, phase: FaceAuthCapsuleState.Phase) {
        if (phase == FaceAuthCapsuleState.Phase.Hidden) {
            view?.show(phase)
            return
        }
        if (view == null) {
            // Same privileged overlay layer used by System UI's display decorations.
            val type = 2026 // TYPE_DISPLAY_OVERLAY, omitted from the public SDK's WindowType.
            @SuppressLint("WrongConstant")
            val windowContext = context.createWindowContext(type, null)
            val wm = requireNotNull(windowContext.getSystemService(WindowManager::class.java))
            val owned = FaceAuthCapsuleView(windowContext).apply {
                nativeLayout = layout
                this.protectedContentTop = this@FaceAuthCapsuleWindow.protectedContentTop
                onRenderFailure = onFailure
                reserveIcons = this@FaceAuthCapsuleWindow.reserveIcons
                onTransitionFinished = { finished ->
                    if (view === this) when (finished) {
                        FaceAuthCapsuleState.Phase.Hidden -> remove()
                        FaceAuthCapsuleState.Phase.Success -> onSuccessFinished()
                        else -> Unit
                    }
                }
            }
            val params = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (150 * windowContext.resources.displayMetrics.density).toInt(),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
                setTitle("ONextBoxFaceAuthCapsule")
            }
            val trusted = Reflect.getStaticIntField(WindowManager.LayoutParams::class.java, "PRIVATE_FLAG_TRUSTED_OVERLAY")
            Reflect.setIntField(params, "privateFlags", trusted)
            wm.addView(owned, params)
            manager = wm; view = owned
        }
        view?.show(phase)
    }

    fun remove() {
        val old = view
        view = null
        old?.dispose()
        try {
            if (old != null) manager?.removeViewImmediate(old)
        } finally {
            manager = null; releaseIcons()
        }
    }
}
