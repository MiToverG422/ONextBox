package com.mi.onextbox.lsp

import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object LauncherFolderOptions {
    private const val TAG = "LauncherFolderOptions"

    fun hook(loader: ClassLoader) {
        runCatching { hookReturn(loader) }.onFailure { HookLog.w(TAG, "Folder return unavailable", it) }
        runCatching { hookAppearance(loader) }.onFailure { HookLog.w(TAG, "Folder appearance unavailable", it) }
    }

    private fun hookReturn(loader: ClassLoader) {
        val launcher = loader.loadClass("com.android.launcher.Launcher")
        val folder = loader.loadClass("com.android.launcher3.folder.Folder")
        val item = loader.loadClass("com.android.launcher3.model.data.ItemInfo")
        val open = folder.getDeclaredMethod("getOpen", loader.loadClass("com.android.launcher3.views.ActivityContext"))
        val info = folder.getDeclaredField("mInfo").apply { isAccessible = true }
        val id = item.getDeclaredField("id").apply { isAccessible = true }
        val container = item.getDeclaredField("container").apply { isAccessible = true }
        val start = launcher.getDeclaredMethod("startActivitySafely", View::class.java, Intent::class.java, item)
        val close = launcher.getMethod("closeOpenFolder", Boolean::class.javaPrimitiveType)
        val pending = WeakHashMap<Any, WeakReference<View>>()
        ModernHookRegistry.installFast("$TAG:start", start) { chain ->
            if (!enabled(LauncherFeature.FolderReturnHome)) return@installFast chain.proceed()
            val activity = chain.thisObject ?: return@installFast chain.proceed()
            val opened = open.invoke(null, activity) as? View
            val app = chain.getArg(2)
            val owner = opened?.let(info::get)
            val fromFolder = owner != null && app != null && id.get(owner) == container.get(app)
            val result = chain.proceed()
            if (result == true && fromFolder && opened != null) pending[activity] = WeakReference(opened)
            result
        }
        for (name in listOf("onStop", "onResume")) {
            ModernHookRegistry.installFast("$TAG:$name", launcher.getDeclaredMethod(name)) { chain ->
                val activity = chain.thisObject
                val origin = pending.remove(activity)?.get()
                if (origin != null && enabled(LauncherFeature.FolderReturnHome) && open.invoke(null, activity) === origin) {
                    close.invoke(activity, false)
                }
                chain.proceed()
            }
        }
    }

    private fun hookAppearance(loader: ClassLoader) {
        val preview = loader.loadClass("com.android.launcher3.folder.OplusPreviewBackground")
        val background = preview.getDeclaredField("mBgView").apply { isAccessible = true }
        val marker = loader.loadClass("com.android.launcher3.folder.FlexibleFolderIcon")
        val indicator = marker.getDeclaredMethod("getIndicator")
        val group = loader.loadClass("com.android.launcher3.stack.view.IMultipleSizeGroupView")
        val extraIndicator = group.getMethod("getExtraIndicator")
        val visibleChild = group.getMethod("getCurrentVisibleView", Boolean::class.javaPrimitiveType)
        val icon = loader.loadClass("com.android.launcher3.folder.FolderIcon")
        val getPreview = icon.getDeclaredMethod("getPreviewBackground")
        val expanded = loader.loadClass("com.android.launcher3.folder.OplusFolder")
        val expandedLayers = listOf("mContentBackground", "mContentAnimationBackground").map {
            expanded.getDeclaredField(it).apply { isAccessible = true }
        }
        val expandedIndicator = expanded.getDeclaredField("mIndicator").apply { isAccessible = true }
        val folder = loader.loadClass("com.android.launcher3.folder.Folder")
        val folderBackground = folder.getDeclaredField("mBackground").apply { isAccessible = true }
        val emptyBackground = GradientDrawable()
        ModernHookRegistry.installFast("$TAG:indicator", indicator.returnType.getDeclaredMethod("onDraw", Canvas::class.java)) { chain ->
            if (!enabled(LauncherFeature.HideFolderIndicator)) return@installFast chain.proceed()
            val view = chain.thisObject as? View ?: return@installFast chain.proceed()
            var parent = view.parent as? View
            while (parent != null) {
                if (marker.isInstance(parent) && indicator.invoke(parent) === view) return@installFast null
                if (group.isInstance(parent) && extraIndicator.invoke(parent) === view &&
                    marker.isInstance(visibleChild.invoke(parent, false))) return@installFast null
                parent = parent.parent as? View
            }
            chain.proceed()
        }
        preview.declaredMethods.filter { it.name == "drawBackground" && it.returnType == Void.TYPE }.forEach { draw ->
            ModernHookRegistry.installFast("$TAG:${draw.toGenericString()}", draw) { chain ->
                if (enabled(LauncherFeature.HideFolderBackground)) null else chain.proceed()
            }
        }
        for (type in listOf(folder, icon, marker)) {
            ModernHookRegistry.installFast("$TAG:draw:${type.name}", type.getDeclaredMethod("dispatchDraw", Canvas::class.java)) { chain ->
                val hideBackground = enabled(LauncherFeature.HideFolderBackground)
                val hideIndicator = enabled(LauncherFeature.HideFolderIndicator)
                if (!hideBackground && !hideIndicator) return@installFast chain.proceed()
                val host = chain.thisObject
                // Folder paints this drawable itself, in both its clipped and unclipped passes.
                val nativeBackground = if (type == folder && hideBackground) folderBackground.get(host) else null
                val hidden = ArrayList<View>(3)
                fun hide(view: Any?) {
                    if (view is View && view.visibility == View.VISIBLE) {
                        hidden.add(view)
                        view.setTransitionVisibility(View.INVISIBLE)
                    }
                }
                try {
                    if (nativeBackground != null) folderBackground.set(host, emptyBackground)
                    if (expanded.isInstance(host)) {
                        if (hideBackground) expandedLayers.forEach { hide(it.get(host)) }
                        if (hideIndicator) hide(expandedIndicator.get(host))
                    } else if (icon.isInstance(host)) {
                        if (hideBackground) getPreview.invoke(host)?.let { hide(background.get(it)) }
                    }
                    chain.proceed()
                } finally {
                    if (nativeBackground != null && folderBackground.get(host) === emptyBackground)
                        folderBackground.set(host, nativeBackground)
                    hidden.forEach { it.setTransitionVisibility(View.VISIBLE) }
                }
            }
        }
    }

    private fun enabled(feature: LauncherFeature) = HookConfigSnapshot.boolean(feature.key, false)
        ?: LspConfig.isLauncherFeatureEnabledXposed(feature)
}
