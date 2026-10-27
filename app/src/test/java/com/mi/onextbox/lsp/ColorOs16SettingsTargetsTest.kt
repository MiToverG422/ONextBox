package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UNUSED_PARAMETER")
class ColorOs16SettingsTargetsTest {
    private val google = "com.oplus.settings.feature.homepage.controller.GooglePreferenceController"
    private val appInfo = "com.oplus.settings.feature.appmanager.AppInfoFeature"
    private val customize = "com.oplus.settings.utils.CustomizeFeatureUtils"
    private val provider = "com.oplus.coreapp.appfeature.AppFeatureProviderUtils"
    private val autoStart = "com.oplus.settings.feature.appmanager.controller.AutoLaunchMgrPreferenceController"
    private val associationStart = "com.oplus.settings.feature.appmanager.controller.AssociationLaunchMgrPreferenceController"

    @Test fun googleSelectsOnlyTheNoArgumentIntegerMethod() {
        val targets = resolve(ColorOs16SettingsFeature.GoogleEntry)
        assertEquals(listOf("getAvailabilityStatus"), targets.methods.map { it.name })
        assertEquals(0, targets.methods.single().parameterCount)
        assertEquals(Int::class.javaPrimitiveType, targets.methods.single().returnType)
    }

    @Test fun googleRejectsWrongReturnTypeStaticMethodAndInheritedFallback() {
        for (wrong in listOf(WrongGoogleReturn::class.java, StaticGoogle::class.java, InheritedGoogle::class.java)) {
            assertRejected(ColorOs16SettingsFeature.GoogleEntry, classes() + (google to wrong))
        }
    }

    @Test fun openButtonRequiresBothMethodsAndTheExactButtonField() {
        val targets = resolve(ColorOs16SettingsFeature.AppOpenButton)
        assertEquals(listOf("isSupportArchingFeature", "updateAppArchiveAndRestoreButtonStatus"),
            targets.methods.map { it.name })
        assertEquals(ProbeController::class.java, targets.methods[1].parameterTypes.single())
        assertEquals(Void.TYPE, targets.methods[1].returnType)
        assertEquals("mRecoverButton", targets.recoveryButton?.name)
        assertEquals(ProbeTextView::class.java, targets.recoveryButton?.type)
    }

    @Test fun openButtonRejectsWrongFieldTypeAndStaticField() {
        for (wrong in listOf(WrongButtonType::class.java, StaticButton::class.java)) {
            assertRejected(ColorOs16SettingsFeature.AppOpenButton, classes() + (appInfo to wrong))
        }
    }

    @Test fun openButtonRejectsAnUpdateMethodForADifferentController() {
        assertRejected(ColorOs16SettingsFeature.AppOpenButton,
            classes() + (appInfo to WrongUpdateController::class.java))
    }

    @Test fun internationalModeResolvesOnlyTheFiveVerifiedBooleanMethods() {
        val targets = resolve(ColorOs16SettingsFeature.InternationalSettings)
        assertEquals(listOf("isExpVersion", "isAppFeatureSupport", "isAppFeatureSupport",
            "isFeatureSupport", "isFeatureSupport"), targets.methods.map { it.name })
        assertEquals(listOf(0, 1, 2, 2, 3), targets.methods.map { it.parameterCount })
        assertTrue(targets.methods.all { it.returnType == Boolean::class.javaPrimitiveType })
        assertEquals(listOf(ProbeResolver::class.java, ProbeFeatureId::class.java, String::class.java),
            targets.methods.last().parameterTypes.toList())
    }

    @Test fun internationalModeRejectsMissingOverloadsAndInstanceProviderMethods() {
        assertRejected(ColorOs16SettingsFeature.InternationalSettings,
            classes() + (provider to MissingProviderOverload::class.java))
        assertRejected(ColorOs16SettingsFeature.InternationalSettings,
            classes() + (provider to InstanceProvider::class.java))
    }

    @Test fun appAutoStartRequiresBothControllersWithIntegerResults() {
        val targets = resolve(ColorOs16SettingsFeature.AppAutoStart)
        assertEquals(2, targets.methods.size)
        assertTrue(targets.methods.all { it.returnType == Int::class.javaPrimitiveType && it.parameterCount == 0 })
        assertRejected(ColorOs16SettingsFeature.AppAutoStart, classes() - associationStart)
        assertRejected(ColorOs16SettingsFeature.AppAutoStart,
            classes() + (associationStart to WrongGoogleReturn::class.java))
    }

    @Test fun probingNeverConstructsControllersOrCallsTheirMethods() {
        constructions = 0
        ColorOs16SettingsFeature.entries.forEach { resolve(it) }
        assertEquals(0, constructions)
    }

    @Test fun aMissingFeatureCannotPreventTheOtherFeaturesFromInstalling() {
        val installed = mutableListOf<ColorOs16SettingsFeature>()
        val failed = mutableListOf<ColorOs16SettingsFeature>()
        val available = classes() - google
        ColorOs16SettingsTargets.installCompatible(
            available::getValue,
            { installed += it.feature },
            { feature, _ -> failed += feature },
        )
        assertEquals(listOf(ColorOs16SettingsFeature.GoogleEntry), failed)
        assertEquals(ColorOs16SettingsFeature.entries.filter { it != ColorOs16SettingsFeature.GoogleEntry }, installed)
    }

    @Test fun installationAndLinkageFailuresAreIsolatedToTheirFeature() {
        val installed = mutableListOf<ColorOs16SettingsFeature>()
        val failed = mutableListOf<ColorOs16SettingsFeature>()
        val available = classes()
        ColorOs16SettingsTargets.installCompatible(
            { name ->
                if (name == customize) throw NoClassDefFoundError("Missing Settings dependency")
                available.getValue(name)
            },
            { targets ->
                if (targets.feature == ColorOs16SettingsFeature.AppOpenButton) error("Hook registration failed")
                installed += targets.feature
            },
            { feature, _ -> failed += feature },
        )
        assertEquals(listOf(ColorOs16SettingsFeature.GoogleEntry, ColorOs16SettingsFeature.AppAutoStart), installed)
        assertEquals(listOf(ColorOs16SettingsFeature.AppOpenButton, ColorOs16SettingsFeature.InternationalSettings), failed)
    }

    @Test fun aThrowingFailureReporterCannotPreventLaterFeaturesFromInstalling() {
        val installed = mutableListOf<ColorOs16SettingsFeature>()
        val reported = mutableListOf<ColorOs16SettingsFeature>()
        val available = classes() - google
        ColorOs16SettingsTargets.installCompatible(
            available::getValue,
            { installed += it.feature },
            { feature, _ ->
                reported += feature
                error("Diagnostic reporting failed")
            },
        )
        assertEquals(listOf(ColorOs16SettingsFeature.GoogleEntry), reported)
        assertEquals(ColorOs16SettingsFeature.entries.filter { it != ColorOs16SettingsFeature.GoogleEntry }, installed)
    }

    private fun resolve(feature: ColorOs16SettingsFeature) =
        ColorOs16SettingsTargets.resolve(feature, classes()::getValue)

    private fun assertRejected(feature: ColorOs16SettingsFeature, available: Map<String, Class<*>>) {
        assertFalse(feature.name, runCatching {
            ColorOs16SettingsTargets.resolve(feature, available::getValue)
        }.isSuccess)
    }

    private fun classes(): Map<String, Class<*>> = mapOf(
        google to ProbeGoogle::class.java,
        appInfo to ProbeAppInfo::class.java,
        customize to ProbeCustomize::class.java,
        provider to ProbeProvider::class.java,
        "$provider\$FeatureID" to ProbeFeatureId::class.java,
        autoStart to ProbeGoogle::class.java,
        associationStart to ProbeGoogle::class.java,
        "com.android.settings.applications.appinfo.AppButtonsPreferenceController" to ProbeController::class.java,
        "android.widget.TextView" to ProbeTextView::class.java,
        "android.content.ContentResolver" to ProbeResolver::class.java,
    )

    private class ProbeController
    private class ProbeTextView
    private class ProbeResolver
    private class ProbeFeatureId

    private open class ProbeGoogle {
        init { constructions++ }
        fun getAvailabilityStatus(): Int = error("Probe must not run host methods")
        fun getAvailabilityStatus(ignored: String): Int = error("Unverified overload")
        fun isAvailable(): Boolean = error("Unrelated feature")
    }

    private class WrongGoogleReturn {
        fun getAvailabilityStatus(): Boolean = false
    }

    private class StaticGoogle {
        companion object {
            @JvmStatic fun getAvailabilityStatus(): Int = 0
        }
    }

    private class InheritedGoogle : ProbeGoogle()

    private class ProbeAppInfo {
        init { constructions++ }
        @JvmField var mRecoverButton: ProbeTextView? = null
        fun isSupportArchingFeature(): Boolean = error("Probe must not read archive state")
        fun updateAppArchiveAndRestoreButtonStatus(controller: ProbeController) {
            error("Probe must not change host views")
        }
        fun updateAppArchiveAndRestoreButtonStatus(unknown: String) = Unit
    }

    private class WrongButtonType {
        @JvmField var mRecoverButton: String? = null
        fun isSupportArchingFeature(): Boolean = false
        fun updateAppArchiveAndRestoreButtonStatus(controller: ProbeController) = Unit
    }

    private class StaticButton {
        fun isSupportArchingFeature(): Boolean = false
        fun updateAppArchiveAndRestoreButtonStatus(controller: ProbeController) = Unit
        companion object {
            @JvmField var mRecoverButton: ProbeTextView? = null
        }
    }

    private class WrongUpdateController {
        @JvmField var mRecoverButton: ProbeTextView? = null
        fun isSupportArchingFeature(): Boolean = false
        fun updateAppArchiveAndRestoreButtonStatus(controller: String) = Unit
    }

    private class ProbeCustomize {
        companion object {
            @JvmStatic fun isExpVersion(): Boolean = error("Probe must not read edition")
            @JvmStatic fun isAppFeatureSupport(key: String): Boolean = error("Probe must not query features")
            @JvmStatic fun isAppFeatureSupport(key: String, id: ProbeFeatureId): Boolean =
                error("Probe must not query features")
            @JvmStatic fun isAppFeatureSupport(key: Int): Boolean = false
        }
    }

    private class ProbeProvider {
        companion object {
            @JvmStatic fun isFeatureSupport(resolver: ProbeResolver, key: String): Boolean =
                error("Probe must not query providers")
            @JvmStatic fun isFeatureSupport(resolver: ProbeResolver, id: ProbeFeatureId, key: String): Boolean =
                error("Probe must not query providers")
            @JvmStatic fun isFeatureSupport(key: String): Boolean = false
        }
    }

    private class MissingProviderOverload {
        companion object {
            @JvmStatic fun isFeatureSupport(resolver: ProbeResolver, key: String): Boolean = false
        }
    }

    private class InstanceProvider {
        fun isFeatureSupport(resolver: ProbeResolver, key: String): Boolean = false
        fun isFeatureSupport(resolver: ProbeResolver, id: ProbeFeatureId, key: String): Boolean = false
    }

    companion object {
        private var constructions = 0
    }
}
