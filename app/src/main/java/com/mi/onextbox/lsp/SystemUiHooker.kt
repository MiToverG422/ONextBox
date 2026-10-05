package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import io.github.libxposed.api.XposedInterface
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// These hooks execute only inside Android 16+ SystemUI. The module app's minSdk is not the host API.
@SuppressLint("NewApi", "StaticFieldLeak")
object SystemUiHooker {
    private const val TAG = "ONextBox-LSP"
    private const val SYSTEM_UI_RESOURCE_PACKAGE = "com.android.systemui"
    private const val ALWAYS_SHOW_DATA_RAT_ICON_RESOURCE = "always_show_data_rat_icon_bool"
    private const val APP_FEATURE_PROVIDER_CLASS =
        "com.oplus.coreapp.appfeature.AppFeatureProviderUtils"
    private const val ORIGIN_NOTIFICATION_BEHAVIOR_FEATURE =
        "com.android.systemui.origin_notification_behavior"
    private const val FEATURE_OPTION_CLASS =
        "com.oplusos.systemui.common.feature.FeatureOption"
    private const val NOTIFICATION_SORT_EX_IMPL_CLASS =
        "com.oplus.systemui.notification.sort.NotificationSortExImpl"
    private const val THEME_OVERLAY_CONTROLLER_CLASS =
        "com.android.systemui.theme.ThemeOverlayController"
    private const val FRAMEWORK_THEME_STYLE_CLASS = "android.content.theming.ThemeStyle"
    private const val MONET_DYNAMIC_SCHEME_CLASS =
        "com.google.ux.material.libmonet.dynamiccolor.DynamicScheme"
    private const val MONET_SPEC_VERSION_CLASS =
        "com.google.ux.material.libmonet.dynamiccolor.ColorSpec\$SpecVersion"
    private const val OPLUS_NODE_SPEC_BUILDER_EX_IMPL_CLASS =
        "com.oplus.systemui.statusbar.notification.collection.render.OplusNodeSpecBuilderExImpl"
    private const val OS17_MOBILE_BINDING_CLASS =
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder\$Os17Binding"
    private const val LEGACY_OS16_MOBILE_BINDING_CLASS =
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.LegacyOplusStatusBarMobileViewBinder\$Os16Binding"
    private const val BIG_TYPE_MOBILE_BINDER_LEGACY_CLASS =
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.BigTypeStatusBarMobileViewBinderLegacy"
    private const val STACKED_MOBILE_ICON_VIEW_MODEL_CLASS =
        "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelImpl"
    private const val OPLUS_STACKED_MOBILE_ICON_VIEW_MODEL_CLASS =
        "com.oplus.systemui.statusbar.pipeline.ui.viewmodel.OplusStackedMobileIconViewModelImpl"
    private const val OPLUS_MOBILE_BINDING_CLASS =
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.AbstractOplusStatusBarMobileViewBinder\$Binding"
    private const val WIFI_VIEW_BINDER_CLASS =
        "com.android.systemui.statusbar.pipeline.wifi.ui.binder.WifiViewBinder"
    private const val OPLUS_WIFI_SIGNAL_EX_IMPL_CLASS =
        "com.oplus.systemui.statusbar.pipeline.OplusWifiSignalExImpl"
    private const val GLOBAL_ACTIONS_IMPL_CLASS =
        "com.android.systemui.globalactions.GlobalActionsImpl"
    private const val GLOBAL_ACTIONS_DIALOG_LITE_CLASS =
        "com.android.systemui.globalactions.GlobalActionsDialogLite"
    private val NATIVE_POWER_MENU_PROVIDER_HOLDER_CLASSES = arrayOf(
        "com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel\$Factory",
        "com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel",
    )
    private val STATUS_BAR_ICON_LIST_CLASSES = arrayOf(
        "com.android.systemui.statusbar.phone.ui.StatusBarIconList",
        "com.android.systemui.statusbar.phone.StatusBarIconList",
    )
    private const val MOBILE_ROAMING_SPACE_VIEW_ID = 0x7f0a0889
    private const val MOBILE_ROAMING_TEXT_VIEW_ID = 0x7f0a088a
    private const val WIFI_ACTIVITY_CONTAINER_VIEW_ID = 0x7f0a0689
    private const val WIFI_ACTIVITY_IN_VIEW_ID = 0x7f0a10c1
    private const val WIFI_ACTIVITY_COMBINED_VIEW_ID = 0x7f0a10c2
    private const val WIFI_ACTIVITY_OUT_VIEW_ID = 0x7f0a10c8
    private val installedHookKeys = ConcurrentHashMap.newKeySet<String>()
    private val internationalNetworkDisplayApplied = AtomicBoolean(false)
    private val internationalNotificationStyleApplied = AtomicBoolean(false)
    private val tonalSpotStyleApplied = AtomicBoolean(false)
    private val monetColorSpecApplied = AtomicBoolean(false)
    private val mobileRoamingIndicatorHidden = AtomicBoolean(false)
    private val networkActivityIndicatorHidden = AtomicBoolean(false)
    private val nativePowerMenuShown = AtomicBoolean(false)
    private val c16NetworkIconOrderApplied = AtomicBoolean(false)
    private val internationalNotificationStyleScope = ThreadLocal<Int>()
    private val nativePowerMenuLock = Any()
    private val guardedWifiActivityRoots = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    @Volatile private var nativePowerMenuProvider: Any? = null
    @Volatile private var nativePowerMenuDialog: Any? = null
    @Volatile private var observedNativePowerMenuDialog: Any? = null

    fun hook(packageName: String, classLoader: ClassLoader?) {
        LspRuntimeStatus.markSystemUiScopeActive()
        if (LspConfig.isSystemUiInternationalNetworkDisplayEnabledXposed()) {
            hookInternationalNetworkDisplayResource()
        }
        if (LspConfig.isSystemUiHideMobileRoamingIndicatorEnabledXposed()) {
            hookHideMobileRoamingIndicator(classLoader)
        }
        if (LspConfig.isSystemUiHideNetworkActivityIndicatorEnabledXposed()) {
            hookHideNetworkActivityIndicator(classLoader)
        }
        if (LspConfig.isSystemUiNativePowerMenuEnabledXposed()) {
            hookNativePowerMenu(classLoader)
        }
        if (LspConfig.isSystemUiRestoreC16NetworkIconOrderEnabledXposed()) {
            hookRestoreC16NetworkIconOrder(classLoader)
        }
        if (LspConfig.isSystemUiInternationalNotificationStyleEnabledXposed()) {
            hookInternationalNotificationStyle(classLoader)
        }
        if (LspConfig.isSystemUiForceTonalSpotEnabledXposed()) {
            hookForceTonalSpotStyle(classLoader)
        }
        val monetColorSpecMode = LspConfig.getSystemUiMonetColorSpecModeXposed()
        if (monetColorSpecMode != LspConfig.SYSTEMUI_MONET_COLOR_SPEC_OFF) {
            hookForceMonetColorSpec(classLoader, monetColorSpecMode)
        }

        val hideQsEdit = LspConfig.isSystemUiHideQsEditEnabledXposed()
        val hideQsSettings = LspConfig.isSystemUiHideQsSettingsEnabledXposed()
        val hideQsMore = LspConfig.isSystemUiHideQsMoreEnabledXposed()
        if (hideQsEdit || hideQsMore) {
            hookControlCenterMenuVisibility(classLoader)
        }
        if (hideQsEdit || hideQsSettings || hideQsMore) {
            hookControlCenterQuickEntranceVisibility(classLoader)
        }
        if (LspConfig.isSystemUiHideQsTopCarrierEnabledXposed()) {
            hookControlCenterTopCarrier(classLoader)
        }
        if (LspConfig.isSystemUiForceNativeClipboardOverlayEnabledXposed()) {
            hookForceNativeClipboardOverlay(classLoader)
        }
        if (LspConfig.isNativeNotifyIconEnabledXposed()) {
            SystemUiNotificationIconHooker.hook(classLoader, packageName)
        }
        if (
            LspConfig.isAodEnhanceEnabledXposed() ||
            LspConfig.isAodPanoramicSupportEnabledXposed() ||
            LspConfig.isAodSingleClickBlockEnabledXposed()
        ) {
            SystemUiAodHooker.hook(classLoader, packageName)
        }
        log("SystemUI hooked in $packageName")
    }

    /**
     * ColorOS 17 writes VIBRANT for most non-blue theme colors. That style also saturates the
     * framework neutral palette, so AOSP Material pages such as PermissionController end up with
     * a strongly tinted full-screen background. Keep the user's seed color and only force the
     * palette style selected by SystemUI's dynamic-color controller to TONAL_SPOT.
     */
    private fun hookForceTonalSpotStyle(classLoader: ClassLoader?) {
        val controllerClass = XposedHelpers.findClassIfExists(
            THEME_OVERLAY_CONTROLLER_CLASS,
            classLoader,
        ) ?: run {
            log("SystemUI Tonal Spot hook unavailable: ThemeOverlayController missing")
            return
        }
        val tonalSpotValue = runCatching {
            val styleClass = XposedHelpers.findClass(
                FRAMEWORK_THEME_STYLE_CLASS,
                classLoader,
            )
            XposedHelpers.callStaticMethod(styleClass, "valueOf", "TONAL_SPOT") as Int
        }.getOrElse {
            // ColorOS 17's framework ThemeStyle maps TONAL_SPOT to 1.
            1
        }
        val methods = controllerClass.declaredMethods.filter { method ->
            method.name == "fetchThemeStyleFromSetting" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Integer.TYPE
        }
        methods.forEach { method ->
            val key = "systemui:force-tonal-spot:${method.toGenericString()}"
            if (!addHookKeyIfAbsent(key)) return@forEach
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = tonalSpotValue
                    if (tonalSpotStyleApplied.compareAndSet(false, true)) {
                        log("SystemUI dynamic palette style forced to TONAL_SPOT ($tonalSpotValue)")
                    }
                }
            })
        }
        log("SystemUI Tonal Spot palette hooks: ${methods.size}")
    }

    /**
     * ColorOS 17 does not expose a color-spec selector. Its bundled Monet library accepts the
     * specification as DynamicScheme constructor argument 5, then normalizes and stores it.
     * Replacing that argument is the earliest stable point to force SPEC_2021 or SPEC_2025.
     */
    private fun hookForceMonetColorSpec(classLoader: ClassLoader?, mode: Int) {
        val specName = when (mode) {
            LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2025 -> "SPEC_2025"
            LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2021 -> "SPEC_2021"
            else -> return
        }
        val dynamicSchemeClass = XposedHelpers.findClassIfExists(
            MONET_DYNAMIC_SCHEME_CLASS,
            classLoader,
        ) ?: run {
            log("SystemUI Monet spec hook unavailable: DynamicScheme missing")
            return
        }
        val specVersionClass = XposedHelpers.findClassIfExists(
            MONET_SPEC_VERSION_CLASS,
            classLoader,
        ) ?: run {
            log("SystemUI Monet spec hook unavailable: SpecVersion missing")
            return
        }
        val forcedSpec = runCatching {
            specVersionClass.getDeclaredField(specName).apply { isAccessible = true }.get(null)
        }.getOrElse { error ->
            log("SystemUI Monet spec hook unavailable: $specName missing: ${error.message}")
            return
        }
        val constructors = dynamicSchemeClass.declaredConstructors.filter { constructor ->
            constructor.parameterCount == 12 &&
                constructor.parameterTypes.getOrNull(5)?.name == MONET_SPEC_VERSION_CLASS
        }
        constructors.forEach { constructor ->
            val key = "systemui:force-monet-spec:$specName:${constructor.toGenericString()}"
            if (!addHookKeyIfAbsent(key)) return@forEach
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[5] = forcedSpec
                    if (monetColorSpecApplied.compareAndSet(false, true)) {
                        log("SystemUI Monet color specification forced to $specName")
                    }
                }
            })
        }
        log("SystemUI Monet $specName constructor hooks: ${constructors.size}")
    }

    /** Equivalent to setting always_show_data_rat_icon_bool=false in SystemUI resources. */
    private fun hookInternationalNetworkDisplayResource() {
        val methods = Resources::class.java.declaredMethods.filter { method ->
            method.name == "getBoolean" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Integer.TYPE &&
                method.returnType == java.lang.Boolean.TYPE
        }
        methods.forEach { method ->
            ModernHookRegistry.installFast(
                key = "systemui:international-network-display:${method.toGenericString()}",
                executable = method,
                hooker = XposedInterface.Hooker { chain ->
                    val resourceId = chain.getArg(0) as? Int ?: return@Hooker chain.proceed()
                    val resources = chain.thisObject as? Resources ?: return@Hooker chain.proceed()
                    val isTarget = runCatching {
                        resources.getResourcePackageName(resourceId) == SYSTEM_UI_RESOURCE_PACKAGE &&
                            resources.getResourceTypeName(resourceId) == "bool" &&
                            resources.getResourceEntryName(resourceId) ==
                            ALWAYS_SHOW_DATA_RAT_ICON_RESOURCE
                    }.getOrDefault(false)
                    if (!isTarget) return@Hooker chain.proceed()

                    if (internationalNetworkDisplayApplied.compareAndSet(false, true)) {
                        log("SystemUI resource override: $ALWAYS_SHOW_DATA_RAT_ICON_RESOURCE=false")
                    }
                    false
                },
            )
        }
        log("SystemUI international network display hook: ${methods.size}")
    }

    /** Hides only the status-bar roaming presentation while preserving telephony roaming state. */
    private fun hookHideMobileRoamingIndicator(classLoader: ClassLoader?) {
        var hookCount = 0

        // A quick SystemUI restart can inflate and bind the roaming TextView before the module
        // finishes installing the vendor binder hook. Catch the already-created view on its
        // first draw, using the exact SystemUI resource id so unrelated TextViews are untouched.
        TextView::class.java.declaredMethods
            .filter { method ->
                method.name == "onDraw" &&
                    method.parameterTypes.size == 1 &&
                    method.returnType == java.lang.Void.TYPE
            }
            .forEach { method ->
                val key = "systemui:hide-mobile-roaming:text-draw:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? TextView ?: return
                        if (view.id != MOBILE_ROAMING_TEXT_VIEW_ID) return

                        view.visibility = View.GONE
                        view.alpha = 0f
                        runCatching {
                            view.rootView.findViewById<View>(MOBILE_ROAMING_SPACE_VIEW_ID)
                        }.getOrNull()?.let { roamingSpace ->
                            roamingSpace.visibility = View.GONE
                            roamingSpace.alpha = 0f
                        }
                        logMobileRoamingIndicatorHidden("ColorOS 17 roaming TextView draw")
                        param.result = null
                    }
                })
                hookCount++
            }

        // ColorOS 17 renders mobile icons through Compose. All three active layout strategies
        // read this presentation-only value before composing the roaming label.
        XposedHelpers.findClassIfExists(STACKED_MOBILE_ICON_VIEW_MODEL_CLASS, classLoader)
            ?.declaredMethods
            ?.filter { method ->
                method.name == "getRoaming" &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == java.lang.Boolean.TYPE
            }
            ?.forEach { method ->
                val key = "systemui:hide-mobile-roaming:stacked-state:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                        logMobileRoamingIndicatorHidden("ColorOS 17 stacked view model")
                    }
                })
                hookCount++
            }

        XposedHelpers.findClassIfExists(OS17_MOBILE_BINDING_CLASS, classLoader)
            ?.declaredMethods
            ?.filter { method ->
                method.name == "bindRoamingText" &&
                    method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
            }
            ?.forEach { method ->
                val key = "systemui:hide-mobile-roaming:os17:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = false
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val binding = param.thisObject ?: return
                        (runCatching {
                            XposedHelpers.getObjectField(binding, "mobileRoamingText")
                        }.getOrNull() as? View)?.visibility = View.GONE
                        (runCatching {
                            XposedHelpers.callMethod(binding, "getRoamingSpace")
                        }.getOrNull() as? View)?.visibility = View.GONE
                        logMobileRoamingIndicatorHidden("ColorOS 17 text binding")
                    }
                })
                hookCount++
            }

        XposedHelpers.findClassIfExists(LEGACY_OS16_MOBILE_BINDING_CLASS, classLoader)
            ?.declaredMethods
            ?.filter { method ->
                method.name == "bindRoamingIcon" &&
                    method.parameterTypes.contentEquals(arrayOf(Integer.TYPE))
            }
            ?.forEach { method ->
                val key = "systemui:hide-mobile-roaming:legacy:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = 0
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        logMobileRoamingIndicatorHidden("legacy icon binding")
                    }
                })
                hookCount++
            }

        XposedHelpers.findClassIfExists(BIG_TYPE_MOBILE_BINDER_LEGACY_CLASS, classLoader)
            ?.declaredMethods
            ?.filter { method ->
                method.name == "bindCustEx\$updateMobileRoaming" &&
                    method.parameterTypes.lastOrNull() == Integer.TYPE
            }
            ?.forEach { method ->
                val key = "systemui:hide-mobile-roaming:big-type:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[param.args.lastIndex] = 0
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.args.filterIsInstance<View>().forEach { view ->
                            view.visibility = View.GONE
                        }
                        logMobileRoamingIndicatorHidden("large-text icon binding")
                    }
                })
                hookCount++
            }

        log("SystemUI hide mobile roaming indicator hooks: $hookCount")
    }

    private fun logMobileRoamingIndicatorHidden(binding: String) {
        if (mobileRoamingIndicatorHidden.compareAndSet(false, true)) {
            log("SystemUI mobile roaming indicator hidden via $binding")
        }
    }

    /** Hides ColorOS' mobile-data upload/download presentation without changing connectivity. */
    private fun hookHideNetworkActivityIndicator(classLoader: ClassLoader?) {
        var hookCount = 0

        XposedHelpers.findClassIfExists(OPLUS_MOBILE_BINDING_CLASS, classLoader)
            ?.declaredMethods
            ?.filter { method ->
                method.name == "updateDataActivity" &&
                    method.parameterTypes.contentEquals(arrayOf(Integer.TYPE))
            }
            ?.forEach { method ->
                val key = "systemui:hide-network-activity:mobile-binding:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = 0
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val binding = param.thisObject ?: return
                        (runCatching {
                            XposedHelpers.callMethod(binding, "getDataActivity")
                        }.getOrNull() as? View)?.let { activityView ->
                            activityView.visibility = View.GONE
                            activityView.alpha = 0f
                        }
                        logNetworkActivityIndicatorHidden("ColorOS mobile binding")
                    }
                })
                hookCount++
            }

        // ColorOS 17's stacked dual-SIM presentation is Compose-based and bypasses the regular
        // ImageView binder, so suppress both its visibility state and drawable resource.
        XposedHelpers.findClassIfExists(OPLUS_STACKED_MOBILE_ICON_VIEW_MODEL_CLASS, classLoader)
            ?.declaredMethods
            ?.filter { method ->
                method.parameterTypes.isEmpty() &&
                    (method.name == "getActivityContainerVisible" ||
                        method.name == "getActivityIndicatorIconResId")
            }
            ?.forEach { method ->
                val key = "systemui:hide-network-activity:stacked:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = if (method.returnType == java.lang.Boolean.TYPE) false else 0
                        logNetworkActivityIndicatorHidden("ColorOS 17 stacked view model")
                    }
                })
                hookCount++
            }

        // C17's Wi-Fi icon uses a vendor-combined wifi_inout ImageView, while the retained AOSP
        // binder still owns wifi_in, wifi_out and their container. Guard all four presentation
        // views because the coroutine collectors can make them visible again as traffic changes.
        arrayOf(WIFI_VIEW_BINDER_CLASS, OPLUS_WIFI_SIGNAL_EX_IMPL_CLASS).forEach { className ->
            val binderClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            binderClass.declaredMethods
                .filter { method ->
                    (method.name == "bind" || method.name == "bindEx") &&
                        method.parameterTypes.firstOrNull()?.let { View::class.java.isAssignableFrom(it) } == true
                }
                .forEach { method ->
                    val key = "systemui:hide-network-activity:wifi:${method.toGenericString()}"
                    if (!addHookKeyIfAbsent(key)) return@forEach
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val root = param.args.firstOrNull() as? View ?: return
                            guardWifiActivityViews(root)
                            logNetworkActivityIndicatorHidden("ColorOS 17 Wi-Fi binding")
                        }
                    })
                    hookCount++
                }
        }

        log("SystemUI hide network activity indicator hooks: $hookCount")
    }

    private fun guardWifiActivityViews(root: View) {
        val activityViews = mutableListOf<View>()
        intArrayOf(
            WIFI_ACTIVITY_CONTAINER_VIEW_ID,
            WIFI_ACTIVITY_IN_VIEW_ID,
            WIFI_ACTIVITY_COMBINED_VIEW_ID,
            WIFI_ACTIVITY_OUT_VIEW_ID,
        ).forEach { viewId ->
            val view = runCatching { root.findViewById<View>(viewId) }.getOrNull()
            if (view != null && view !in activityViews) activityViews += view
        }
        if (activityViews.isEmpty()) return

        fun hideActivityViews() {
            activityViews.forEach { view ->
                if (view.visibility != View.GONE) view.visibility = View.GONE
                if (view.alpha != 0f) view.alpha = 0f
            }
        }

        hideActivityViews()
        if (guardedWifiActivityRoots.put(root, true) != null) return
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> hideActivityViews() }
        root.viewTreeObserver.addOnPreDrawListener {
            hideActivityViews()
            true
        }
    }

    private fun logNetworkActivityIndicatorHidden(binding: String) {
        if (networkActivityIndicatorHidden.compareAndSet(false, true)) {
            log("SystemUI network activity indicator hidden via $binding")
        }
    }

    /** Routes the vendor power-key entry to the AOSP GlobalActionsDialogLite bundled in C17. */
    private fun hookNativePowerMenu(classLoader: ClassLoader?) {
        var hookCount = 0

        XposedHelpers.findClassIfExists(GLOBAL_ACTIONS_DIALOG_LITE_CLASS, classLoader)?.let { dialogClass ->
            val key = "systemui:native-power-menu:dialog-constructors"
            if (addHookKeyIfAbsent(key)) {
                XposedBridge.hookAllConstructors(dialogClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        observedNativePowerMenuDialog = param.thisObject
                    }
                })
                hookCount += dialogClass.declaredConstructors.size
            }
        }

        NATIVE_POWER_MENU_PROVIDER_HOLDER_CLASSES.forEach { className ->
            val holderClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            val key = "systemui:native-power-menu:provider:$className"
            if (!addHookKeyIfAbsent(key)) return@forEach
            XposedBridge.hookAllConstructors(holderClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val holder = param.thisObject ?: return
                    runCatching {
                        XposedHelpers.getObjectField(holder, "globalActionsDialogLiteProvider")
                    }.getOrNull()?.let { provider ->
                        nativePowerMenuProvider = provider
                    }
                }
            })
            hookCount += holderClass.declaredConstructors.size
        }

        val implementationClass = XposedHelpers.findClassIfExists(
            GLOBAL_ACTIONS_IMPL_CLASS,
            classLoader,
        ) ?: run {
            log("SystemUI native power menu unavailable: GlobalActionsImpl missing")
            return
        }
        implementationClass.declaredMethods
            .filter { method ->
                method.name == "showGlobalActions" || method.name == "showOrHideGlobalActions"
            }
            .forEach { method ->
                val key = "systemui:native-power-menu:route:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val host = param.thisObject ?: return
                        if (!showNativePowerMenu(host)) return
                        param.result = null
                    }
                })
                hookCount++
            }
        log("SystemUI native power menu hooks: $hookCount")
    }

    private fun showNativePowerMenu(host: Any): Boolean {
        val context = runCatching {
            XposedHelpers.getObjectField(host, "mContext") as? Context
        }.getOrNull() ?: return false
        val dialog = obtainNativePowerMenuDialog() ?: return false
        return runCatching {
            val displayId = (XposedHelpers.callMethod(context, "getDisplayId") as? Int) ?: 0
            XposedHelpers.callMethod(dialog, "showOrHideDialog", null, displayId)
            if (nativePowerMenuShown.compareAndSet(false, true)) {
                log("SystemUI power-key menu routed to GlobalActionsDialogLite")
            }
            true
        }.getOrElse { error ->
            log("SystemUI native power menu routing failed: ${error.message}")
            synchronized(nativePowerMenuLock) {
                if (nativePowerMenuDialog === dialog) nativePowerMenuDialog = null
                if (observedNativePowerMenuDialog === dialog) observedNativePowerMenuDialog = null
            }
            false
        }
    }

    private fun obtainNativePowerMenuDialog(): Any? {
        nativePowerMenuDialog?.let { return it }
        synchronized(nativePowerMenuLock) {
            nativePowerMenuDialog?.let { return it }
            val dialog = nativePowerMenuProvider?.let { provider ->
                runCatching {
                    XposedHelpers.callMethod(provider, "get")
                }.getOrElse { error ->
                    log("SystemUI native power menu provider failed: ${error.message}")
                    null
                }
            }
            if (dialog?.javaClass?.name == GLOBAL_ACTIONS_DIALOG_LITE_CLASS) {
                nativePowerMenuDialog = dialog
            }
            return nativePowerMenuDialog ?: observedNativePowerMenuDialog
        }
    }

    /**
     * ColorOS 17 places both the stacked-mobile and regular-mobile slots before Wi-Fi. Moving the
     * Wi-Fi slot in the source array restores ColorOS 16's Wi-Fi -> SIM1 -> SIM2 visual order and
     * lets the existing icon controller keep handling subscription additions and removals.
     */
    private fun hookRestoreC16NetworkIconOrder(classLoader: ClassLoader?) {
        var hookCount = 0
        STATUS_BAR_ICON_LIST_CLASSES.forEach { className ->
            val iconListClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            iconListClass.declaredConstructors
                .filter { constructor ->
                    constructor.parameterTypes.size == 1 &&
                        constructor.parameterTypes[0].isArray &&
                        constructor.parameterTypes[0].componentType == String::class.java
                }
                .forEach { constructor ->
                    val key = "systemui:c16-network-icon-order:${constructor.toGenericString()}"
                    if (!addHookKeyIfAbsent(key)) return@forEach
                    XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val original = param.args.getOrNull(0) as? Array<*> ?: return
                            val slots = original.map { slot -> slot as? String ?: return }
                            val wifiIndex = slots.indexOf("wifi")
                            val firstMobileIndex = slots.indexOfFirst { slot ->
                                slot == "stacked_mobile" || slot == "mobile"
                            }
                            if (wifiIndex < 0 || firstMobileIndex < 0 || wifiIndex < firstMobileIndex) {
                                return
                            }

                            val reordered = slots.toMutableList()
                            reordered.removeAt(wifiIndex)
                            val insertionIndex = reordered.indexOfFirst { slot ->
                                slot == "stacked_mobile" || slot == "mobile"
                            }
                            if (insertionIndex < 0) return
                            reordered.add(insertionIndex, "wifi")
                            param.args[0] = reordered.toTypedArray()

                            if (c16NetworkIconOrderApplied.compareAndSet(false, true)) {
                                log("SystemUI network icon order restored: wifi before mobile slots")
                            }
                        }
                    })
                    hookCount++
                }
        }
        log("SystemUI ColorOS 16 network icon order hooks: $hookCount")
    }

    /** Enables SystemUI's global pinned/alerting/silent notification sections. */
    private fun hookInternationalNotificationStyle(classLoader: ClassLoader?) {
        var hookCount = 0
        val featureOptionClass = XposedHelpers.findClassIfExists(
            FEATURE_OPTION_CLASS,
            classLoader
        )
        featureOptionClass?.declaredMethods
            ?.filter { method ->
                method.name == "isExpRegion" &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == java.lang.Boolean.TYPE
            }
            ?.forEach { method ->
                val key = "systemui:notification-style:exp-region:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isInternationalNotificationStyleScopeActive()) return
                        if (internationalNotificationStyleApplied.compareAndSet(false, true)) {
                            log("SystemUI notification pipeline switched to global sections")
                        }
                        param.result = true
                    }
                })
                hookCount++
            }

        val scopedTargets = mapOf(
            NOTIFICATION_SORT_EX_IMPL_CLASS to setOf(
                "createNotifSection",
                "getGroupChildrenComparator",
                "modifyOrderedSection"
            ),
            OPLUS_NODE_SPEC_BUILDER_EX_IMPL_CLASS to setOf("buildNodeSpec")
        )
        scopedTargets.forEach { (className, methodNames) ->
            val targetClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            targetClass.declaredMethods
                .filter { method -> method.name in methodNames }
                .forEach { method ->
                    val key = "systemui:notification-style:scope:${method.toGenericString()}"
                    if (!addHookKeyIfAbsent(key)) return@forEach
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            enterInternationalNotificationStyleScope()
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            exitInternationalNotificationStyleScope()
                        }
                    })
                    hookCount++
                }
        }

        // Older SystemUI builds consume this provider value directly. ColorOS 17 keeps the
        // query but discards its result, so the scoped pipeline hooks above are required.
        val providerClass = XposedHelpers.findClassIfExists(
            APP_FEATURE_PROVIDER_CLASS,
            classLoader
        )
        providerClass?.declaredMethods
            ?.filter { method ->
                method.returnType == java.lang.Boolean.TYPE &&
                    method.name in setOf("getBoolean", "isFeatureSupport") &&
                    method.parameterTypes.any { parameterType ->
                        parameterType == String::class.java
                    }
            }
            ?.forEach { method ->
                val key = "systemui:notification-style:legacy:${method.toGenericString()}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args.none { it == ORIGIN_NOTIFICATION_BEHAVIOR_FEATURE }) return
                        param.result = true
                    }
                })
                hookCount++
            }
        log("SystemUI international notification style hooks: $hookCount")
    }

    private fun enterInternationalNotificationStyleScope() {
        internationalNotificationStyleScope.set(
            (internationalNotificationStyleScope.get() ?: 0) + 1
        )
    }

    private fun exitInternationalNotificationStyleScope() {
        val depth = (internationalNotificationStyleScope.get() ?: 1) - 1
        if (depth <= 0) {
            internationalNotificationStyleScope.remove()
        } else {
            internationalNotificationStyleScope.set(depth)
        }
    }

    private fun isInternationalNotificationStyleScopeActive(): Boolean {
        return (internationalNotificationStyleScope.get() ?: 0) > 0
    }

    private fun hookControlCenterMenuVisibility(classLoader: ClassLoader?) {
        val popupClasses = listOf(
            "com.oplus.systemui.qs.widget.MoreButtonPopupWindow",
            "com.oplus.systemui.plugins.qs.quickentrance.popupwindow.OplusQSMoreButtonPopupWindow"
        ).mapNotNull { className ->
            XposedHelpers.findClassIfExists(className, classLoader)
        }
        popupClasses.forEach { popupClass ->
            popupClass.declaredMethods
                .filter { method ->
                    method.name == "initList\$1" ||
                        method.name == "initList" ||
                        method.name.startsWith("initList")
                }
                .forEach { method ->
                    val key = "qs_more_menu_visibility|${method.declaringClass.name}|${method.name}"
                    if (!addHookKeyIfAbsent(key)) return@forEach
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            applyControlCenterMenuVisibility(param.thisObject)
                        }
                    })
                }
            popupClass.declaredMethods
                .filter { method -> method.name == "show" || method.name.startsWith("show") }
                .forEach { method ->
                    val key = "qs_more_menu_visibility|${method.declaringClass.name}|${method.name}|show|${method.parameterTypes.joinToString { it.name }}"
                    if (!addHookKeyIfAbsent(key)) return@forEach
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            applyControlCenterMenuVisibility(param.thisObject)
                            refreshControlCenterMenuList(param.thisObject)
                        }
                    })
                }
            val constructorKey = "qs_more_menu_visibility|${popupClass.name}|constructors"
            if (addHookKeyIfAbsent(constructorKey)) {
                XposedBridge.hookAllConstructors(popupClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applyControlCenterMenuVisibility(param.thisObject)
                        refreshControlCenterMenuList(param.thisObject)
                    }
                })
            }
            log("SystemUI control center menu visibility hook: ${popupClass.name}")
        }
        val controllerClass = XposedHelpers.findClassIfExists(
            "com.oplus.systemui.plugins.qs.quickentrance.popupwindow.OplusQSMoreButtonPopupWindowController",
            classLoader
        )
        controllerClass?.declaredMethods
            ?.filter { method -> method.name == "showEdit" || method.name == "showPopupWindow" }
            ?.forEach { method ->
                val key = "qs_more_menu_visibility|${method.declaringClass.name}|${method.name}|block"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (
                            method.name == "showEdit" &&
                            LspConfig.isSystemUiHideQsEditEnabledXposed()
                        ) {
                            param.result = null
                        }
                        if (
                            method.name == "showPopupWindow" &&
                            LspConfig.isSystemUiHideQsMoreEnabledXposed()
                        ) {
                            param.result = null
                        }
                    }
                })
            }
    }

    private fun hookControlCenterQuickEntranceVisibility(classLoader: ClassLoader?) {
        val componentClass = XposedHelpers.findClassIfExists(
            "com.oplus.systemui.plugins.qs.quickentrance.OplusQSQuickEntranceComponent",
            classLoader
        ) ?: return
        val constructorKey = "qs_quick_entrance_visibility|${componentClass.name}|constructors"
        if (addHookKeyIfAbsent(constructorKey)) {
            XposedBridge.hookAllConstructors(componentClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    applyControlCenterQuickEntranceVisibility(param.thisObject)
                }
            })
        }
        componentClass.declaredMethods
            .filter { method ->
                method.name.startsWith("update") ||
                    method.name == "onViewAttached" ||
                    method.name == "refreshItemViewBackground"
            }
            .forEach { method ->
                val key = "qs_quick_entrance_visibility|${method.declaringClass.name}|${method.name}|${method.parameterTypes.joinToString { it.name }}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applyControlCenterQuickEntranceVisibility(param.thisObject)
                    }
                })
            }
        log("SystemUI control center quick entrance visibility hook: ${componentClass.name}")
    }

    private fun hookForceNativeClipboardOverlay(classLoader: ClassLoader?) {
        val featureOptionClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.FeatureOption",
            classLoader
        ) ?: return
        val methods = featureOptionClass.declaredMethods.filter { method ->
            method.name == "isExpRegion" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Boolean::class.javaPrimitiveType
        }
        methods.forEach { method ->
            val key = "force_native_clipboard_overlay|${method.declaringClass.name}|${method.name}"
            if (!addHookKeyIfAbsent(key)) return@forEach
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (
                        LspConfig.isSystemUiForceNativeClipboardOverlayEnabledXposed() &&
                        isClipboardPrimaryClipChangedCall()
                    ) {
                        param.result = true
                    }
                }
            })
        }
        log("SystemUI force native clipboard overlay hook: ${methods.size}")
    }

    private fun isClipboardPrimaryClipChangedCall(): Boolean {
        return Thread.currentThread().stackTrace.any { frame ->
            frame.className == "com.android.systemui.clipboardoverlay.ClipboardListener" &&
                frame.methodName == "onPrimaryClipChanged"
        }
    }

    private fun applyControlCenterMenuVisibility(popupWindow: Any?) {
        if (popupWindow == null) return
        if (LspConfig.isSystemUiHideQsEditEnabledXposed()) {
            setFieldViewGone(popupWindow, "editButton")
        }
        if (LspConfig.isSystemUiHideQsMoreEnabledXposed()) {
            setFieldViewGone(popupWindow, "outSwitchView")
            setFieldViewGone(popupWindow, "usersView")
        }
    }

    private fun applyControlCenterQuickEntranceVisibility(component: Any?) {
        if (component == null) return
        if (LspConfig.isSystemUiHideQsEditEnabledXposed()) {
            setFieldViewGone(component, "editBtn")
            setFieldViewGone(component, "editBtnRedDot")
        }
        if (LspConfig.isSystemUiHideQsSettingsEnabledXposed()) {
            setFieldViewGone(component, "settingsButton")
            setFieldViewGone(component, "settingsButtonIcon")
        }
        if (LspConfig.isSystemUiHideQsMoreEnabledXposed()) {
            setFieldViewGone(component, "moreBtn")
            setFieldViewGone(component, "moreBtnIcon")
            setFieldViewGone(component, "moreBtnRedDot")
        }
    }

    private fun refreshControlCenterMenuList(popupWindow: Any?) {
        if (popupWindow == null) return
        runCatching {
            XposedHelpers.callMethod(popupWindow, "initList")
        }.recoverCatching {
            XposedHelpers.callMethod(popupWindow, "initList\$1")
        }
    }

    private fun hookControlCenterTopCarrier(classLoader: ClassLoader?) {
        val carrierClass = XposedHelpers.findClassIfExists(
            "com.oplus.systemui.qs.widget.OplusSecondCarrierText",
            classLoader
        ) ?: return
        val constructorKey = "qs_top_carrier_visibility|${carrierClass.name}|constructors"
        if (addHookKeyIfAbsent(constructorKey)) {
            XposedBridge.hookAllConstructors(carrierClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    hideControlCenterTopCarrier(param.thisObject)
                }
            })
        }
        val refreshMethodNames = setOf(
            "onAttachedToWindow",
            "onConfigurationChanged",
            "onSizeChanged",
            "onWindowFocusChanged",
            "setMarqueeAllowedByPanel",
            "updateTextSize\$1"
        )
        carrierClass.declaredMethods
            .filter { method -> method.name in refreshMethodNames }
            .forEach { method ->
                val key = "qs_top_carrier_visibility|${method.declaringClass.name}|${method.name}|${method.parameterTypes.joinToString { it.name }}"
                if (!addHookKeyIfAbsent(key)) return@forEach
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        hideControlCenterTopCarrier(param.thisObject)
                    }
                })
            }
        log("SystemUI control center top carrier hook: ${carrierClass.name}")
    }

    private fun hideControlCenterTopCarrier(target: Any?) {
        if (!LspConfig.isSystemUiHideQsTopCarrierEnabledXposed()) return
        (target as? View)?.let { view ->
            view.visibility = View.GONE
            view.alpha = 0f
        }
    }

    private fun setFieldViewGone(target: Any, fieldName: String) {
        val view = runCatching {
            XposedHelpers.getObjectField(target, fieldName) as? View
        }.getOrNull() ?: return
        view.visibility = View.GONE
        view.alpha = 0f
    }

    private fun log(message: String) {
        HookLog.i(TAG, message)
    }

    private fun addHookKeyIfAbsent(key: String): Boolean {
        return installedHookKeys.add(key)
    }
}
