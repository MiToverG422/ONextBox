package com.mi.onextbox.lsp

import android.content.res.TypedArray
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.reflect.Field
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

internal object LauncherCustomizationHooker {
    private const val TAG = "LauncherCustomization"

    internal class Targets(loader: ClassLoader) {
        val bubble = loader.loadClass("com.android.launcher3.BubbleTextView")
        val display = bubble.getDeclaredField("mDisplay").apply { isAccessible = true }
        val iconSize = bubble.getDeclaredField("mIconSize").apply { isAccessible = true }
        val getIcon = bubble.getDeclaredMethod("getIcon")
        val applyIcon = bubble.getDeclaredMethod("applyCompoundDrawables", android.graphics.drawable.Drawable::class.java)
        val iconBindings = listOf(bubble, loader.loadClass("com.android.launcher3.OplusBubbleTextView"))
            .flatMap { it.declaredMethods.filter { method -> method.name in setOf("setIcon", "applyCompoundDrawables") &&
                method.parameterTypes.contentEquals(arrayOf(android.graphics.drawable.Drawable::class.java)) } }
        val iconCallers = listOf(bubble, loader.loadClass("com.android.launcher3.OplusBubbleTextView"))
            .flatMap { it.declaredMethods.filter { method -> method.name in setOf("applyFromWorkspaceItem",
                "applyFromApplicationInfo", "applyIconAndLabel", "applyFromItemInfoWithIcon", "setIconVisible") } }
        val iconInitializers = listOf(bubble, loader.loadClass("com.android.launcher3.OplusBubbleTextView"))
            .mapNotNull { type -> runCatching {
                type.getDeclaredMethod("getInitIconSize", TypedArray::class.java, Int::class.javaPrimitiveType)
            }.getOrNull() }.distinct()
        val paged = loader.loadClass("com.android.launcher3.folder.FolderPagedView")
        val organizer = paged.getDeclaredField("mOrganizer").apply { isAccessible = true }
        val gridType = loader.loadClass("com.android.launcher3.folder.FolderGridOrganizer")
        val limits = listOf("mMaxCountX", "mMaxCountY", "mMaxItemsPerPage").map {
            gridType.getDeclaredField(it).apply { require(type == Int::class.javaPrimitiveType); isAccessible = true }
        }
        val dimensions = paged.getDeclaredMethod("getDesiredFoldCellSize")
        private fun pairField(vararg names: String): Field = names.firstNotNullOf { name ->
            runCatching { dimensions.returnType.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
        }
        val cellWidth = pairField("a", "first")
        val cellHeight = pairField("b", "second")
        val cellPair = dimensions.returnType.getDeclaredConstructor(Any::class.java, Any::class.java)
            .apply { isAccessible = true }
        val prepare = listOf(paged, loader.loadClass("com.android.launcher3.folder.OplusFolderPagedView"))
            .flatMap { type -> type.declaredMethods.filter { it.name in setOf("setupContentDimensions", "bindItems") } }
            .distinct().also { require(it.isNotEmpty() && iconInitializers.isNotEmpty()) }
        val creators = listOf(paged, loader.loadClass("com.android.launcher3.folder.OplusFolderPagedView"))
            .flatMap { it.declaredMethods.filter { method -> method.name == "createAndAddNewPage" && method.parameterCount == 0 } }
        val gridCounts = listOf("mGridCountX", "mGridCountY").map { paged.getDeclaredField(it).apply { isAccessible = true } }
        val cell = loader.loadClass("com.android.launcher3.CellLayout")
        val cellDimensions = cell.getDeclaredMethod("setCellDimensions", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val cellGrid = cell.getDeclaredMethod("setGridSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val cellSizes = listOf("mCellWidth", "mCellHeight").map { cell.getDeclaredField(it).apply { isAccessible = true } }
        val folderCallers = (listOf(paged, loader.loadClass("com.android.launcher3.folder.OplusFolderPagedView")) +
            listOfNotNull(runCatching { loader.loadClass("com.android.launcher3.folder.FolderPagedViewExtImpV2") }.getOrNull()))
            .flatMap { it.declaredMethods.filter { method -> method.name in setOf("arrangeChildren", "bindItems", "createAndAddNewPage", "setupContentDimensions") } }
        val animation = runCatching { LauncherFolderAnimationGeometry(loader) }.getOrNull()
    }

    private class Grid(val original: IntArray, var applied: IntArray)
    private class Session(val target: Targets) {
        val grids = WeakHashMap<Any, Grid>()
        val pages = WeakHashMap<View, Any>()
        val folderSize = ThreadLocal<IntArray>()
        val icons = WeakHashMap<View, IntArray>()
        val cells = WeakHashMap<View, IntArray>()
        private var loggedIcons = false
        private var loggedFolder = false
        private var loggedFolderIcon = false
        private var restoring = false

        private fun parentCell(view: View): View? {
            var parent = view.parent as? View
            while (parent != null) {
                if (target.cell.isInstance(parent)) return parent
                parent = parent.parent as? View
            }
            return null
        }

        fun sizeIcon(view: View) {
            if (restoring) return
            val display = target.display.getInt(view)
            val previous = icons[view]
            if (display !in setOf(0, 2, 8, 9)) return
            val current = target.iconSize.getInt(view)
            if (current <= 0) return
            val native = if (previous != null && previous[1] == current) previous[0] else current
            val folderEnabled = display == 2 && target.animation != null &&
                SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.FolderLayout)
            val box = if (folderEnabled) folderSize.get() ?: parentCell(view)?.let { page ->
                IntArray(2) { target.cellSizes[it].getInt(page) }
            } ?: previous?.takeIf { it.size >= 4 }?.copyOfRange(2, 4) else null
            val text = view as? TextView
            val labelHeight = text?.paint?.fontMetrics?.let { ceil(it.bottom - it.top).toInt() } ?: 0
            val applied = if (display == 2) {
                if (box == null) native else LauncherFolderSizing.fitIcon(native, box[0], box[1],
                    labelHeight, text?.compoundDrawablePadding ?: 0, (4 * view.resources.displayMetrics.density).roundToInt())
            } else if (SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.DesktopIconSize)) {
                SystemCustomizationRules.scaled(native, SystemCustomizationSettings.value(SystemCustomizationSettings.iconPercent))
            } else native
            target.iconSize.setInt(view, applied)
            if (applied == native && !folderEnabled) icons.remove(view)
            else icons[view] = intArrayOf(native, applied, box?.get(0) ?: 0, box?.get(1) ?: 0)
            if (applied != native && !loggedIcons) {
                loggedIcons = true
                HookLog.i(TAG, "Native icon binding resized: $native -> $applied; display=$display")
            }
            if (folderEnabled && box != null && !loggedFolderIcon) {
                loggedFolderIcon = true
                HookLog.i(TAG, "Expanded folder icon fitted: $native -> $applied; cell=${box[0]} x ${box[1]}; label=$labelHeight; spacing=${text?.compoundDrawablePadding}")
            }
        }

        private fun refreshIcons(group: ViewGroup) {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (target.bubble.isInstance(child)) {
                    val before = target.iconSize.getInt(child)
                    sizeIcon(child)
                    val icon = target.getIcon.invoke(child) as? android.graphics.drawable.Drawable ?: continue
                    val size = target.iconSize.getInt(child)
                    if (before != size || icon.bounds.width() != size || icon.bounds.height() != size) {
                        target.applyIcon.invoke(child, icon)
                        child.requestLayout()
                    }
                } else if (child is ViewGroup) refreshIcons(child)
            }
        }

        fun refreshPages(view: View) {
            val parent = view as? ViewGroup ?: return
            for (i in 0 until parent.childCount) {
                val page = parent.getChildAt(i)
                resizePage(view, page)
                if (page is ViewGroup) refreshIcons(page)
            }
        }

        fun resizePage(view: View, page: View) {
            val grid = target.organizer.get(view)?.let { grids[it] } ?: return
            if (!SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.FolderLayout)) return
            val current = IntArray(2) { target.cellSizes[it].getInt(page) }
            if (current.any { it <= 0 }) return
            val previous = cells[page]
            val native = if (previous != null && current[0] == previous[2] && current[1] == previous[3]) previous else current
            val width = (native[0].toFloat() * grid.original[0] / grid.applied[0]).roundToInt().coerceAtLeast(1)
            val height = (native[1] * minOf(1f, grid.original[1].toFloat() / grid.applied[1])).roundToInt().coerceAtLeast(1)
            target.cellDimensions.invoke(page, width, height)
            target.cellGrid.invoke(page, grid.applied[0], grid.applied[1])
            cells[page] = intArrayOf(native[0], native[1], width, height)
            if (!loggedFolder) {
                loggedFolder = true
                HookLog.i(TAG, "Native folder page resized: ${grid.applied[0]} x ${grid.applied[1]}; cell=$width x $height")
            }
        }

        fun prepare(view: View): IntArray? {
            val organizer = target.organizer.get(view) ?: return null
            val current = IntArray(3) { target.limits[it].getInt(organizer) }
            val enabled = target.animation != null && SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.FolderLayout)
            val previous = grids[organizer]
            if (!enabled) {
                if (previous != null) restore(organizer, previous)
                grids.remove(organizer)
                pages.remove(view)
                return null
            }
            if (current.any { it <= 0 }) return null
            val native = if (previous != null && current.contentEquals(previous.applied)) previous.original else current
            val (columns, rows) = SystemCustomizationRules.folderGrid(
                SystemCustomizationSettings.value(SystemCustomizationSettings.folderColumns),
                SystemCustomizationSettings.value(SystemCustomizationSettings.folderRows))
            val applied = intArrayOf(columns, rows, columns * rows)
            grids[organizer] = Grid(native, applied)
            pages[view] = organizer
            target.limits.forEachIndexed { index, field -> field.setInt(organizer, applied[index]) }
            target.gridCounts.forEachIndexed { index, field -> field.setInt(view, applied[index]) }
            val dimensions = target.dimensions.invoke(view)
            val width = (target.cellWidth.get(dimensions) as Number).toInt()
            val height = (target.cellHeight.get(dimensions) as Number).toInt()
            return intArrayOf((width.toFloat() * native[0] / columns).roundToInt().coerceAtLeast(1),
                (height * minOf(1f, native[1].toFloat() / rows)).roundToInt().coerceAtLeast(1))
        }

        fun restore(organizer: Any, grid: Grid) {
            target.limits.forEachIndexed { index, field ->
                if (field.getInt(organizer) == grid.applied[index]) field.setInt(organizer, grid.original[index])
            }
        }

        fun capture() = pages.mapNotNull { (view, organizer) -> grids[organizer]?.let {
            arrayOf(view, organizer, it.original.copyOf(), it.applied.copyOf())
        } }.toTypedArray()

        fun detach() {
            restoring = true
            try {
                grids.toMap().forEach { (organizer, grid) -> restore(organizer, grid) }
                icons.toMap().forEach { (view, sizes) -> if (target.iconSize.getInt(view) == sizes[1]) {
                    target.iconSize.setInt(view, sizes[0])
                    target.getIcon.invoke(view)?.let { target.applyIcon.invoke(view, it) }
                } }
                cells.toMap().forEach { (view, sizes) -> target.cellDimensions.invoke(view, sizes[0], sizes[1]) }
                grids.clear(); pages.clear(); icons.clear(); cells.clear()
            } finally { restoring = false }
        }

        fun restore(saved: Any?) {
            (saved as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach { row ->
                val view = row.getOrNull(0) as? View ?: return@forEach
                val organizer = row.getOrNull(1) ?: return@forEach
                val original = row.getOrNull(2) as? IntArray ?: return@forEach
                val applied = row.getOrNull(3) as? IntArray ?: return@forEach
                if (original.size != 3 || applied.size != 3) return@forEach
                pages[view] = organizer
                grids[organizer] = Grid(original, original)
                prepare(view)
                refreshPages(view)
            }
        }
    }

    fun hook(loader: ClassLoader) {
        val key = "$TAG@${System.identityHashCode(loader)}"
        var session: Session? = null
        runCatching {
            val targets = Targets(loader)
            val owner = Session(targets).also { session = it }
            targets.iconBindings.forEachIndexed { index, method ->
                ModernHookRegistry.installFast("$key:icon:$index", method) { chain ->
                    (chain.thisObject as? View)?.let(owner::sizeIcon)
                    chain.proceed()
                }
            }
            targets.prepare.forEachIndexed { index, method ->
                ModernHookRegistry.installFast("$key:folder:$index", method) { chain ->
                    val view = chain.thisObject as? View ?: return@installFast chain.proceed()
                    val previous = owner.folderSize.get()
                    owner.folderSize.set(owner.prepare(view))
                    try {
                        val result = chain.proceed()
                        owner.refreshPages(view)
                        result
                    } finally {
                        if (previous == null) owner.folderSize.remove() else owner.folderSize.set(previous)
                    }
                }
            }
            targets.animation?.install("$key:animation") { view -> owner.pages.containsKey(view) }
            targets.creators.forEachIndexed { index, method ->
                ModernHookRegistry.installFast("$key:page:$index", method) { chain ->
                    val view = chain.thisObject as View
                    owner.prepare(view)
                    chain.proceed().also { page ->
                        if (page is View) {
                            owner.prepare(view)
                            owner.resizePage(view, page)
                        }
                    }
                }
            }
            (targets.iconCallers + targets.folderCallers).distinct().forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            HotReloadLifecycle.register(key, capture = owner::capture, detach = owner::detach,
                restore = owner::restore, canReload = { owner.pages.keys.none(View::isShown) })
            HookLog.i(TAG, "Native icon binding and folder page adapters installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            session?.detach()
            HookLog.w(TAG, "Native launcher sizing adapter unavailable; system layout retained", it)
        }
    }
}
