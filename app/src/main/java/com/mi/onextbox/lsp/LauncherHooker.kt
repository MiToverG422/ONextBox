package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.lang.reflect.Modifier
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
    private const val BOTTOM_SEARCH_INJECTOR_CLASS = "com.android.launcher.bottomsearch.j"
    private const val BOTTOM_SEARCH_MANAGER_CLASS = "com.android.launcher.bottomsearch.i"
    private const val BOTTOM_SEARCH_IMPL_CLASS = "com.android.launcher.bottomsearch.e"
    private const val EXPORT_BOTTOM_SEARCH_CLASS =
        "com.android.launcher.bottomsearch.ExportBottomSearch"
    private const val DOMESTIC_BOTTOM_SEARCH_CLASS =
        "com.android.launcher.bottomsearch.DomesticBottomSearch"
    private const val DOMESTIC_BOTTOM_SEARCH_SUPPORT_CLASS =
        "com.android.launcher.bottomsearch.DomesticBottomSearch\$a"
    private const val APP_FEATURE_UTILS_CLASS = "com.android.common.util.AppFeatureUtils"
    private const val BOTTOM_SEARCH_UTILS_CLASS = "com.android.launcher.bottomsearch.k"
    private const val FEATURE_OPTION_CLASS = "com.android.common.config.FeatureOption"
    private const val LAUNCHER_SETTINGS_FRAGMENT_CLASS =
        "com.android.launcher.settings.LauncherSettingsFragment"
    private const val SETTINGS_COMPAT_RUNNABLE_CLASS = "com.android.launcher.c0"
    private const val TASKBAR_UTILS_CLASS = "com.android.launcher3.taskbar.TaskbarUtils"

    private val installedHookKeys = ConcurrentHashMap.newKeySet<String>()
    private val guardedCardLabels = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    fun hook(classLoader: ClassLoader?) {
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
            hookCount += hookCardLabelTextMethods(classLoader)

            HookLog.i(TAG, "Widget label hook installed: $hookCount methods")
        }

        if (searchBarMode != LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF) {
            hookSearchBarMode(classLoader, searchBarMode)
        }
    }

    private fun hookSearchBarMode(classLoader: ClassLoader?, mode: Int) {
        val managerClass = XposedHelpers.findClassIfExists(
            BOTTOM_SEARCH_MANAGER_CLASS,
            classLoader,
        )
        val implementationClass = XposedHelpers.findClassIfExists(
            BOTTOM_SEARCH_IMPL_CLASS,
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
            HookLog.w(TAG, "C17 search-bar implementation is unavailable")
            return
        }

        val implementationField = managerClass.declaredFields.firstOrNull { field ->
            Modifier.isStatic(field.modifiers) && implementationClass.isAssignableFrom(field.type)
        } ?: run {
            HookLog.w(TAG, "C17 bottom-search manager implementation field is unavailable")
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
                    "Selected C17 search-bar implementation: " +
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
        val injectorKey = "$BOTTOM_SEARCH_INJECTOR_CLASS#a"
        if (installedHookKeys.add(injectorKey)) {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    BOTTOM_SEARCH_INJECTOR_CLASS,
                    classLoader,
                    "a",
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

        hookCount += hookSettingsEntries(classLoader, mode)
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
                className = DOMESTIC_BOTTOM_SEARCH_SUPPORT_CLASS,
                methodName = "b",
                classLoader = classLoader,
            )
        } else if (mode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL) {
            hookCount += forceBooleanResult(
                className = FEATURE_OPTION_CLASS,
                methodName = "getHasQsbAtBottomFeature",
                classLoader = classLoader,
            )
            hookCount += hookInternationalSwitchState(classLoader)
            hookCount += hookInternationalSwitchRefresh(
                classLoader = classLoader,
                implementationField = implementationField,
            )
        }
        HookLog.i(TAG, "C17 search-bar entry mode $mode installed: $hookCount methods")
    }

    private fun hookSettingsEntries(classLoader: ClassLoader?, mode: Int): Int {
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
                            applySettingsEntryMode(param.thisObject, mode)
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
            SETTINGS_COMPAT_RUNNABLE_CLASS,
            classLoader,
        )
        if (runnableClass != null) {
            val key = "$SETTINGS_COMPAT_RUNNABLE_CLASS#run#searchBarEntry"
            if (installedHookKeys.add(key)) {
                val handles = runCatching {
                    XposedBridge.hookAllMethods(
                        runnableClass,
                        "run",
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
                                if (fragment != null) applySettingsEntryMode(fragment, mode)
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

    private fun applySettingsEntryMode(fragment: Any?, mode: Int) {
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
            runCatching { XposedHelpers.callMethod(category, "f", entry) }
        }
        val selectedEntry = when (mode) {
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA -> domesticEntry
            LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL -> internationalEntry
            else -> null
        } ?: return
        runCatching { XposedHelpers.callMethod(selectedEntry, "setEnabled", true) }
        runCatching { XposedHelpers.callMethod(category, "b", selectedEntry) }
            .onFailure { error ->
                HookLog.w(TAG, "Failed to expose the selected launcher search entry", error)
            }
    }

    private fun hookInternationalSwitchState(classLoader: ClassLoader?): Int {
        val managerClass = XposedHelpers.findClassIfExists(
            BOTTOM_SEARCH_MANAGER_CLASS,
            classLoader,
        ) ?: return 0
        val utilsClass = XposedHelpers.findClassIfExists(
            BOTTOM_SEARCH_UTILS_CLASS,
            classLoader,
        ) ?: return 0
        val key = "$BOTTOM_SEARCH_MANAGER_CLASS#r#actualSwitch"
        if (!installedHookKeys.add(key)) return 0
        val handles = runCatching {
            XposedBridge.hookAllMethods(
                managerClass,
                "r",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() ?: return
                        param.result = XposedHelpers.callStaticMethod(utilsClass, "o", context)
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
    ): Int {
        val utilsClass = XposedHelpers.findClassIfExists(
            BOTTOM_SEARCH_UTILS_CLASS,
            classLoader,
        ) ?: return 0
        val taskbarUtilsClass = XposedHelpers.findClassIfExists(
            TASKBAR_UTILS_CLASS,
            classLoader,
        ) ?: return 0
        val key = "$BOTTOM_SEARCH_UTILS_CLASS#t#internationalRefresh"
        if (!installedHookKeys.add(key)) return 0
        val handles = runCatching {
            XposedBridge.hookAllMethods(
                utilsClass,
                "t",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args.size != 3 || param.args[2] != true) return
                        val launcher = XposedHelpers.callStaticMethod(
                            taskbarUtilsClass,
                            "getOplusLauncher",
                        ) ?: return
                        val implementation = implementationField.get(null) ?: return
                        XposedHelpers.callMethod(implementation, "o", launcher, false)
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

    private fun hookCardLabelTextMethods(classLoader: ClassLoader?): Int {
        val labelClass = XposedHelpers.findClassIfExists(BUBBLE_TEXT_VIEW_CLASS, classLoader)
            ?: run {
                HookLog.w(TAG, "Widget label target missing: $BUBBLE_TEXT_VIEW_CLASS")
                return 0
            }
        return listOf(
            "setTextVisibility" to false,
            "setTextAlpha" to 0f,
        ).sumOf { (methodName, hiddenValue) ->
            val key = "$BUBBLE_TEXT_VIEW_CLASS#$methodName#cardLabelOnly"
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
        var ancestor = label.parent
        while (ancestor is View) {
            if (ancestor.javaClass.name.startsWith("com.android.launcher3.card.")) {
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
        label.visibility = View.GONE
        label.alpha = 0f

        val needsGuard = synchronized(guardedCardLabels) {
            guardedCardLabels.put(label, true) == null
        }
        if (!needsGuard) return

        // Card titles can be restored after a drag/edit transition without going through the
        // initial binding callbacks. Re-apply the hidden state whenever launcher lays it out.
        label.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (view.visibility != View.GONE) view.visibility = View.GONE
            if (view.alpha != 0f) view.alpha = 0f
        }
    }
}
