package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePayHookContractTest {
    private fun source(path: String): String =
        listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()

    @Test fun cardHookResolvesTheRiskPresentationByMeaningAndReadsTheLivePreference() {
        val hook = source("main/java/com/mi/onextbox/lsp/SecurePayHooker.kt")
        assertTrue(hook.contains("usingStrings(SecurePayReminderRules.RISK_CARD_LOG)"))
        assertTrue(hook.contains("paramTypes(\"java.util.List\")"))
        assertTrue(hook.contains("returnType = \"void\""))
        assertTrue(hook.contains("usingStrings(SecurePayReminderRules.LIVE_LOG_TAG, SecurePayReminderRules.LIVE_REFRESH_LOG)"))
        assertTrue(hook.contains("deoptimize(refresh.declaringClass.declaredMethods.toList())"))
        assertTrue(hook.contains("if (!SecurePaySettings.enabledXposed()) return@Hooker chain.proceed()"))
        listOf("SecurityScanService", "RiskState.FIXED", "setStatusBarFunction", "updateDetectionState",
            "ScanUICrossSceneHighRiskDialog", "dispatchFoundRisk").forEach { assertFalse(it, hook.contains(it)) }
    }

    @Test fun notificationsAreStillPostedWithUnmodifiedCallerOwnedObjects() {
        val hook = source("main/java/com/mi/onextbox/lsp/SecurePayHooker.kt")
            .substringAfter("private fun hookLiveNotification()")
        assertTrue(hook.contains("SecurePayReminderRules.shouldStripLiveAlert("))
        assertTrue(hook.contains("original.channelId"))
        assertTrue(hook.contains("original.clone()"))
        assertTrue(hook.contains("Bundle(original.extras ?: Bundle())"))
        assertTrue(hook.contains("putBoolean(SecurePayReminderRules.PROMOTION_EXTRA, false)"))
        assertTrue(hook.contains("param.args[index] = normal"))
        assertTrue(hook.contains("Build.VERSION.SDK_INT >= 36"))
        assertFalse(hook.contains("param.result ="))
        assertFalse(hook.contains("cancel("))
    }

    @Test fun newPageHasScopeNavigationSearchAndInventoryWiring() {
        val scope = source("main/resources/META-INF/xposed/scope.list")
        assertEquals(1, scope.lineSequence().count { it == "com.coloros.securepay" })
        assertTrue(source("main/AndroidManifest.xml").contains("<package android:name=\"com.coloros.securepay\" />"))
        val scoped = source("main/java/com/mi/onextbox/lsp/OosLspModuleEntry.kt")
            .substringAfter("SecurePaySettings.PACKAGE_NAME ->").substringBefore("GameAssistantSettings.PACKAGE_NAME")
        assertTrue(scoped.contains("SecurePayHooker.hook(classLoader, target.sourceDir)"))
        val routes = source("main/java/com/mi/onextbox/ui/Root.kt")
        assertTrue(routes.contains("subclass(RootRoute.FeatureSecurePay::class)"))
        assertTrue(routes.contains("entry<RootRoute.FeatureSecurePay>"))
        val screen = source("main/java/com/mi/onextbox/ui/screens/FeaturesScreen.kt")
        assertTrue(screen.contains("FeaturePageMode.SecurePay -> SecurePayFeaturesPage()"))
        assertTrue(screen.contains("FeaturePageMode.SecurePay -> listOf(SecurePaySettings.PACKAGE_NAME)"))
        listOf("FeatureSearchIndex.kt", "EnabledFeatureCatalog.kt", "EnabledFeatureActions.kt").forEach {
            assertTrue(it, source("main/java/com/mi/onextbox/ui/screens/$it").contains("R.string.secure_pay_hide_risk_cloud_title"))
        }
    }

    @Test fun everySupportedLanguageHasAllThreeNewStringsWithoutTerminalPeriods() {
        val locales = listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")
        val keys = setOf("secure_pay_title", "secure_pay_hide_risk_cloud_title", "secure_pay_hide_risk_cloud_summary")
        for (locale in locales) {
            val file = listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml"))
                .first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            val strings = (0 until nodes.length).map { nodes.item(it) }
                .filter { it.attributes.getNamedItem("name").nodeValue in keys }
            assertEquals(locale, 3, strings.size)
            strings.forEach {
                assertTrue(locale, it.textContent.isNotBlank())
                assertFalse(locale, it.textContent.endsWith(".") || it.textContent.endsWith("。"))
            }
        }
    }
}
