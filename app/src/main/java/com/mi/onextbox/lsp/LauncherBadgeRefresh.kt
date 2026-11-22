package com.mi.onextbox.lsp

import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.ref.WeakReference

internal class LauncherBadgeRefresh(loader: ClassLoader) {
    private val main = Handler(Looper.getMainLooper())
    private val launcher = loader.loadClass("com.android.launcher.Launcher")
    private var activity = WeakReference<Activity>(null)
    private val bubble = loader.loadClass("com.android.launcher3.BubbleTextView")
    private val folder = loader.loadClass("com.android.launcher3.folder.FolderIcon")
    private val shortcut = ModernReflect.findClassIfExists("com.android.launcher3.viewcontainer.ShortcutContainer", loader)
    private var lastApplied: Pair<Boolean, Boolean>? = null
    private var preferences: SharedPreferences? = null
    private val refresh = Runnable {
        if (HotReloadLifecycle.isRetiring) return@Runnable
        val appearance = LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.ColorfulNotificationDot) to
            LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.NotificationDotShadow)
        if (appearance == lastApplied) return@Runnable
        val root = activity.get()?.window?.peekDecorView() as? ViewGroup ?: return@Runnable
        if (!root.isAttachedToWindow || root.windowVisibility != View.VISIBLE) return@Runnable
        invalidateHosts(root)
        lastApplied = appearance
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == MODERN_PREFERENCES_REVISION_KEY ||
            key == LauncherFeature.ColorfulNotificationDot.key || key == LauncherFeature.NotificationDotShadow.key) schedule()
    }

    fun install() {
        val key = "LauncherBadgeRefresh@${System.identityHashCode(launcher.classLoader)}"
        val focus = launcher.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType)
        ModernHookRegistry.installFast("$key:focus", focus) { chain ->
            chain.proceed().also {
                if (chain.getArg(0) == true) {
                    activity = WeakReference(chain.thisObject as? Activity)
                    schedule()
                }
            }
        }
        HotReloadLifecycle.register(key, capture = { activity.get() }, detach = ::detach, restore = { saved ->
            activity = WeakReference(saved as? Activity)
            attach()
        })
        HotReloadLifecycle.afterCommit { attach() }
    }

    private fun attach() {
        if (preferences == null) {
            preferences = ModernHookRuntime.remotePreferences("lsp_features")?.also {
                it.registerOnSharedPreferenceChangeListener(listener)
            }
        }
        schedule()
    }

    private fun detach() {
        preferences?.unregisterOnSharedPreferenceChangeListener(listener)
        preferences = null
        main.removeCallbacks(refresh)
        activity.clear()
        lastApplied = null
    }

    private fun schedule() {
        main.removeCallbacks(refresh)
        main.post(refresh)
    }

    private fun invalidateHosts(view: View) {
        if (bubble.isInstance(view) || folder.isInstance(view) || shortcut?.isInstance(view) == true) view.invalidate()
        if (view is ViewGroup) for (index in 0 until view.childCount) invalidateHosts(view.getChildAt(index))
    }
}
