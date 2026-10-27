package com.mi.onextbox.lsp

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal enum class ColorOs16SettingsFeature {
    GoogleEntry,
    AppOpenButton,
    InternationalSettings,
    AppAutoStart,
}

internal data class ColorOs16SettingsTargets(
    val feature: ColorOs16SettingsFeature,
    val methods: List<Method>,
    val recoveryButton: Field? = null,
) {
    companion object {
        private const val CUSTOMIZE = "com.oplus.settings.utils.CustomizeFeatureUtils"
        private const val PROVIDER = "com.oplus.coreapp.appfeature.AppFeatureProviderUtils"

        fun resolve(
            feature: ColorOs16SettingsFeature,
            resolveClass: (String) -> Class<*>,
        ): ColorOs16SettingsTargets {
            fun method(
                owner: String,
                name: String,
                returnType: Class<*>,
                static: Boolean = false,
                vararg parameters: Class<*>,
            ): Method = resolveClass(owner).getDeclaredMethod(name, *parameters).also {
                check(it.returnType == returnType && Modifier.isStatic(it.modifiers) == static &&
                    Modifier.isPublic(it.modifiers) && !Modifier.isAbstract(it.modifiers) &&
                    !Modifier.isNative(it.modifiers)) { "Unexpected C16 Settings method: $it" }
            }

            val boolean = Boolean::class.javaPrimitiveType!!
            val integer = Int::class.javaPrimitiveType!!
            return when (feature) {
                ColorOs16SettingsFeature.GoogleEntry -> ColorOs16SettingsTargets(feature, listOf(
                    method("com.oplus.settings.feature.homepage.controller.GooglePreferenceController",
                        "getAvailabilityStatus", integer),
                ))

                ColorOs16SettingsFeature.AppOpenButton -> {
                    val owner = "com.oplus.settings.feature.appmanager.AppInfoFeature"
                    val controller = resolveClass("com.android.settings.applications.appinfo.AppButtonsPreferenceController")
                    val button = resolveClass(owner).getDeclaredField("mRecoverButton").also {
                        check(!Modifier.isStatic(it.modifiers) &&
                            it.type == resolveClass("android.widget.TextView")) {
                            "Unexpected C16 Settings recovery button: $it"
                        }
                    }
                    ColorOs16SettingsTargets(feature, listOf(
                        method(owner, "isSupportArchingFeature", boolean),
                        method(owner, "updateAppArchiveAndRestoreButtonStatus", Void.TYPE, false, controller),
                    ), button)
                }

                ColorOs16SettingsFeature.InternationalSettings -> {
                    val featureId = resolveClass("$PROVIDER\$FeatureID")
                    val resolver = resolveClass("android.content.ContentResolver")
                    ColorOs16SettingsTargets(feature, listOf(
                        method(CUSTOMIZE, "isExpVersion", boolean, true),
                        method(CUSTOMIZE, "isAppFeatureSupport", boolean, true, String::class.java),
                        method(CUSTOMIZE, "isAppFeatureSupport", boolean, true, String::class.java, featureId),
                        method(PROVIDER, "isFeatureSupport", boolean, true, resolver, String::class.java),
                        method(PROVIDER, "isFeatureSupport", boolean, true, resolver, featureId, String::class.java),
                    ))
                }

                ColorOs16SettingsFeature.AppAutoStart -> ColorOs16SettingsTargets(feature, listOf(
                    method("com.oplus.settings.feature.appmanager.controller.AutoLaunchMgrPreferenceController",
                        "getAvailabilityStatus", integer),
                    method("com.oplus.settings.feature.appmanager.controller.AssociationLaunchMgrPreferenceController",
                        "getAvailabilityStatus", integer),
                ))
            }
        }

        fun installCompatible(
            resolveClass: (String) -> Class<*>,
            install: (ColorOs16SettingsTargets) -> Unit,
            failed: (ColorOs16SettingsFeature, Throwable) -> Unit,
        ) {
            ColorOs16SettingsFeature.entries.forEach { feature ->
                runCatching { install(resolve(feature, resolveClass)) }
                    .onFailure { error -> runCatching { failed(feature, error) } }
            }
        }
    }
}
