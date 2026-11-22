package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

internal class FaceAuthCapsuleHost(
    private val findHost: () -> ViewGroup?,
    private val inflateShell: () -> View?,
    private val layout: () -> FaceAuthCapsuleNativeLayout?,
    private val protectedContentTop: () -> Float?,
    private val occupied: () -> Boolean,
    private val onSuccessFinished: () -> Unit,
    private val reserveIcons: (FaceAuthCapsuleBounds) -> Boolean,
    private val releaseIcons: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private var host: ViewGroup? = null
    private var view: FaceAuthCapsuleView? = null
    private val beforeDraw = ViewTreeObserver.OnPreDrawListener {
        reconcileLayout()
        true
    }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { remove() }
    }

    fun show(phase: FaceAuthCapsuleState.Phase) {
        val target = findHost()?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
        if (host !== target) remove()
        if (phase == FaceAuthCapsuleState.Phase.Hidden) {
            view?.show(phase)
            return
        }
        if (target == null) return
        if (view == null) {
            val owned = FaceAuthCapsuleView(target.context, inflateShell() ?: return).apply {
                nativeLayout = layout
                this.protectedContentTop = this@FaceAuthCapsuleHost.protectedContentTop
                onRenderFailure = onFailure
                reserveIcons = this@FaceAuthCapsuleHost.reserveIcons
                onTransitionFinished = { finished ->
                    if (view === this) when (finished) {
                        FaceAuthCapsuleState.Phase.Hidden -> remove()
                        FaceAuthCapsuleState.Phase.Success -> onSuccessFinished()
                        else -> Unit
                    }
                }
            }
            host = target
            view = owned
            // Keep the plugin's indexed children and touch handling intact.
            target.overlay.add(owned)
            target.addOnAttachStateChangeListener(attachment)
            target.viewTreeObserver.addOnPreDrawListener(beforeDraw)
        }
        reconcileLayout()
        view?.show(phase)
    }

    private fun reconcileLayout() {
        val owner = host ?: return
        val layer = view ?: return
        if (layer.width != owner.width || layer.height != owner.height) {
            layer.measure(View.MeasureSpec.makeMeasureSpec(owner.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(owner.height, View.MeasureSpec.EXACTLY))
            layer.layout(0, 0, owner.width, owner.height)
        }
        val alpha = if (occupied()) 0f else 1f
        if (layer.alpha != alpha) {
            layer.alpha = alpha
            layer.forgetReservation()
            if (alpha == 0f) releaseIcons()
        }
    }

    fun remove() {
        val owner = host
        val old = view
        host = null; view = null
        owner?.removeOnAttachStateChangeListener(attachment)
        if (owner?.viewTreeObserver?.isAlive == true) owner.viewTreeObserver.removeOnPreDrawListener(beforeDraw)
        old?.dispose()
        if (old != null) owner?.overlay?.remove(old)
        releaseIcons()
    }
}
