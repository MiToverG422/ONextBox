package com.mi.onextbox.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogsContractTest {
    @Test fun aboutEntryIsBetweenUpdateAndSettingsAndUsesAnIndependentRoute() {
        val page = mainFile("java/com/mi/onextbox/ui/settings/SettingsMainPage.kt").readText()
            .substringAfter("fun AboutMainPage(").substringBefore("private fun AboutGroupSpacer()")
        assertTrue(page.indexOf("R.string.about_logs_title") > page.indexOf("R.string.setting_software_update"))
        assertTrue(page.indexOf("R.string.setting_theme_settings") > page.indexOf("R.string.about_logs_title"))
        val root = mainFile("java/com/mi/onextbox/ui/Root.kt").readText()
        assertTrue(root.contains("AboutPageMode.Logs -> RootRoute.Logs"))
        assertTrue(root.contains("subclass(RootRoute.Logs::class)"))
        assertTrue(root.contains("entry<RootRoute.Logs> { RootAboutEntry(AboutPageMode.Logs) }"))
        val route = mainFile("java/com/mi/onextbox/ui/settings/SettingsScreen.kt").readText()
        assertTrue(route.contains("pageMode == AboutPageMode.Logs"))
        assertTrue(route.contains("AppLogsRoute("))
    }

    @Test fun bothUiStylesUseAVirtualizedListAndStyleSpecificComponents() {
        val page = mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText()
        assertTrue(page.contains("contentScrollable = false"))
        assertTrue(page.contains("LazyColumn("))
        assertTrue(page.contains("key = { it.key }"))
        assertFalse(page.contains("AppUiStyle.Material3Expressive"))
        assertTrue(page.contains("SettingsGroup"))
        assertTrue(page.contains("ColorOsTopBarButton("))
        assertTrue(page.contains("ColorOsDropdownPopup("))
        assertTrue(page.contains("CouiConfirmDialog("))
    }

    @Test fun actionsReuseNativePopupRowsAndProperlyGroupedMaterialShapes() {
        val page = mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText()
            .substringAfter("private fun RowScope.LogsTopBarActions(")
        assertTrue(page.contains("ColorOsDropdownPopup("))
        assertFalse(page.contains("MenuDefaults.standaloneItemShape"))
        assertTrue(page.contains("selectionMenu = false"))
        assertFalse(page.contains("heightIn(min = 48.dp)"))
        val popup = mainFile("java/com/mi/onextbox/ui/settings/ColorOsDropdownPreference.kt").readText()
        assertTrue(popup.contains("role = if (selectionMenu) Role.RadioButton else Role.Button"))
        assertTrue(popup.contains("if (selectionMenu) this.selected = selected"))
        assertTrue(popup.contains("sharedMaskOffset.animateTo("))
        assertTrue(popup.contains(".height(40.dp)"))
    }

    @Test fun logExpansionAnimatesOutgoingTextAndHeightWithReadableMetadata() {
        val page = mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText()
            .substringAfter("internal fun LogRecordCard(").substringBefore("private fun RowScope.LogsTopBarActions(")
        assertTrue(page.contains("AnimatedContent("))
        assertTrue(page.contains("SizeTransform(clip = true) { _, _ -> sizeSpec }"))
        assertTrue(page.contains("spring<IntSize>("))
        assertTrue(page.contains("fadeIn(enterFade) togetherWith fadeOut(exitFade)"))
        assertTrue(page.contains("animateFloatAsState("))
        assertTrue(page.contains("if (canExpand) onClick()"))
        assertTrue(page.contains("it.hasVisualOverflow || it.lineCount > LogPreviewLines"))
        assertTrue(page.contains("private const val LogPreviewLines = 4"))
        assertTrue(page.contains("record.origin.ifEmpty"))
        assertTrue(page.contains("LogText(record.timestamp"))
        assertTrue(page.contains("stateDescription = expansionDescription"))
    }

    @Test fun expandingRowsCannotRunASecondPlacementAnimationOrDrawIntoTheFollowingCard() {
        val page = mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText()
        val list = page.substringAfter("items(visibleRecords, key = { it.key })").substringBefore("CouiConfirmDialog(")
        assertTrue(list.contains("modifier = logRecordListAnimation()"))
        val animation = page.substringAfter("internal fun LazyItemScope.logRecordListAnimation()")
            .substringBefore("internal fun LogLevelBadge(")
        assertTrue(animation.contains("placementSpec = null"))
        assertFalse(animation.contains("placementSpec = spring("))
        assertTrue(page.contains("Box(modifier.clipToBounds())"))
        assertTrue(page.contains("SizeTransform(clip = true)"))
    }

    @Test fun levelBadgesCenterTextWithoutTheCouiMainStyleFontPadding() {
        val badge = mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText()
            .substringAfter("internal fun LogLevelBadge(").substringBefore("private fun LogText(")
        assertTrue(badge.contains("contentAlignment = Alignment.Center"))
        assertTrue(badge.contains("textAlign = TextAlign.Center"))
        assertTrue(badge.contains("PlatformTextStyle(includeFontPadding = false)"))
        assertTrue(badge.contains("LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both"))
        assertTrue(badge.contains(".heightIn(min = 24.dp)"))
        assertFalse(badge.contains("MaterialTheme.typography.labelSmall"))
        assertTrue(badge.contains("COUITheme.textStyles.body2"))
    }

    @Test fun readingFrameworkLogsIsBoundedAndReadOnlyAndCannotClearOtherModules() {
        val reader = mainFile("java/com/mi/onextbox/ui/common/ModuleLogReader.kt").readText()
        assertTrue(reader.contains("Shell.getCachedShell()"))
        assertTrue(reader.contains("tail -c 1048576"))
        listOf("rm ", "truncate ", "setprop ", "Shell.getShell(").forEach { assertFalse(reader.contains(it)) }
        val page = mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText()
        assertTrue(page.contains("showClear = source == LogSource.APP"))
        assertTrue(page.contains("AppLogStore.clear()"))
        assertFalse(page.contains("/data/adb/"))
        val export = mainFile("java/com/mi/onextbox/ui/common/LogExport.kt").readText()
        assertTrue(export.contains("file.parentFile == context.cacheDir.canonicalFile"))
    }

    @Test fun everyLocaleIncludesAllLogPageLabelsWithMatchingPlaceholders() {
        val locales = listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")
        val keys = Regex("R.string.((?:about_logs|logs_)[a-z_]*)").findAll(
            mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText(),
        ).map { it.groupValues[1] }.toSet()
        assertTrue(keys.size >= 30)
        for (locale in locales) {
            val xml = mainFile("res/$locale/strings.xml").readText()
            for (key in keys) assertEquals("$locale: $key", 1, Regex("name=\"$key\"").findAll(xml).count())
            val count = Regex("name=\"logs_count\">([^<]+)").find(xml)!!.groupValues[1]
            assertTrue(count.contains("%1\$d") && count.contains("%2\$d"))
        }
    }

    @Test fun logPageOmitsInstructionHintsButKeepsRecordCounts() {
        val page = mainFile("java/com/mi/onextbox/ui/settings/AppLogsPage.kt").readText()
        assertFalse(page.contains("item(key = \"hint\")"))
        assertFalse(page.contains("R.string.logs_app_hint"))
        assertFalse(page.contains("R.string.logs_module_hint"))
        assertTrue(page.contains("R.string.logs_count"))
    }

    private fun mainFile(path: String): File = listOf(File("src/main/$path"), File("app/src/main/$path"))
        .first { it.isFile }
}
