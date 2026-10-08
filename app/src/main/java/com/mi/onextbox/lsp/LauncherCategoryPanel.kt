package com.mi.onextbox.lsp

import android.app.Activity
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.RelativeLayout
import android.widget.FrameLayout
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Modifier
import org.json.JSONArray

// Standalone native view for last-page categories.
internal class LauncherCategoryPanel(
    private val activity: Activity,
    private val manager: Any,
    val drawer: ViewGroup,
    val drawerController: Any,
    private val creating: ThreadLocal<Boolean>,
    private val registerStore: (Any) -> Unit,
    private val unregisterStore: (Any) -> Unit,
    private val modeScope: LauncherCategoryModeScope,
) {
    lateinit var apps: ViewGroup
        private set
    val root: ViewGroup? get() = if (::apps.isInitialized) apps else null
    lateinit var controller: Any
        private set
    lateinit var store: Any
        private set
    val drawerStore = requireNotNull(ModernReflect.callMethod(drawer, "getAppsStore"))
    var active = false
        private set
    private var disposed = false
    private var synchronizing = false
    private var drawerVisibility = View.INVISIBLE
    private var navigationColor = 0
    private var navigationDividerColor = 0
    private var navigationContrast = false
    private val resourceIds = mutableMapOf<String, Int>()
    private val location = IntArray(2)
    private val drawerListeners = listeners().filter { ownsView(it, drawer) }
    private val panelListeners = mutableListOf<Any>()
    private val searchBackgroundBounds = Rect()
    private var topBlur: LauncherCategoryTopBlur? = null
    private var contentTop = 0
    private var statusTop = 0
    private var layoutDirty = true
    private var layingOut = false
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastStatus = -1
    private var lastNavigation = -1
    private var lastIme = -1
    private var verticalParents = emptyList<ViewGroup>()
    private val dimensions = mutableMapOf<String, Int>()
    private val orderPrefs by lazy { activity.getSharedPreferences("onextbox_category_page", Activity.MODE_PRIVATE) }
    private var savedOrder: List<String>? = null
    private var orderChanged = false
    private var finishingFolders = false
    private val bubbleClass = ModernReflect.findClass("com.android.launcher3.BubbleTextView", drawer.javaClass.classLoader)
    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { layoutDirty = true }
    private val layoutListener = ViewTreeObserver.OnPreDrawListener {
        if (active) runCatching {
            clampVerticalTransforms()
            val insets = apps.rootWindowInsets
            if (layoutDirty || apps.width != lastWidth || apps.height != lastHeight ||
                (insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: -1) != lastStatus ||
                (insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0) != lastNavigation ||
                (insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0) != lastIme
            ) applyLayout()
        }.onFailure { HookLog.w("LauncherCategoryPage", "Category layout unavailable", it) }
        true
    }

    fun prepare() {
        if (::apps.isInitialized) return
        val before = listeners().toList()
        creating.set(true)
        try {
            val parent = drawer.parent as ViewGroup
            val layout = drawer.resources.getIdentifier("all_apps", "layout", "com.android.launcher")
            require(layout != 0) { "Native apps layout missing" }
            apps = activity.layoutInflater.inflate(layout, parent, false) as ViewGroup
            apps.id = View.generateViewId()
            apps.visibility = View.INVISIBLE
            apps.isSaveEnabled = false
            verticalParents = listOfNotNull(
                view("b_level_apps_view_translate") as? ViewGroup,
                view("apps_view_translate") as? ViewGroup,
                view("all_apps_content") as? ViewGroup,
            )
            val contentParent = view("apps_view_translate") as? ViewGroup
            if (contentParent != null) {
                topBlur = LauncherCategoryTopBlur(activity)
                contentParent.addView(topBlur, RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            }
            store = requireNotNull(ModernReflect.callMethod(apps, "getAppsStore"))
            registerStore(store)
            controller = ModernReflect.newInstance(drawerController.javaClass, activity)
            val nativeTranslate = ModernReflect.getObjectField(drawerController, "mTranslateAppsView") as View
            ModernReflect.setObjectField(controller, "mTranslateAppsView", requireNotNull(apps.findViewById<View>(nativeTranslate.id)))
            val scrim = requireNotNull(ModernReflect.getObjectField(drawerController, "mScrimView"))
            val scrimVisibility = (scrim as View).visibility
            try { ModernReflect.callMethod(controller, "setupViews", scrim, apps) }
            finally { scrim.visibility = scrimVisibility }
            // Search attachment callbacks register their own data listener through the launcher entry.
            ModernReflect.setObjectField(activity, "mAppsView", apps)
            ModernReflect.setObjectField(activity, "mAllAppsController", controller)
            try { parent.addView(apps, parent.indexOfChild(drawer) + 1) }
            finally {
                ModernReflect.setObjectField(activity, "mAppsView", drawer)
                ModernReflect.setObjectField(activity, "mAllAppsController", drawerController)
            }
            val insets = Rect(ModernReflect.getObjectField(drawer, "mInsets") as Rect)
            ModernReflect.callMethod(apps, "setInsets", insets)
            panelListeners.addAll(listeners().filter { listener -> before.none { it === listener } })
            panelListeners.forEach { listeners().remove(it) }
            syncStore(drawerStore)
            ModernReflect.callMethod(apps, "updateCategoryTabVisibility", true, false)
            ModernReflect.callMethod(apps, "switchToTab", 1)
            applyLayout()
            apps.viewTreeObserver.addOnPreDrawListener(layoutListener)
            apps.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
            apps.requestLayout()
        } catch (error: Throwable) {
            panelListeners.addAll(listeners().filter { listener -> before.none { it === listener } && panelListeners.none { it === listener } })
            dispose()
            throw error
        } finally {
            creating.remove()
        }
    }

    fun activate() {
        prepare()
        if (active) return
        drawerVisibility = drawer.visibility
        syncStore(drawerStore)
        active = true
        layoutDirty = true
        navigationColor = activity.window.navigationBarColor
        navigationDividerColor = activity.window.navigationBarDividerColor
        navigationContrast = activity.window.isNavigationBarContrastEnforced
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
        activity.window.isNavigationBarContrastEnforced = false
        ModernReflect.setObjectField(activity, "mAppsView", apps)
        ModernReflect.setObjectField(activity, "mAllAppsController", controller)
        drawerListeners.forEach { listeners().remove(it) }
        panelListeners.forEach { if (listeners().none { listener -> listener === it }) listeners().add(it) }
        drawer.visibility = View.INVISIBLE
        ensureCategories()
    }

    fun deactivate() {
        if (!active) return
        finishClosingFolders(force = true)
        // Restore the entry first so subsequent state handling uses the normal drawer.
        ModernReflect.setObjectField(activity, "mAppsView", drawer)
        ModernReflect.setObjectField(activity, "mAllAppsController", drawerController)
        active = false
        activity.window.navigationBarColor = navigationColor
        activity.window.navigationBarDividerColor = navigationDividerColor
        activity.window.isNavigationBarContrastEnforced = navigationContrast
        panelListeners.forEach { listeners().remove(it) }
        drawerListeners.forEach { if (listeners().none { listener -> listener === it }) listeners().add(it) }
        apps.visibility = View.INVISIBLE
        apps.translationX = 0f
        apps.translationY = 0f
        drawer.visibility = drawerVisibility
        remapHandlers(ModernReflect.getObjectField(manager, "mStateHandlers"))
    }

    fun ownsFolder(folder: ViewGroup): Boolean {
        if (!active) return false
        var icon = ModernReflect.getObjectField(folder, "mFolderIcon") as? View
        while (icon != null && icon !== apps) icon = icon.parent as? View
        return icon === apps
    }

    fun folderAnimationNodes(folder: ViewGroup): List<View> {
        val nodes = mutableListOf<View>()
        fun collect(view: View) {
            if (bubbleClass.isInstance(view)) nodes.add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        collect(folder)
        val extension = ModernReflect.getObjectField(folder, "sOPlusFolderExtV2")
        val animation = extension?.let { ModernReflect.getObjectField(it, "mFolderAninatorManager") }
        if (animation != null) {
            nodes.addAll((ModernReflect.getObjectField(animation, "mAllAnimIconBubbleTextView") as? List<*>)
                .orEmpty().filterIsInstance<View>())
        }
        return nodes.distinct()
    }

    fun releaseClosedFolder(folder: ViewGroup, nodes: List<View>) {
        // Folder render nodes do not hide with their parent; detach them before exit.
        for (node in nodes) {
            ModernReflect.callMethod(node, "setMultiNodeEnable", false, false, false)
            ModernReflect.callMethod(node, "setOriginFancyDrawable", null)
        }
        (folder.parent as? ViewGroup)?.removeView(folder)
        val background = ModernReflect.getObjectField(apps, "mBackgroundAnimationManager")
        if (background != null) {
            ModernReflect.callMethod(background, "resetFolderAnimState")
            ModernReflect.callMethod(background, "cancelFolderCloseSearchRelatedAnimAndResetToClosedState")
            ModernReflect.setObjectField(background, "mBlur", 0f)
        }
        view("apps_view_translate")?.setRenderEffect(null)
        verticalParents.forEach { it.alpha = 1f }
        val categories = ModernReflect.callMethod(apps, "getCategoryRecyclerView") as? ViewGroup
        if (categories != null) for (index in 0 until categories.childCount) {
            categories.getChildAt(index).apply { alpha = 1f; scaleX = 1f; scaleY = 1f }
        }
        layoutDirty = true
    }

    fun finishClosingFolders(force: Boolean = false) {
        if (!active || finishingFolders) return
        val dragLayer = ModernReflect.callMethod(activity, "getDragLayer") as ViewGroup
        val folders = (0 until dragLayer.childCount).map { dragLayer.getChildAt(it) }
            .filterIsInstance<ViewGroup>().filter {
                it.javaClass.name == "com.android.launcher3.allapps.categoryfolder.CategoryFolder" && ownsFolder(it) &&
                    (force || ModernReflect.callMethod(it, "isOpen") != true)
            }
        finishingFolders = true
        try {
            modeScope.withDrawer {
                for (folder in folders) {
                    val nodes = folderAnimationNodes(folder)
                    ModernReflect.callMethod(folder, "resetFolderAnimator")
                    if (folder.parent != null) ModernReflect.callMethod(folder, "close", false)
                    releaseClosedFolder(folder, nodes)
                }
            }
        } finally { finishingFolders = false }
    }

    fun remapHandlers(handlers: Any?): Any? {
        if (handlers == null || !::controller.isInitialized) return handlers
        for (index in 0 until java.lang.reflect.Array.getLength(handlers)) {
            val handler = java.lang.reflect.Array.get(handlers, index)
            if (handler === drawerController || handler === controller) {
                java.lang.reflect.Array.set(handlers, index, if (active) controller else drawerController)
            }
        }
        return handlers
    }

    @Synchronized
    fun syncStore(source: Any) {
        if (!::store.isInitialized || disposed || synchronizing || (source !== store && source !== drawerStore)) return
        val target = if (source === store) drawerStore else store
        synchronizing = true
        try {
            // Use the original bound list when the normal drawer has no index.
            val apps = ModernReflect.callMethod(source, if (source === drawerStore) "getAllApps" else "getApps") as Array<*>
            val targetApps = ModernReflect.callMethod(target, "getAllApps") as Array<*>
            val count = apps.size
            val flags = ModernReflect.getObjectField(source, "mModelFlags") as Int
            val targetFlags = ModernReflect.getObjectField(target, "mModelFlags") as Int
            if (LauncherCategoryPageRules.sameAppSnapshot(apps, targetApps, flags, targetFlags)) return
            // Sync installed apps only, not suggestions, queries, scroll position, or filter state.
            if (target === store) modeScope.withDrawer { ModernReflect.callMethod(target, "setApps", apps, 0, count, flags) }
            else ModernReflect.callMethod(target, "setApps", apps, 0, count, flags)
        } finally {
            synchronizing = false
        }
    }

    fun ensureCategories() {
        if (!::store.isInitialized || disposed) return
        val list = requireNotNull(ModernReflect.callMethod(apps, "getAppsForCategory"))
        val items = ModernReflect.callMethod(list, "getCategoryAdapterItems") as List<*>
        val installed = ModernReflect.callMethod(store, "getApps") as Array<*>
        if (items.isEmpty() && installed.isNotEmpty()) {
            // Bind on first entry without waiting for delayed normal drawer notifications.
            ModernReflect.callMethod(list, "invalidateCategoryDataFingerprint")
            ModernReflect.setObjectField(list, "isNeedAnimation", false)
            modeScope.withDrawer { ModernReflect.callMethod(list, "onAppsUpdated") }
        }
        HookLog.d("LauncherCategoryPage", "Category content: apps=${installed.size}, groups=${items.size}")
    }

    fun applyLayout() {
        if (!::apps.isInitialized || disposed || layingOut) return
        layingOut = true
        try {
            applyFullLayout()
            val insets = apps.rootWindowInsets
            lastWidth = apps.width
            lastHeight = apps.height
            lastStatus = insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: -1
            lastNavigation = insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
            lastIme = insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
            layoutDirty = false
        } finally { layingOut = false }
    }

    fun prepareHorizontalEntry() {
        applyLayout()
        // Bring the palette in with search instead of waiting for the native vertical drawer to finish.
        modeScope.withDrawer { ModernReflect.callMethod(controller, "initColorFilter", 0f) }
    }

    private fun clampVerticalTransforms() {
        for (parent in verticalParents) {
            if (parent.translationY != 0f) parent.translationY = 0f
            if (parent.scaleX != 1f) parent.scaleX = 1f
            if (parent.scaleY != 1f) parent.scaleY = 1f
        }
        view("search_container_all_apps")?.let { if (it.translationY != 0f) it.translationY = 0f }
    }

    private fun applyFullLayout() {
        // Remove the normal drawer tab space; start the standalone page below the status bar.
        resetVerticalPadding(apps)
        apps.clipChildren = true
        for (parent in verticalParents) {
                resetVerticalPadding(parent)
                if (active) {
                    // Keep standalone icon targets independent of native vertical drawer offsets and scaling.
                    parent.translationY = 0f
                    parent.scaleX = 1f
                    parent.scaleY = 1f
                }
                val params = parent.layoutParams as? ViewGroup.MarginLayoutParams
                if (params != null && (params.topMargin != 0 || params.bottomMargin != 0)) {
                    params.topMargin = 0
                    params.bottomMargin = 0
                    parent.layoutParams = params
                }
        }
        view("all_apps_bg_layer")?.let { if (it.background != null) it.background = null }
        for (name in listOf("drawer_tab_view", "category_tab_header", "category_tab", "all_apps_menu", "all_apps_header")) {
            view(name)?.let { if (it.visibility != View.GONE) it.visibility = View.GONE }
        }
        val search = view("search_container_all_apps") ?: return
        val params = search.layoutParams as? RelativeLayout.LayoutParams ?: return
        val rowWidth = LauncherCategoryPageRules.searchRowWidth(apps.width, dimension("category_folder_padding_start"))
        if (rowWidth > 0 && params.width != rowWidth) {
            params.width = rowWidth
            params.removeRule(RelativeLayout.ALIGN_PARENT_START)
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
            params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT)
            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            params.addRule(RelativeLayout.CENTER_HORIZONTAL)
            params.leftMargin = 0
            params.rightMargin = 0
            params.marginStart = 0
            params.marginEnd = 0
            search.layoutParams = params
            ModernReflect.callMethod(search, "resetColorFilter", false)
        }
        val insets = apps.rootWindowInsets
        val status = insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: (ModernReflect.getObjectField(apps, "mInsets") as Rect).top
        val navigation = insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        val ime = insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
        val rootTop = windowTop(apps)
        val rowHeight = maxOf(
            dimension("all_apps_search_content_height"),
            view("drawer_search_container_bg")?.measuredHeight ?: 0,
            view("color_filter_container_view")?.measuredHeight ?: 0,
        )
        val layout = LauncherCategoryPageRules.layout(status, rootTop, rowHeight, navigation, ime, apps.resources.displayMetrics.density)
        contentTop = layout.contentTop
        statusTop = (status - rootTop).coerceAtLeast(0)
        topBlur?.let { blur ->
            val blurParams = blur.layoutParams as RelativeLayout.LayoutParams
            if (blurParams.height != contentTop) {
                blurParams.height = contentTop
                blur.layoutParams = blurParams
            }
            blur.updateFade(statusTop)
        }
        val rowTop = (layout.searchTop - (windowTop(search.parent as View) - rootTop)).coerceAtLeast(0)
        if (params.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM) != 0 ||
            params.getRule(RelativeLayout.ALIGN_PARENT_TOP) == 0 || params.topMargin != rowTop || params.bottomMargin != 0
        ) {
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            params.topMargin = rowTop
            params.bottomMargin = 0
            search.layoutParams = params
        }
        if (search.paddingBottom != 0) search.setPadding(search.paddingLeft, search.paddingTop, search.paddingRight, 0)
        if (search.translationY != 0f) search.translationY = 0f
        for (name in listOf("all_apps_tabs_view_pager", "all_apps_search", "all_apps_color_filter_paged_view")) {
            val container = view(name) ?: continue
            if (container is ViewGroup) {
                resetVerticalPadding(container)
                container.clipChildren = true
                container.clipToPadding = true
            }
            val containerParams = container.layoutParams as? RelativeLayout.LayoutParams ?: continue
            // Use a full-screen viewport so content can scroll behind search without a hard clip below it.
            val top = 0
            // Draw into the gesture area and use list padding for the bottom safe inset.
            val bottom = LauncherCategoryPageRules.listImeInset(name == "all_apps_search", ime)
            if (containerParams.topMargin != top || containerParams.bottomMargin != bottom ||
                containerParams.getRule(RelativeLayout.BELOW) != 0
            ) {
                containerParams.removeRule(RelativeLayout.BELOW)
                containerParams.topMargin = top
                containerParams.bottomMargin = bottom
                container.layoutParams = containerParams
            }
        }
        // Remove list padding reserved for the old tabs and bottom search bar.
        val recyclers = listOfNotNull(
            ModernReflect.callMethod(apps, "getCategoryRecyclerView"),
            ModernReflect.callMethod(apps, "getMainRecyclerView"),
            view("apps_list_view_search"), view("apps_list_view_search_animate"),
        )
        for (recycler in recyclers) {
            val view = recycler as? ViewGroup ?: continue
            val isSearch = view.id == resourceId("apps_list_view_search") || view.id == resourceId("apps_list_view_search_animate")
            val bottomPadding = if (isSearch) layout.bottomInset - ime.coerceAtLeast(0)
                else navigation.coerceAtLeast(0) + (8 * apps.resources.displayMetrics.density).toInt()
            val topPadding = LauncherCategoryPageRules.contentPadding(layout.contentTop, layoutTop(view))
            if (view.paddingTop != topPadding || view.paddingBottom != bottomPadding) {
                view.setPadding(view.paddingLeft, topPadding, view.paddingRight, bottomPadding)
            }
            view.clipChildren = true
            view.clipToPadding = false
        }
        val colors = view("all_apps_color_filter_paged_view") as? ViewGroup ?: return
        for (index in 0 until colors.childCount) {
            val page = colors.getChildAt(index) as? ViewGroup ?: continue
            val recycler = page.findViewById<ViewGroup>(resourceId("apps_list_view_color_filter")) ?: continue
            val title = page.findViewById<View>(resourceId("title_color_filter")) ?: continue
            val titleParams = title.layoutParams as? FrameLayout.LayoutParams ?: continue
            val titleHeight = titleParams.height.takeIf { it > 0 } ?: title.measuredHeight
            val pageTop = layoutTop(page)
            val colorLayout = LauncherCategoryPageRules.colorLayout(layout.contentTop, titleHeight, apps.resources.displayMetrics.density)
            val titleTop = LauncherCategoryPageRules.contentPadding(colorLayout.titleTop, pageTop)
            if (titleParams.gravity != (Gravity.TOP or Gravity.START) || titleParams.topMargin != titleTop || titleParams.bottomMargin != 0) {
                titleParams.gravity = Gravity.TOP or Gravity.START
                titleParams.topMargin = titleTop
                titleParams.bottomMargin = 0
                title.layoutParams = titleParams
            }
            val listParams = recycler.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (listParams.height != ViewGroup.LayoutParams.MATCH_PARENT || listParams.gravity != Gravity.TOP ||
                listParams.topMargin != 0 || listParams.bottomMargin != 0
            ) {
                listParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                listParams.gravity = Gravity.TOP
                listParams.topMargin = 0
                listParams.bottomMargin = 0
                recycler.layoutParams = listParams
            }
            val topPadding = LauncherCategoryPageRules.contentPadding(colorLayout.listTop, pageTop)
            val bottomPadding = navigation.coerceAtLeast(0) + (8 * apps.resources.displayMetrics.density).toInt()
            if (recycler.paddingTop != topPadding || recycler.paddingBottom != bottomPadding) {
                recycler.setPadding(recycler.paddingLeft, topPadding, recycler.paddingRight, bottomPadding)
            }
            recycler.clipToPadding = false
            recycler.clipChildren = true
            // Keep the native color-switch spring and align its pivot with the top palette.
            val pivot = layout.searchTop + rowHeight / 2f - pageTop
            ModernReflect.setObjectField(colors, "serachContainerCenterHeight", page.height - pivot)
            page.pivotY = pivot
        }
    }

    fun syncSearchBackground(searchBar: View, bounds: Rect) {
        if (!::apps.isInitialized || disposed || bounds.isEmpty || searchBackgroundBounds == bounds) return
        val background = view("drawer_search_container_bg") ?: return
        // COUI width changes do not update background bounds; update the native blur clipping too.
        searchBackgroundBounds.set(bounds)
        (ModernReflect.getObjectField(searchBar, "mBackgroundRect") as Rect).set(bounds)
        ModernReflect.callMethod(apps, "setSearchbgBounds", bounds.left, bounds.top, bounds.right, bounds.bottom, background)
        background.invalidateOutline()
    }

    fun prepareIconReturn(folder: ViewGroup? = null) {
        applyLayout()
        if (!active) return
        // Finish layout before return animations query targets; retain expanded folders' own viewports.
        for (root in listOfNotNull(apps, folder)) {
            if (!root.isAttachedToWindow || !root.isLayoutRequested || root.width <= 0 || root.height <= 0) continue
            root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
            root.layout(root.left, root.top, root.right, root.bottom)
        }
    }

    fun fadeBounds(view: View): LauncherCategoryPageRules.Fade =
        LauncherCategoryPageRules.topFade(statusTop, contentTop, layoutTop(view), view.height)

    fun ownsStore(value: Any?): Boolean = ::store.isInitialized && store === value

    private fun categoryOrder(): List<String> = savedOrder ?: runCatching {
        val values = JSONArray(orderPrefs.getString("folder_order", "[]"))
        List(values.length()) { values.getString(it) }
    }.getOrDefault(emptyList()).also { savedOrder = it }

    fun saveCategoryOrder(items: List<*>) {
        savedOrder = items.filterNotNull().filter { ModernReflect.getObjectField(it, "viewType") == 1 }
            .map { ModernReflect.getObjectField(it, "enumType") as String }
        orderChanged = true
    }

    fun commitCategoryOrder() {
        if (!orderChanged) return
        orderPrefs.edit().putString("folder_order", JSONArray(categoryOrder()).toString()).apply()
        orderChanged = false
    }

    @Suppress("UNCHECKED_CAST")
    fun restoreCategoryOrder(value: Any?) {
        val order = categoryOrder()
        if (order.isEmpty()) return
        val items = value as? MutableList<Any> ?: return
        val slots = items.indices.filter { ModernReflect.getObjectField(items[it], "viewType") == 1 }
        val folders = slots.map { items[it] }
        val aliases = folders.map { ModernReflect.getObjectField(it, "enumType") as String }
        val sorted = LauncherCategoryPageRules.categoryOrder(aliases, order)
        val byAlias = aliases.zip(folders).toMap()
        if (sorted.size != slots.size) return
        for (index in slots.indices) {
            val folder = byAlias.getValue(sorted[index])
            ModernReflect.setObjectField(folder, "categoryFolderOrder", index + 1)
            items[slots[index]] = folder
        }
    }

    private fun resetVerticalPadding(view: View) {
        if (view.paddingTop != 0 || view.paddingBottom != 0) view.setPadding(view.paddingLeft, 0, view.paddingRight, 0)
    }

    private fun windowTop(view: View): Int {
        view.getLocationInWindow(location)
        return location[1]
    }

    private fun layoutTop(view: View): Int {
        var current = view
        var top = 0
        while (current !== apps) {
            top += current.top
            current = current.parent as? View ?: break
        }
        return top
    }

    fun dispose() {
        if (disposed) return
        commitCategoryOrder()
        deactivate()
        disposed = true
        if (::apps.isInitialized) {
            if (apps.viewTreeObserver.isAlive) {
                apps.viewTreeObserver.removeOnPreDrawListener(layoutListener)
                apps.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
            // Use this page during both attachment and removal to preserve the normal drawer search listener.
            ModernReflect.setObjectField(activity, "mAppsView", apps)
            ModernReflect.setObjectField(activity, "mAllAppsController", if (::controller.isInitialized) controller else drawerController)
            try { (apps.parent as? ViewGroup)?.removeView(apps) }
            finally {
                ModernReflect.setObjectField(activity, "mAppsView", drawer)
                ModernReflect.setObjectField(activity, "mAllAppsController", drawerController)
            }
            runCatching { ModernReflect.callMethod(activity, "removeOnDeviceProfileChangeListener", apps) }
        }
        panelListeners.forEach { listeners().remove(it) }
        if (::controller.isInitialized) runCatching { ModernReflect.callMethod(activity, "removeOnDeviceProfileChangeListener", controller) }
        if (::store.isInitialized) unregisterStore(store)
    }

    private fun view(name: String): View? {
        val id = resourceId(name)
        return if (id == 0) null else apps.findViewById(id)
    }

    private fun resourceId(name: String): Int =
        resourceIds.getOrPut(name) { apps.resources.getIdentifier(name, "id", "com.android.launcher") }

    private fun dimension(name: String): Int = dimensions.getOrPut(name) {
        val id = apps.resources.getIdentifier(name, "dimen", "com.android.launcher")
        if (id == 0) 0 else apps.resources.getDimensionPixelSize(id)
    }

    @Suppress("UNCHECKED_CAST")
    private fun listeners(): MutableList<Any> = ModernReflect.getObjectField(manager, "mListeners") as MutableList<Any>

    private fun ownsView(listener: Any, root: View): Boolean = listener.javaClass.declaredFields.any { field ->
        if (Modifier.isStatic(field.modifiers) || !View::class.java.isAssignableFrom(field.type)) false
        else runCatching {
            field.isAccessible = true
            var view = field.get(listener) as? View
            while (view != null && view !== root) view = view.parent as? View
            view === root
        }.getOrDefault(false)
    }
}
