package com.mi.onextbox.lsp

import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import com.mi.onextbox.lsp.QsCornerReflect as Reflect

/** Use exactly the same native smooth path as the background, including temporary overrides. */
internal class QsCornerDrawableOutline(private val drawable: Drawable) : ViewOutlineProvider() {
    fun usesDrawable(candidate: Drawable): Boolean = drawable === candidate

    override fun getOutline(view: View, outline: Outline) {
        val provider = Reflect.callMethod(drawable, "getPathProvider") as? ViewOutlineProvider
        if (provider != null) provider.getOutline(view, outline) else outline.setEmpty()
    }
}
