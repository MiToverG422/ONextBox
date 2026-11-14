package com.mi.onextbox.lsp

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

/** Reads layout parameters even when an empty native capsule is GONE and has never been measured. */
internal object FaceAuthCapsuleNativePosition {
    fun read(view: View): FaceAuthCapsuleNativeLayout? = runCatching {
        val height = (Reflect.callMethod(view, "getMaxHeight") as? Int)?.takeIf { it > 0 } ?: return null
        val parent = view.parent as? FrameLayout ?: return null
        if (!parent.isLaidOut || parent.width <= 0 || parent.height <= 0) return null
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return null
        val area = if (params.gravity == Gravity.CENTER &&
            params.height == FrameLayout.LayoutParams.MATCH_PARENT &&
            listOf(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin).all { it == 0 }) {
            // The plugin moves the visual capsule with parent padding, not the child's top.
            // Child bounds can be zero or left over from a previous resolution while GONE.
            val position = IntArray(2).also(parent::getLocationOnScreen)
            FaceAuthCapsuleBounds((position[0] + parent.paddingLeft).toFloat(),
                (position[1] + parent.paddingTop).toFloat(),
                (position[0] + parent.width - parent.paddingRight).toFloat(),
                (position[1] + parent.height - parent.paddingBottom).toFloat())
        } else {
            if (view.visibility == View.GONE || !view.isLaidOut || view.width <= 0 || view.height < height) return null
            val position = IntArray(2).also(view::getLocationOnScreen)
            FaceAuthCapsuleBounds(position[0].toFloat(), position[1].toFloat(),
                (position[0] + view.width).toFloat(), (position[1] + view.height).toFloat())
        }
        val resources = view.resources
        fun dimension(name: String): Float? = resources.getIdentifier(name, "dimen", "com.oplus.systemui.plugins")
            .takeIf { it != 0 }?.let(resources::getDimension)
        val minWidth = dimension("capsule_view_min_width") ?: return null
        val radius = dimension("capsule_corner_radius") ?: return null
        val blankWidth = runCatching { (Reflect.callMethod(view, "getBlankWidth") as? Int)?.toFloat() }.getOrNull()
        val blank = runCatching {
            // This method is obfuscated across plugin builds; its signature and Pair contract are stable.
            val method = view.javaClass.declaredMethods.singleOrNull {
                it.returnType.name == "kotlin.Pair" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            }?.apply { isAccessible = true } ?: return@runCatching null
            val pair = method.invoke(view, height) ?: return@runCatching null
            val offset = (view.measuredHeight - height) / 2
            val top = (Reflect.callMethod(pair, "getFirst") as? Int) ?: return@runCatching null
            val bottom = (Reflect.callMethod(pair, "getSecond") as? Int) ?: return@runCatching null
            // Remove the native method's measured-height offset before using the current parent area.
            (top - offset).toFloat() to (bottom - offset).toFloat()
        }.getOrNull()
        FaceAuthCapsulePosition.fromNativeArea(area, height.toFloat(), minWidth, radius,
            blankWidth, blank?.first, blank?.second)
    }.getOrNull()
}
