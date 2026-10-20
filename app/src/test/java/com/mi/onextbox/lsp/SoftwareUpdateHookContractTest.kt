package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SoftwareUpdateHookContractTest {
    @Test fun allTenFeaturesHaveIndependentOffByDefaultPreferences() {
        assertEquals(10, SoftwareUpdateFeature.entries.size)
        assertEquals(10, SoftwareUpdateFeature.entries.map { it.key }.distinct().size)
        val settings = source("lsp/SoftwareUpdateSettings.kt")
        assertTrue(settings.contains("getBoolean(feature.key, false)"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(feature.key, false)"))
    }

    @Test fun updaterMainAndUiProcessesReloadAutomaticallyWhenHealthy() {
        for (process in listOf("com.oplus.ota", "com.oplus.ota:ui", "com.oplus.sau", "com.oplus.sau:SetupZipService")) {
            assertTrue(ModuleHotReloadPolicy.canReload(process, false, setOf(process.substringBefore(':')), true))
            assertFalse(ModuleHotReloadPolicy.canReload(process, false, setOf(process.substringBefore(':')), false))
        }
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains("SoftwareUpdateHooker.hook(classLoader, target.sourceDir)"))
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains("SystemFileUpdateHooker.hook(classLoader, target.sourceDir)"))
    }

    @Test fun securityStatusChangesDoNotModifySystemPropertiesOrActualInstallationVerification() {
        val hook = source("lsp/SoftwareUpdateHooker.kt")
        val targets = source("lsp/SoftwareUpdateTargets.kt")
        assertTrue(targets.contains("device is root from su binary file:"))
        assertTrue(hook.contains("SoftwareUpdateRules.maskSecurityReport(text, hideRoot, hideBootloader)"))
        for (forbidden in listOf("ro.boot", "vbmeta", "verity", "UpdateEngine", "applyPayload", "upgradeCheck", "isLocked", "M0", "a1")) {
            assertFalse(forbidden, hook.contains(forbidden))
        }
        assertFalse(hook.contains("JSONObject::class.java.getDeclaredMethod"))
        assertFalse(hook.contains("SystemProperties"))
    }

    @Test fun sharedBootloaderGuardIsOverriddenOnceOnlyWithinNativeLocalEntryCalls() {
        val hook = source("lsp/SoftwareUpdateHooker.kt")
        val targets = source("lsp/SoftwareUpdateTargets.kt")
        assertTrue(hook.contains("listOf(\"localPicker\", \"localPreflight\")"))
        assertTrue(hook.contains("bootloaderScope.enter(enabled(SoftwareUpdateFeature.HideBootloader)) { chain.proceed() }"))
        assertTrue(hook.contains("enabled(SoftwareUpdateFeature.HideBootloader) && bootloaderScope.consumeOverride()"))
        assertTrue(targets.contains("ota server grant this local update"))
        assertTrue(targets.contains("ota:check_forbid_local_update"))
        assertTrue(targets.contains("android.intent.action.OPEN_DOCUMENT"))
        assertFalse(hook.contains("resolved(\"bootloaderGuard\") { chain -> if (enabled"))
    }

    @Test fun versionReminderOnlyBlocksTheNativeAutomaticDownloadPopup() {
        val hook = source("lsp/SoftwareUpdateHooker.kt")
        assertTrue(hook.contains("resolved(\"updatePopup\") { chain -> if (enabled(SoftwareUpdateFeature.HideUpdatePopup)) null else chain.proceed() }"))
        assertTrue(source("lsp/SoftwareUpdateTargets.kt").contains("sellMode has disable showNotifyDownloadDialog"))
        for (forbidden in listOf("Dialog::class", "NotificationManager::class", "canDrawOverlays\"", "notifyProgress", "showInstallWarningDialog")) {
            assertFalse(forbidden, hook.contains(forbidden))
        }
    }

    @Test fun aPreviousGenerationTargetCacheIsUpgradedInsteadOfSilentlySkippingNewHooks() {
        val hook = source("lsp/SoftwareUpdateHooker.kt")
        assertTrue(hook.contains("saved?.containsKey(SoftwareUpdateTargets.CACHE_SCHEMA) == true"))
        assertTrue(hook.contains("if (saved != null) check(refreshed.isSuccess)"))
        assertTrue(hook.contains("saved.orEmpty() + refreshed.getOrDefault(emptyMap())"))
    }

    @Test fun automaticDownloadJobsAndQueuedMessagesHaveSeparateGuards() {
        val hook = source("lsp/SoftwareUpdateHooker.kt")
        assertTrue(hook.contains("com.oplus.ota.download.AutoDownloadJobService"))
        assertTrue(hook.contains("SoftwareUpdateRules.blocksMessage("))
        assertTrue(hook.contains("SoftwareUpdateRules.blocksAction("))
        assertTrue(hook.contains("Service.START_NOT_STICKY"))
        assertFalse(hook.contains("stopSelf"))
        assertFalse(hook.contains("cancelPayload"))
    }

    @Test fun localInstallationReusesTheNativeListenerWithoutGlobalSettingWrites() {
        val hook = source("lsp/SoftwareUpdateHooker.kt")
        assertTrue(hook.contains("menuScope.enter(enabled(SoftwareUpdateFeature.ShowLocalInstall)) { chain.proceed() }"))
        assertTrue(hook.contains("controller.refresh(activity)"))
        assertFalse(hook.contains("Settings.Global.put"))
        assertFalse(hook.contains("setEnabled(true)"))
    }

    @Test fun detailMenusAndDialogsRetireBeforeTheNewGenerationRebinds() {
        val controller = source("lsp/SoftwareUpdateMenuController.kt")
        assertTrue(controller.contains("canReload = { tasks.isEmpty() }"))
        assertTrue(controller.contains("menus.values.forEach { it.removeItem(DETAILS_ID) }"))
        assertTrue(controller.contains("dialogs.keys.toList().forEach(::dismiss)"))
        assertTrue(controller.contains("main.removeCallbacksAndMessages(null)"))
        assertTrue(controller.contains("bind(activity, menu)"))
        assertTrue(controller.contains("HotReloadLifecycle.withHook"))
        assertTrue(source("lsp/SoftwareUpdateHooker.kt").contains("capture = { targets }"))
    }

    @Test fun packageDetailsOnlyReadTheLocalCacheAndNeverTriggerAnUpdate() {
        val reader = source("lsp/SoftwareUpdatePackageInfo.kt")
        assertTrue(reader.contains("content://com.oplus.ota/pkgList?no_notify=1"))
        assertTrue(reader.contains("result.size < 128"))
        for (forbidden in listOf("startService", "sendBroadcast", "applyPayload", ".insert(", ".update(", ".delete(", "HttpURLConnection")) {
            assertFalse(forbidden, reader.contains(forbidden))
        }
    }

    private fun source(path: String) = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}
