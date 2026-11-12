package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import kotlin.math.roundToInt

internal object LauncherSearchWidth {
    const val CHINA = "launcher_dock_search_width_china"
    const val GOOGLE = "launcher_dock_search_width_google"
    const val DEFAULT = 100
    const val MIN = 60
    const val MAX = 120

    fun read(context: Context, key: String): Int =
        LspPreferenceStore.prefs(context).getInt(key, DEFAULT).coerceIn(MIN, MAX)

    fun save(context: Context, key: String, percent: Int) =
        ModernXposedPreferenceSync.setIntAndSync(context, key, percent.coerceIn(MIN, MAX))

    fun install(loader: ClassLoader, mode: Int) {
        val key = if (mode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA) CHINA else GOOGLE
        val container = loader.loadClass("com.android.launcher.bottomsearch.BottomSearchBoxContainerView")
        val layer = loader.loadClass("com.android.launcher3.OplusDragLayer")
        val measure = layer.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE)
        val launcher = loader.loadClass("com.android.launcher.Launcher")
        val dragLayer = launcher.getMethod("getDragLayer")

        fun dock(parent: ViewGroup): View? {
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                if (container.isInstance(child)) return child
            }
            return null
        }

        ModernHookRegistry.installFast("dockSearch:measureWidth", measure) { chain ->
            val percent = HookConfigSnapshot.int(key, DEFAULT) ?: DEFAULT
            if (percent == DEFAULT) return@installFast chain.proceed()
            val parent = chain.thisObject as ViewGroup
            val params = dock(parent)?.layoutParams as? FrameLayout.LayoutParams ?: return@installFast chain.proceed()
            val original = params.width
            val available = View.MeasureSpec.getSize(chain.args[0] as Int) - parent.paddingLeft -
                parent.paddingRight - params.leftMargin - params.rightMargin
            if (original <= 0 || available <= 0) return@installFast chain.proceed()
            params.width = (original * (percent.coerceIn(MIN, MAX) / 100f)).roundToInt().coerceIn(1, available)
            try {
                chain.proceed()
            } finally {
                // Keep the launcher's own width as the baseline for the next layout or rotation.
                params.width = original
            }
        }
        ModernHookRegistry.installFast("dockSearch:refreshWidth", launcher.getDeclaredMethod("onResume")) { chain ->
            val result = chain.proceed()
            (dragLayer.invoke(chain.thisObject) as? ViewGroup)?.let { dock(it)?.requestLayout() }
            result
        }
    }
}
