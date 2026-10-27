package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FluidCloudMediaHookContractTest {
    @Test fun onlyTheDedicatedMediaIslandPipelineIsFiltered() {
        val hook = source("lsp/FluidCloudMediaBlacklistHooker.kt")
        val targets = source("lsp/FluidCloudMediaTargets.kt")
        assertTrue(targets.contains("com.oplus.systemui.media.seedling.OplusMediaSeedlingController"))
        assertTrue(targets.contains("getDeclaredMethod(\"getSortedEntries\")"))
        assertTrue(hook.contains("FluidCloudMediaRules.filter(entries"))
        assertFalse(hook.contains("onMediaDataRemoved"))
        assertFalse(hook.contains("MediaSessionManager"))
        assertFalse(hook.contains("NotificationManager"))
        assertFalse(hook.contains("setMediaControlBlackList"))
        assertFalse(hook.contains("oplus_media_controller_config_sp"))
    }

    @Test fun disabledColdStartDoesNotInstallNativeHooksOrScanDex() {
        val hook = source("lsp/FluidCloudMediaBlacklistHooker.kt")
        assertTrue(hook.contains("if (saved == null && !FluidCloudMediaSettings.isEnabledXposed()) return"))
        assertTrue(hook.indexOf("if (saved == null &&") < hook.indexOf("FluidCloudMediaTargets.resolve"))
        assertTrue(hook.contains("if (targets == null) install(null)"))
    }

    @Test fun preferenceChangesRefreshNativeIslandsWithoutDeletingMediaClients() {
        val hook = source("lsp/FluidCloudMediaBlacklistHooker.kt")
        assertTrue(hook.contains("key == FluidCloudMediaSettings.ENABLED"))
        assertTrue(hook.contains("key == FluidCloudMediaSettings.PACKAGES"))
        assertTrue(hook.contains("main.postDelayed(refresh, 80L)"))
        assertTrue(hook.contains("native.refresh.invoke(owner, 0"))
        assertTrue(hook.contains("unregisterOnSharedPreferenceChangeListener(listener)"))
        assertTrue(hook.contains("HotReloadLifecycle.withHook<Unit>"))
        assertFalse(hook.contains("mediaClients.remove"))
    }

    @Test fun replacementReplaysHooksAndExportsOnlyHostObjectsAndReflectiveTargets() {
        val hook = source("lsp/FluidCloudMediaBlacklistHooker.kt")
        assertTrue(hook.contains("HotReloadLifecycle.restoredState(stateKey)"))
        assertTrue(hook.contains("session.install(saved?.getOrNull(0) as? Array<*>)"))
        assertTrue(hook.contains("arrayOf(targets?.values,"))
        assertTrue(hook.contains("seedlings.keys.toTypedArray()"))
        assertTrue(hook.contains("resumeAfterRejection = session::schedule"))
        assertTrue(hook.contains("main.removeCallbacks(refresh)"))
        assertTrue(hook.contains("if (saved == null) session.restore(null)"))
    }

    @Test fun signaturesAreCheckedAndInlinedCallersAreDeoptimized() {
        val targets = source("lsp/FluidCloudMediaTargets.kt")
        assertTrue(targets.contains("List::class.java.isAssignableFrom(entries.returnType)"))
        assertTrue(targets.contains("packageName.returnType == String::class.java"))
        assertTrue(targets.contains("handler.getDeclaredMethod(\"handleMessage\", Message::class.java)"))
        assertTrue(targets.contains("seedling.getDeclaredMethod(\"start\")"))
        assertFalse(targets.contains("DexKitBridge"))
        val hook = source("lsp/FluidCloudMediaBlacklistHooker.kt")
        assertTrue(hook.contains("deoptimize(it)"))
        assertTrue(hook.contains("ModernHookRegistry.unhookPrefix(prefix)"))
        assertTrue(hook.contains("if (targets == null) return@Hooker original"))
    }

    @Test fun pausedExistingIslandsAreDiscoveredWithoutCreatingNewDependencies() {
        val targets = source("lsp/FluidCloudMediaTargets.kt")
        assertTrue(targets.contains("\"mDependencies\""))
        assertTrue(targets.contains("\"mObservers\""))
        assertTrue(targets.contains("it.type == controllerType"))
        assertTrue(targets.contains("owner?.takeIf(seedlingType::isInstance)"))
        assertFalse(targets.contains("getDependencyInner"))
        assertTrue(source("lsp/FluidCloudMediaBlacklistHooker.kt").contains("native.existingSeedlings(loader)"))
    }

    @Test fun appPickerHasNativeNavigationSearchAndFrozenPackageVisibility() {
        val root = source("ui/Root.kt")
        assertTrue(root.contains("entry<RootRoute.FeatureSystemUiFluidCloudMediaBlacklist>"))
        val picker = source("ui/screens/FluidCloudMediaBlacklistPage.kt")
        assertTrue(picker.contains("SettingsPageSurface("))
        assertTrue(picker.contains("LazyColumn("))
        assertTrue(picker.contains("FeatureSearchBar(search"))
        assertTrue(picker.contains("SettingsSwitchPreference("))
        assertTrue(source("ui/screens/MediaBlacklistListModel.kt").contains("selected.filterNot { it in known }"))
        assertTrue(picker.contains("FluidCloudMediaRules.isPackageName(it)"))
        val manifest = File(mainDir(), "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertTrue(manifest.contains("QUERY_ALL_PACKAGES"))
        assertTrue(picker.contains("manageableApplications(pm)"))
        assertTrue(source("ui/screens/NotificationBatchPage.kt").contains("manageableApplications(pm)"))
        val catalog = source("ui/screens/ManageableAppCatalog.kt")
        assertTrue(catalog.contains("MATCH_DISABLED_COMPONENTS"))
        assertTrue(catalog.contains("MATCH_UNINSTALLED_PACKAGES"))
        assertTrue(catalog.contains("ApplicationInfo.FLAG_INSTALLED"))
        assertFalse(catalog.contains("info.enabled"))
    }

    @Test fun disabledPreferenceKeepsTheListAndDefaultIsOptIn() {
        val settings = source("lsp/FluidCloudMediaSettings.kt")
        val toggle = settings.substringAfter("fun setEnabled").substringBefore("fun setPackages")
        assertFalse(toggle.contains("putStringSet"))
        assertFalse(toggle.contains("remove("))
        assertTrue(settings.contains("getBoolean(ENABLED, false)"))
        assertTrue(settings.contains("HookConfigSnapshot.stringSet(PACKAGES, emptySet())"))
        assertTrue(settings.contains("else emptySet()"))
    }

    @Test fun appRowsStayLazyAndUseTheFilteredListForJoinedCardBoundaries() {
        val picker = source("ui/screens/FluidCloudMediaBlacklistPage.kt")
        assertTrue(picker.contains("itemsIndexed(listed, key = { _, app -> app.packageName })"))
        assertTrue(picker.contains("SettingsLazyGroupItem(index = index, count = listed.size,"))
        assertTrue(picker.contains("remember(apps, selected, query, locale)"))
        assertTrue(picker.contains("mediaBlacklistApps(apps, selected, query, locale)"))
        assertFalse(picker.contains("SettingsGroup(bottomPadding = 8.dp)"))
        assertFalse(picker.contains("SettingsListGroup("))
    }

    @Test fun joinedRowsUseStyleSpecificShapeAndDividerRules() {
        val group = source("ui/settings/SettingsListGroup.kt")
            .substringAfter("internal fun SettingsLazyGroupItem(")
            .substringBefore("/** Joined list cards")
        assertFalse(group.contains("AppUiStyle.Material3Expressive"))
        assertFalse(group.contains("AppUiStyle.Miuix"))
        assertTrue(group.contains("val cornerRadius = AppUiTokens.CardCornerRadius"))
        assertTrue(group.contains("if (index == 0) cornerRadius else 0.dp"))
        assertTrue(group.contains("if (last) cornerRadius else 0.dp"))
        assertTrue(group.contains("squircleSurface(colors.color, top, top, bottom, bottom)"))
        assertTrue(group.contains("if (!last) SettingsDivider()"))
        assertTrue(group.contains("if (last) groupBottom else 0.dp"))
        assertTrue(group.contains("animateDpAsState("))
        assertTrue(group.contains("spring(dampingRatio = 1f, stiffness = 800f)"))
        assertFalse(group.contains("animateContentSize"))
    }

    @Test fun reorderingAnimatesPlacementWithoutHeightAnimationOrForcedScrollingToTheTop() {
        val picker = source("ui/screens/FluidCloudMediaBlacklistPage.kt")
        assertTrue(picker.contains("state = listState"))
        assertTrue(picker.contains("mediaBlacklistItemAnimation(raised = app.packageName == movingPackage)"))
        assertTrue(picker.contains("placementSpec = tween(280, easing = FastOutSlowInEasing)"))
        assertTrue(picker.contains(".zIndex(if (raised) 1f else 0f)"))
        assertFalse(picker.contains("animateContentSize"))
        assertFalse(picker.contains("animateScrollToItem"))
        val update = picker.substringAfter("fun updateSelected(").substringBefore("fun change(")
        assertTrue(update.contains("if (updated == selected) return"))
        assertTrue(update.contains("requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)"))
        assertTrue(update.indexOf("requestScrollToItem") < update.indexOf("selected = updated"))
        assertTrue(picker.contains("scope.launch { updateSelected(readPackages(context)) }"))
        assertTrue(picker.contains("FluidCloudMediaSettings::packages, FluidCloudMediaSettings::setPackages"))
    }

    @Test fun blacklistDescriptionIncludesTheRestoredUnaffectedPlaybackSuffixInEveryLocale() {
        val suffixes = mapOf("values" to "without affecting", "values-zh-rCN" to "不影响", "values-ja-rCN" to "不影响",
            "values-zh-rTW" to "不影響", "values-b+yue+Hant" to "唔影響", "values-ja" to "影響しない",
            "values-ko" to "영향", "values-ru" to "не затрагивая", "values-vi" to "không ảnh hưởng")
        File(mainDir(), "res").listFiles()!!.filter { it.name.startsWith("values") && File(it, "strings.xml").isFile }.forEach { dir ->
            val summary = File(dir, "strings.xml").readText()
                .substringAfter("<string name=\"fluid_media_blacklist_summary\">").substringBefore("</string>")
            assertTrue(dir.name, summary.contains(suffixes.getValue(dir.name)))
        }
        assertTrue(File(mainDir(), "res/values-zh-rCN/strings.xml").readText().contains(
            "<string name=\"fluid_media_blacklist_summary\">禁止所选应用的音乐及播放信息显示在流体云，不影响播放和控制中心播放卡片</string>"))
    }

    @Test fun translationsAndSearchEntriesCoverEveryNewString() {
        val resourceDir = File(mainDir(), "res")
        val keys = listOf("group", "blacklist_title", "blacklist_summary", "apps_title", "selected_count", "search_hint",
            "save_failed", "loading", "load_failed", "no_results", "add_package")
        resourceDir.listFiles()!!.filter { it.name.startsWith("values") && File(it, "strings.xml").isFile }.forEach { dir ->
            val text = File(dir, "strings.xml").readText()
            keys.forEach { assertTrue("${dir.name}: $it", text.contains("name=\"fluid_media_$it\"")) }
            assertTrue(text.contains("%1\$d"))
        }
        assertTrue(source("ui/screens/FeatureSearchIndex.kt").contains("R.string.fluid_media_blacklist_title"))
    }

    private fun mainDir() = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(mainDir(), "java/com/mi/onextbox/$path").readText().replace("\r\n", "\n")
}
