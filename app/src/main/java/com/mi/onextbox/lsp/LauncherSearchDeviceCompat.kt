package com.mi.onextbox.lsp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** Bottom-search compatibility for foldable and tablet layouts. */
internal object LauncherSearchDeviceCompat {
    private const val TAG = "ONextBox-Launcher"
    private const val APPLICATION = "com.android.common.LauncherApplication"
    private const val LAUNCHER = "com.android.launcher.Launcher"
    private const val BASE_LAUNCHER = "com.android.launcher3.Launcher"
    private const val MODE_MANAGER = "com.android.launcher.mode.LauncherModeManager"
    private const val SCREEN_UTILS = "com.android.common.util.ScreenUtils"
    private const val FEATURE_UTILS = "com.android.common.util.AppFeatureUtils"
    private const val SEARCH_IMPL = "com.android.launcher.bottomsearch.e"
    private const val SEARCH_MANAGER = "com.android.launcher.bottomsearch.i"
    private const val SEARCH_CONTAINER = "com.android.launcher.bottomsearch.BottomSearchBoxContainerView"
    private const val EXPORT_SEARCH = "com.android.launcher.bottomsearch.ExportBottomSearch"
    private const val LAUNCHER_STATE = "com.android.launcher3.LauncherState"
    private const val STATE_MANAGER = "com.android.launcher3.statemanager.StateManager"
    private const val CHECK_INTERVAL_MS = 50L
    private const val MAX_CHECKS = 60

    private var bootstrapInstalled = false

    @Synchronized
    fun install(loader: ClassLoader?, selectedClass: Class<*>, implementationField: Field): Int {
        if (bootstrapInstalled) return 0
        return runCatching {
            val applicationClass = ModernReflect.findClass(APPLICATION, loader)
            val onCreate = ModernReflect.findDeclaredMethodExact(applicationClass, "onCreate")
            val bootstrap = LauncherSearchBootstrap {
                installRecovery(loader, selectedClass, implementationField)
            }
            // OEM feature initialization needs the context set by attachBaseContext.
            ModernHookBridge.hookMethod(onCreate, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || !isRepairEnabled(selectedClass)) return
                    val application = param.thisObject as? Application ?: return
                    bootstrap.onApplicationReady(
                        application.baseContext != null &&
                            ModernReflect.callStaticMethod(applicationClass, "getAppContext") is Context,
                    )
                }
            })
            bootstrapInstalled = true
            1
        }.onFailure { HookLog.w(TAG, "Failed to defer search compatibility", it) }.getOrDefault(0)
    }

    private fun installRecovery(loader: ClassLoader?, selectedClass: Class<*>, implementationField: Field) {
        val handles = mutableListOf<XposedInterface.HookHandle>()
        runCatching {
            val features = ModernReflect.findClass(FEATURE_UTILS, loader)
            val tablet = ModernReflect.callStaticMethod(features, "isTablet") == true
            val foldScreen = ModernReflect.callStaticMethod(features, "isFoldScreen") == true
            if (!tablet && !foldScreen) {
                HookLog.i(TAG, "Search compatibility skipped: phone launcher")
                return
            }
            val launcher = ModernReflect.findClass(LAUNCHER, loader)
            val screen = ModernReflect.findClass(SCREEN_UTILS, loader)
            val impl = ModernReflect.findClass(SEARCH_IMPL, loader)
            val manager = ModernReflect.findClass(SEARCH_MANAGER, loader)
            val container = ModernReflect.findClass(SEARCH_CONTAINER, loader)
            val international = selectedClass.name == EXPORT_SEARCH
            val state = if (international) ModernReflect.findClass(LAUNCHER_STATE, loader) else null
            val repairEnabled = { isRepairEnabled(selectedClass) }
            val recovery = RecoveryController(
                screen, impl, manager, container, selectedClass, implementationField,
                foldScreen, repairEnabled, state?.getField("NORMAL")?.get(null),
                state?.getField("HOTSEAT_ICONS")?.getInt(null) ?: 0,
            )
            if (international) {
                // The native coroutine may finish after our recovery task was cancelled.
                // Observe its actual completion, not just the call that starts the coroutine.
                val baseLauncher = ModernReflect.findClass(BASE_LAUNCHER, loader)
                val bind = ModernReflect.findDeclaredMethodExact(
                    selectedClass, "q", selectedClass, baseLauncher, Boolean::class.javaPrimitiveType!!,
                )
                check(Modifier.isStatic(bind.modifiers) && bind.returnType == Void.TYPE)
                handles += ModernHookBridge.hookMethod(bind, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || !repairEnabled()) return
                        val activity = param.args.getOrNull(1) as? Activity ?: return
                        recovery.onBindingComplete(activity)
                    }
                })
                val stateManager = ModernReflect.findClass(STATE_MANAGER, loader)
                for (name in listOf("onStateTransitionStart", "onStateTransitionEnd", "onStateTransitionCanceled")) {
                    val methods = ModernHookBridge.hookAllMethods(stateManager, name, object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable != null || !repairEnabled()) return
                            val owner = param.thisObject ?: return
                            val activity = ModernReflect.getObjectField(owner, "mActivity") as? Activity ?: return
                            if (!launcher.isInstance(activity)) return
                            when (name) {
                                "onStateTransitionStart" -> recovery.onPageTransition(activity)
                                "onStateTransitionEnd" -> recovery.onPageSettled(activity)
                                else -> recovery.schedule(activity)
                            }
                        }
                    })
                    handles += methods
                    check(methods.isNotEmpty()) { "Missing search recovery state callback: $name" }
                }
                val support = ModernReflect.findDeclaredMethodExact(selectedClass, "e", Context::class.java)
                check(support.returnType == Boolean::class.javaPrimitiveType)
                handles += ModernHookBridge.hookMethod(support, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || param.result != false || !repairEnabled()) return
                        if (LauncherSearchRecoveryRules.supportsInternationalDevice(
                                foldScreen = foldScreen,
                                tablet = tablet,
                            )
                        ) param.result = true
                    }
                })
            }
            if (tablet || (international && foldScreen)) {
                val baseLauncher = ModernReflect.findClass(BASE_LAUNCHER, loader)
                val modes = ModernReflect.findClass(MODE_MANAGER, loader)
                val layoutSupport = ModernReflect.findDeclaredMethodExact(impl, "n", baseLauncher)
                check(layoutSupport.returnType == Boolean::class.javaPrimitiveType)
                handles += ModernHookBridge.hookMethod(layoutSupport, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || param.result != false || !repairEnabled()) return
                        if (!selectedClass.isInstance(implementationField.get(null))) return
                        val activity = param.args.firstOrNull() as? Activity ?: return
                        val profile = ModernReflect.callMethod(activity, "getDeviceProfile") ?: return
                        val config = ModernReflect.callMethod(profile, "config") ?: return
                        val mode = ModernReflect.callStaticMethod(modes, "getInstance") ?: return
                        val landscape = ModernReflect.getObjectField(config, "e") == true
                        val drawerOrStandard = ModernReflect.callMethod(mode, "isInDrawerMode") == true ||
                            ModernReflect.callMethod(mode, "isStandardMode") == true
                        val supported = if (international) {
                            LauncherSearchRecoveryRules.supportsInternationalLayout(
                                foldScreen, tablet, landscape, drawerOrStandard,
                            )
                        } else {
                            LauncherSearchRecoveryRules.supportsTabletLayout(
                                tablet = tablet,
                                landscape = landscape,
                                drawerOrStandard = drawerOrStandard,
                            )
                        }
                        if (supported) param.result = true
                    }
                })
            }
            for (name in listOf("onCreate", "onResume", "onConfigurationChanged", "onIdpChanged", "finishBindingItems")) {
                handles += ModernHookBridge.hookAllMethods(launcher, name, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null && repairEnabled()) {
                            (param.thisObject as? Activity)?.let(recovery::schedule)
                        }
                    }
                })
            }
            for (name in if (international) listOf("onPause", "onDestroy") else listOf("onDestroy")) {
                handles += ModernHookBridge.hookAllMethods(launcher, name, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? Activity)?.let(recovery::cancel)
                    }
                })
            }
            HookLog.i(TAG, "Search compatibility installed after application startup: ${handles.size} methods, tablet=$tablet")
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            HookLog.w(TAG, "Failed to install search compatibility; partial hooks removed", error)
        }
    }

    private fun isRepairEnabled(selectedClass: Class<*>): Boolean {
        val mode = LspConfig.getLauncherSearchBarModeXposed()
        val selectedMode = if (selectedClass.name == EXPORT_SEARCH) {
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL
        } else {
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA
        }
        return mode == selectedMode && LauncherSearchRecoveryRules.isEnabled(
            mode, LspConfig.isLauncherSearchCompatibilityEnabledXposed(),
        )
    }

    private class RecoveryController(
        private val screen: Class<*>,
        private val impl: Class<*>,
        private val manager: Class<*>,
        private val container: Class<*>,
        private val selectedClass: Class<*>,
        private val implementationField: Field,
        private val foldScreen: Boolean,
        private val repairEnabled: () -> Boolean,
        private val normalState: Any?,
        private val hotseatElements: Int,
    ) {
        private val international = selectedClass.name == EXPORT_SEARCH
        private val handler = Handler(Looper.getMainLooper())
        private val pending = WeakHashMap<Activity, RecoveryTask>()

        fun schedule(launcher: Activity) {
            if (launcher.isDestroyed || launcher.isFinishing) return
            cancel(launcher)
            RecoveryTask(launcher).also { task ->
                pending[launcher] = task
                handler.postDelayed(task, CHECK_INTERVAL_MS)
            }
        }

        fun cancel(launcher: Activity) {
            pending.remove(launcher)?.let(handler::removeCallbacks)
        }

        fun onPageTransition(launcher: Activity) {
            cancel(launcher)
            // Release our settled-page visibility override before native alpha animation starts.
            synchronizeCurrentView(launcher, transitioning = true)
        }

        fun onPageSettled(launcher: Activity) {
            synchronizeCurrentView(launcher, transitioning = false)
            schedule(launcher)
        }

        private fun readPage(launcher: Activity): LauncherSearchPage? {
            val stateManager = ModernReflect.callMethod(launcher, "getStateManager") ?: return null
            val state = ModernReflect.callMethod(stateManager, "getState") ?: return null
            return LauncherSearchPage(
                resumed = ModernReflect.callMethod(launcher, "isResumed") as? Boolean ?: return null,
                transitioning = ModernReflect.callMethod(stateManager, "isInTransition") as? Boolean ?: return null,
                normal = state === normalState,
                hotseatVisible = ModernReflect.callMethod(state, "areElementsVisible", launcher, hotseatElements)
                    as? Boolean ?: return null,
            )
        }

        private fun canUseSearch(launcher: Activity, implementation: Any): Boolean =
            LauncherSearchRecoveryRules.canRestore(
                nativeSwitchEnabled = ModernReflect.callStaticMethod(manager, "r", launcher) == true,
                providerSupported = ModernReflect.callMethod(implementation, "f", launcher) == true,
                layoutSupported = ModernReflect.callStaticMethod(impl, "n", launcher) == true,
            )

        private fun hide(view: View) {
            view.alpha = 0f
            view.visibility = View.INVISIBLE
        }

        private fun synchronizeView(launcher: Activity, view: View, page: LauncherSearchPage?) {
            if (page == null || page.hideBoundView) {
                hide(view)
                return
            }
            val hotseat = ModernReflect.callMethod(launcher, "getHotseat") as? View
            if (hotseat == null) {
                hide(view)
                return
            }
            // The OEM already propagates these properties from Hotseat while animating.
            // Only fill the missing initial/final snapshot for a newly bound or restored view.
            view.translationX = hotseat.translationX
            view.translationY = hotseat.translationY
            view.scaleX = hotseat.scaleX
            view.scaleY = hotseat.scaleY
            view.alpha = hotseat.alpha
            view.visibility = hotseat.visibility
        }

        private fun synchronizeCurrentView(launcher: Activity, transitioning: Boolean) {
            runCatching {
                if (!international || !repairEnabled() || launcher.isDestroyed) return
                val layer = ModernReflect.callMethod(launcher, "getDragLayer") as? ViewGroup ?: return
                val implementation = implementationField.get(null) ?: return
                if (!selectedClass.isInstance(implementation)) return
                val view = currentView(implementation, layer) ?: return
                synchronizeView(launcher, view, readPage(launcher)?.copy(transitioning = transitioning))
            }.onFailure { HookLog.w(TAG, "Failed to synchronize bottom search page state", it) }
        }

        // Detach only search containers belonging to this layer. Keep widget IDs and preferences.
        private fun clearObsoleteViews(implementation: Any, layer: ViewGroup, keep: View? = null) {
            val referenced = ModernReflect.callMethod(implementation, "l") as? View
            for (index in layer.childCount - 1 downTo 0) {
                val child = layer.getChildAt(index)
                if (child !== keep && container.isInstance(child)) {
                    hide(child)
                    layer.removeView(child)
                }
            }
            if (referenced != null && referenced !== keep && referenced.parent == null) {
                ModernReflect.callMethod(implementation, "p", null)
            }
        }

        fun onBindingComplete(launcher: Activity) {
            runCatching {
                if (!international || !repairEnabled()) return
                val layer = ModernReflect.callMethod(launcher, "getDragLayer") as? ViewGroup ?: return
                val implementation = implementationField.get(null) ?: return
                if (!selectedClass.isInstance(implementation)) return
                val view = currentView(implementation, layer) ?: return
                if (launcher.isDestroyed || launcher.isFinishing || !canUseSearch(launcher, implementation)) {
                    clearObsoleteViews(implementation, layer)
                    return
                }
                clearObsoleteViews(implementation, layer, keep = view)
                val page = readPage(launcher)
                // An asynchronous bind can finish after a fold, page switch, pause or destruction.
                // Do not let an obsolete bind produce a visible, clickable frame in the drawer.
                // Adding the widget requests layout. It can already follow the live Hotseat
                // animation while recovery waits for measured bounds before resizing it.
                if (readLayout(launcher, layer, requireLayoutComplete = false) == null) hide(view)
                else synchronizeView(launcher, view, page)
                if (page?.resumed == true) schedule(launcher)
            }.onFailure { HookLog.w(TAG, "Failed to reconcile completed bottom search binding", it) }
        }

        private fun readLayout(
            launcher: Activity,
            layer: ViewGroup,
            requireLayoutComplete: Boolean = true,
        ): LauncherSearchLayout? {
            if (!layer.isAttachedToWindow || (requireLayoutComplete && layer.isLayoutRequested)) return null
            if (ModernReflect.callMethod(launcher, "isResumed") != true) return null
            if (foldScreen) {
                if (ModernReflect.callStaticMethod(screen, "isChangingFoldState") == true) return null
                if (ModernReflect.callStaticMethod(screen, "isDragLayerUsingLargeScreenSize", launcher) == true ||
                    ModernReflect.callStaticMethod(screen, "isDragLayerUsingSmallScreenSize", launcher) == true
                ) return null
            }
            val profile = ModernReflect.callMethod(launcher, "getDeviceProfile") ?: return null
            val config = ModernReflect.callMethod(profile, "config") ?: return null
            val folded = foldScreen && ModernReflect.callStaticMethod(screen, "isFoldScreenFolded") == true
            if (foldScreen && !folded &&
                ModernReflect.callStaticMethod(screen, "isFoldScreenExpanded") != true
            ) return null
            return LauncherSearchLayout(
                folded, layer.width, layer.height,
                System.identityHashCode(layer), System.identityHashCode(profile),
                landscape = ModernReflect.getObjectField(config, "e") == true,
            )
        }

        private fun currentView(implementation: Any, layer: ViewGroup): View? {
            val view = ModernReflect.callMethod(implementation, "l") as? View ?: return null
            return view.takeIf { container.isInstance(it) && it.parent === layer }
        }

        private fun resize(launcher: Activity, implementation: Any, view: View) {
            ModernReflect.callStaticMethod(manager, "v", launcher)
            val height = ModernReflect.callMethod(implementation, "d", launcher) as? Int ?: return
            val params = view.layoutParams ?: return
            if (height > 0 && params.height != height) {
                params.height = height
                view.layoutParams = params
            }
        }

        private inner class RecoveryTask(launcher: Activity) : Runnable {
            private val owner = WeakReference(launcher)
            private val stability = LauncherSearchRecovery()
            private var checks = 0
            private var requestedLayout: LauncherSearchLayout? = null

            override fun run() {
                val launcher = owner.get() ?: return
                if (pending[launcher] !== this) return
                if (launcher.isDestroyed || launcher.isFinishing) {
                    cancel(launcher)
                    return
                }
                checks++
                val finished = runCatching { restore(launcher) }
                    .onFailure { HookLog.w(TAG, "Search compatibility recovery failed", it) }
                    .getOrDefault(true)
                // Binding may complete synchronously and replace this task from its callback.
                if (pending[launcher] !== this) return
                if (finished || checks >= MAX_CHECKS) {
                    pending.remove(launcher)
                    if (!finished) HookLog.d(TAG, "Search recovery deferred, layout or widget is not ready")
                } else {
                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }

            private fun restore(launcher: Activity): Boolean {
                if (!repairEnabled()) return true
                val layer = ModernReflect.callMethod(launcher, "getDragLayer") as? ViewGroup
                val layout = layer?.let { readLayout(launcher, it) }
                if (!stability.isStable(layout)) return false
                checkNotNull(layer)
                checkNotNull(layout)
                val implementation = implementationField.get(null) ?: return true
                if (!selectedClass.isInstance(implementation)) return true
                if (!canUseSearch(launcher, implementation)) {
                    if (international) clearObsoleteViews(implementation, layer)
                    return true
                }
                val view = currentView(implementation, layer)
                val page = if (international) readPage(launcher) else null
                if (international) {
                    clearObsoleteViews(implementation, layer, keep = view)
                    if (page == null || page.transitioning) {
                        stability.isStable(null)
                        return false
                    }
                    if (!page.canRebind) {
                        if (view != null) synchronizeView(launcher, view, page)
                        return true
                    }
                }
                if (view != null) {
                    resize(launcher, implementation, view)
                    if (international) synchronizeView(launcher, view, page)
                    return true
                }
                if (requestedLayout != layout) {
                    val oldView = ModernReflect.callMethod(implementation, "l") as? View
                    if (oldView != null) {
                        (oldView.parent as? ViewGroup)?.removeView(oldView)
                        ModernReflect.callMethod(implementation, "p", null)
                    }
                    if (selectedClass.name == EXPORT_SEARCH) {
                        ModernReflect.callMethod(implementation, "h", launcher)
                    }
                    requestedLayout = layout
                    ModernReflect.callMethod(implementation, "o", launcher, false)
                    HookLog.d(TAG, "Rebinding bottom search, folded=${layout.folded}, size=${layout.width}x${layout.height}")
                }
                return false
            }
        }
    }
}
