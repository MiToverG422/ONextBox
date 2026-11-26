package com.mi.onextbox.lsp

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemUiInactiveHooksContractTest {
    private fun source(name: String) = File("src/main/java/com/mi/onextbox/lsp/$name.kt").readText()

    @Test fun disabledRenderingGroupsWaitForPreferencesBeforeInstallingHooks() {
        val activation = source("SystemUiHookActivation")
        assertTrue(activation.contains("if (hadHooks || (!HotReloadLifecycle.isStaging && enabled()))"))
        assertTrue(activation.contains("HotReloadLifecycle.hasRestoredState(stateKey)"))
        assertTrue(activation.contains("capture = { observer.installed }"))
        assertTrue(activation.contains("HotReloadLifecycle.afterCommit(observer::observe)"))
        assertTrue(activation.contains("resumeAfterRejection = observer::observe"))
        assertTrue(activation.contains("if (HookConfigSnapshot.isAvailable && !SafeModeRuntime.blocked) pending.forEach { it.tryInstall() }"))
        assertTrue(activation.contains("if (!installed && enabled()) install(late = true)"))
        assertTrue(activation.contains("if (!refreshPosted) refreshPosted = main.post(refresh)"))
        val groups = mapOf(
            "CouiDividerHooker" to "CouiDividerSettings::enabledXposed",
            "QsTileCornerHooker" to "QsTileCornerSettings::enabledXposed",
            "QsSliderPercentageHooker" to "QsSliderPercentageSettings::enabledXposed",
            "SoftLightGlassHooker" to "SoftLightGlassSettings::enabledXposed",
            "SystemUiAppearanceHooker" to "barEnabled() || headerEnabled() || cornersEnabled()",
        )
        groups.forEach { (hook, preference) ->
            assertTrue(hook, source(hook).substringAfter("SystemUiHookActivation.install(").contains(preference))
        }
    }

    @Test fun auxiliarySystemUiPackagesUseTheSameCouiGateAsTheMainPackage() {
        val entry = source("OosLspModuleEntry")
        assertTrue(entry.contains("val hostProcess = processName.substringBefore(':')"))
        assertTrue(entry.contains("CouiDividerHooker.hook(packageName, classLoader, target.sourceDir, systemUi = hostProcess == PACKAGE_SYSTEM_UI)"))
        val divider = source("CouiDividerHooker")
        assertTrue(divider.contains("if (systemUi) {"))
        assertTrue(divider.contains("ApkDexSession.withSession(apkPath) { installHooks() }"))
    }

    @Test fun remainingNotificationAndKeyguardHooksAreInstalledOnlyWhenNeeded() {
        val groups = mapOf(
            "FluidCloudMaterialHooker" to "LspConfig.KEY_FLUID_CLOUD_MATERIAL",
            "HideNotificationClearHooker" to "HideNotificationClearSettings::enabledXposed",
            "NotificationClearButtonHooker" to "LspConfig.isRoundNotificationClearButtonEnabledXposed()",
            "PowerIndicatorHooker" to "PowerIndicatorSettings.ENABLED",
            "RetainLockscreenNotificationsHooker" to "RetainLockscreenNotificationsSettings::enabledXposed",
            "StatusBarScrollToTopHooker" to "::doubleTapEnabled",
            "PasswordEmergencyHooker" to "::enabled",
            "PinShuffleInputHooker" to "::enabled",
            "PinSwipeInputHooker" to "::enabled",
        )
        groups.forEach { (hook, preference) ->
            val entry = source(hook).substringAfter("SystemUiHookActivation.install(").substringBefore("\n    private fun")
            assertTrue(hook, entry.contains(preference))
        }
        val fluid = source("FluidCloudMaterialHooker")
        assertTrue(fluid.contains("if (materialHooksInstalled) installPlugin(loader)"))
        assertTrue(fluid.contains("pluginLoaders.forEach(::installPlugin)"))
        assertTrue(source("PowerIndicatorHooker").contains("if (enabled()) roots.toList().forEach(::bind) else detachOwners()"))
    }

    @Test fun lateActivationDiscoversExistingTilesSlidersAndNativeMaterials() {
        assertTrue(source("QsTileCornerHooker").contains("SystemUiWindowViews.find(loader) { it.javaClass.name == QS_ROOT }"))
        assertTrue(source("QsControlCenterSliderCorners").contains("if (late) SystemUiWindowViews.find(loader)"))
        assertTrue(source("QsSliderPercentageHooker").contains("if (late) SystemUiWindowViews.find(loader)"))
        val glass = source("SoftLightGlassHooker")
        assertTrue(glass.contains("adoptExistingShaders = late ||"))
        assertTrue(glass.contains("firstDraw && adoptExistingShaders && upgradeExistingDrawable != null"))
        assertTrue(glass.contains("nativePrograms.keys.toTypedArray(), adoptExistingShaders"))
    }

    @Test fun disabledGlassCompilesTheNativeSourceAndKeepsItsBindingsForActivation() {
        val hook = source("SoftLightGlassHooker")
        val constructor = hook.substringAfter("install(\"program\", ctor").substringBefore("val cache =")
        assertTrue(constructor.contains("marker != null && systemUi && !active()"))
        assertTrue(constructor.contains("useNative -> input.substring(marker.length)"))
        assertTrue(constructor.contains("let(::rememberNative)"))

        val cache = hook.substringAfter("install(\"cached:").substringBefore("val factory =")
        assertTrue(cache.contains("marker != null && systemUi && !active()"))
        assertTrue(cache.contains("args[0] = source.substring(marker.length)"))
        assertTrue(cache.contains("if (it is RuntimeShader) rememberNative(it)"))
        assertTrue(hook.contains("shader in programs || shader in nativePrograms"))
        assertTrue(hook.contains("result !in programs && result !in nativePrograms"))
    }

    @Test fun inactiveQsObserversKeepCandidatesWithoutRequestingFrames() {
        val observer = source("QsCornerViewObservers")
        assertTrue(observer.contains("if (!listening && configurationToken(view) != null)"))
        assertTrue(Regex("""fun markDirty\(\) \{\s+if \(!listening\) return""").containsMatchIn(observer))
        assertTrue(observer.contains("if (!enabled) dispose()"))
        assertTrue(observer.contains("key in settingsKeys"))
        assertTrue(observer.contains("resumeAfterRejection = ::activate"))
        assertTrue(observer.contains("listening && configurationToken(view) == null"))
        assertTrue(observer.contains("if (!HotReloadLifecycle.isStaging) guard.attach()"))
        val hierarchy = source("QsCornerHierarchyObservers")
        assertTrue(hierarchy.contains("if (enabled) guard.attach() else guard.dispose()"))
        assertTrue(source("QsTileCornerHooker").contains("onRefresh = { hierarchy?.refresh() }"))
    }

    @Test fun networkSpeedSchedulesExistingViewRefreshAfterHookReplacement() {
        val hook = source("NetworkSpeedHooker")
        assertTrue(hook.contains("installRefreshInterval(loader)"))
        assertTrue(hook.contains("SystemUiHookActivation.install(APPEARANCE, appearanceKeys"))
        assertTrue(hook.contains("HotReloadLifecycle.afterCommit(::observe)"))
        assertTrue(hook.contains("resumeAfterRejection = ::observe"))
        assertTrue(hook.contains("SystemUiWindowViews.find(loader, type::isInstance)"))
        val visibility = source("NetworkSpeedVisibility")
        assertTrue(visibility.indexOf("if (state == null || !settings.hideLowSpeed)") <
            visibility.indexOf("visible.invoke(state)"))
    }

    @Test fun clockHooksOnlyRefreshLiveViewsAfterCommitAndRearmAfterRejection() {
        listOf("ClockPeriodHooker", "ClockSpacingHooker", "ClockTabularDigitsHooker").forEach { name ->
            val hook = source(name)
            assertTrue(name, hook.contains("SystemUiHookActivation.install("))
            assertTrue(name, hook.contains("HotReloadLifecycle.afterCommit(::scheduleRefresh)"))
            assertTrue(name, hook.contains("resumeAfterRejection = ::scheduleRefresh"))
        }
    }

    @Test fun appearanceDiscoveryAndAnimationPreparationAreTriggeredByPreferences() {
        val hook = source("SystemUiAppearanceHooker")
        assertTrue(hook.contains("if (newBar || newHeader || newCorners) discover("))
        assertTrue(hook.contains("if (newCorners) corners?.prepareAnimations()"))
        assertTrue(hook.contains("if (!configured && views.isEmpty()) return@installFast result"))
        val theme = source("ThemeNightResourcesHook")
        assertTrue(theme.contains("enabled() && prepared.compareAndSet(false, true)"))
    }
}
