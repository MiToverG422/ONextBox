package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class StatusBarOrderPageContractTest {
    private fun source(path: String) = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()
    @Test fun editorLivesOnASerializedNestedPageWithTheSystemUiRestartScope() {
        val screen = source("main/java/com/mi/onextbox/ui/screens/FeaturesScreen.kt")
        assertTrue(screen.contains("SystemUiIconOrder(true)"))
        assertTrue(screen.contains("FeaturePageMode.SystemUiIconOrder -> StatusBarIconOrderPage()"))
        assertTrue(screen.substringAfter("internal fun featureRestartPackages").contains("FeaturePageMode.SystemUiIconOrder,"))
        val root = source("main/java/com/mi/onextbox/ui/Root.kt")
        assertTrue(root.contains("subclass(RootRoute.FeatureSystemUiIconOrder::class)"))
        assertTrue(root.contains("entry<RootRoute.FeatureSystemUiIconOrder>"))
        val parent = source("main/java/com/mi/onextbox/ui/screens/SystemUiFeaturesPage.kt")
        assertTrue(parent.contains("StatusBarLayoutSettingsGroup { onOpenSubPage(FeaturePageMode.SystemUiIconOrder) }"))
        val ui = source("main/java/com/mi/onextbox/ui/screens/UiCustomizationSettings.kt")
        val parentGroup = ui.substringAfter("internal fun StatusBarLayoutSettingsGroup").substringBefore("internal fun StatusBarIconOrderPage")
        assertFalse(parentGroup.contains("value.order.forEachIndexed"))
        assertTrue(parentGroup.contains("onClick = onOpenOrder"))
        assertTrue(ui.substringAfter("internal fun StatusBarIconOrderPage").contains("FeatureAnimatedGroup"))
    }
    @Test fun compactPageHasOneJoinedGroupWithResetButNoDuplicateMaster() {
        val page = source("main/java/com/mi/onextbox/ui/screens/UiCustomizationSettings.kt")
            .substringAfter("internal fun StatusBarIconOrderPage").substringBefore("private fun slotTitle")
        assertEquals(1, Regex("FeatureAnimatedGroup\\(").findAll(page).count())
        assertTrue(page.contains("FeatureAnimatedGroup(animateChanges = true)"))
        assertFalse(page.contains("row(\"enabled\")"))
        assertTrue(page.contains("row(\"reset\")"))
        assertTrue(page.contains("StatusBarLayoutSettings.resetOrder(context)"))
        assertFalse(page.contains("SettingsSection(title = stringResource(R.string.statusbar_sim_order_title))"))
        assertTrue(page.substringAfter("if (slot == \"mobile\")").contains("row(\"sim-$"))
        assertTrue(page.contains("StatusBarLayoutSettings.saveOrder(context, next)"))
        assertTrue(page.contains("StatusBarLayoutSettings.saveSimOrder(context, value.simEnabled, next)"))
    }
    @Test fun restoredResetCannotEraseTheMasterOrMargins() {
        val reset = source("main/java/com/mi/onextbox/lsp/StatusBarLayoutSettings.kt").substringAfter("fun resetOrder(")
        assertTrue(reset.contains("remove(ORDER).remove(SIM_ORDER).remove(SIM_ENABLED)"))
        listOf("remove(ORDER_ENABLED)", "MARGINS_ENABLED", "putInt(LEFT", "putInt(RIGHT", ".clear()").forEach {
            assertFalse(it, reset.contains(it))
        }
        assertTrue(reset.contains("LspConfig.syncReadableState(context)"))
    }
    @Test fun dualSimSortingReusesTheExistingHolderPipelineAndNeverChangesTheDataSim() {
        val hook = source("main/java/com/mi/onextbox/lsp/DataSimSignalHooker.kt")
        assertTrue(hook.contains("setNewMobileIconSubIds"))
        assertTrue(hook.contains("it.subscriptionId to it.simSlotIndex"))
        assertTrue(hook.contains("StatusBarLayoutRules.reorderSubscriptions"))
        assertTrue(hook.contains("DataSimSignalRules.select(ordered"))
        assertTrue(hook.contains("param.args[0] = ArrayList(selected)"))
        assertTrue(hook.contains("owner.remember(target, ids, selected)"))
        assertFalse(hook.contains("setDefaultDataSubscriptionId"))
        val config = source("main/java/com/mi/onextbox/lsp/StatusBarLayoutSettings.kt")
        assertTrue(config.contains("orderEnabledXposed() && HookConfigSnapshot.boolean(SIM_ENABLED, false)"))
    }
    @Test fun inventorySearchAndQuickEditsPointToTheChildAndKeepParentDependency() {
        val index = source("main/java/com/mi/onextbox/ui/screens/FeatureSearchIndex.kt")
        assertTrue(index.contains("Entry(FeaturePageMode.SystemUiStatusBarLayout, R.string.statusbar_order_title)"))
        assertTrue(index.contains("Entry(FeaturePageMode.SystemUiIconOrder, R.string.statusbar_sim1)"))
        val catalog = source("main/java/com/mi/onextbox/ui/screens/EnabledFeatureCatalog.kt")
        assertTrue(catalog.contains("Entry(R.string.statusbar_sim_order_title, FeaturePageMode.SystemUiIconOrder, parents = setOf(R.string.statusbar_order_title))"))
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/EnabledFeatureActions.kt").contains("R.string.statusbar_sim_order_title to"))
    }
    @Test fun simSlotQueryKeepsNativeOrderWhenSystemUiLacksPermission() {
        val hook = source("main/java/com/mi/onextbox/lsp/DataSimSignalHooker.kt")
        val query = hook.substringAfter("private fun subscriptionSlots(").substringBefore("private class Owner")
        assertTrue(query.contains("checkSelfPermission(Manifest.permission.READ_PHONE_STATE)"))
        assertTrue(query.contains("!= PackageManager.PERMISSION_GRANTED) return emptyMap()"))
        assertTrue(query.contains("catch (_: SecurityException)"))
        assertTrue(query.contains("catch (_: RuntimeException)"))
        assertFalse(source("main/AndroidManifest.xml").contains("android.permission.READ_PHONE_STATE"))
    }
    @Test fun newStringsAreCompleteInEverySupportedLanguage() {
        val keys = setOf("statusbar_order_settings_title", "statusbar_order_groups", "statusbar_sim_order_title",
            "statusbar_order_reset", "feature_only_data_sim_independent_title", "statusbar_sim1", "statusbar_sim2", "statusbar_icon_logging", "statusbar_icon_data_saver",
            "statusbar_icon_headset", "statusbar_icon_work_profile", "statusbar_icon_zen", "statusbar_icon_no_sim", "statusbar_icon_dynamic_info")
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val file = listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml")).first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            val strings = (0 until nodes.length).map { nodes.item(it) }.filter { it.attributes.getNamedItem("name").nodeValue in keys }
            assertEquals(locale, keys.size, strings.size)
            strings.forEach { assertTrue(locale, it.textContent.isNotBlank()); assertFalse(locale, it.textContent.endsWith(".") || it.textContent.endsWith("。")) }
        }
    }
    @Test fun requestedDescriptionsCannotAppearInUiOrSearch() {
        val keys = listOf("statusbar_order_summary", "statusbar_margins_summary", "statusbar_order_settings_summary",
            "statusbar_order_page_summary", "statusbar_order_reset_summary", "statusbar_sim_order_summary")
        for (path in listOf("main/java/com/mi/onextbox/ui/screens/UiCustomizationSettings.kt",
            "main/java/com/mi/onextbox/ui/screens/FeatureSearchIndex.kt", "main/res/values/strings.xml")) {
            val text = source(path)
            keys.forEach { assertFalse("$path: $it", text.contains(it)) }
        }
    }
}
