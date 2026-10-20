package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.FluidCloudBrowserSettings
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRestartPlanTest {
    @Test fun appOnlyPlansDeduplicateTargetsWithoutRebootingByDefault() {
        val plan = FeatureRestartPlan(listOf("com.android.settings", "", "com.android.settings"))
        assertEquals(listOf("com.android.settings"), plan.appPackages)
        assertFalse(plan.includesFramework)
        assertFalse(plan.requiresDeviceReboot)
        assertFalse(plan.shouldRebootDevice(requested = false))
        assertTrue(plan.shouldRebootDevice(requested = true))
    }

    @Test fun mixedPlansKeepFrameworkOutOfAppCommandsAndRequireExplicitRebootOptIn() {
        val plan = FeatureRestartPlan(listOf("android", "system", "com.android.settings"))
        assertEquals(listOf("com.android.settings"), plan.appPackages)
        assertTrue(plan.includesFramework)
        assertFalse(plan.requiresDeviceReboot)
        assertFalse(plan.shouldRebootDevice(requested = false))
        assertTrue(plan.shouldRebootDevice(requested = true))
    }

    @Test fun frameworkOnlyPlansCannotPretendToRestartAnAppProcess() {
        for (packages in listOf(listOf("system"), listOf("android"), listOf("android", "system"))) {
            val plan = FeatureRestartPlan(packages)
            assertTrue(plan.appPackages.isEmpty())
            assertTrue(plan.requiresDeviceReboot)
            assertTrue(plan.shouldRebootDevice(requested = false))
        }
    }

    @Test fun anEmptyPlanDoesNotRequireAReboot() {
        val plan = FeatureRestartPlan(emptyList())
        assertTrue(plan.appPackages.isEmpty())
        assertFalse(plan.includesFramework)
        assertFalse(plan.requiresDeviceReboot)
        assertFalse(plan.shouldRebootDevice(requested = false))
    }

    @Test fun settingsAppearanceRestartsSettingsAndIdentifiesItsFrameworkDependency() {
        for (mode in listOf(FeaturePageMode.Settings, FeaturePageMode.SettingsAppearance)) {
            val plan = FeatureRestartPlan(featureRestartPackages(mode))
            assertEquals(listOf("com.android.settings"), plan.appPackages)
            assertTrue(plan.includesFramework)
            assertFalse(plan.shouldRebootDevice(requested = false))
        }
        for (mode in listOf(FeaturePageMode.SettingsApps, FeaturePageMode.SettingsPermissions,
            FeaturePageMode.SettingsRegion, FeaturePageMode.SettingsAboutDevice)) {
            assertEquals(listOf("com.android.settings"), featureRestartPackages(mode))
        }
    }

    @Test fun nestedPageNamesDoNotChangeTheirApplicationScopes() {
        assertEquals(listOf("com.android.launcher"), featureRestartPackages(FeaturePageMode.Desktop))
        for (mode in listOf(FeaturePageMode.DesktopLayout,
            FeaturePageMode.DesktopRecent, FeaturePageMode.DesktopPages)) {
            assertEquals(featureRestartPackages(FeaturePageMode.Desktop), featureRestartPackages(mode))
        }
        assertEquals(listOf("com.android.systemui"), featureRestartPackages(FeaturePageMode.SystemUiControlCenter))
        assertEquals(listOf("com.android.launcher"),
            featureRestartPackages(FeaturePageMode.DesktopIcons))
        assertEquals(listOf("com.android.systemui", "com.coloros.colordirectservice", "com.oplus.metis"),
            featureRestartPackages(FeaturePageMode.SystemUiFluidCloud))
    }

    @Test fun dialogUsesActualExecutionTargetsRatherThanTheSubpageTitle() {
        val source = file("main/java/com/mi/onextbox/ui/screens/FeaturesScreen.kt").readText()
        val invocation = source.substringAfter("val modeToRestart = restartConfirmMode")
            .substringBefore("private fun Modifier.extendPastBottom")
        assertTrue(invocation.contains("title = stringResource(R.string.feature_restart_scope_confirm_title)"))
        assertFalse(invocation.contains("featurePageTitle(modeToRestart)"))
        val dialog = source.substringAfter("private fun FeatureRestartConfirmDialog(")
            .substringBefore("private fun FeatureMainPage(")
        assertTrue(dialog.contains("FeatureRestartPlan(displayedTargets)"))
        assertTrue(dialog.contains("plan.appPackages.map"))
        assertTrue(dialog.contains("packages = targets"))
        assertTrue(dialog.contains("val displayedTargets = if (show) targets else lastVisibleTargets"))
        assertTrue(dialog.contains("if (show) lastVisibleTargets = targets"))
        assertFalse(dialog.contains("if (!show) emptyList()"))
        assertTrue(dialog.contains("remember(displayedTargets) { mutableStateOf(false) }"))
        assertTrue(dialog.contains("if (!plan.requiresDeviceReboot)"))
        assertTrue(dialog.contains("R.string.feature_restart_framework_only_summary"))
        assertTrue(dialog.contains("R.string.feature_restart_device_action"))
        assertTrue(dialog.contains("plan.shouldRebootDevice(restartSystem)"))
        assertTrue(dialog.contains("heightIn(max = 240.dp)"))
        assertTrue(dialog.contains("verticalScroll(rememberScrollState())"))
    }

    @Test fun confirmationStringsAreCompleteWithoutStaleTitlePlaceholdersInEveryLocale() {
        val keys = listOf("feature_restart_scope_confirm_title", "feature_restart_scope_confirm_summary",
            "feature_restart_framework_notice", "feature_restart_framework_only_summary",
            "feature_restart_device_summary", "feature_restart_device_action", "feature_restart_system_option")
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-vi", "values-ru")) {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(file("main/res/$locale/strings.xml")).getElementsByTagName("string")
            val strings = (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
            for (key in keys) {
                assertTrue("$locale:$key", strings[key]?.isNotBlank() == true)
                assertFalse("$locale:$key", strings.getValue(key).contains("%"))
            }
        }
    }

    private fun file(path: String): File = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }
}
