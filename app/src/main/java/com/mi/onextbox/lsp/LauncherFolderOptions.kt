package com.mi.onextbox.lsp

import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
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
        runCatching { hookExpandedIndicator(loader) }
            .onFailure { HookLog.w(TAG, "Expanded folder indicator unavailable", it) }
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

    private fun hookExpandedIndicator(loader: ClassLoader) {
        val folder = loader.loadClass("com.android.launcher3.folder.OplusFolder")
        val indicator = folder.getDeclaredField("mIndicator").apply { isAccessible = true }
        val isFolderIndicator = { view: View ->
            view.hasAncestor { parent -> folder.isInstance(parent) && indicator.get(parent) === view }
        }
        val draw = indicator.type.getDeclaredMethod("dispatchDraw", Canvas::class.java)
        ModernHookRegistry.installFast("$TAG:expanded-indicator", draw) { chain ->
            if (!enabled(LauncherFeature.HideFolderIndicator)) return@installFast chain.proceed()
            val view = chain.thisObject as? View ?: return@installFast chain.proceed()
            // Paging invalidates the indicator's own display list, separately from the folder.
            // Its renderer is also used by the workspace, so check the actual owning folder.
            if (isFolderIndicator(view)) null else chain.proceed()
        }
        hookIndicatorTouch(indicator.type, "dispatchTouchEvent", isFolderIndicator)
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
        val folder = loader.loadClass("com.android.launcher3.folder.Folder")
        val folderBackground = folder.getDeclaredField("mBackground").apply { isAccessible = true }
        val emptyBackground = GradientDrawable()
        val isFolderIndicator = { view: View ->
            view.hasAncestor { parent ->
                (marker.isInstance(parent) && indicator.invoke(parent) === view) ||
                    (group.isInstance(parent) && extraIndicator.invoke(parent) === view &&
                        marker.isInstance(visibleChild.invoke(parent, false)))
            }
        }
        ModernHookRegistry.installFast("$TAG:indicator", indicator.returnType.getDeclaredMethod("onDraw", Canvas::class.java)) { chain ->
            if (!enabled(LauncherFeature.HideFolderIndicator)) return@installFast chain.proceed()
            val view = chain.thisObject as? View ?: return@installFast chain.proceed()
            if (isFolderIndicator(view)) null else chain.proceed()
        }
        hookIndicatorTouch(indicator.returnType, "onTouchEvent", isFolderIndicator)
        preview.declaredMethods.filter { it.name == "drawBackground" && it.returnType == Void.TYPE }.forEach { draw ->
            ModernHookRegistry.installFast("$TAG:${draw.toGenericString()}", draw) { chain ->
                if (enabled(LauncherFeature.HideFolderBackground)) null else chain.proceed()
            }
        }
        for (type in listOf(folder, icon, marker)) {
            ModernHookRegistry.installFast("$TAG:draw:${type.name}", type.getDeclaredMethod("dispatchDraw", Canvas::class.java)) { chain ->
                if (!enabled(LauncherFeature.HideFolderBackground)) return@installFast chain.proceed()
                val host = chain.thisObject
                // Folder paints this drawable itself, in both its clipped and unclipped passes.
                val nativeBackground = if (type == folder) folderBackground.get(host) else null
                val hidden = ArrayList<View>(2)
                fun hide(view: Any?) {
                    if (view is View && view.visibility == View.VISIBLE) {
                        hidden.add(view)
                        view.setTransitionVisibility(View.INVISIBLE)
                    }
                }
                try {
                    if (nativeBackground != null) folderBackground.set(host, emptyBackground)
                    if (expanded.isInstance(host)) {
                        expandedLayers.forEach { hide(it.get(host)) }
                    } else if (icon.isInstance(host)) {
                        getPreview.invoke(host)?.let { hide(background.get(it)) }
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

    private fun hookIndicatorTouch(
        type: Class<*>, methodName: String, isFolderIndicator: (View) -> Boolean,
    ) {
        runCatching {
            val method = type.getDeclaredMethod(methodName, MotionEvent::class.java)
            ModernHookRegistry.installFast("$TAG:touch:${type.name}", method) { chain ->
                val event = chain.getArg(0) as? MotionEvent
                if (event?.actionMasked != MotionEvent.ACTION_DOWN ||
                    !enabled(LauncherFeature.BlockFolderIndicatorTouch) ||
                    !enabled(LauncherFeature.HideFolderIndicator)) return@installFast chain.proceed()
                val view = chain.thisObject as? View ?: return@installFast chain.proceed()
                // Decline a new gesture so the parent can route it to another target.
                if (isFolderIndicator(view)) false else chain.proceed()
            }
        }.onFailure { HookLog.w(TAG, "Folder indicator touch hook unavailable for ${type.name}", it) }
    }

    private inline fun View.hasAncestor(matches: (View) -> Boolean): Boolean {
        var ancestor = parent as? View
        while (ancestor != null) {
            if (matches(ancestor)) return true
            ancestor = ancestor.parent as? View
        }
        return false
    }

    private fun enabled(feature: LauncherFeature) = HookConfigSnapshot.boolean(feature.key, false)
        ?: LspConfig.isLauncherFeatureEnabledXposed(feature)
}
