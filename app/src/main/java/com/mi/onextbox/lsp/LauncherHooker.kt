package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** ColorOS launcher-only hooks. */
object LauncherHooker {
    private const val TAG = "ONextBox-Launcher"
    private const val LAUNCHER_CARD_VIEW_CLASS = "com.android.launcher3.card.LauncherCardView"
    private const val TITLE_CARD_VIEW_CLASS = "com.android.launcher3.card.TitleCardView"
    private const val GROUP_CARD_VIEW_CLASS =
        "com.android.launcher3.card.groupcard.GroupCardView"
    private const val BUBBLE_TEXT_VIEW_CLASS = "com.android.launcher3.BubbleTextView"
    private const val OPLUS_BUBBLE_TEXT_VIEW_CLASS = "com.android.launcher3.OplusBubbleTextView"
    private const val APP_WIDGET_HOST_VIEW_CLASS = "com.android.launcher3.widget.LauncherAppWidgetHostView"
    private const val CUSTOM_APP_WIDGET_HOST_VIEW_CLASS = "com.android.launcher3.widget.CustomLauncherAppWidgetHostView"
    private const val EXPORT_BOTTOM_SEARCH_CLASS =
        "com.android.launcher.bottomsearch.ExportBottomSearch"
    private const val DOMESTIC_BOTTOM_SEARCH_CLASS =
        "com.android.launcher.bottomsearch.DomesticBottomSearch"
    private const val APP_FEATURE_UTILS_CLASS = "com.android.common.util.AppFeatureUtils"
    private const val FEATURE_OPTION_CLASS = "com.android.common.config.FeatureOption"
    private const val LAUNCHER_SETTINGS_FRAGMENT_CLASS =
        "com.android.launcher.settings.LauncherSettingsFragment"
    private const val SETTINGS_COMPAT_RUNNABLE_CLASS = "com.android.launcher.c0"
    private const val TASKBAR_UTILS_CLASS = "com.android.launcher3.taskbar.TaskbarUtils"

    private val installedHookKeys = ConcurrentHashMap.newKeySet<String>()
    private val guardedCardLabels = Collections.synchronizedMap(WeakHashMap<View, View.OnLayoutChangeListener>())
    private val titleOwnerClasses = ConcurrentHashMap<Class<*>, Boolean>()

    fun hook(classLoader: ClassLoader?, searchBarOnly: Boolean = false) {
        if (searchBarOnly) {
            val searchBarMode = LspConfig.getLauncherSearchBarModeXposed()
            if (searchBarMode != LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF) {
                hookSearchBarMode(classLoader, searchBarMode)
            }
            return
        }
        HotReloadLifecycle.register("launcher:widget-labels",
            capture = { synchronized(guardedCardLabels) { guardedCardLabels.keys.toTypedArray() } },
            detach = {
                synchronized(guardedCardLabels) {
                    guardedCardLabels.forEach { (view, listener) -> view.removeOnLayoutChangeListener(listener) }
                    guardedCardLabels.clear()
                }
            },
            restore = { saved ->
                if (LspConfig.isLauncherHideWidgetLabelsEnabledXposed()) {
                    (saved as? Array<*>)?.filterIsInstance<View>()?.forEach(::keepCardLabelHidden)
                }
            })
        val hideWidgetLabels = LspConfig.isLauncherHideWidgetLabelsEnabledXposed()
        val searchBarMode = LspConfig.getLauncherSearchBarModeXposed()
        if (!hideWidgetLabels && searchBarMode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF) return

        if (hideWidgetLabels) {
            var hookCount = 0
            hookCount += hookCardArgumentMethod(
                className = LAUNCHER_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "hideOrShowItemTitle",
                argument = true,
            )
            hookCount += hookCardArgumentMethod(
                className = LAUNCHER_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "notifyTextVisible",
                argument = false,
            )
            hookCount += hookCardArgumentMethod(
                className = LAUNCHER_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "setTextVisible",
                argument = false,
            )
            hookCount += hookCardArgumentMethod(
                className = LAUNCHER_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "setTextAlpha",
                argument = 0f,
            )
            hookCount += hookCardClass(
                className = TITLE_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodNames = listOf(
                    "onAttachedToWindow",
                    "handleGetViewCallback",
                    "setCardName",
                    "setCardNameAndUpdateDb",
                    "setCardModel",
                    "setTag",
                ),
            )
            hookCount += hookCardArgumentMethod(
                className = TITLE_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "setTextVisible",
                argument = false,
            )
            hookCount += hookCardArgumentMethod(
                className = TITLE_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "setTextAlpha",
                argument = 0f,
            )
            hookCount += hookCardClass(
                className = GROUP_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodNames = listOf(
                    "onAttachedToWindow",
                    "handleGetViewCallback",
                    "setCardName",
                    "setCardNameAndUpdateDb",
                    "setCardModel",
                    "setTag",
                ),
            )
            hookCount += hookCardArgumentMethod(
                className = GROUP_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "setTextVisible",
                argument = false,
            )
            hookCount += hookCardArgumentMethod(
                className = GROUP_CARD_VIEW_CLASS,
                classLoader = classLoader,
                methodName = "setTextAlpha",
                argument = 0f,
            )
            hookCount += hookNativeWidgetLabels(classLoader)
            hookCount += hookWidgetLabelMethods(
                classLoader, BUBBLE_TEXT_VIEW_CLASS,
                listOf("setTextVisibility" to false, "setTextAlpha" to 0f),
            )
            hookCount += hookWidgetLabelMethods(
                classLoader, OPLUS_BUBBLE_TEXT_VIEW_CLASS,
                listOf("setVisibility" to View.GONE, "setAlpha" to 0f, "setAlphaInner" to 0f),
            )

            HookLog.i(TAG, "Widget label hook installed: $hookCount methods")
        }

        if (searchBarMode != LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF) {
            hookSearchBarMode(classLoader, searchBarMode)
        }
    }

    private fun hookSearchBarMode(classLoader: ClassLoader?, mode: Int) {
        val targets = LauncherSearchTargets.forVersion(SystemAdaptationDetector.current())
        val managerClass = XposedHelpers.findClassIfExists(
            targets.manager,
            classLoader,
        )
        val implementationClass = XposedHelpers.findClassIfExists(
            targets.implementation,
            classLoader,
        )
        val selectedImplementationName = when (mode) {
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA -> DOMESTIC_BOTTOM_SEARCH_CLASS
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL -> EXPORT_BOTTOM_SEARCH_CLASS
            else -> return
        }
        val selectedImplementationClass = XposedHelpers.findClassIfExists(
            selectedImplementationName,
            classLoader,
        )
        if (managerClass == null ||
            implementationClass == null ||
            selectedImplementationClass == null
        ) {
            HookLog.w(TAG, "Search-bar implementation is unavailable: ${targets.manager}")
            return
        }

        val implementationField = targets.resolveProxyField(managerClass, implementationClass) ?: run {
            HookLog.w(TAG, "Bottom-search manager implementation field is unavailable: ${targets.manager}")
            return
        }
        implementationField.isAccessible = true

        val selectImplementation = {
            val current = implementationField.get(null)
            if (!selectedImplementationClass.isInstance(current)) {
                implementationField.set(
                    null,
                    XposedHelpers.newInstance(selectedImplementationClass),
                )
                HookLog.i(
                    TAG,
                    "Selected search-bar implementation (${if (targets.namedApi) "C16" else "C17"}): " +
                        selectedImplementationClass.simpleName,
                )
            }
        }

        // The injector can run before PackageReady on some launcher builds. Apply the selection
        // immediately as well as after the injector so switching CN/Global never leaves the
        // implementation chosen from the firmware region.
        runCatching(selectImplementation)
            .onFailure { error ->
                HookLog.w(TAG, "Failed to select the bottom-search implementation", error)
            }

        var hookCount = 0
        val injectorKey = "${targets.injector}#${targets.injectorMethod}"
        if (installedHookKeys.add(injectorKey)) {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    targets.injector,
                    classLoader,
                    targets.injectorMethod,
                    Context::class.java,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            selectImplementation()
                        }
                    },
                )
            }.onSuccess {
                hookCount++
            }.onFailure { error ->
                installedHookKeys.remove(injectorKey)
                HookLog.w(TAG, "Failed to replace the bottom-search implementation", error)
            }
        }

        hookCount += hookSettingsEntries(classLoader, mode, targets)
        if (mode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA) {
            // DomesticBottomSearch contains two product gates in addition to the user's native
            // switch: a launcher feature flag and an A/K-series allowlist. Treat the selected
            // China implementation as supported, while leaving its native enable switch and
            // browser-widget checks intact so this does not force the bar to stay visible.
            hookCount += forceBooleanResult(
                className = APP_FEATURE_UTILS_CLASS,
                methodName = "isBottomSearchBoxEnable",
                classLoader = classLoader,
            )
            hookCount += forceBooleanResult(
                className = targets.domesticSupport,
                methodName = targets.domesticSupportMethod,
                classLoader = classLoader,
            )
        } else if (mode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL) {
            hookCount += forceBooleanResult(
                className = FEATURE_OPTION_CLASS,
                methodName = "getHasQsbAtBottomFeature",
                classLoader = classLoader,
            )
            hookCount += hookInternationalSwitchState(classLoader, targets)
            hookCount += hookInternationalSwitchRefresh(
                classLoader = classLoader,
                implementationField = implementationField,
                targets = targets,
            )
        }
        if (!targets.namedApi && LspConfig.isLauncherSearchCompatibilityEnabledXposed()) {
            hookCount += LauncherSearchDeviceCompat.install(
                classLoader,
                selectedImplementationClass,
                implementationField,
            )
        }
        HookLog.i(TAG, "${if (targets.namedApi) "C16" else "C17"} search-bar entry mode $mode installed: $hookCount methods")
    }

    private fun hookSettingsEntries(classLoader: ClassLoader?, mode: Int, targets: LauncherSearchTargets): Int {
        val settingsClass = XposedHelpers.findClassIfExists(
            LAUNCHER_SETTINGS_FRAGMENT_CLASS,
            classLoader,
        ) ?: run {
            HookLog.w(TAG, "Launcher settings fragment is unavailable")
            return 0
        }
        var count = listOf("initPreferences", "onResume").sumOf { methodName ->
            val key = "$LAUNCHER_SETTINGS_FRAGMENT_CLASS#$methodName#searchBarEntry"
            if (!installedHookKeys.add(key)) return@sumOf 0
            val handles = runCatching {
                XposedBridge.hookAllMethods(
                    settingsClass,
                    methodName,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            applySettingsEntryMode(param.thisObject, mode, targets)
                        }
                    },
                )
            }.onFailure { error ->
                installedHookKeys.remove(key)
                HookLog.w(TAG, "Failed to hook $key", error)
            }.getOrDefault(emptySet())
            if (handles.isEmpty()) installedHookKeys.remove(key)
            handles.size
        }

        val runnableClass = XposedHelpers.findClassIfExists(
            if (targets.namedApi) "$LAUNCHER_SETTINGS_FRAGMENT_CLASS\$mCompatStrategyChangeListener\$1"
            else SETTINGS_COMPAT_RUNNABLE_CLASS,
            classLoader,
        )
        if (runnableClass != null) {
            val callbackName = if (targets.namedApi) "onCompatStrategyChanged" else "run"
            val key = "${runnableClass.name}#$callbackName#searchBarEntry"
            if (installedHookKeys.add(key)) {
                val handles = runCatching {
                    XposedBridge.hookAllMethods(
                        runnableClass,
                        callbackName,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val fragment = param.thisObject?.javaClass?.declaredFields
                                    ?.asSequence()
                                    ?.mapNotNull { field ->
                                        runCatching {
                                            field.isAccessible = true
                                            field.get(param.thisObject)
                                        }.getOrNull()
                                    }
                                    ?.firstOrNull {
                                        settingsClass.isInstance(it)
                                    }
                                if (fragment != null) applySettingsEntryMode(fragment, mode, targets)
                            }
                        },
                    )
                }.onFailure { error ->
                    installedHookKeys.remove(key)
                    HookLog.w(TAG, "Failed to hook $key", error)
                }.getOrDefault(emptySet())
                if (handles.isEmpty()) installedHookKeys.remove(key)
                count += handles.size
            }
        }
        return count
    }

    private fun applySettingsEntryMode(fragment: Any?, mode: Int, targets: LauncherSearchTargets) {
        if (fragment == null) return
        val category = runCatching {
            XposedHelpers.getObjectField(fragment, "mLauncherCategory")
        }.getOrNull() ?: return
        val domesticEntry = runCatching {
            XposedHelpers.getObjectField(fragment, "mOpenBottomSearchBoxEntry")
        }.getOrNull()
        val internationalEntry = runCatching {
            XposedHelpers.getObjectField(fragment, "mDockSearchBoxForQSB")
        }.getOrNull()

        listOfNotNull(domesticEntry, internationalEntry).forEach { entry ->
            runCatching { XposedHelpers.callMethod(category, targets.removePreference, entry) }
        }
        val selectedEntry = when (mode) {
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA -> domesticEntry
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL -> internationalEntry
            else -> null
        } ?: return
        runCatching { XposedHelpers.callMethod(selectedEntry, "setEnabled", true) }
        runCatching { XposedHelpers.callMethod(category, targets.addPreference, selectedEntry) }
            .onFailure { error ->
                HookLog.w(TAG, "Failed to expose the selected launcher search entry", error)
            }
    }

    private fun hookInternationalSwitchState(classLoader: ClassLoader?, targets: LauncherSearchTargets): Int {
        val managerClass = XposedHelpers.findClassIfExists(
            targets.manager,
            classLoader,
        ) ?: return 0
        val utilsClass = XposedHelpers.findClassIfExists(
            targets.utils,
            classLoader,
        ) ?: return 0
        val key = "${targets.manager}#${targets.enabledMethod}#actualSwitch"
        if (!installedHookKeys.add(key)) return 0
        val handles = runCatching {
            XposedBridge.hookAllMethods(
                managerClass,
                targets.enabledMethod,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() ?: return
                        param.result = XposedHelpers.callStaticMethod(utilsClass, targets.switchOnMethod, context)
                    }
                },
            )
        }.onFailure { error ->
            installedHookKeys.remove(key)
            HookLog.w(TAG, "Failed to hook $key", error)
        }.getOrDefault(emptySet())
        if (handles.isEmpty()) installedHookKeys.remove(key)
        return handles.size
    }

    private fun hookInternationalSwitchRefresh(
        classLoader: ClassLoader?,
        implementationField: java.lang.reflect.Field,
        targets: LauncherSearchTargets,
    ): Int {
        val utilsClass = XposedHelpers.findClassIfExists(
            targets.utils,
            classLoader,
        ) ?: return 0
        val taskbarUtilsClass = XposedHelpers.findClassIfExists(
            TASKBAR_UTILS_CLASS,
            classLoader,
        ) ?: return 0
        val key = "${targets.utils}#${targets.saveSwitchMethod}#internationalRefresh"
        if (!installedHookKeys.add(key)) return 0
        val handles = runCatching {
            XposedBridge.hookAllMethods(
                utilsClass,
                targets.saveSwitchMethod,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args.size != 3 || param.args[2] != true) return
                        val launcher = XposedHelpers.callStaticMethod(
                            taskbarUtilsClass,
                            "getOplusLauncher",
                        ) ?: return
                        val implementation = implementationField.get(null) ?: return
                        XposedHelpers.callMethod(implementation, targets.refreshMethod, launcher, false)
                    }
                },
            )
        }.onFailure { error ->
            installedHookKeys.remove(key)
            HookLog.w(TAG, "Failed to hook $key", error)
        }.getOrDefault(emptySet())
        if (handles.isEmpty()) installedHookKeys.remove(key)
        return handles.size
    }

    private fun forceBooleanResult(
        className: String,
        methodName: String,
        classLoader: ClassLoader?,
    ): Int {
        val targetClass = XposedHelpers.findClassIfExists(className, classLoader) ?: run {
            HookLog.w(TAG, "Taskbar search target missing: $className")
            return 0
        }
        val key = "$className#$methodName"
        if (!installedHookKeys.add(key)) return 0

        val handles = runCatching {
            XposedBridge.hookAllMethods(
                targetClass,
                methodName,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                },
            )
        }.onFailure { error ->
            installedHookKeys.remove(key)
            HookLog.w(TAG, "Failed to hook $key", error)
        }.getOrDefault(emptySet())

        if (handles.isEmpty()) installedHookKeys.remove(key)
        return handles.size
    }

    private fun hookCardClass(
        className: String,
        classLoader: ClassLoader?,
        methodNames: List<String>,
    ): Int {
        val targetClass = XposedHelpers.findClassIfExists(className, classLoader) ?: run {
            HookLog.w(TAG, "Widget label target missing: $className")
            return 0
        }

        return methodNames.sumOf { methodName ->
            val key = "$className#$methodName"
            if (!installedHookKeys.add(key)) return@sumOf 0

            val handles = runCatching {
                XposedBridge.hookAllMethods(
                    targetClass,
                    methodName,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            hideCardLabel(param.thisObject)
                        }
                    },
                )
            }.onFailure { error ->
                installedHookKeys.remove(key)
                HookLog.w(TAG, "Failed to hook $key", error)
            }.getOrDefault(emptySet())

            if (handles.isEmpty()) {
                installedHookKeys.remove(key)
            }
            handles.size
        }
    }

    private fun hookCardArgumentMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        argument: Any,
    ): Int {
        val targetClass = XposedHelpers.findClassIfExists(className, classLoader) ?: run {
            HookLog.w(TAG, "Widget label target missing: $className")
            return 0
        }
        val key = "$className#$methodName#hideWidgetLabel"
        if (!installedHookKeys.add(key)) return 0

        val handles = runCatching {
            XposedBridge.hookAllMethods(
                targetClass,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args.isNotEmpty()) param.args[0] = argument
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        hideCardLabel(param.thisObject)
                    }
                },
            )
        }.onFailure { error ->
            installedHookKeys.remove(key)
            HookLog.w(TAG, "Failed to hook $key", error)
        }.getOrDefault(emptySet())

        if (handles.isEmpty()) installedHookKeys.remove(key)
        return handles.size
    }

    private fun hookNativeWidgetLabels(classLoader: ClassLoader?): Int {
        var count = hookCardClass(
            className = APP_WIDGET_HOST_VIEW_CLASS,
            classLoader = classLoader,
            methodNames = listOf("onAttachedToWindow", "setAppWidget", "endDrag"),
        )
        for ((methodName, argument) in listOf(
            "hideOrShowItemTitle" to true,
            "setSelfTitleVisible" to false,
            "setTitleVisible" to false,
        )) {
            count += hookCardArgumentMethod(APP_WIDGET_HOST_VIEW_CLASS, classLoader, methodName, argument)
        }
        count += hookCardArgumentMethod(
            LauncherWidgetLabelRules.BASE_WIDGET_HOST, classLoader, "titleFadeIn", 0f,
        )
        count += hookCardArgumentMethod(
            CUSTOM_APP_WIDGET_HOST_VIEW_CLASS, classLoader, "notifyTextVisible", false,
        )
        count += hookCardClass(
            className = CUSTOM_APP_WIDGET_HOST_VIEW_CLASS,
            classLoader = classLoader,
            methodNames = listOf("updateTitleText"),
        )
        return count
    }

    private fun hookWidgetLabelMethods(
        classLoader: ClassLoader?,
        className: String,
        methods: List<Pair<String, Any>>,
    ): Int {
        val labelClass = XposedHelpers.findClassIfExists(className, classLoader)
            ?: run {
                HookLog.w(TAG, "Widget label target missing: $className")
                return 0
            }
        return methods.sumOf { (methodName, hiddenValue) ->
            val key = "$className#$methodName#widgetLabelOnly"
            if (!installedHookKeys.add(key)) return@sumOf 0
            val handles = runCatching {
                XposedBridge.hookAllMethods(
                    labelClass,
                    methodName,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val label = param.thisObject as? View ?: return
                            if (!isCardLabel(label)) return
                            if (param.args.isNotEmpty()) param.args[0] = hiddenValue
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            val label = param.thisObject as? View ?: return
                            if (isCardLabel(label)) keepCardLabelHidden(label)
                        }
                    },
                )
            }.onFailure { error ->
                installedHookKeys.remove(key)
                HookLog.w(TAG, "Failed to hook $key", error)
            }.getOrDefault(emptySet())

            if (handles.isEmpty()) installedHookKeys.remove(key)
            handles.size
        }
    }

    private fun hideCardLabel(cardView: Any?) {
        if (cardView == null) return
        sequenceOf("getLauncherCardName", "getSelfTitle")
            .mapNotNull { methodName ->
                runCatching {
                    XposedHelpers.callMethod(cardView, methodName) as? View
                }.getOrNull()
            }
            .distinct()
            .forEach(::keepCardLabelHidden)
    }

    private fun isCardLabel(label: View): Boolean {
        if (guardedCardLabels.containsKey(label)) return true
        var ancestor = label.parent
        while (ancestor is View) {
            val isTitleOwner = titleOwnerClasses.computeIfAbsent(ancestor.javaClass) { targetClass ->
                val classNames = generateSequence<Class<*>>(targetClass) { it.superclass }.map { it.name }
                LauncherWidgetLabelRules.isTitleOwner(classNames)
            }
            if (isTitleOwner) {
                val ownsLabel = sequenceOf("getLauncherCardName", "getSelfTitle").any { methodName ->
                    runCatching {
                        XposedHelpers.callMethod(ancestor, methodName) === label
                    }.getOrDefault(false)
                }
                if (ownsLabel) return true
            }
            ancestor = ancestor.parent
        }
        return false
    }

    private fun keepCardLabelHidden(label: View) {
        if (HotReloadLifecycle.isRetiring) return
        if (label.visibility != View.GONE) label.visibility = View.GONE
        if (label.alpha != 0f) label.alpha = 0f

        // Keep widget titles hidden after dragging or editing.
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (HotReloadLifecycle.isRetiring) return@OnLayoutChangeListener
            if (view.visibility != View.GONE) view.visibility = View.GONE
            if (view.alpha != 0f) view.alpha = 0f
        }
        synchronized(guardedCardLabels) {
            if (guardedCardLabels.containsKey(label)) return
            guardedCardLabels[label] = listener
        }
        label.addOnLayoutChangeListener(listener)
    }
}
