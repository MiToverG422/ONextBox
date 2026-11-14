package com.mi.onextbox.lsp

import android.view.View
import java.util.WeakHashMap

/** MotionLayout creates/replaces the ringer buttons after bind and owns their constraints. */
internal class AospVolumeRingerSize {
    private data class Original(val scaleX: Float, val scaleY: Float, val pivotX: Float, val pivotY: Float,
        val explicitPivot: Boolean, val listener: View.OnLayoutChangeListener)
    private val originals = WeakHashMap<View, Original>()

    fun accepts(view: View) = runCatching {
        view.resources.getResourceEntryName(view.id) == "volume_ringer_drawer"
    }.getOrDefault(false)

    fun apply(view: View, percent: Int) {
        if (AospVolumeSize.percent(percent) == 100) {
            val original = originals.remove(view) ?: return
            view.removeOnLayoutChangeListener(original.listener)
            view.scaleX = original.scaleX
            view.scaleY = original.scaleY
            if (original.explicitPivot) {
                view.pivotX = original.pivotX
                view.pivotY = original.pivotY
            } else view.resetPivot()
            return
        }
        val original = originals.getOrPut(view) {
            val listener = View.OnLayoutChangeListener { target, _, _, _, _, _, _, _, _ -> anchor(target) }
            Original(view.scaleX, view.scaleY, view.pivotX, view.pivotY, view.isPivotSet, listener)
                .also { view.addOnLayoutChangeListener(listener) }
        }
        // Scale the whole native drawer, including buttons added later and their backgrounds.
        // View's matrix also transforms touch coordinates; native open/close constraints stay intact.
        val scale = AospVolumeSize.percent(percent) / 100f
        view.scaleX = original.scaleX * scale
        view.scaleY = original.scaleY * scale
        anchor(view)
    }

    private fun anchor(view: View) {
        view.pivotX = view.width.toFloat()
        view.pivotY = view.height.toFloat()
    }
}
