package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.reflect.Field

internal object LauncherFolderSizing {
    fun fitIcon(native: Int, width: Int, height: Int, label: Int, spacing: Int, gap: Int): Int {
        if (native <= 0 || width <= 0 || height <= 0) return native
        return minOf(native, (width - gap.coerceAtLeast(0)).coerceAtLeast(1),
            (height - label.coerceAtLeast(0) - spacing.coerceAtLeast(0)).coerceAtLeast(1))
    }

    fun previewScale(nativeIcon: Int, renderedIcon: Int, scale: Float): Float =
        if (nativeIcon > 0 && renderedIcon > 0) scale * nativeIcon / renderedIcon else scale
}

internal class LauncherFolderAnimationGeometry(loader: ClassLoader) {
    private fun Class<*>.field(name: String) = getDeclaredField(name).apply { isAccessible = true }
    private val base = loader.loadClass("com.android.launcher3.folder.FolderAnimationManager")
    private val manager = loader.loadClass("com.android.launcher3.folder.OplusFolderAnimationManager")
    val factory = manager.getDeclaredMethod("getAnimator")
    private val content = base.field("mContent")
    private val profile = base.field("mDeviceProfile")
    private val opening = base.field("mIsOpening")
    private val folder = base.field("mFolder")
    private val folderIcon = folder.type.field("mFolderIcon")
    private val previewVerifier = folderIcon.type.field("mPreviewVerifier")
    private val folderParams = profile.type.field("mFolderParam")
    private val iconParams = profile.type.field("mIconParam")
    private val animationWidth = folderParams.type.field("l")
    private val animationHeight = folderParams.type.field("m")
    private val animationIcon = iconParams.type.field("d")
    private val organizer = content.type.field("mOrganizer")
    private val capacity = organizer.type.field("mMaxItemsPerPage")
    private val currentPage = content.type.getDeclaredMethod("getCurrentCellLayout")
    private val cell = loader.loadClass("com.android.launcher3.CellLayout")
    private val cellWidth = cell.field("mCellWidth")
    private val cellHeight = cell.field("mCellHeight")
    private val children = cell.getDeclaredMethod("getShortcutsAndWidgets")
    private val bubble = loader.loadClass("com.android.launcher3.BubbleTextView")
    private val iconSize = bubble.field("mIconSize")
    private val helper = loader.loadClass("com.android.launcher3.folder.big.FolderOpenAnimHelper")
    private val helperOwner = helper.field("folderAnimationManager")
    val preview = helper.getDeclaredMethod("getFolderAnimParma", Int::class.javaPrimitiveType,
        bubble, loader.loadClass("com.android.launcher3.folder.FolderIcon"), Int::class.javaPrimitiveType)
    private val previewParams = preview.returnType.field("previewItemDrawingParams")
    private val copyParams = previewParams.type.getDeclaredMethod("copy")
    private val previewScale = previewParams.type.field("scale")
    private val callers = listOf(manager, loader.loadClass("com.android.launcher3.folder.FolderExtImplV2"))
        .flatMap { it.declaredMethods.filter { method -> method.name in setOf(
            "getAnimator", "getFullFolderAnimatorSet", "getFolderAnimator") } }
    private data class Animation(val owner: Any, val nativeIcon: Int, val renderedIcon: Int)
    private data class Change(val field: Field, val owner: Any, val before: Int, val applied: Int) {
        fun restore() { if (field.getInt(owner) == applied) field.setInt(owner, before) }
    }
    private val active = ThreadLocal<Animation>()
    private val logged = hashSetOf<String>()

    init {
        require(listOf(animationWidth, animationHeight, animationIcon, cellWidth, cellHeight, capacity)
            .all { it.type == Int::class.javaPrimitiveType })
        require(previewScale.type == Float::class.javaPrimitiveType && copyParams.returnType == previewParams.type)
    }

    fun install(key: String, accepts: (View) -> Boolean) {
        ModernHookRegistry.installFast("$key:factory", factory) { chain ->
            val host = chain.thisObject ?: return@installFast chain.proceed()
            val view = content.get(host) as? View ?: return@installFast chain.proceed()
            if (active.get() != null || !accepts(view) ||
                !SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.FolderLayout)) {
                return@installFast chain.proceed()
            }
            val page = currentPage.invoke(view) as? View ?: return@installFast chain.proceed()
            val items = children.invoke(page) as? ViewGroup ?: return@installFast chain.proceed()
            val first = (0 until items.childCount).map(items::getChildAt).firstOrNull(bubble::isInstance)
                ?: return@installFast chain.proceed()
            val width = cellWidth.getInt(page)
            val height = cellHeight.getInt(page)
            val renderedIcon = iconSize.getInt(first)
            if (width <= 0 || height <= 0 || renderedIcon <= 0) return@installFast chain.proceed()
            val deviceProfile = profile.get(host)
            val nativeFolder = folderParams.get(deviceProfile)
            val nativeIcon = iconParams.get(deviceProfile)
            val referenceIcon = animationIcon.getInt(nativeIcon)
            val verifier = folder.get(host)?.let(folderIcon::get)?.let(previewVerifier::get)
            val itemCapacity = organizer.get(view)?.let(capacity::getInt) ?: 0
            val changes = arrayListOf<Change>()
            fun replace(field: Field, owner: Any, value: Int) {
                val before = field.getInt(owner)
                if (before != value) {
                    changes += Change(field, owner, before, value)
                    field.setInt(owner, value)
                }
            }
            try {
                replace(animationWidth, nativeFolder, width)
                replace(animationHeight, nativeFolder, height)
                replace(animationIcon, nativeIcon, renderedIcon)
                if (verifier != null && itemCapacity > 0) replace(capacity, verifier, itemCapacity)
                active.set(Animation(host, referenceIcon, renderedIcon))
                val signature = "${opening.getBoolean(host)}:$width:$height:$renderedIcon:$itemCapacity"
                if (logged.add(signature)) HookLog.i("LauncherCustomization",
                    "Native folder animation geometry: opening=${opening.getBoolean(host)}; cell=$width x $height; icon=$renderedIcon; capacity=$itemCapacity")
                chain.proceed()
            } finally {
                active.remove()
                changes.asReversed().forEach(Change::restore)
            }
        }
        ModernHookRegistry.installFast("$key:preview", preview) { chain ->
            val result = chain.proceed()
            val animation = active.get()
            if (result != null && animation != null && animation.owner === helperOwner.get(chain.thisObject) &&
                animation.nativeIcon != animation.renderedIcon) {
                val original = previewParams.get(result)
                val copy = copyParams.invoke(original)
                previewScale.setFloat(copy, LauncherFolderSizing.previewScale(animation.nativeIcon,
                    animation.renderedIcon, previewScale.getFloat(original)))
                previewParams.set(result, copy)
            }
            result
        }
        callers.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
    }
}
