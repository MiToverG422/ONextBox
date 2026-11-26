package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.AdaptationHookGate
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object CouiDividerViewHooker {
    const val STATE = "coui:divider-clips"
    private val dividers = WeakHashMap<View, DividerClip>()
    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    init {
        HotReloadLifecycle.register(STATE,
            capture = { synchronized(dividers) { dividers.keys.toTypedArray() } },
            detach = {
                synchronized(dividers) { dividers.toMap().also { dividers.clear() } }
                    .forEach { (view, clip) -> clip.dispose(view) }
            },
            restore = { saved -> (saved as? Array<*>)?.filterIsInstance<View>()?.forEach(::watch) })
    }

    fun hook() {
        ModernHotReloadSession.current?.retireLegacyFirstSlot(View::class.java.getDeclaredMethod("draw", Canvas::class.java))
        View::class.java.declaredConstructors.filter {
            it.parameterTypes.contentEquals(arrayOf(Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }.forEach { constructor ->
            ModernHookRegistry.installFast("ONextBox-CouiDividerViews:constructor", constructor,
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val view = chain.thisObject as? View
                        val attrs = chain.args[1] as? AttributeSet
                        if (view != null && attrs != null && CouiDividerViewRules.isSimpleView(view.javaClass.name) &&
                            CouiDividerHooker.isSystemContext(view.context)) {
                            rememberDivider(view, attrs)
                        }
                    }
                    result
                })
        }
    }

    private fun rememberDivider(view: View, attrs: AttributeSet) {
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
            watch(view)
        }
    }

    private fun watch(view: View) {
        synchronized(dividers) {
            if (view in dividers) return
            val clip = DividerClip(view)
            dividers[view] = clip
            view.addOnAttachStateChangeListener(clip)
            if (view.isAttachedToWindow) clip.onViewAttachedToWindow(view)
        }
    }

    private class DividerClip(view: View) : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private val target = WeakReference(view)
        private val current = Rect()
        private var saved: Rect? = null
        private var hidden = false
        private var tree: WeakReference<ViewTreeObserver>? = null

        override fun onViewAttachedToWindow(view: View) {
            val observer = view.viewTreeObserver
            if (tree?.get() === observer) return
            observer.addOnPreDrawListener(this)
            tree = WeakReference(observer)
        }

        override fun onPreDraw(): Boolean = HotReloadLifecycle.withInfrastructureHook(fallback = { true }) {
            target.get()?.let { view ->
                if (AdaptationHookGate.isBoundProfileActive() && CouiDividerSettings.enabledXposed()) {
                    val hasClip = view.getClipBounds(current)
                    if (!hidden || !hasClip || current != EMPTY) {
                        saved = if (hasClip) Rect(current) else null
                        view.clipBounds = EMPTY
                        hidden = true
                    }
                } else restore(view)
            }
            true
        }

        private fun restore(view: View) {
            if (!hidden) return
            if (view.getClipBounds(current) && current == EMPTY) view.clipBounds = saved
            hidden = false
            saved = null
        }

        override fun onViewDetachedFromWindow(view: View) {
            tree?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            tree = null
            restore(view)
        }

        fun dispose(view: View) {
            onViewDetachedFromWindow(view)
            view.removeOnAttachStateChangeListener(this)
        }

        companion object { private val EMPTY = Rect() }
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
