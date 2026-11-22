package com.mi.onextbox.lsp

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal object KeyguardGestureHandle {
    private const val OWNER = "keyguard-gesture-handle"
    private val feature = LspConfig.KeyguardFeature.GestureHandle
    private val main = Handler(Looper.getMainLooper())
    private var area = WeakReference<FrameLayout>(null)
    private var handle = WeakReference<Handle>(null)
    private var status = WeakReference<Any>(null)
    private var navigationHidden: Boolean? = null
    private var prefs: SharedPreferences? = null
    private val refresh = Runnable {
        if (!HotReloadLifecycle.isRetiring) runCatching { update() }.onFailure {
            removeHandle()
            HookLog.w(OWNER, "Could not update lock screen handle", it)
        }
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == feature.key) main.post(refresh)
    }

    fun hook(loader: ClassLoader) {
        val host = Reflect.findClassIfExists("com.oplus.systemui.keyguard.ui.view.OplusKeyguardBottomAreaView", loader) ?: return
        val controller = Reflect.findClassIfExists("com.android.systemui.statusbar.StatusBarStateControllerImpl", loader) ?: return
        val nav = Reflect.findClassIfExists("com.android.systemui.navigationbar.views.NavigationBar", loader)
        fun after(method: java.lang.reflect.Method, action: (ModernMethodHook.MethodHookParam) -> Unit) {
            ModernHookRegistry.installCompat("$OWNER:${method.name}", method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null) action(param)
                }
            })
        }
        after(host.getDeclaredMethod("onAttachedToWindow")) { param ->
            val view = param.thisObject as FrameLayout
            if (view.display?.displayId == android.view.Display.DEFAULT_DISPLAY) {
                area = WeakReference(view)
                refresh.run()
            }
        }
        for (method in controller.declaredMethods.filter { (it.name == "setState" && it.parameterCount == 2) ||
                (it.name == "setIsDozing" && it.parameterCount == 1) }) {
            after(method) { param ->
                val owner = param.thisObject ?: return@after
                status = WeakReference(owner)
                refresh.run()
            }
        }
        nav?.declaredMethods?.firstOrNull { it.name == "setWindowState" && it.parameterCount == 3 }?.let { method ->
            after(method) { param ->
                if (param.args[0] == android.view.Display.DEFAULT_DISPLAY && param.args[1] == 2) {
                    navigationHidden = param.args[2] == 2
                    refresh.run()
                }
            }
        }
        fun start(saved: Any?) {
            val restored = saved as? Array<*>
            status = WeakReference(restored?.getOrNull(0) ?: status.get())
            navigationHidden = restored?.getOrNull(1) as? Boolean ?: navigationHidden
            if (prefs == null) {
                prefs = ModernHookRuntime.remotePreferences("lsp_features")
                prefs?.registerOnSharedPreferenceChangeListener(listener)
            }
            refresh.run()
        }
        HotReloadLifecycle.register(OWNER, capture = { arrayOf(status.get(), navigationHidden) }, detach = {
            main.removeCallbacks(refresh)
            prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            prefs = null
            removeHandle()
        }, restore = ::start, resumeAfterRejection = { start(null) })
        HotReloadLifecycle.afterCommit { start(HotReloadLifecycle.restoredState(OWNER)) }
    }

    private fun update() {
        if (!LspConfig.isKeyguardFeatureEnabledXposed(feature)) {
            if (handle.get() != null) removeHandle()
            return
        }
        val host = area.get()?.takeIf { it.isAttachedToWindow } ?: android.view.inspector.WindowInspector.getGlobalWindowViews()
            .firstNotNullOfOrNull { root ->
                val id = root.resources.getIdentifier("keyguard_bottom_area", "id", "com.android.systemui")
                (root.findViewById<View>(id) as? FrameLayout)?.takeIf { it.display?.displayId == android.view.Display.DEFAULT_DISPLAY }
            }?.also { area = WeakReference(it) } ?: return
        val controller = status.get() ?: run {
            val loader = host.javaClass.classLoader
            val dependency = Reflect.findClass("com.android.systemui.Dependency", loader).getField("sDependency").get(null)
                ?: return
            Reflect.callMethod(dependency, "getDependencyInner",
                Reflect.findClass("com.android.systemui.plugins.statusbar.StatusBarStateController", loader))
                ?.also { status = WeakReference(it) } ?: return
        }
        var view = handle.get()
        if (view?.parent !== host) {
            removeHandle()
            view = Handle(host.context)
            host.addView(view)
            handle = WeakReference(view)
        }
        val lockScreen = Reflect.callMethod(controller, "getState") == 1 && Reflect.callMethod(controller, "isDozing") == false
        view.showOnKeyguard(lockScreen, navigationHidden)
    }

    private fun removeHandle() {
        handle.get()?.let { (it.parent as? FrameLayout)?.removeView(it) }
        handle.clear()
    }

    private class Handle(context: Context) : View(context) {
        private var onKeyguard = false
        private var systemBarHidden: Boolean? = null
        private var gestural = false
        private val shape = GradientDrawable()
        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = INVISIBLE
            background = shape
            applyStyle()
        }

        private fun dimen(name: String, pkg: String, fallbackDp: Int): Int {
            val id = resources.getIdentifier(name, "dimen", pkg)
            return if (id != 0) resources.getDimensionPixelSize(id)
            else (fallbackDp * resources.displayMetrics.density).roundToInt()
        }

        private fun applyStyle() {
            val navMode = resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
            gestural = navMode != 0 && resources.getInteger(navMode) == 2
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val width = dimen(if (landscape) "navigation_gesture_view_landscape_width" else "navigation_gesture_view_width", "oplus", 108)
            val height = dimen("navigation_handle_height", "com.android.systemui", 4)
            layoutParams = FrameLayout.LayoutParams(width, height, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dimen("navigation_handle_bottom", "com.android.systemui", 2)
            }
            shape.cornerRadius = dimen("navigation_handle_radius", "com.android.systemui", 2).toFloat()
            val hints = runCatching {
                context.getSystemService(WallpaperManager::class.java)?.getWallpaperColors(WallpaperManager.FLAG_LOCK)?.colorHints
            }.getOrNull() ?: 0
            shape.setColor(if (hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) Color.BLACK else Color.WHITE)
        }

        fun showOnKeyguard(show: Boolean, navigationHidden: Boolean?) {
            onKeyguard = show
            systemBarHidden = navigationHidden
            updateVisibility(rootWindowInsets)
        }

        private fun updateVisibility(insets: WindowInsets?) {
            val hidden = systemBarHidden ?: (insets?.isVisible(WindowInsets.Type.navigationBars()) == false)
            visibility = if (gestural && onKeyguard && hidden) VISIBLE else INVISIBLE
        }

        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            updateVisibility(rootWindowInsets ?: insets)
            return super.onApplyWindowInsets(insets)
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            applyStyle()
        }
    }
}
