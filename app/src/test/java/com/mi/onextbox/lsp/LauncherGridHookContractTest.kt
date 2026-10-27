package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LauncherGridHookContractTest {
    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()

    @Test fun freshDisabledScopeDoesNotResolveOrInstallHooks() {
        val text = source("lsp/LauncherGridApplySession.kt")
        val guard = text.indexOf("if (!LauncherGridSettings.stateXposed().enabled && saved?.getOrNull(0) == null) return")
        assertTrue(guard >= 0 && guard < text.indexOf("LauncherGridHooker.installMenuHooks"))
        assertTrue(guard < text.indexOf("ModernHookRegistry.installFast"))
        assertTrue(text.contains("if (!state.enabled) return"))
    }

    @Test fun customGridExtendsTheSharedNativeListAndNeverWritesTheLayoutDatabase() {
        val text = source("lsp/LauncherGridHooker.kt")
        assertTrue(text.contains("building.get()?.grid ?: state().takeIf { it.enabled && allowed() }?.grid"))
        assertTrue(text.contains("LauncherGridRules.expand(options, maximum)"))
        assertTrue(text.contains("install(\"supported\", targets.supported"))
        assertTrue(text.contains("if (result == true) result else runCatching"))
        assertTrue(text.contains("targets.allowed()"))
        assertTrue(text.contains("val result = chain.proceed()"))
        for (write in listOf("saveLayoutInfo", "saveLayoutXY", "setColumnsAndRows", "SQLiteDatabase", "Settings.Global.put", "setOnClickListener")) {
            assertFalse(write, text.contains(write))
        }
    }

    @Test fun hotReloadHasAnOwnerAndVetoesMenusAndNativeMigration() {
        val text = source("lsp/LauncherGridHooker.kt")
        assertTrue(text.contains("capture = session::capture"))
        assertTrue(text.contains("detach = session::detach"))
        assertTrue(text.contains("canReload = session::canReload"))
        val session = source("lsp/LauncherGridApplySession.kt")
        assertTrue(session.contains("queue.running == null && menus?.canReload() != false"))
        assertTrue(session.contains("arrayOf(targets?.values, menus?.capture(), native?.values, queue.capture())"))
        assertTrue(session.contains("unregisterOnSharedPreferenceChangeListener(listener)"))
        assertTrue(session.contains("main.removeCallbacks(update)"))
        assertTrue(text.contains("isAttachedToWindow"))
        assertTrue(text.contains("targets.busy.none"))
        assertTrue(text.contains("intArrayOf(pinned.grid.columns, pinned.grid.rows"))
        assertFalse(text.contains("arrayOf(adapter, pinned)"))
    }

    @Test fun settingsUseOneAtomicValueAndTheApplicationWriteQueue() {
        val settings = source("lsp/LauncherGridSettings.kt")
        assertTrue(settings.contains("putString(CONFIG, LauncherGridRules.encode(state))"))
        assertTrue(settings.contains("editor.putString(REQUEST, LauncherGridApplyRequest.create"))
        assertTrue(settings.contains("else editor.remove(REQUEST)"))
        assertTrue(settings.contains("check(editor.commit())"))
        assertTrue(settings.contains("HookConfigSnapshot.string(CONFIG, null)"))
        assertTrue(settings.contains("LspConfig.syncReadableState(context)"))
        val ui = source("ui/screens/LauncherGridSettingsGroup.kt")
        assertTrue(ui.contains("FeatureSettingWriteQueue.application.enqueue"))
        assertEquals(2, Regex("onValueChangeFinished = ").findAll(ui).count())
        assertFalse(ui.contains("SettingsCardRow"))
        assertFalse(ui.contains("launcher_custom_grid_summary"))
        assertTrue(ui.contains("summary = \"\""))
        assertTrue(source("lsp/LspConfig.kt").contains("prefs(context).edit().clear().commit()"))
    }

    @Test fun entryAndSearchUseDesktopLayout() {
        val page = source("ui/screens/DesktopFeaturesPage.kt")
        assertTrue(page.substringAfter("mode == FeaturePageMode.DesktopLayout")
            .substringBefore("mode == FeaturePageMode.DesktopRecent").contains("LauncherGridSettingsGroup()"))
        assertTrue(source("ui/screens/FeatureSearchIndex.kt").contains(
            "Entry(FeaturePageMode.DesktopLayout, R.string.launcher_custom_grid_title)"))
        val group = page.substringAfter("mode == FeaturePageMode.DesktopLayout").substringBefore("SettingsSection(title = stringResource(R.string.launcher_group_widgets))")
        assertEquals(1, Regex("SettingsGroup ").findAll(group).count())
        assertFalse(source("ui/screens/LauncherGridSettingsGroup.kt").contains("SettingsGroup {"))
        assertTrue(source("lsp/LauncherFeaturesHooker.kt").contains("LauncherGridHooker.hook(classLoader, apkPath)"))
    }

    @Test fun previewResolutionUsesRawDexReferencesInsteadOfJadxAliases() {
        val targets = source("lsp/LauncherGridTargets.kt")
        assertTrue(targets.contains("LauncherGridPreviewFields.resolve(previewType"))
        assertTrue(targets.contains("drawData.usingFields.map { it.field.getFieldInstance(loader) }"))
        assertFalse(targets.contains("f14740t"))
        assertFalse(targets.contains("previewType.getDeclaredField"))
        assertTrue(source("lsp/LauncherGridPreviewFields.kt").contains("referenced.distinct()"))
    }

    @Test fun validationTargetsUseTheSharedListAndUpgradeOldHotReloadMetadata() {
        val targets = source("lsp/LauncherGridTargets.kt")
        assertTrue(targets.contains("val supported = options.callers.single"))
        assertTrue(targets.contains("it.paramTypeNames == listOf(\"int\", \"int\")"))
        assertTrue(targets.contains("invoked.descriptor == wordlessData.descriptor"))
        assertTrue(targets.contains("options.callers + supported.callers"))
        assertTrue(source("lsp/LauncherGridHooker.kt").contains("it.size == LauncherGridTargets.SCHEMA_SIZE"))
    }

    @Test fun everySupportedLocaleHasCompleteAndConsistentResources() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val names = mutableSetOf<Set<String>>()
        for (folder in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val strings = parser.parse(File(root, "$folder/strings.xml")).getElementsByTagName("string")
            val gridNames = (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }
                .filter { it.startsWith("launcher_custom_grid_") }.toSet()
            assertEquals(folder, 7, gridNames.size)
            names += gridNames
        }
        assertEquals(1, names.size)
    }

    @Test fun directApplyUsesOnlyExplicitFreshRequestsAndNativeMigration() {
        val session = source("lsp/LauncherGridApplySession.kt")
        assertTrue(session.contains("val values = preferences?.all"))
        assertTrue(session.contains("isCurrent(SystemClock.elapsedRealtime(), state)"))
        assertTrue(session.contains("queue.running != null || queue.pending == null"))
        assertTrue(session.contains("target.apply.invoke(null, host, applying.grid.columns, applying.grid.rows, false)"))
        assertTrue(session.contains("chain.thisObject === task && task != null"))
        assertTrue(session.contains("target.ready(host)"))
        assertFalse(session.contains("sendBroadcast"))
        assertFalse(session.contains("SQLiteDatabase"))
        assertFalse(session.contains("saveLayoutInfo"))
        val targets = source("lsp/LauncherGridApplyTargets.kt")
        assertTrue(targets.contains("getDeclaredMethod(\"applyLayoutSettings\", *signature)"))
        assertTrue(targets.contains("getDeclaredMethod(\"onPostExecute\", Any::class.java)"))
        assertTrue(targets.contains("current === normal.get(null) || current === background.get(null)"))
        assertTrue(targets.contains("dragging.invoke"))
    }

    @Test fun requestIsNeverExportedOrImportedByConfigurationBackup() {
        val text = source("ui/common/ConfigBackup.kt")
        assertTrue(text.contains("values.filterKeys { it != LauncherGridSettings.REQUEST }"))
        assertTrue(text.contains("if (key == LauncherGridSettings.REQUEST) return@forEach"))
    }
}
