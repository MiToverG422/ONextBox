package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

internal object CouiDividerViewHooker {
    private val dividers = WeakHashMap<View, Boolean>()
    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    fun hook() {
        View::class.java.declaredConstructors.filter {
            it.parameterTypes.contentEquals(arrayOf(Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }.forEach { constructor ->
            ModernHookRegistry.installFast("ONextBox-CouiDividerViews:constructor", constructor,
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val view = chain.thisObject as? View
                        val attrs = chain.args[1] as? AttributeSet
                        if (view != null && attrs != null && CouiDividerHooker.isSystemContext(view.context)) {
                            rememberDivider(view, attrs)
                        }
                    }
                    result
                })
        }
        ModernHookRegistry.installFast("ONextBox-CouiDividerViews:draw", View::class.java.getDeclaredMethod("draw", Canvas::class.java),
            XposedInterface.Hooker { chain ->
                val view = chain.thisObject as? View
                if (view != null && CouiDividerSettings.enabledXposed() &&
                    synchronized(dividers) { dividers.containsKey(view) } && CouiDividerHooker.shouldHide(view)) null
                else chain.proceed()
            })
    }

    private fun rememberDivider(view: View, attrs: AttributeSet) {
        if (!CouiDividerViewRules.isSimpleView(view.javaClass.name)) return
        val raw = attrs.getAttributeValue(ANDROID, "background").orEmpty()
        val backgroundId = attrs.getAttributeResourceValue(ANDROID, "background", 0).takeIf { it != 0 }
            ?: raw.removePrefix("?").removePrefix("@").toIntOrNull() ?: 0
        if (backgroundId == 0) return
        val background = runCatching { view.resources.getResourceEntryName(backgroundId) }.getOrNull() ?: return
        if (!CouiDividerViewRules.isDividerBackground(background)) return
        val array = view.context.obtainStyledAttributes(attrs, R.styleable.ONextBoxDividerView)
        val width: Int
        val height: Int
        try {
            width = array.getLayoutDimension(R.styleable.ONextBoxDividerView_android_layout_width, 0)
            height = array.getLayoutDimension(R.styleable.ONextBoxDividerView_android_layout_height, 0)
        } finally {
            array.recycle()
        }
        if (CouiDividerViewRules.isDividerView(view.javaClass.name, width, height, view.resources.displayMetrics.density)) {
            synchronized(dividers) { dividers[view] = true }
        }
    }
}

internal object CouiDividerViewRules {
    private val simpleViews = setOf("android.view.View", "android.widget.ImageView", "androidx.appcompat.widget.AppCompatImageView")
    fun isSimpleView(className: String): Boolean = className in simpleViews
    fun isDividerBackground(name: String): Boolean = name == "couiColorDivider" ||
        name == "coui_color_divider" || name == "coui_preference_category_divider_color" ||
        name.startsWith("coui_divider_horizontal") || name.startsWith("coui_divider_vertical")

    fun isDividerView(className: String, width: Int, height: Int, density: Float): Boolean {
        if (className !in simpleViews) return false
        val maxThickness = (2f * density).coerceAtLeast(1f)
        fun thin(value: Int) = value > 0 && value <= maxThickness
        fun long(value: Int) = value == -1 || value >= 8f * density
        return (thin(height) && long(width)) || (thin(width) && long(height))
    }
}
