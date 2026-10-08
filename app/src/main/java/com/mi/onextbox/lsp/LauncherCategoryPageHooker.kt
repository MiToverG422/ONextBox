package com.mi.onextbox.lsp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Rect
import android.graphics.Shader
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import com.mi.onextbox.lsp.LauncherCategoryPageRules.Direction
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.math.abs

// Native app category entry beyond the rightmost home page.
internal object LauncherCategoryPageHooker {
    private const val TAG = "LauncherCategoryPage"

    fun hook(loader: ClassLoader) {
        runCatching {
            val api = NativeApi(loader)
            val sessions = ConcurrentHashMap<Activity, Session>()
            fun isPanelView(view: Any?): Boolean = view is View && sessions.values.any { it.isPanelView(view) }
            fun isActivePanelView(view: Any?): Boolean = view is View && sessions.values.any { it.hasActivePanel && it.isPanelView(view) }
            val unavailable = WeakHashMap<Activity, Boolean>()
            val failedGestures = WeakHashMap<Activity, Boolean>()
            val reordering = ThreadLocal<Session>()
            fun install(method: Method, callback: (XposedInterface.Chain) -> Any?) {
                ModernHookRegistry.installFast("$TAG:${method.toGenericString()}", method, XposedInterface.Hooker(callback))
            }
            val standardLoader = ModernReflect.findClass("com.android.launcher3.model.OplusStandardLoaderResults", loader)
            val baseLoader = ModernReflect.findClass("com.android.launcher3.model.OplusBaseLoaderResults", loader)
            install(baseLoader.getDeclaredMethod("shouldBindAllAppsForTaskbar")) { chain ->
                // Standard mode binds taskbar data by default; categories need the full app list.
                LauncherCategoryPageRules.shouldBindApps(
                    LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.RightmostCategories),
                    chain.thisObject?.javaClass === standardLoader, chain.proceed() as Boolean,
                )
            }
            install(standardLoader.getDeclaredMethod("preBindAllApps")) { chain ->
                val result = chain.proceed()
                if (chain.thisObject?.javaClass === standardLoader &&
                    LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.RightmostCategories)
                ) {
                    runCatching { api.prepareStandardCategories(chain.thisObject!!) }
                        .onFailure { HookLog.w(TAG, "Failed to prepare standard-mode category data", it) }
                }
                result
            }
            install(api.launcher.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java)) { chain ->
                val activity = chain.thisObject as? Activity ?: return@install chain.proceed()
                val event = chain.getArg(0) as? MotionEvent ?: return@install chain.proceed()
                if (!LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.RightmostCategories)) {
                    runCatching { sessions.remove(activity)?.dispose() }
                        .onFailure { HookLog.w(TAG, "Failed to close category entry", it) }
                    return@install chain.proceed()
                }
                if (failedGestures[activity] == true) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) failedGestures.remove(activity)
                    else {
                        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                            failedGestures.remove(activity)
                        }
                        return@install true
                    }
                }
                if (unavailable[activity] == true) return@install chain.proceed()
                var session = sessions[activity]
                val handled = try {
                    if (session == null) {
                        session = Session(activity, api)
                        sessions[activity] = session
                    }
                    session.handle(event) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        try { chain.proceed(arrayOf(cancel)) } finally { cancel.recycle() }
                    }
                } catch (error: Throwable) {
                    val claimed = session?.ownsGesture == true
                    runCatching { session?.abort() }
                        .onFailure { HookLog.w(TAG, "Failed to restore native state", it) }
                    runCatching { session?.dispose() }
                    sessions.remove(activity)
                    unavailable[activity] = true
                    if (claimed) failedGestures[activity] = true
                    HookLog.w(TAG, "Category entry unavailable, keeping native gestures", error)
                    claimed
                }
                if (handled) true else chain.proceed()
            }
            install(api.launcher.getDeclaredMethod("onBackPressed")) { chain ->
                val session = sessions[chain.thisObject]
                val handled = runCatching { session?.handleBack() == true }.getOrElse { error ->
                    runCatching { session?.abort() }
                    HookLog.w(TAG, "Category back gesture unavailable", error)
                    false
                }
                if (handled) null else chain.proceed()
            }
            for (name in listOf("onPause", "onDestroy", "onConfigurationChanged")) {
                val method = if (name == "onConfigurationChanged") {
                    api.launcher.getDeclaredMethod(name, Configuration::class.java)
                } else api.launcher.getDeclaredMethod(name)
                install(method) { chain ->
                    val activity = chain.thisObject as? Activity
                    if (activity != null) {
                        runCatching {
                            sessions[activity]?.abort(restoreState = name != "onDestroy", closeLibrary = name == "onConfigurationChanged")
                            if (name != "onPause") sessions[activity]?.dispose(restoreState = name != "onDestroy")
                        }
                            .onFailure { HookLog.w(TAG, "Failed to restore category transition", it) }
                        if (name != "onPause") {
                            sessions.remove(activity)
                            unavailable.remove(activity)
                            failedGestures.remove(activity)
                        }
                    }
                    chain.proceed()
                }
            }
            install(api.stateManager.getDeclaredMethod("onStateTransitionStart", api.baseState)) { chain ->
                val session = sessions.values.firstOrNull { it.manager === chain.thisObject }
                session?.let {
                    runCatching { session.onNativeStateChange(chain.getArg(0)) }
                        .onFailure { HookLog.w(TAG, "Failed to release category gesture", it) }
                }
                if (session?.isPanelDestination(chain.getArg(0)) == true) api.panelModeScope.withDrawer { chain.proceed() }
                else chain.proceed()
            }
            install(api.stateManager.getDeclaredMethod("onStateTransitionEnd", api.baseState)) { chain ->
                val session = sessions.values.firstOrNull { it.manager === chain.thisObject }
                val result = if (session?.isPanelDestination(chain.getArg(0)) == true)
                    api.panelModeScope.withDrawer { chain.proceed() } else chain.proceed()
                session?.let {
                    runCatching { session.onNativeStateComplete(chain.getArg(0)) }
                        .onFailure { HookLog.w(TAG, "Failed to restore drawer entry", it) }
                }
                result
            }
            install(api.stateManager.getDeclaredMethod("getStateHandlers")) { chain ->
                val handlers = chain.proceed()
                sessions.values.firstOrNull { it.manager === chain.thisObject }?.remapHandlers(handlers) ?: handlers
            }
            install(api.stateManager.getDeclaredMethod(
                "goToState", api.baseState, Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                Animator.AnimatorListener::class.java,
            )) { chain ->
                val session = sessions.values.firstOrNull { it.manager === chain.thisObject }
                if (session?.shouldReturnToPanel(chain.getArg(0)) == true) {
                    val args = chain.args.toTypedArray()
                    args[0] = api.state("ALL_APPS")
                    HookLog.d(TAG, "Category app return target: ALL_APPS")
                    return@install api.panelModeScope.withDrawer { chain.proceed(args) }
                }
                val horizontalExit = chain.getArg(1) == true && runCatching { session?.handleHomeDestination(
                        chain.getArg(0), chain.getArg(3) as? Animator.AnimatorListener,
                    ) == true }.getOrElse {
                        runCatching { session?.abort() }
                        HookLog.w(TAG, "Category home transition unavailable", it)
                        false
                    }
                if (horizontalExit) null
                else if (session?.isPanelDestination(chain.getArg(0)) == true) api.panelModeScope.withDrawer { chain.proceed() }
                else chain.proceed()
            }
            install(api.stateManager.getDeclaredMethod("getRestState")) { chain ->
                val session = sessions.values.firstOrNull { it.manager === chain.thisObject }
                if (session?.retainsAppReturn == true) api.state("ALL_APPS") else chain.proceed()
            }
            for (method in api.stateManager.declaredMethods.filter { it.name in setOf(
                "createAnimationToNewWorkspace", "createAnimationToNewWorkspaceInternal", "goToStateAnimated",
                "goToToggleStateWithSpringStart",
            ) }) {
                install(method) { chain ->
                    val session = sessions.values.firstOrNull { it.manager === chain.thisObject }
                    if (session?.shouldReturnToPanel(chain.getArg(0)) != true) {
                        return@install if (session?.isPanelDestination(chain.getArg(0)) == true)
                            api.panelModeScope.withDrawer { chain.proceed() } else chain.proceed()
                    }
                    val args = chain.args.toTypedArray()
                    args[0] = api.state("ALL_APPS")
                    HookLog.d(TAG, "Category app return animation: ${method.name}")
                    api.panelModeScope.withDrawer { chain.proceed(args) }
                }
            }
            // Select the return target before the gesture to avoid briefly showing the home page.
            runCatching {
                val activityInterface = ModernReflect.findClass("com.android.quickstep.r3", loader)
                val homeTarget = activityInterface.declaredMethods.single { it.name == "J" && !it.isBridge }
                install(homeTarget) { chain ->
                    if ((chain.getArg(0) as? Enum<*>)?.name != "HOME") return@install chain.proceed()
                    val activity = ModernReflect.callStaticMethod(activityInterface, "S")
                    if (sessions[activity]?.retainsAppReturn == true) api.state("ALL_APPS") else chain.proceed()
                }
            }.onFailure { HookLog.w(TAG, "Category gesture return target unavailable", it) }
            val injector = ModernReflect.findClass("com.android.launcher3.statemanager.StateManagerInjector", loader)
            install(injector.getDeclaredMethod("injectReapplyState")) { chain ->
                val activity = ModernReflect.getObjectField(chain.thisObject!!, "mActivity")
                if (sessions[activity]?.shouldRetainPanelState() != true) chain.proceed()
                // Keep the standalone page when native checks reset standard-mode ALL_APPS to NORMAL.
                else api.panelModeScope.withDrawer { chain.proceed() }
            }
            val drawerGuard = ModernReflect.findClass("com.android.launcher3.allapps.OplusDrawerCanBeBlockedUtilsKt", loader)
            install(drawerGuard.declaredMethods.single { it.name == "isDrawerOpenGestureAtCollapsedProgress" }) { chain ->
                // Horizontal pages do not use the native vertical drawer expansion progress.
                if (sessions[chain.getArg(0)]?.hasActivePanel == true) false else chain.proceed()
            }
            for (name in listOf(
                "com.android.launcher3.uioverrides.touchcontrollers.OplusPortraitStatesTouchController",
                "com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController",
                "com.android.launcher3.touch.AllAppsSwipeController",
            )) {
                val touchController = ModernReflect.findClass(name, loader)
                install(touchController.getDeclaredMethod("canInterceptTouch\$2", MotionEvent::class.java)) { chain ->
                    val activity = ModernReflect.getObjectField(chain.thisObject!!, "mLauncher")
                    // Disable only the standalone drawer toggle; retain native RecyclerView scrolling.
                    if (sessions[activity]?.hasActivePanel == true) false else chain.proceed()
                }
            }
            for (name in listOf(
                "com.android.launcher3.uioverrides.touchcontrollers.NavBarToHomeTouchController",
                "com.android.launcher3.uioverrides.touchcontrollers.OplusPortraitStatesTouchController",
            )) {
                val touchController = ModernReflect.findClass(name, loader)
                install(touchController.getDeclaredMethod("onControllerInterceptTouchEvent", MotionEvent::class.java)) { chain ->
                    val activity = ModernReflect.getObjectField(chain.thisObject!!, "mLauncher")
                    if (sessions[activity]?.hasActivePanel == true) false else chain.proceed()
                }
            }
            val appsClass = ModernReflect.findClass("com.android.launcher3.allapps.OplusLauncherAllAppsContainerView", loader)
            appsClass.declaredMethods.first { it.name == "getAppsList" }.let { method ->
                install(method) { chain ->
                    if (api.creatingPanel.get() == true) chain.getArg(1)?.let { api.panelStores[it] = true }
                    chain.proceed()
                }
            }
            val appsList = ModernReflect.findClass("com.android.launcher3.allapps.LauncherAlphabeticalAppsList", loader)
            for (name in listOf("refillCategoryAdapterItemsPredictedApps", "refillCategoryAdapterItemsRecentAddApps")) {
                install(appsList.getDeclaredMethod(name)) { chain ->
                    if (api.isPanelStore(ModernReflect.getObjectField(chain.thisObject!!, "mAllAppsStore"))) null else chain.proceed()
                }
            }
            val baseList = ModernReflect.findClass("com.android.launcher3.allapps.OplusAlphabeticalAppsList", loader)
            install(baseList.declaredMethods.first { it.name == "getTargetPredictedApps" }) { chain ->
                if (api.isPanelStore(chain.getArg(0))) emptyList<Any>() else chain.proceed()
            }
            val utils = ModernReflect.findClass("com.android.launcher3.allapps.AllAppsUtils", loader)
            install(utils.declaredMethods.single { it.name == "isNeedRefillPredictionsApps" }) { chain ->
                val list = chain.getArg(2)
                if (list != null && api.isPanelStore(ModernReflect.getObjectField(list, "mAllAppsStore"))) false
                else chain.proceed()
            }
            val categoryDrag = ModernReflect.findClass("com.android.launcher3.allapps.OplusCategoryItemTouchHelperCallback", loader)
            install(categoryDrag.declaredMethods.single { it.name == "onSelectedChanged" }) { chain ->
                val recycler = ModernReflect.getObjectField(chain.thisObject!!, "mRecyclerView") as? View
                val session = sessions.values.firstOrNull { recycler != null && it.isPanelView(recycler) }
                if (session == null) return@install chain.proceed()
                session.onCategoryDragChanged(chain.getArg(1) == 2)
                api.panelModeScope.withDrawer { chain.proceed() }
            }
            install(categoryDrag.declaredMethods.single { it.name == "onViewReleased" }) { chain ->
                val recycler = ModernReflect.getObjectField(chain.thisObject!!, "mRecyclerView") as? View
                val session = sessions.values.firstOrNull { recycler != null && it.isPanelView(recycler) }
                val result = chain.proceed()
                session?.commitCategoryOrder()
                result
            }
            val baseAdapter = ModernReflect.findClass("com.android.launcher3.allapps.BaseAllAppsAdapter", loader)
            install(baseAdapter.getDeclaredMethod("swapPosition", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { chain ->
                val list = ModernReflect.getObjectField(chain.thisObject!!, "mApps")!!
                val store = ModernReflect.getObjectField(list, "mAllAppsStore")
                val session = sessions.values.firstOrNull { it.isPanelStore(store) } ?: return@install chain.proceed()
                val previous = reordering.get()
                reordering.set(session)
                try { api.panelModeScope.withDrawer { chain.proceed() } }
                finally { if (previous == null) reordering.remove() else reordering.set(previous) }
            }
            val categoryType = ModernReflect.findClass("com.android.launcher3.allapps.appcategory.AppCategoryType", loader)
            install(categoryType.declaredMethods.single { it.name == "saveItemsOrder" }) { chain ->
                val session = reordering.get() ?: return@install chain.proceed()
                // Save standalone ordering without changing the normal drawer category order.
                session.saveCategoryOrder(chain.getArg(0) as List<*>)
                null
            }
            install(baseList.declaredMethods.single { it.name == "notifyCategoryPageUpdate" }) { chain ->
                val store = ModernReflect.getObjectField(chain.thisObject!!, "mAllAppsStore")
                val session = sessions.values.firstOrNull { it.isPanelStore(store) } ?: return@install chain.proceed()
                session.restoreCategoryOrder(chain.getArg(1))
                val result = chain.proceed()
                ModernReflect.callMethod(chain.thisObject, "syncCategoryPageFingerprintAfterFolderReorder")
                result
            }
            val storeClass = ModernReflect.findClass("com.android.launcher3.allapps.AllAppsStore", loader)
            install(storeClass.getDeclaredMethod("notifyUpdate")) { chain ->
                val result = chain.proceed()
                sessions.values.toList().forEach { session ->
                    runCatching { session.syncStore(chain.thisObject!!) }
                        .onFailure { HookLog.w(TAG, "Failed to refresh category app list", it) }
                }
                result
            }
            val nativeStore = ModernReflect.findClass("com.android.launcher3.allapps.OplusAllAppsStore", loader)
            install(nativeStore.declaredMethods.single { it.name == "addOrUpdateApps" }) { chain ->
                // Standard mode has no drawer index; the standalone page still needs full app binding.
                if (api.isPanelStore(chain.thisObject)) api.panelModeScope.withDrawer { chain.proceed() }
                else chain.proceed()
            }
            install(nativeStore.declaredMethods.single { it.name == "setApps" && it.parameterCount == 4 }) { chain ->
                val result = chain.proceed()
                sessions.values.toList().forEach { session ->
                    runCatching { session.syncStore(chain.thisObject!!) }
                        .onFailure { HookLog.w(TAG, "Failed to bind category app list", it) }
                }
                result
            }
            val adapter = ModernReflect.findClass("com.android.launcher3.allapps.LauncherAllAppsGridAdapter", loader)
            install(adapter.getDeclaredMethod("getItemCount")) { chain ->
                val list = ModernReflect.getObjectField(chain.thisObject!!, "mApps")!!
                if (!api.isPanelStore(ModernReflect.getObjectField(list, "mAllAppsStore"))) chain.proceed()
                // Keep each adapter's own list and match search animation counts to bound items.
                else api.panelModeScope.withDrawer { chain.proceed() }
            }
            for (name in listOf(
                "com.android.launcher3.allapps.OplusAllAppsGridAdapter",
                "com.android.launcher3.allapps.search.OplusAllAppsSearchAdapter",
            )) {
                val headerAdapter = ModernReflect.findClass(name, loader)
                install(headerAdapter.getDeclaredMethod("getEmptyHeaderHeight")) { chain ->
                    val list = ModernReflect.getObjectField(chain.thisObject!!, "mApps")!!
                    if (api.isPanelStore(ModernReflect.getObjectField(list, "mAllAppsStore"))) 0 else chain.proceed()
                }
            }
            val taskbar = ModernReflect.findClass("com.android.launcher3.taskbar.TaskbarUtils", loader)
            install(taskbar.declaredMethods.single { it.name == "getScreenWidth" }) { chain ->
                api.searchRowWidth.get()?.takeIf { it > 0 } ?: chain.proceed()
            }
            val search = ModernReflect.findClass("com.android.launcher3.allapps.search.LauncherAppsSearchContainerLayout", loader)
            install(api.mode.getDeclaredMethod("isInDrawerMode")) { chain ->
                if (api.panelModeScope.active) true else chain.proceed()
            }
            install(api.mode.getDeclaredMethod("isStandardMode")) { chain ->
                if (api.panelModeScope.active) false else chain.proceed()
            }
            for (name in listOf("setupColorFilter", "resetColorFilter", "inflateSearchViewAnimate\$1", "onLayout", "resetSearchBoxToOriginalWidthInNonColorMode")) {
                val method = search.declaredMethods.first { it.name == name }
                // Native width queries may be inlined; retain the standalone row width.
                if (name == "resetColorFilter" || name == "resetSearchBoxToOriginalWidthInNonColorMode") {
                    if (!ModernHookRuntime.requireModule().deoptimize(method)) HookLog.w(TAG, "Search width caller deoptimization unavailable")
                }
                install(method) { chain ->
                    if (api.creatingPanel.get() != true && !isPanelView(chain.thisObject)) return@install chain.proceed()
                    // Enable the native palette only during standalone search setup, without changing desktop mode.
                    api.withSearchRow(chain.thisObject as View) { api.panelModeScope.withDrawer { chain.proceed() } }
                }
            }
            install(appsClass.declaredMethods.single { it.name == "onStartingActivity" }) { chain ->
                // Native closing animations use these fields to find the original category icon.
                if (!isActivePanelView(chain.thisObject)) return@install chain.proceed()
                val session = sessions.values.firstOrNull { it.hasActivePanel && it.isPanelView(chain.thisObject as View) }
                runCatching { session?.onStartingPanelActivity(chain.getArg(0) as? View) }
                    .onFailure { HookLog.w(TAG, "Category app return destination unavailable", it) }
                val result = api.panelModeScope.withDrawer { chain.proceed() }
                session?.recordIconGeometry("launch", chain.getArg(0) as? View)
                result
            }
            val categoryFolder = ModernReflect.findClass("com.android.launcher3.allapps.categoryfolder.CategoryFolder", loader)
            install(categoryFolder.getDeclaredMethod("closeQuitAllApp", Boolean::class.javaPrimitiveType)) { chain ->
                val folder = chain.thisObject as ViewGroup
                val session = sessions.values.firstOrNull { it.isPanelFolder(folder) }
                // Retain the standalone folder on app return instead of closing the entire drawer.
                if (session?.shouldKeepExpandedFolder(folder) == true) {
                    HookLog.d(TAG, "Category folder retained for app return")
                    null
                } else chain.proceed()
            }
            install(categoryFolder.getDeclaredMethod("closeComplete", Boolean::class.javaPrimitiveType)) { chain ->
                val folder = chain.thisObject as ViewGroup
                val session = sessions.values.firstOrNull { it.isPanelFolder(folder) }
                    ?: return@install chain.proceed()
                val nodes = runCatching { session.folderAnimationNodes(folder) }.getOrElse { emptyList() }
                val result = api.panelModeScope.withDrawer { chain.proceed() }
                runCatching { session.releaseClosedFolder(folder, nodes) }
                    .onFailure { HookLog.w(TAG, "Category folder close cleanup unavailable", it) }
                result
            }
            val baseLauncher = ModernReflect.findClass("com.android.launcher3.Launcher", loader)
            // Home callbacks may inline folder closing; retain the standalone return-state check.
            for (caller in listOf(baseLauncher, api.launcher).flatMap { type ->
                type.declaredMethods.filter { it.name == "onNewIntent" || it.name == "closeOpenFolder" }
            }) {
                if (!ModernHookRuntime.requireModule().deoptimize(caller)) HookLog.w(TAG, "Category folder close caller deoptimization unavailable")
            }
            install(baseLauncher.getDeclaredMethod("isBackToAllApps")) { chain ->
                if (sessions[chain.thisObject]?.retainsAppReturn == true) true else chain.proceed()
            }
            val nativeSearchBar = ModernReflect.findClass("com.coui.appcompat.searchview.COUISearchBar", loader)
            val backgroundBounds = nativeSearchBar.getDeclaredField("mBackgroundRect").apply { isAccessible = true }
            install(nativeSearchBar.getDeclaredMethod("onLayout", Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )) { chain ->
                val result = chain.proceed()
                val bar = chain.thisObject as View
                sessions.values.firstOrNull { it.isPanelView(bar) }?.syncSearchBackground(bar, backgroundBounds.get(bar) as Rect)
                result
            }
            val appCloseTarget = api.launcher.declaredMethods.single { it.name == "getFirstMatchForAppClose" }
            if (!ModernHookRuntime.requireModule().deoptimize(appCloseTarget)) HookLog.w(TAG, "Category app close caller deoptimization unavailable")
            install(appCloseTarget) { chain ->
                val session = sessions[chain.thisObject]
                if (session?.hasActivePanel != true) return@install chain.proceed()
                session.prepareIconReturn()
                val result = api.panelModeScope.withDrawer { chain.proceed() }
                session.recordIconGeometry("return", result as? View)
                result
            }
            val nativeController = ModernReflect.findClass("com.android.launcher3.allapps.OplusAllAppsTransitionController", loader)
            for (name in listOf("uninterested", "setState", "setStateWithAnimation")) {
                for (method in nativeController.declaredMethods.filter { it.name == name && !it.isBridge }) {
                    install(method) { chain ->
                        val session = sessions.values.firstOrNull { it.isPanelController(chain.thisObject) }
                        // Skip vertical drawer motion for horizontal transitions; let native code commit the final state.
                        if (name == "setStateWithAnimation" && session?.ownsGesture == true) null
                        else if (session != null) api.panelModeScope.withDrawer { chain.proceed() }
                        else chain.proceed()
                    }
                }
            }
            install(search.getDeclaredMethod("onSearchBarClick")) { chain ->
                if (isActivePanelView(chain.thisObject)) api.panelModeScope.withDrawer { chain.proceed() }
                else chain.proceed()
            }
            val searchBase = ModernReflect.findClass("com.android.launcher3.allapps.search.LauncherTaskbarAppsSearchContainerLayout", loader)
            install(searchBase.declaredMethods.single { it.name == "onSearchResult" }) { chain ->
                if (!isActivePanelView(chain.thisObject)) return@install chain.proceed()
                val result = api.panelModeScope.withDrawer { chain.proceed() }
                val matched = (chain.getArg(0) as? List<*>)?.size ?: 0
                HookLog.d(TAG, "Category search results: matches=$matched")
                result
            }
            val searchLists = ModernReflect.findClass("com.android.launcher3.allapps.search.SearchListUtils", loader)
            install(searchLists.declaredMethods.single { it.name == "getSearchListTargetTranslateY" }) { chain ->
                // Align standalone results at the top without the normal drawer's bottom offset.
                if (isPanelView(chain.getArg(1))) 0f else chain.proceed()
            }
            val imeCallback = ModernReflect.findClass("com.android.launcher3.allapps.search.TranslateInsetsAnimationCallback", loader)
            fun isPanelIme(callback: Any?): Boolean = callback != null &&
                isActivePanelView(ModernReflect.getObjectField(callback, "view"))
            for (name in listOf("onPrepare", "onStart", "onProgress", "onEnd")) {
                install(imeCallback.declaredMethods.single { it.name == name }) { chain ->
                    if (isPanelIme(chain.thisObject)) api.panelModeScope.withDrawer { chain.proceed() }
                    else chain.proceed()
                }
            }
            install(imeCallback.declaredMethods.single { it.name == "updateTranslateY" }) { chain ->
                if (!isPanelIme(chain.thisObject)) return@install chain.proceed()
                api.panelModeScope.withDrawer {
                    chain.proceed(arrayOf(0f, 0f, chain.getArg(2), chain.getArg(3)))
                }
            }
            install(imeCallback.declaredMethods.single { it.name == "startSearchViewAndRecyclerViewAnim" }) { chain ->
                if (!isPanelIme(chain.thisObject)) return@install chain.proceed()
                val view = ModernReflect.getObjectField(chain.thisObject!!, "view") as View
                val margin = view.resources.getIdentifier("all_apps_category_search_container_margin_bottom_search", "dimen", "com.android.launcher")
                require(margin != 0) { "Native search margin missing" }
                // Cancel the native bottom search bar offset while retaining animation completion and search updates.
                val offset = view.paddingBottom - view.resources.getDimensionPixelSize(margin)
                api.panelModeScope.withDrawer { chain.proceed(arrayOf(offset, chain.getArg(1))) }
            }
            val holder = ModernReflect.findClass("com.android.launcher3.allapps.BaseAllAppsContainerView\$AdapterHolder", loader)
            install(holder.getDeclaredMethod("applyPadding")) { chain ->
                val result = chain.proceed()
                val view = ModernReflect.getObjectField(chain.thisObject!!, "mRecyclerView") as? View
                sessions.values.firstOrNull { view != null && it.isPanelView(view) }?.applyPanelLayout()
                result
            }
            val recycler = ModernReflect.findClass("com.android.launcher3.allapps.OplusAllAppsRecyclerView", loader)
            val colorPages = ModernReflect.findClass("com.android.launcher3.allapps.colorfilter.OplusColorFilterPagedView", loader)
            for (name in listOf("updateLayoutForPage", "updateListLayoutParam", "updateTitleLayoutParam", "updateSwitchPageAnimParam")) {
                val method = colorPages.declaredMethods.single { it.name == name }
                install(method) { chain ->
                    val target = if (Modifier.isStatic(method.modifiers)) chain.getArg(0) else chain.thisObject
                    val result = chain.proceed()
                    sessions.values.firstOrNull { target is View && it.isPanelView(target) }?.applyPanelLayout()
                    result
                }
            }
            install(ModernReflect.findMethodExact(recycler, "shouldInterceptBeyondTopAndBottom", arrayOf(MotionEvent::class.java))) { chain ->
                if (isPanelView(chain.thisObject)) false else chain.proceed()
            }
            for (name in listOf("getTopFadeHeightLimit", "getBottomFadeLimit")) {
                install(recycler.declaredMethods.single { it.name == name }) { chain ->
                    val view = chain.thisObject as View
                    val session = sessions.values.firstOrNull { it.isPanelView(view) }
                    if (session == null) chain.proceed()
                    else if (name == "getTopFadeHeightLimit") session.fadeBounds(view)?.end ?: 0 else view.height
                }
            }
            install(recycler.getDeclaredMethod("draw", Canvas::class.java)) { chain ->
                val view = chain.thisObject as View
                val session = sessions.values.firstOrNull { it.isPanelView(view) } ?: return@install chain.proceed()
                // Align the top fade with standalone search and remove the old drawer's solid bottom overlay.
                session.fadeBounds(view)?.let { api.preparePanelFade(view, it) }
                val canvas = chain.getArg(0) as Canvas
                val checkpoint = canvas.save()
                canvas.clipRect(view.scrollX, view.scrollY, view.scrollX + view.width, view.scrollY + view.height)
                try { chain.proceed() } finally { canvas.restoreToCount(checkpoint) }
            }
            val allAppsState = ModernReflect.findClass("com.android.launcher3.uioverrides.states.AllAppsState", loader)
            install(allAppsState.declaredMethods.first { it.name == "getWorkspaceScrimColor" }) { chain ->
                if (sessions[chain.getArg(0)]?.hasActivePanel == true) Color.TRANSPARENT else chain.proceed()
            }
            val baseApps = ModernReflect.findClass("com.android.launcher3.allapps.BaseAllAppsContainerView", loader)
            install(baseApps.getDeclaredMethod("drawOnScrim", Canvas::class.java)) { chain ->
                if (isPanelView(chain.thisObject)) null else chain.proceed()
            }
            val pager = ModernReflect.findClass("com.android.launcher3.allapps.OplusCategoryPagedView", loader)
            for (name in listOf("onInterceptTouchEvent", "onTouchEvent")) {
                install(ModernReflect.findMethodExact(pager, name, arrayOf(MotionEvent::class.java))) { chain ->
                    if (pager.isInstance(chain.thisObject) && sessions.values.any { it.isPanelView(chain.thisObject as View) }) false
                    else chain.proceed()
                }
            }
            HookLog.i(TAG, "Rightmost category page hooks installed")
        }.onFailure { HookLog.w(TAG, "Rightmost category page hooks unavailable", it) }
    }

    private class NativeApi(loader: ClassLoader) {
        val creatingPanel = ThreadLocal<Boolean>()
        val panelModeScope = LauncherCategoryModeScope()
        val searchRowWidth = ThreadLocal<Int>()
        fun <T> withSearchRow(view: View, block: () -> T): T {
            val previous = searchRowWidth.get()
            val width = view.layoutParams?.width?.takeIf { it > 0 } ?: view.width
            if (width > 0) searchRowWidth.set(width)
            try { return block() }
            finally { if (previous == null) searchRowWidth.remove() else searchRowWidth.set(previous) }
        }
        val panelStores: MutableMap<Any, Boolean> = Collections.synchronizedMap(WeakHashMap())
        fun isPanelStore(store: Any?): Boolean = store != null && panelStores[store] == true
        val launcher = ModernReflect.findClass("com.android.launcher.Launcher", loader)
        val stateManager = ModernReflect.findClass("com.android.launcher3.statemanager.StateManager", loader)
        val baseState = ModernReflect.findClass("com.android.launcher3.statemanager.BaseState", loader)
        val state = ModernReflect.findClass("com.android.launcher3.LauncherState", loader)
        val config = ModernReflect.findClass("com.android.launcher3.states.StateAnimationConfig", loader)
        val mode = ModernReflect.findClass("com.android.launcher.mode.LauncherModeManager", loader)
        private val floating = ModernReflect.findClass("com.android.launcher3.AbstractFloatingView", loader)
        private val activityContext = ModernReflect.findClass("com.android.launcher3.views.ActivityContext", loader)
        private val playback = ModernReflect.findClass("com.android.launcher3.anim.AnimatorPlaybackController", loader)
        val createAnimation = stateManager.getDeclaredMethod("createAnimationToNewWorkspace", baseState, config)
        val goToState = stateManager.getDeclaredMethod("goToState", baseState, Boolean::class.javaPrimitiveType)
        val getState = stateManager.getDeclaredMethod("getState")
        val setFraction = playback.getDeclaredMethod("setPlayFraction", Float::class.javaPrimitiveType)
        val dispatchStart = playback.getDeclaredMethod("dispatchOnStart")
        val dispatchCancel = playback.getDeclaredMethod("dispatchOnCancel")
        val dispatchEnd = playback.getDeclaredMethod("dispatchOnEnd")
        private val topView = floating.getDeclaredMethod("getTopOpenView", activityContext)
        private val appClosing = runCatching {
            ModernReflect.findClass("com.android.launcher3.anim.engine.helper.AnimStateQueryUtil", loader)
                .getDeclaredMethod("isAppClosing", ModernReflect.findClass("com.android.launcher3.Launcher", loader))
        }.onFailure { HookLog.w(TAG, "Category return animation query unavailable", it) }.getOrNull()
        private var touchQueryWarningShown = false

        fun isPanelTouch(activity: Activity): Boolean {
            if (!activity.hasWindowFocus()) return false
            return runCatching {
                LauncherCategoryPageRules.isPanelTouch(true,
                    ModernReflect.callMethod(activity, "isResumed") as? Boolean,
                    appClosing?.invoke(null, activity) as? Boolean,
                    ModernReflect.callMethod(ModernReflect.callMethod(activity, "getOverviewPanel"), "isGustureActive") as? Boolean,
                )
            }.getOrElse {
                // Retain the return target if a state query fails instead of closing the category page.
                if (!touchQueryWarningShown) {
                    touchQueryWarningShown = true
                    HookLog.w(TAG, "Category return touch query unavailable", it)
                }
                false
            }
        }

        fun prepareStandardCategories(results: Any) {
            val appState = requireNotNull(ModernReflect.getObjectField(results, "mApp"))
            val context = requireNotNull(ModernReflect.callMethod(appState, "getContext"))
            val appList = requireNotNull(ModernReflect.getObjectField(results, "mBgAllAppsList"))
            val installed = ModernReflect.getObjectField(appList, "data") as ArrayList<*>
            val loader = results.javaClass.classLoader
            // Use native category data and icon color parsing on the launcher loading thread.
            val categoryMove = ModernReflect.findClass("com.android.launcher3.allapps.categoryfolder.CategoryMoveHelper", loader)
            ModernReflect.callStaticMethod(categoryMove, "loadCategoryMapFromDatabase", context, false)
            val assets = ModernReflect.findClass("s2.l", loader)
            ModernReflect.callMethod(ModernReflect.callStaticMethod(assets, "q", context), "z", installed)
            HookLog.d(TAG, "Standard loader prepared: apps=${installed.size}")
            val palette = ModernReflect.findClass("com.android.launcher3.allapps.colorfilter.DrawerIconPalette", loader)
            val executors = ModernReflect.findClass("ej.a", loader)
            val executor = executors.getDeclaredField("a").apply { isAccessible = true }.get(null) as Executor
            executor.execute {
                runCatching {
                    ModernReflect.callStaticMethod(palette, "parseAllColorDatas")
                    HookLog.d(TAG, "Standard palette ready=${ModernReflect.callStaticMethod(palette, "loadFinished")}")
                }.onFailure { HookLog.w(TAG, "Failed to parse standard-mode icon colors", it) }
            }
        }

        fun topView(activity: Activity): Any? = topView.invoke(null, activity)
        private val fades = WeakHashMap<View, Pair<LauncherCategoryPageRules.Fade, LinearGradient>>()
        fun preparePanelFade(view: View, fade: LauncherCategoryPageRules.Fade) {
            val cached = fades[view]
            if (cached?.first == fade && ModernReflect.getObjectField(view, "mTopFadeShader") === cached.second &&
                ModernReflect.getObjectField(view, "mNeedRefreshFadeHeight") == false &&
                ModernReflect.getObjectField(view, "mBottomFadeHeight") == 0
            ) return
            val shader = if (cached?.first == fade) cached.second else {
                val start = if (fade.end > 0) fade.start.toFloat() / fade.end else 0f
                LinearGradient(0f, 0f, 0f, 1f,
                    intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, start.coerceIn(0f, 0.9999f), 1f), Shader.TileMode.CLAMP,
                ).also { fades[view] = fade to it }
            }
            val top = ModernReflect.getObjectField(view, "mCustomTopFadeHeight") as IntArray
            top[0] = fade.start
            top[1] = 0
            top[2] = fade.end - fade.start
            (ModernReflect.getObjectField(view, "mCustomBottomFadeHeight") as IntArray).fill(0)
            ModernReflect.setObjectField(view, "mTopFadeHeight", fade.end)
            ModernReflect.setObjectField(view, "mTopFadeShader", shader)
            ModernReflect.setObjectField(view, "mBottomFadeHeight", 0)
            ModernReflect.setObjectField(view, "mNeedRefreshFadeHeight", false)
            view.isVerticalFadingEdgeEnabled = false
        }
        fun state(name: String): Any = state.getDeclaredField(name).get(null)!!
        fun supportsMode(): Boolean {
            val instance = mode.getDeclaredMethod("getInstance").invoke(null)
            return LauncherCategoryPageRules.supportsMode(
                ModernReflect.callMethod(instance, "isStandardMode") == true,
                ModernReflect.callMethod(instance, "isInDrawerMode") == true,
            )
        }

        fun animationConfig(): Any = config.getDeclaredConstructor().newInstance().also {
            config.getDeclaredField("duration").setLong(it, 360L)
            config.getDeclaredField("userControlled").setBoolean(it, true)
        }
    }

    private class Session(private val activity: Activity, private val api: NativeApi) {
        val manager = requireNotNull(ModernReflect.callMethod(activity, "getStateManager"))
        private val workspace = ModernReflect.callMethod(activity, "getWorkspace") as ViewGroup
        private val drawer = ModernReflect.callMethod(activity, "getAppsView") as ViewGroup
        private val drawerController = requireNotNull(ModernReflect.callMethod(activity, "getAllAppsController"))
        private var panel: LauncherCategoryPanel? = null
        private val apps: ViewGroup get() = panel?.takeIf { it.active }?.apps ?: drawer
        private val controller: Any get() = panel?.takeIf { it.active }?.controller ?: drawerController
        private val content: View get() = ModernReflect.callMethod(controller, "getAllAppContent") as View
        private val translate: View get() = if (content.resourceName() == "b_level_apps_view_translate") content
            else nativeView("apps_view_translate") ?: content
        private val hotseat = ModernReflect.callMethod(activity, "getHotseat") as View
        private val indicator = ModernReflect.callMethod(workspace, "getPageIndicator") as? View
        private val desktopExtras = listOfNotNull(hotseat, indicator)
        private val normal = api.state("NORMAL")
        private val allApps = api.state("ALL_APPS")
        private val configuration = ViewConfiguration.get(activity)
        private val interpolator = ModernReflect.callMethod(workspace, "getMDefaultInterpolator") as TimeInterpolator
        private val slop = configuration.scaledTouchSlop.toFloat()
        private val flingThreshold = runCatching {
            (ModernReflect.getObjectField(workspace, "mFlingThresholdVelocity") as Int).toFloat()
        }.getOrDefault(configuration.scaledMinimumFlingVelocity.toFloat())
        private var candidate: Direction? = null
        private var direction: Direction? = null
        private var downX = 0f
        private var downY = 0f
        private var pointer = -1
        private var tracker: VelocityTracker? = null
        private var playback: Any? = null
        private var animator: ValueAnimator? = null
        private var snapshots = emptyList<ViewState>()
        private var normalSurfaces = emptyList<ViewState>()
        private var openedFromEdge = false
        private var fraction = 0f
        private var viewport = 0f
        private var changingState = false
        private var discardUntilUp = false
        private var entryPage = -1
        private var startingFraction = 0f
        private var verticalExit = false
        private var waitingForEdge = false
        private var nativeHomeExit = false
        private var settlingComplete = false
        private var categoryDragging = false
        private var launchedFromPanel = false
        private var returningFolder: ViewGroup? = null
        private var transitionApps: ViewGroup? = null
        private var transitionContent: View? = null
        private var transitionTranslate: View? = null
        private var appsBaselineX = 0f
        private var contentBaselineAlpha = 1f
        val ownsGesture: Boolean get() = direction != null || discardUntilUp || animator != null
        val hasActivePanel: Boolean get() = panel?.active == true
        val retainsAppReturn: Boolean get() = LauncherCategoryPageRules.retainAppReturn(
            hasActivePanel, openedFromEdge, launchedFromPanel, changingState || direction != null,
        )

        fun handle(event: MotionEvent, cancelOriginal: () -> Unit): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) discardUntilUp = false
            if (discardUntilUp) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    discardUntilUp = false
                }
                return true
            }
            if (animator != null) {
                val pending = direction
                if (event.actionMasked == MotionEvent.ACTION_DOWN && pending != null &&
                    LauncherCategoryPageRules.handOffDesktopTouch(pending, settlingComplete)
                ) {
                    // A new press during exit belongs to the desktop; preserve the long-press start.
                    animator?.end()
                } else {
                    if (nativeHomeExit) return true
                    if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
                    val running = animator
                    animator = null
                    running?.removeAllListeners()
                    running?.removeAllUpdateListeners()
                    running?.cancel()
                    startingFraction = fraction
                    downX = event.rawX
                    downY = event.rawY
                    releaseTracker()
                    pointer = event.getPointerId(0)
                    tracker = VelocityTracker.obtain().apply { addMovement(event) }
                    return true
                }
            }
            if (categoryDragging && direction == null) {
                candidate = null
                releaseTracker()
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // App return gestures also dispatch desktop touches; do not clear the return target early.
                    if (launchedFromPanel && hasActivePanel && api.getState.invoke(manager) === allApps &&
                        api.isPanelTouch(activity)
                    ) {
                        launchedFromPanel = false
                        returningFolder = null
                        HookLog.d(TAG, "Category app return released by panel touch")
                    }
                    releaseTracker()
                    startingFraction = 0f
                    verticalExit = isHomeGestureStart(event)
                    candidate = candidateAt(event)
                    if (verticalExit) candidate = Direction.Exit
                    waitingForEdge = candidate == Direction.Enter && !atEntryEdge()
                    if (candidate != null) {
                        downX = event.rawX
                        downY = event.rawY
                        pointer = event.getPointerId(0)
                        tracker = VelocityTracker.obtain().apply { addMovement(event) }
                    }
                    return false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    candidate = null
                    releaseTracker()
                    if (direction != null) {
                        settle(false, 0f)
                        discardUntilUp = true
                        return true
                    }
                    return false
                }
            }
            tracker?.addMovement(event)
            val dx = if (verticalExit) downY - event.rawY else event.rawX - downX
            val dy = if (verticalExit) event.rawX - downX else event.rawY - downY
            if ((event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP) && direction == null) {
                val pending = candidate ?: return false
                if (!LauncherCategoryPageRules.canClaimSwipe(categoryDragging, event.eventTime - event.downTime,
                        ViewConfiguration.getLongPressTimeout())) {
                    candidate = null
                    releaseTracker()
                    return false
                }
                if (abs(dy) > slop * 1.5f && abs(dy) >= abs(dx) ||
                    LauncherCategoryPageRules.forwardDistance(dx, pending) < -slop * 1.5f
                ) {
                    candidate = null
                    releaseTracker()
                    return false
                }
                if (waitingForEdge) {
                    if (atEntryEdge()) {
                        // Count dragging only after reaching the last page, excluding the previous page transition.
                        waitingForEdge = false
                        downX = event.rawX
                        downY = event.rawY
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        candidate = null
                        releaseTracker()
                    }
                    return false
                }
                if (!LauncherCategoryPageRules.canStart(dx, dy, pending, slop)) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        candidate = null
                        releaseTracker()
                    }
                    return false
                }
                if (!canBegin(pending)) {
                    candidate = null
                    releaseTracker()
                    return false
                }
                direction = pending
                cancelOriginal()
                begin(pending)
            }
            val active = direction ?: run {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    candidate = null
                    releaseTracker()
                }
                return false
            }
            val effectiveDx = LauncherCategoryPageRules.continuedDelta(dx, startingFraction, viewport, active)
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> render(LauncherCategoryPageRules.progress(effectiveDx, viewport, active))
                MotionEvent.ACTION_UP -> {
                    render(LauncherCategoryPageRules.progress(effectiveDx, viewport, active))
                    tracker?.computeCurrentVelocity(1000, configuration.scaledMaximumFlingVelocity.toFloat())
                    val velocity = if (verticalExit) -(tracker?.getYVelocity(pointer) ?: 0f)
                        else tracker?.getXVelocity(pointer) ?: 0f
                    settle(LauncherCategoryPageRules.shouldComplete(effectiveDx, viewport, velocity, active, flingThreshold), velocity)
                    releaseTracker()
                }
                MotionEvent.ACTION_CANCEL -> {
                    settle(false, 0f)
                    releaseTracker()
                }
            }
            return true
        }

        private fun isHomeGestureStart(event: MotionEvent): Boolean {
            if (!hasActivePanel || !openedFromEdge || api.getState.invoke(manager) !== allApps ||
                api.topView(activity) != null || isSearching() || !api.supportsMode() ||
                ModernReflect.callMethod(activity, "isAllAppsViewInSelectState") == true ||
                ModernReflect.callMethod(ModernReflect.callMethod(activity, "getDragController"), "isDragging") == true
            ) return false
            val location = IntArray(2)
            apps.getLocationOnScreen(location)
            val bottom = location[1] + apps.height
            val inset = apps.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
            val region = maxOf(inset, (48f * apps.resources.displayMetrics.density).toInt())
            return event.rawY >= bottom - region && event.rawY <= bottom
        }

        private fun atEntryEdge(): Boolean {
            if (entryPage < 0) return false
            val edge = ModernReflect.callMethod(workspace, "getScrollForPage", entryPage) as Int
            return LauncherCategoryPageRules.isAtEntryEdge(workspace.scrollX, edge, slop.toInt())
        }

        private fun candidateAt(event: MotionEvent): Direction? {
            if (!api.supportsMode() || api.topView(activity) != null ||
                ModernReflect.callMethod(activity, "isWorkspaceLoading") == true ||
                ModernReflect.callMethod(activity, "isAllAppsViewInSelectState") == true ||
                ModernReflect.callMethod(ModernReflect.callMethod(activity, "getDragController"), "isDragging") == true
            ) return null
            val state = api.getState.invoke(manager)
            if (state === normal) {
                if (ModernReflect.callMethod(workspace, "isSwitchingState") == true) return null
                val page = ModernReflect.callMethod(workspace, "getCurrentPage") as Int
                val next = ModernReflect.callMethod(workspace, "getNextPage") as Int
                val count = ModernReflect.callMethod(workspace, "getPageCount") as Int
                val panels = ModernReflect.callMethod(workspace, "getPanelCount") as Int
                val rtl = workspace.layoutDirection == View.LAYOUT_DIRECTION_RTL
                entryPage = LauncherCategoryPageRules.entryPage(page, next, count, panels, rtl)
                if (entryPage < 0 ||
                    (ModernReflect.callMethod(workspace, "getScreenIdForPageIndex", entryPage) as Int) < 0 ||
                    !workspace.contains(event) || nestedTouchTarget(workspace, event, skipPager = false)
                ) return null
                return Direction.Enter
            }
            if (state === allApps && openedFromEdge && !isSearching() &&
                ModernReflect.callMethod(apps, "isShowingCategoryTab") == true
            ) {
                val category = ModernReflect.callMethod(apps, "getCategoryRecyclerView") as? View ?: return null
                if (category.contains(event) && !nestedTouchTarget(category, event, skipPager = true)) return Direction.Exit
            }
            return null
        }

        private fun canBegin(pending: Direction): Boolean {
            if (categoryDragging || api.topView(activity) != null || !api.supportsMode() ||
                ModernReflect.callMethod(activity, "isAllAppsViewInSelectState") == true ||
                ModernReflect.callMethod(ModernReflect.callMethod(activity, "getDragController"), "isDragging") == true
            ) return false
            if (pending == Direction.Exit) return api.getState.invoke(manager) === allApps && openedFromEdge && !isSearching()
            val page = ModernReflect.callMethod(workspace, "getCurrentPage") as Int
            val next = ModernReflect.callMethod(workspace, "getNextPage") as Int
            val count = ModernReflect.callMethod(workspace, "getPageCount") as Int
            val panels = ModernReflect.callMethod(workspace, "getPanelCount") as Int
            return api.getState.invoke(manager) === normal && atEntryEdge() &&
                (LauncherCategoryPageRules.entryPage(page, next, count, panels, workspace.layoutDirection == View.LAYOUT_DIRECTION_RTL) >= 0 ||
                    LauncherCategoryPageRules.isRightmostPage(entryPage, count, panels, workspace.layoutDirection == View.LAYOUT_DIRECTION_RTL)) &&
                ModernReflect.callMethod(drawer, "needShowCategoryTab") == true &&
                ModernReflect.callMethod(drawer, "getCategoryRecyclerView") != null
        }

        private fun begin(pending: Direction) {
            viewport = workspace.width.toFloat().coerceAtLeast(1f)
            if (pending == Direction.Enter) {
                normalSurfaces = listOfNotNull(workspace, hotseat, indicator).map(::ViewState)
                ModernReflect.callMethod(workspace, "snapToPageImmediately", entryPage)
                if (panel == null) panel = LauncherCategoryPanel(
                    activity, manager, drawer, drawerController, api.creatingPanel,
                    { api.panelStores[it] = true }, { api.panelStores.remove(it) }, api.panelModeScope,
                )
                panel!!.activate()
                panel!!.prepareHorizontalEntry()
            } else panel?.finishClosingFolders()
            transitionApps = apps
            transitionContent = content
            transitionTranslate = translate
            appsBaselineX = transitionApps!!.translationX
            contentBaselineAlpha = transitionContent!!.alpha
            snapshots = listOfNotNull(workspace, transitionApps, transitionContent, transitionTranslate, hotseat, indicator)
                .distinct().map(::ViewState)
            changingState = true
            try {
                playback = api.createAnimation.invoke(manager, if (pending == Direction.Enter) allApps else normal, api.animationConfig())
                api.dispatchStart.invoke(playback)
                if (pending == Direction.Enter) {
                    ModernReflect.callMethod(apps, "updateCategoryTabVisibility", true, false)
                    ModernReflect.callMethod(apps, "switchToTab", 1)
                }
                render(0f)
            } finally {
                changingState = false
            }
            HookLog.d(TAG, "Horizontal category transition: $pending")
        }

        private fun render(progress: Float) {
            fraction = progress
            api.setFraction.invoke(playback, progress)
            val active = direction ?: return
            val offsets = LauncherCategoryPageRules.offsets(progress, viewport, active)
            val baseline = normalSurfaces.firstOrNull { it.view === workspace } ?: return
            baseline.restore()
            workspace.visibility = View.VISIBLE
            workspace.translationX = baseline.x + offsets.workspace
            val open = if (active == Direction.Enter) progress else 1f - progress
            for (view in desktopExtras) {
                val original = normalSurfaces.first { it.view === view }
                original.restore()
                view.translationX = original.x + offsets.workspace
                view.alpha = original.alpha * (1f - open)
            }
            val apps = transitionApps ?: return
            val content = transitionContent ?: return
            val translate = transitionTranslate ?: return
            apps.translationX = appsBaselineX + offsets.apps
            if (apps.translationY != 0f) apps.translationY = 0f
            if (apps.alpha != 1f) apps.alpha = 1f
            if (apps.visibility != View.VISIBLE) apps.visibility = View.VISIBLE
            drawer.visibility = View.INVISIBLE
            translate.translationY = 0f
            translate.alpha = 1f
            translate.visibility = View.VISIBLE
            content.translationY = 0f
            content.scaleX = 1f
            content.scaleY = 1f
            content.alpha = if (active == Direction.Enter) 1f else contentBaselineAlpha
        }

        private fun settle(complete: Boolean, velocity: Float, completion: Animator.AnimatorListener? = null) {
            val active = direction ?: return
            candidate = null
            settlingComplete = complete
            val target = if (complete) 1f else 0f
            val distance = viewport * abs(target - fraction)
            val nativeDuration = if (abs(velocity) >= flingThreshold) {
                ModernReflect.callMethod(workspace, "calculateScrollDuration", distance, velocity.toInt()) as Int
            } else runCatching { ModernReflect.getObjectField(workspace, "mPageSnapAnimationDuration") as Int }.getOrDefault(360)
            val animation = ValueAnimator.ofFloat(fraction, target)
            animation.duration = LauncherCategoryPageRules.settleDuration(nativeDuration)
            animation.interpolator = interpolator
            animation.addUpdateListener {
                runCatching { render(it.animatedValue as Float) }.onFailure { error ->
                    HookLog.w(TAG, "Category animation interrupted", error)
                    abort()
                }
            }
            animation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    runCatching { finish(active, complete) }.onFailure {
                        HookLog.w(TAG, "Failed to complete category transition", it)
                        abort()
                    }
                    runCatching { completion?.onAnimationEnd(animation) }
                        .onFailure { HookLog.w(TAG, "Category home completion unavailable", it) }
                }
            })
            if (completion != null) animation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    runCatching { completion.onAnimationStart(animation) }
                        .onFailure { HookLog.w(TAG, "Category home start unavailable", it) }
                }
                override fun onAnimationCancel(animation: Animator) {
                    runCatching { completion.onAnimationCancel(animation) }
                        .onFailure { HookLog.w(TAG, "Category home cancellation unavailable", it) }
                }
            })
            animator = animation
            animation.start()
        }

        private fun finish(active: Direction, complete: Boolean) {
            val showCategories = LauncherCategoryPageRules.showCategories(active, complete)
            changingState = true
            try {
                restoreViews()
                if (!complete) api.dispatchCancel.invoke(playback)
                api.dispatchEnd.invoke(playback)
                playback = null
                api.goToState.invoke(manager, if (showCategories) allApps else normal, false)
                ModernReflect.callMethod(manager, "reapplyState")
                check(api.getState.invoke(manager) === if (showCategories) allApps else normal) {
                    "Native launcher rejected category destination"
                }
                if (showCategories) {
                    ModernReflect.callMethod(apps, "updateCategoryTabVisibility", true, false)
                    ModernReflect.callMethod(apps, "switchToTab", 1)
                    panel?.ensureCategories()
                    panel?.applyLayout()
                } else {
                    panel?.deactivate()
                    launchedFromPanel = false
                    returningFolder = null
                    releaseDesktopTouch()
                    normalSurfaces = emptyList()
                }
                openedFromEdge = showCategories
                HookLog.d(TAG, "Horizontal category settled: visible=$showCategories")
            } finally {
                direction = null
                startingFraction = 0f
                verticalExit = false
                waitingForEdge = false
                nativeHomeExit = false
                transitionApps = null
                transitionContent = null
                transitionTranslate = null
                changingState = false
                releaseTracker()
            }
        }

        fun handleBack(): Boolean {
            if (!openedFromEdge || ownsGesture || api.getState.invoke(manager) !== allApps || isSearching() ||
                api.topView(activity) != null || ModernReflect.callMethod(apps, "isShowingCategoryTab") != true
            ) return false
            direction = Direction.Exit
            begin(Direction.Exit)
            settle(true, 0f)
            return true
        }

        fun handleHomeDestination(state: Any?, listener: Animator.AnimatorListener?): Boolean {
            if (state !== normal || !hasActivePanel || !openedFromEdge || changingState || ownsGesture ||
                api.getState.invoke(manager) !== allApps || api.topView(activity) != null
            ) return false
            direction = Direction.Exit
            nativeHomeExit = true
            begin(Direction.Exit)
            settle(true, 0f, listener)
            return true
        }

        fun onNativeStateChange(state: Any?) {
            if (changingState) return
            if (hasActivePanel) HookLog.d(TAG, "Category native state start: state=$state, return=$launchedFromPanel")
            if (ownsGesture) abort(restoreState = false)
        }

        fun onNativeStateComplete(state: Any?) {
            if (hasActivePanel && !changingState) HookLog.d(TAG, "Category native state end: state=$state, return=$launchedFromPanel")
            if (!changingState && state === normal && retainsAppReturn) {
                // Completion may still target NORMAL; restore the standalone page in the same frame.
                changingState = true
                try {
                    api.goToState.invoke(manager, allApps, false)
                    ModernReflect.callMethod(manager, "reapplyState")
                    panel?.prepareIconReturn()
                } finally { changingState = false }
                return
            }
            if (!changingState && (state === normal || state === api.state("OVERVIEW")) && !retainsAppReturn) {
                categoryDragging = false
                candidate = null
                releaseTracker()
                panel?.deactivate()
                openedFromEdge = false
                normalSurfaces = emptyList()
            }
        }

        fun onStartingPanelActivity(view: View?) {
            launchedFromPanel = true
            returningFolder = panelFolderFor(view)
            ModernReflect.callMethod(manager, "setRestState", allApps)
            HookLog.d(TAG, "Category app return armed: folder=${returningFolder != null}")
        }

        fun shouldReturnToPanel(state: Any?): Boolean = state === normal && retainsAppReturn
        fun shouldKeepExpandedFolder(folder: ViewGroup): Boolean =
            LauncherCategoryPageRules.retainExpandedFolder(retainsAppReturn, returningFolder === folder)
        private fun panelFolderFor(view: View?): ViewGroup? {
            var current = view
            while (current != null) {
                if (current is ViewGroup && current.javaClass.name == "com.android.launcher3.allapps.categoryfolder.CategoryFolder" &&
                    isPanelFolder(current)
                ) return current
                current = current.parent as? View
            }
            return null
        }
        fun isPanelFolder(folder: ViewGroup): Boolean = panel?.ownsFolder(folder) == true
        fun folderAnimationNodes(folder: ViewGroup): List<View> = panel?.folderAnimationNodes(folder).orEmpty()
        fun releaseClosedFolder(folder: ViewGroup, nodes: List<View>) {
            panel?.releaseClosedFolder(folder, nodes)
            if (returningFolder === folder) {
                returningFolder = null
                launchedFromPanel = false
            }
        }

        fun syncStore(source: Any) { panel?.syncStore(source) }
        fun isPanelStore(value: Any?): Boolean = panel?.ownsStore(value) == true
        fun onCategoryDragChanged(dragging: Boolean) {
            categoryDragging = dragging
            if (dragging) {
                candidate = null
                releaseTracker()
            }
        }
        fun saveCategoryOrder(items: List<*>) { panel?.saveCategoryOrder(items) }
        fun commitCategoryOrder() { panel?.commitCategoryOrder() }
        fun restoreCategoryOrder(items: Any?) { panel?.restoreCategoryOrder(items) }
        fun applyPanelLayout() { panel?.applyLayout() }
        fun fadeBounds(view: View): LauncherCategoryPageRules.Fade? = panel?.fadeBounds(view)
        fun prepareIconReturn() { panel?.prepareIconReturn(returningFolder) }
        fun syncSearchBackground(view: View, bounds: Rect) {
            val row = ModernReflect.callMethod(panel?.apps, "getSearchView") as? View ?: return
            api.withSearchRow(row) { panel?.syncSearchBackground(view, bounds) }
        }
        fun recordIconGeometry(event: String, view: View?) {
            if (view == null) { HookLog.d(TAG, "Category icon $event: target=false"); return }
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val ancestors = mutableListOf<String>()
            var parent = view.parent as? View
            while (parent != null && isPanelView(parent)) {
                ancestors.add("${parent.resourceName()}:y=${parent.translationY},s=${parent.scaleY}")
                parent = parent.parent as? View
            }
            HookLog.d(TAG, "Category icon $event: panel=${isPanelView(view)}, folder=${panelFolderFor(view) != null}, x=${location[0]}, y=${location[1]}, parents=$ancestors")
        }
        fun remapHandlers(handlers: Any?): Any? = panel?.remapHandlers(handlers) ?: handlers
        fun shouldRetainPanelState(): Boolean = hasActivePanel && api.getState.invoke(manager) === allApps
        fun isPanelDestination(state: Any?): Boolean = hasActivePanel && state === allApps
        fun isPanelController(value: Any?): Boolean = hasActivePanel && panel?.controller === value
        fun isPanelView(view: View): Boolean {
            val root = panel?.root ?: return false
            var current: View? = view
            while (current != null && current !== root) current = current.parent as? View
            return current === root
        }

        fun dispose(restoreState: Boolean = true) {
            abort(restoreState, closeLibrary = true)
            panel?.dispose()
            panel = null
        }

        fun abort(restoreState: Boolean = true, closeLibrary: Boolean = false) {
            candidate = null
            releaseTracker()
            val active = direction
            val running = animator
            animator = null
            running?.removeAllListeners()
            running?.removeAllUpdateListeners()
            running?.cancel()
            restoreViews()
            changingState = true
            try {
                if (playback != null) {
                    api.dispatchCancel.invoke(playback)
                    api.dispatchEnd.invoke(playback)
                }
                playback = null
                if (restoreState && (active != null || closeLibrary && openedFromEdge)) {
                    val restoreDesktop = active == Direction.Enter || closeLibrary
                    api.goToState.invoke(manager, if (restoreDesktop) normal else allApps, false)
                    ModernReflect.callMethod(manager, "reapplyState")
                    if (restoreDesktop) panel?.deactivate()
                    else ModernReflect.callMethod(apps, "switchToTab", 1)
                }
                if (active == Direction.Enter || closeLibrary) {
                    panel?.deactivate()
                    if (restoreState) releaseDesktopTouch()
                    normalSurfaces = emptyList()
                    openedFromEdge = false
                    launchedFromPanel = false
                    returningFolder = null
                }
            } finally {
                if (active != null) discardUntilUp = true
                direction = null
                startingFraction = 0f
                verticalExit = false
                waitingForEdge = false
                nativeHomeExit = false
                categoryDragging = false
                transitionApps = null
                transitionContent = null
                transitionTranslate = null
                changingState = false
            }
        }

        private fun restoreViews() {
            snapshots.forEach { it.restore() }
            snapshots = emptyList()
        }

        private fun releaseTracker() {
            tracker?.recycle()
            tracker = null
            pointer = -1
        }

        private fun releaseDesktopTouch() {
            ModernReflect.callMethod(workspace, "resetTouchState")
            workspace.requestDisallowInterceptTouchEvent(false)
            ModernReflect.callMethod(activity, "setTouchInProgress", false)
        }

        private fun isSearching(): Boolean = ModernReflect.callMethod(apps, "hasEnterSearchMode") == true

        private fun nativeView(name: String): View? {
            val id = apps.resources.getIdentifier(name, "id", "com.android.launcher")
            return if (id == 0) null else apps.findViewById(id)
        }
    }

    private class ViewState(val view: View) {
        val x = view.translationX
        private val y = view.translationY
        val alpha = view.alpha
        private val scaleX = view.scaleX
        private val scaleY = view.scaleY
        private val visibility = view.visibility

        fun restore() {
            view.translationX = x
            view.translationY = y
            view.alpha = alpha
            view.scaleX = scaleX
            view.scaleY = scaleY
            view.visibility = visibility
        }
    }

    private fun View.resourceName(): String? = runCatching { resources.getResourceEntryName(id) }.getOrNull()

    private fun View.contains(event: MotionEvent): Boolean =
        Rect().let { getGlobalVisibleRect(it) && it.contains(event.rawX.toInt(), event.rawY.toInt()) }

    private fun nestedTouchTarget(view: View, event: MotionEvent, skipPager: Boolean, root: Boolean = true): Boolean {
        if (!view.contains(event)) return false
        if (view is AppWidgetHostView) return true
        val containerPager = view.javaClass.name.endsWith("AllAppsPagedView")
        if (!root && (!skipPager || !containerPager) && (view.canScrollHorizontally(-1) || view.canScrollHorizontally(1))) return true
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                if (child.contains(event) && nestedTouchTarget(child, event, skipPager, root = false)) return true
            }
        }
        return false
    }
}
