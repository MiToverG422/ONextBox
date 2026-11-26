package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

/** Find already-attached controls when a hook is first enabled after SystemUI startup. */
internal object SystemUiWindowViews {
    private fun roots(loader: ClassLoader): List<View> = runCatching {
        val global = Reflect.callStaticMethod(Reflect.findClass("android.view.WindowManagerGlobal", loader), "getInstance")
        val views = global?.let { Reflect.getObjectField(it, "mViews") } as? List<*>
        views?.filterIsInstance<View>().orEmpty()
    }.getOrDefault(emptyList())

    fun find(loader: ClassLoader, matches: (View) -> Boolean): List<View> {
        val found = mutableListOf<View>()
        fun visit(view: View) {
            if (matches(view)) found += view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        roots(loader).forEach(::visit)
        return found
    }
}
