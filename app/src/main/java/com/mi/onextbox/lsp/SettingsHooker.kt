package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import android.view.View
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Hooks for explicit feature toggles that execute inside the system Settings process. */
object SettingsHooker {
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val SETTINGS_CN_FEATURE = "com.android.settings.cn_version"
    private const val SETTINGS_WALLET_PREFERENCE_KEY = "wallet_and_payment"
    private const val SETTINGS_WALLET_TITLE_RESOURCE = "wallet_wallet_name"
    private val SETTINGS_DOMESTIC_ABOUT_DEVICE_KEYS = setOf(
        "fix_info",
        "authentication_info",
        "key_large_model_disclosure",
        "contributor_info",
    )
    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val internationalSettingsApplied = AtomicBoolean(false)

    fun hook(classLoader: ClassLoader, packageName: String) {
        if (packageName != SETTINGS_PACKAGE) return
        if (!hookedLoaders.add(System.identityHashCode(classLoader))) return

        hookInternationalSettings(classLoader)
        SettingsDomesticAuxiliaryHooker.hook(classLoader)
        PermissionUiStyleHooker.hookSettings(classLoader)
        hookForceAppAutoStart(classLoader)
        hookInternationalWallet(classLoader)
        hookRestoreDomesticAboutDevice(classLoader)
        hookRestoreSmartLock(classLoader)
        hookConfigureNotificationRedirect(classLoader)
        hookForceGoogleEntry(classLoader)
        hookSkipSpecialPermissionRiskConfirm(classLoader)
        hookRestoreAppOpenButton(classLoader)
        runCatching { C15AboutGridHooker.hook(classLoader) }
            .onFailure { log("C15 About device grid hook registration failed", it) }
        runCatching { C15AboutHardwareHooker.hook(classLoader) }
            .onFailure { log("C15 About device hardware hook registration failed", it) }
        runCatching { C15AboutOtaHooker.hook(classLoader) }
            .onFailure { log("C15 About device OTA hook registration failed", it) }
        runCatching { SettingsRefreshRateHooker.hook(classLoader) }
            .onFailure { log("Settings refresh-rate hook registration failed", it) }
    }

/** Settings edition gate, retaining domestic style and its fragment allow-list. */
    private fun hookInternationalSettings(classLoader: ClassLoader) {
        var hookCount = 0

        val customizeFeatureUtils = XposedHelpers.findClassIfExists(
            "com.oplus.settings.utils.CustomizeFeatureUtils",
            classLoader
        )
        if (customizeFeatureUtils != null) {
            hookCount += hookConstantResult(
                hookClass = customizeFeatureUtils,
                methodName = "isExpVersion",
                result = true,
            )
            hookCount += hookChinaFeatureQueries(customizeFeatureUtils, "isAppFeatureSupport")
        }

        val appFeatureUtils = XposedHelpers.findClassIfExists(
            "com.oplus.coreapp.appfeature.AppFeatureProviderUtils",
            classLoader
        )
        if (appFeatureUtils != null) {
            hookCount += hookChinaFeatureQueries(appFeatureUtils, "isFeatureSupport")
        }

        log("International Settings hooks: $hookCount")
    }

    /** Restores the domestic automatic-start card while global Settings mode remains enabled. */
    private fun hookForceAppAutoStart(classLoader: ClassLoader) {
        val controllerClasses = listOf(
            "com.oplus.settings.feature.appmanager.controller.AutoLaunchMgrPreferenceController",
            "com.oplus.settings.feature.appmanager.controller.AssociationLaunchMgrPreferenceController",
        )
        var hookCount = 0
        controllerClasses.forEach { className ->
            val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            runCatching {
                XposedBridge.hookAllMethods(
                    hookClass,
                    "getAvailabilityStatus",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsForceAppAutoStartEnabledXposed()) return
                            param.result = 0
                        }
                    }
                )
                hookCount++
            }.onFailure { log("hook $className.getAvailabilityStatus failed", it) }
        }
        log("Force app auto-start hooks: $hookCount")
    }

    /** Shows the domestic wallet target as the global-style Wallet entry. */
    private fun hookInternationalWallet(classLoader: ClassLoader) {
        val hookClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.feature.homepage.TopLevelWalletAndPaymentPreferenceController",
            classLoader,
        ) ?: return
        var hookCount = 0

        runCatching {
            XposedBridge.hookAllMethods(
                hookClass,
                "getAvailabilityStatus",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsInternationalWalletEnabledXposed()) return
                        param.result = 0
                    }
                },
            )
            hookCount++
        }.onFailure { log("hook international Wallet availability failed", it) }

        runCatching {
            XposedBridge.hookAllMethods(
                hookClass,
                "displayPreference",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsInternationalWalletEnabledXposed()) return
                        runCatching {
                            val screen = param.args.firstOrNull() ?: return@runCatching
                            val preference = XposedHelpers.callMethod(
                                screen,
                                "findPreference",
                                SETTINGS_WALLET_PREFERENCE_KEY,
                            ) ?: return@runCatching
                            val controller = param.thisObject ?: return@runCatching
                            val context = XposedHelpers.getObjectField(controller, "mContext")
                                ?: return@runCatching
                            val resources = XposedHelpers.callMethod(context, "getResources")
                                ?: return@runCatching
                            val titleId = XposedHelpers.callMethod(
                                resources,
                                "getIdentifier",
                                SETTINGS_WALLET_TITLE_RESOURCE,
                                "string",
                                SETTINGS_PACKAGE,
                            ) as? Int ?: 0
                            if (titleId != 0) {
                                val title = XposedHelpers.callMethod(context, "getText", titleId)
                                XposedHelpers.callMethod(preference, "setTitle", title)
                            }
                            XposedHelpers.callMethod(preference, "setVisible", true)
                        }.onFailure { log("apply international Wallet entry failed", it) }
                    }
                },
            )
            hookCount++
        }.onFailure { log("hook international Wallet presentation failed", it) }

        log("International Wallet hooks: $hookCount")
    }

    /** Restores the four CN-only entries hidden at the bottom of About device in global mode. */
    private fun hookRestoreDomesticAboutDevice(classLoader: ClassLoader) {
        var hookCount = 0

        listOf(
            "com.oplus.settings.feature.deviceinfo.controller.FixInfoPreferenceController",
            "com.oplus.settings.feature.deviceinfo.controller.ContributorInfoPreferenceController",
        ).forEach { className ->
            val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            runCatching {
                XposedBridge.hookAllMethods(
                    hookClass,
                    "getAvailabilityStatus",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsRestoreDomesticAboutDeviceEnabledXposed()) return
                            param.result = 0
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook $className.getAvailabilityStatus failed", it) }
        }

        val typedController = XposedHelpers.findClassIfExists(
            "com.oplus.settings.feature.controller.TypedPreferenceController",
            classLoader,
        )
        if (typedController != null) {
            runCatching {
                XposedBridge.hookAllMethods(
                    typedController,
                    "isAvailable",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsRestoreDomesticAboutDeviceEnabledXposed()) return
                            val controller = param.thisObject ?: return
                            val key = runCatching {
                                XposedHelpers.callMethod(controller, "getPreferenceKey") as? String
                            }.getOrNull()
                            if (key == "authentication_info" || key == "key_large_model_disclosure") {
                                param.result = true
                            }
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook TypedPreferenceController.isAvailable failed", it) }
        }

        val deviceInfoFragment = XposedHelpers.findClassIfExists(
            "com.oplus.settings.feature.deviceinfo.aboutphone.DeviceInfoFragment",
            classLoader,
        )
        if (deviceInfoFragment != null) {
            runCatching {
                XposedBridge.hookAllMethods(
                    deviceInfoFragment,
                    "onResume",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsRestoreDomesticAboutDeviceEnabledXposed()) return
                            val fragment = param.thisObject ?: return
                            SETTINGS_DOMESTIC_ABOUT_DEVICE_KEYS.forEach { key ->
                                runCatching {
                                    val preference = XposedHelpers.callMethod(
                                        fragment,
                                        "findPreference",
                                        key,
                                    ) ?: return@runCatching
                                    XposedHelpers.callMethod(preference, "setVisible", true)
                                }.onFailure { log("restore About device entry $key failed", it) }
                            }
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook DeviceInfoFragment.onResume failed", it) }
        }

        log("Restore domestic About device hooks: $hookCount")
    }

    /** Restores ColorOS's own Smart Lock card and keeps its credential flow and target intact. */
    private fun hookRestoreSmartLock(classLoader: ClassLoader) {
        var hookCount = 0

        val settingsUtils = XposedHelpers.findClassIfExists(
            "com.oplus.settings.utils.SettingsUtils",
            classLoader,
        )
        if (settingsUtils != null) {
            runCatching {
                XposedBridge.hookAllMethods(
                    settingsUtils,
                    "isSmartLockSupport",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsRestoreSmartLockEnabledXposed()) return
                            param.result = true
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook SettingsUtils.isSmartLockSupport failed", it) }
        }

        val hookClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.feature.password.controller.SmartLockController",
            classLoader,
        )
        if (hookClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(
                    hookClass,
                    "isAvailable",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsRestoreSmartLockEnabledXposed()) return
                            param.result = true
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook SmartLockController.isAvailable failed", it) }
        }

        log("Restore Smart Lock hooks: $hookCount")
    }

    private fun hookConstantResult(
        hookClass: Class<*>,
        methodName: String,
        result: Any,
    ): Int = runCatching {
        XposedBridge.hookAllMethods(
            hookClass,
            methodName,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!LspConfig.isSettingsInternationalEnabledXposed()) return
                    param.result = if (methodName == "isExpVersion" &&
                        SettingsDomesticAuxiliaryHooker.isDomesticScopeActive()
                    ) false else result
                    reportInternationalSettingsApplied()
                }
            }
        )
        1
    }.getOrElse {
        log("hook ${hookClass.name}.$methodName failed", it)
        0
    }

    private fun hookChinaFeatureQueries(hookClass: Class<*>, methodName: String): Int =
        runCatching {
            XposedBridge.hookAllMethods(
                hookClass,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsInternationalEnabledXposed()) return
                        if (param.args.none { it == SETTINGS_CN_FEATURE }) return
                        param.result = SettingsDomesticAuxiliaryHooker.isDomesticScopeActive()
                        reportInternationalSettingsApplied()
                    }
                }
            )
            1
        }.getOrElse {
            log("hook ${hookClass.name}.$methodName failed", it)
            0
        }

    private fun reportInternationalSettingsApplied() {
        if (internationalSettingsApplied.compareAndSet(false, true)) {
            log("Settings switched to international mode")
        }
    }

    private fun hookForceGoogleEntry(classLoader: ClassLoader) {
        val hookClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.feature.homepage.controller.GooglePreferenceController",
            classLoader
        ) ?: return
        runCatching {
            XposedBridge.hookAllMethods(
                hookClass,
                "getAvailabilityStatus",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsForceGoogleEntryEnabledXposed()) return
                        param.result = 0
                    }
                }
            )
        }.onFailure { log("hook GooglePreferenceController.getAvailabilityStatus failed", it) }
    }

    private fun hookConfigureNotificationRedirect(classLoader: ClassLoader) {
        val hookClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.SettingsActivityPlugin\$ConfigureNotificationSettings",
            classLoader
        ) ?: return
        runCatching {
            XposedBridge.hookAllMethods(
                hookClass,
                "onCreate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isNativeNotificationBubblesEnabledXposed()) return
                        param.result = null
                    }
                }
            )
        }.onFailure { log("hook ConfigureNotificationSettings redirect failed", it) }
    }

    private fun hookRestoreAppOpenButton(classLoader: ClassLoader) {
        val className = "com.oplus.settings.feature.appmanager.AppInfoFeature"
        val hookClass = XposedHelpers.findClassIfExists(className, classLoader) ?: return
        runCatching {
            // Use the OEM's existing open-button branch, icon, and launchApplication callback.
            // Keep this override local to app details rather than disabling system archiving.
            XposedBridge.hookAllMethods(hookClass, "isSupportArchingFeature", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (LspConfig.isSettingsRestoreAppOpenButtonEnabledXposed()) {
                        param.result = false
                    }
                }
            })
            XposedBridge.hookAllMethods(hookClass, "updateAppArchiveAndRestoreButtonStatus", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!LspConfig.isSettingsRestoreAppOpenButtonEnabledXposed()) return
                    val feature = param.thisObject ?: return
                    // Clear a previously visible archive button when this feature object is reused.
                    (XposedHelpers.getObjectField(feature, "mRecoverButton") as? View)
                        ?.visibility = View.GONE
                }
            })
            log("Restore app open button hooks registered")
        }.onFailure { log("Restore app open button hook registration failed", it) }
    }

    private fun hookSkipSpecialPermissionRiskConfirm(classLoader: ClassLoader) {
        val className = "com.oplus.settings.applications.specialaccess.SpecialPermRiskConfirmHelper"
        val hookClass = XposedHelpers.findClassIfExists(className, classLoader) ?: run {
            log("Special permission risk confirmation hook unavailable: $className")
            return
        }
        runCatching {
            val methods = hookClass.declaredMethods.filter {
                it.name == "show" &&
                    it.returnType == Boolean::class.javaPrimitiveType && it.parameterCount == 5
            }
            methods.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsSkipSpecialPermissionRiskConfirmEnabledXposed()) return
                        val confirmAction = param.args.getOrNull(4) as? Runnable ?: return
                        // Both file access and accessibility supply their own grant action.
                        // Treat this explicit user request as confirmed, then report it handled
                        // so accessibility does not fall back to another warning dialog.
                        confirmAction.run()
                        param.result = true
                        log("Skipped special permission risk confirmation panel")
                    }
                })
            }
            log("Special permission risk confirmation hooks: ${methods.size}")
        }.onFailure { log("Special permission risk confirmation hook registration failed", it) }
    }

    private fun log(message: String, throwable: Throwable? = null) {
        HookLog.i("SettingsHooker", message, throwable)
    }
}
