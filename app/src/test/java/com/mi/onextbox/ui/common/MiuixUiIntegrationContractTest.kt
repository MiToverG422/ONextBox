package com.mi.onextbox.ui.common

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class MiuixUiIntegrationContractTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun source(path: String) = File(app, "src/main/java/com/mi/onextbox/ui/$path.kt").readText()

    @Test fun officialStableAndroidArtifactIsPinnedInTheCatalogAndUsedByTheApp() {
        val root = app.absoluteFile.normalize().parentFile
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        assertTrue(catalog.contains("miuix = \"0.9.4\""))
        assertTrue(catalog.contains("top.yukonga.miuix.kmp:miuix-ui-android"))
        assertTrue(File(app, "build.gradle.kts").readText().contains("implementation(libs.miuix.ui)"))
    }

    @Test fun bothAppearanceMenusOfferTheNewStyleAndKeepAllExistingStyles() {
        val dropdown = source("settings/SettingsUiStyleDropdown")
        listOf("ui_style_coloros", "ui_style_material3_expressive", "ui_style_miuix").forEach { assertTrue(dropdown.contains(it)) }
        val wizard = source("onboarding/OnboardingScreen")
        assertTrue(Regex("AppUiStyle\\.Miuix to stringResource\\(R\\.string\\.ui_style_miuix\\)")
            .findAll(wizard).count() >= 2)
        assertTrue(wizard.contains("AppUiStyle.Miuix -> R.string.ui_style_miuix"))
    }

    @Test fun nativeThemePreservesAllThemeModesAndSharesItsPaletteWithLegacyContent() {
        val theme = source("common/MiuixAppTheme")
        assertTrue(theme.contains("ColorSchemeMode.valueOf(mode.name)"))
        assertTrue(theme.contains("remember(mode, keyColor, paletteStyle, colorSpec)"))
        assertTrue(theme.contains("ThemePaletteStyle.entries.getOrNull(paletteStyle)"))
        assertTrue(theme.contains("ThemeColorSpec.entries.getOrNull(colorSpec)"))
        assertTrue(theme.contains("Material3ExpressiveAppTheme(mode = mode, keyColor = keyColor, use2026 = style == AppUiStyle.Material3Expressive)"))
        assertTrue(theme.contains("COUITheme(colors = if (active) compatibleColors else COUITheme.colorScheme)"))
        assertTrue(theme.contains("LocalIndication provides if (active) LocalIndication.current else originalIndication"))
        assertTrue(theme.contains("LocalOverscrollFactory provides if (active) LocalOverscrollFactory.current else originalOverscroll"))
        assertTrue(theme.indexOf("MiuixTheme(controller = controller)") > theme.indexOf("MaterialTheme(colorScheme = if (active)"))
        assertTrue(source("Root").contains("AppUiStyleAppTheme(style = appUiStyle"))
    }

    @Test fun commonSettingsComponentsUseNativeMiuixInsteadOfTheColorOsSwitchAndSlider() {
        assertTrue(source("settings/SettingsComponents").contains("top.yukonga.miuix.kmp.basic.Card("))
        assertTrue(source("settings/SettingsComponents").contains("top.yukonga.miuix.kmp.basic.SmallTitle("))
        assertTrue(source("settings/SettingsPreferenceRows").contains("top.yukonga.miuix.kmp.basic.Switch("))
        assertTrue(source("screens/FeatureSettingsSliders").contains("top.yukonga.miuix.kmp.basic.Slider("))
        assertTrue(source("screens/FeatureSettingsSliders").contains("if (!nativeMiuixHaptics) hapticTick()"))
        assertTrue(source("screens/FeatureSettingsSliders").contains("enabled = enabled && validRange"))
    }

    @Test fun styleChangesDoNotBranchOrReplaceTheRememberedNavigationContent() {
        val theme = source("common/MiuixAppTheme")
        val host = theme.substringAfter("Material3ExpressiveAppTheme(mode = mode, keyColor = keyColor, use2026 = style == AppUiStyle.Material3Expressive) {")
            .substringBefore("private fun MiuixAppTheme(")
        assertTrue(host.contains("MiuixAppTheme(style == AppUiStyle.Miuix"))
        assertFalse(host.contains("if (style"))
        assertFalse(host.contains("else content()"))
        assertTrue(theme.contains("content = content,"))
    }

    @Test fun dropdownKeepsDisabledSelectionsIndependentCallbacksAndCollapseBehavior() {
        val menu = source("settings/MiuixSettingsComponents")
        listOf("DropdownImpl(", "WindowListPopup(", "ListPopupColumn {", "enabled = item.enabled",
            "if (item.enabled && enabled && interactionEnabled)", "item.onClick?.invoke()", "if (collapseOnSelection) expand(false)",
            "onExpandedChange?.invoke(value)").forEach { assertTrue(it, menu.contains(it)) }
        assertTrue(source("settings/SettingsPreferenceRows").contains("switchEnabled && !hiddenHoldEnabled"))
    }

    @Test fun pageChromeKeepsBackScrollImeAndBottomInsetHandling() {
        val page = source("settings/MiuixSettingsPageSurface")
        listOf("SmallTopAppBar(", "TopAppBar(", "ScrollResetEffect(scrollResetKey)", "externalScrollState",
            ".imePadding()", ".consumeWindowInsets(", "bottomContentPadding", "onBack?.invoke()")
            .forEach { assertTrue(it, page.contains(it)) }
        assertTrue(source("layout/PageHost").contains("MiuixHomePage("))
        assertTrue(source("settings/SettingsPageSurface").contains("MiuixSettingsPageSurface("))
    }

    @Test fun navigationReusesExistingTabsAndPagerActionsWithoutCreatingSeparateRoutes() {
        val bar = source("layout/MiuixBottomNavigationBar")
        assertTrue(bar.contains("tabs.forEachIndexed"))
        assertTrue(bar.contains("tab.selectedIcon else tab.unselectedIcon"))
        assertTrue(bar.contains("stringResource(tab.titleRes)"))
        val root = source("Root")
        assertTrue(root.contains("MiuixBottomNavigationBar("))
        assertTrue(root.contains("Material3ExpressiveBottomNavigationBar("))
        assertTrue(root.contains("appUiStyle == AppUiStyle.ColorOs && liquidBottomBarSupported"))
    }

    @Test fun homeUsesActualSharedRuntimeStatusAndExistingDeviceInformationHelpers() {
        val home = source("home/HomeMiuix")
        listOf("HomeVersionInfo", "versionInfo.lsposedReady", "versionInfo.lsposedStatus",
            "LspMissingScopesNotice", "rememberAppVersionName()", "rememberDeviceMarketName()",
            "detectHomeRegionText(context)", "Build.FINGERPRINT", "onHeroLongPress()")
            .forEach { assertTrue(it, home.contains(it)) }
        assertTrue(source("home/HomeScreen").indexOf("MiuixHomeContent(") < source("home/HomeScreen").indexOf("HomeHeroCard("))
        listOf("Shell.cmd", "ProcessBuilder", "setprop", "Settings.Global.put").forEach { assertFalse(home.contains(it)) }
    }

    @Test fun homeHasTheStatusWatermarkAndIconInformationLayoutWithUntruncatedDetails() {
        val home = source("home/HomeMiuix")
        listOf("PressFeedbackType.Tilt", "Alignment.BottomEnd", "MiuixHomeInfoRow(",
            "Arrangement.spacedBy(24.dp)", "Column(Modifier.weight(1f)", "Icons.Rounded.Fingerprint")
            .forEach { assertTrue(it, home.contains(it)) }
        assertFalse(home.contains("TextOverflow.Ellipsis"))
        assertFalse(home.contains("maxLines = 1"))
        assertTrue(source("layout/MiuixHomePage").contains("padding(horizontal = 12.dp)"))
    }

    @Test fun homeDetailsFollowMaterialOrderAndHeroAlwaysUsesMarketName() {
        val home = source("home/HomeMiuix")
        val material = source("home/HomeMaterial3Expressive")
        val titleKeys = setOf("home_info_root", "home_status_lsp", "home_info_region",
            "home_info_android_api", "info_device_model", "home_info_system_version",
            "home_info_system_architecture", "home_info_system_fingerprint", "home_info_kernel_version")
        fun order(text: String) = Regex("R\\.string\\.(\\w+)")
            .findAll(text.substringAfter("val rows = listOf("))
            .map { it.groupValues[1] }.filter { it in titleKeys }.toList()
        assertEquals(9, order(home).size)
        assertEquals(order(material), order(home))
        assertTrue(home.contains("Text(marketName,"))
        assertFalse(home.contains("\"LSPosed\""))
    }

    @Test fun dialogsAndToolbarPopupsHaveTheirOwnNativeMiuixBranch() {
        assertTrue(source("common/CouiDialogs").contains("top.yukonga.miuix.kmp.window.WindowDialog("))
        assertTrue(source("common/ColorOsPopup").contains("top.yukonga.miuix.kmp.window.WindowListPopup("))
        assertTrue(source("common/ColorOsTopBarButton").contains("top.yukonga.miuix.kmp.basic.IconButton("))
    }

    @Test fun everySupportedLocaleHasExactlyOneNewBrandLabel() {
        listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja",
            "values-ja-rCN", "values-ko", "values-vi", "values-ru").forEach { locale ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(app, "src/main/res/$locale/strings.xml")).getElementsByTagName("string")
            val labels = (0 until nodes.length).map { nodes.item(it) }
                .filter { it.attributes.getNamedItem("name").nodeValue == "ui_style_miuix" }
            assertEquals(locale, 1, labels.size)
            assertEquals("Miuix（Testing）", labels.single().textContent)
        }
    }
}
