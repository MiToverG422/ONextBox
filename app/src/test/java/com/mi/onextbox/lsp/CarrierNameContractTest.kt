package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class CarrierNameContractTest {
    private fun source(path: String): String = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()

    @Test fun hooksAreSystemUiOnlyAndPreserveNativeFormattingAndNetworkState() {
        val entry = source("main/java/com/mi/onextbox/lsp/OosLspModuleEntry.kt")
        assertEquals(1, Regex("CarrierNameHooker.hook").findAll(entry).count())
        assertTrue(entry.substringAfter("PACKAGE_SYSTEM_UI -> {").substringBefore("PACKAGE_SETTINGS").contains("CarrierNameHooker.hook(classLoader)"))
        val hook = source("main/java/com/mi/onextbox/lsp/CarrierNameHooker.kt")
        assertTrue(hook.contains("val owner = scope.owner() ?: return@Hooker original"))
        assertTrue(hook.contains("com.android.keyguard.CarrierTextManager"))
        assertTrue(hook.contains("com.oplus.systemui.qs.OplusQSCarrierTextController"))
        assertTrue(hook.contains("operatorNameForDisplaySlot"))
        assertTrue(hook.contains("getSimStateForSlotId"))
        assertTrue(hook.contains("mServiceStates"))
        assertTrue(hook.contains("getDataRegistrationState"))
        assertTrue(hook.contains("Settings.Global.AIRPLANE_MODE_ON"))
        for (unsafe in listOf("setDisplayName", "setCarrierName", "SystemProperties.set", "Settings.Global.put", "TextView::class", "setSimState")) {
            assertFalse(unsafe, hook.contains(unsafe))
        }
    }

    @Test fun nullableCoroutineResumeArgumentsAndContinuationAreUntouched() {
        val hook = source("main/java/com/mi/onextbox/lsp/CarrierNameHooker.kt").substringAfter("\"\$TAG:operatorFlow\"")
        assertTrue(hook.contains("chain.getArg(0) as? String"))
        assertTrue(hook.contains("!text.isNullOrEmpty()"))
        assertTrue(hook.contains("args[0] = replacement"))
        assertFalse(hook.contains("args[1] ="))
    }

    @Test fun serviceStateIsReadForTheActualSubscriptionWithCacheOnlyAsFallback() {
        val hook = source("main/java/com/mi/onextbox/lsp/CarrierNameHooker.kt")
        assertTrue(hook.contains("createForSubscriptionId(subId)"))
        assertTrue(hook.contains("getServiceState(TelephonyManager.INCLUDE_LOCATION_DATA_NONE)"))
        assertTrue(hook.contains("checkSelfPermission(Manifest.permission.READ_PHONE_STATE)"))
        assertTrue(hook.contains("val cached = if (current == null)"))
        assertTrue(hook.contains("CarrierNameRules.registration(cached, current)"))
        assertTrue(hook.contains("services?.get(subId)"))
        assertTrue(hook.contains("\"isEmergencyOnly\""))
        assertFalse(hook.contains("getDefaultDataSubscriptionId"))
        assertFalse(hook.contains("services?.get(slot)"))
    }

    @Test fun finalMergeUsesACloneAndKeepsNativeFilteringOrderingAndWarnings() {
        val hook = source("main/java/com/mi/onextbox/lsp/CarrierNameHooker.kt")
            .substringAfter("\"\$TAG:finalMerge\"").substringBefore("// C17's home status bar")
        assertTrue(hook.contains("val slot = info.simSlotIndex"))
        assertTrue(hook.contains("slot !in 0..1"))
        assertTrue(hook.contains("next[slot].isNullOrEmpty()"))
        assertTrue(hook.contains("Array<CharSequence?>(original.size) { original[it] as? CharSequence }"))
        assertTrue(hook.contains("\"isEmergencyOnlyBySlot\", count, slot, emergencyUpdate"))
        assertTrue(hook.contains("if (emergency) continue"))
        assertTrue(hook.contains("scope.resolve(slot, info.subscriptionId)"))
        assertTrue(hook.contains("val args = chain.args.toTypedArray()"))
        assertTrue(hook.contains("args[1] = names"))
        assertTrue(hook.contains("chain.proceed(args)"))
        assertFalse(hook.contains("args[0] ="))
        assertFalse(hook.contains("args[2] ="))
        assertFalse(hook.contains("setObjectField"))
        assertFalse(hook.contains("original[slot] ="))
    }

    @Test fun dualUiEditorsUseStableAnimatedRowsAndAppLifetimeWrites() {
        val ui = source("main/java/com/mi/onextbox/ui/screens/CarrierNameSettingsGroup.kt")
        assertTrue(ui.contains("FeatureAnimatedGroup(animateChanges = true)"))
        assertTrue(ui.contains("row(key, visible = state.enabled)"))
        assertTrue(ui.contains("hasDividerAbove = above, hasDividerBelow = below"))
        assertTrue(ui.contains("CouiConfirmDialog("))
        assertFalse(ui.contains("OutlinedTextField(state = input"))
        assertTrue(ui.contains("CouiTextField(state = input"))
        assertTrue(ui.contains("FeatureSettingWriteQueue.application.enqueue(write)"))
        assertTrue(ui.contains("CarrierNameSettings.resetNames(application)"))
        assertTrue(ui.contains("prefs.registerOnSharedPreferenceChangeListener(listener)"))
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/SystemUiFeaturesPage.kt").contains("CarrierNameSettingsGroup()"))
    }

    @Test fun searchEnabledFeaturesAndDefaultResetIncludeTheSetting() {
        for (file in listOf("FeatureSearchIndex.kt", "EnabledFeatureCatalog.kt", "EnabledFeatureActions.kt")) {
            assertTrue(file, source("main/java/com/mi/onextbox/ui/screens/$file").contains("R.string.carrier_name_title"))
        }
        val setting = source("main/java/com/mi/onextbox/lsp/CarrierNameSettings.kt")
        assertTrue(setting.contains("it.getBoolean(ENABLED, false)"))
        assertTrue(setting.contains("LspConfig.syncReadableState(context)"))
        val reset = setting.substringAfter("fun resetNames(")
        assertFalse(reset.contains("putBoolean(ENABLED"))
        assertTrue(reset.contains("remove(SIM1).remove(SIM2)"))
        // This store is already included in whole-config export, import and default reset.
        assertTrue(source("main/java/com/mi/onextbox/ui/common/ConfigBackup.kt").contains("LSP_PREFS_NAME"))
        assertTrue(source("main/java/com/mi/onextbox/lsp/LspConfig.kt").substringAfter("fun resetToDefaults(").contains("prefs(context).edit().clear().commit()"))
    }

    @Test fun everySupportedLocaleHasAllCarrierNameStringsAndTheSameLimitPlaceholder() {
        val keys = listOf("group", "title", "summary", "sim1", "sim2", "original", "hint", "reset").map { "carrier_name_$it" }
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val file = listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml")).first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            val strings = (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
            keys.forEach { key ->
                assertTrue("$locale/$key", strings[key]?.isNotBlank() == true)
                assertFalse("$locale/$key", strings.getValue(key).endsWith(".") || strings.getValue(key).endsWith("。"))
            }
            assertTrue(strings.getValue("carrier_name_hint").contains("%1\$d"))
        }
    }
}
