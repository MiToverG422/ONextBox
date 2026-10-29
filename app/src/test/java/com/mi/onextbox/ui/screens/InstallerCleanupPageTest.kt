package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.InstallerCleanupSettings
import com.mi.onextbox.lsp.ModuleHotReloadPolicy
import com.mi.onextbox.lsp.SystemAdaptationVersion
import org.junit.Assert.*
import org.junit.Test

class InstallerCleanupPageTest {
    @Test fun installerPagesHaveSeparateScopesControlsAndSearchDestinations() {
        assertEquals(listOf(InstallerCleanupSettings.APP_DETAIL), featureRestartPackages(FeaturePageMode.AppInstaller))
        assertEquals(listOf(InstallerCleanupSettings.PACKAGE_INSTALLER), featureRestartPackages(FeaturePageMode.PackageInstaller))
        assertTrue(ModuleHotReloadPolicy.supportedPackages.containsAll(InstallerCleanupSettings.packages))
        val app = SharedFeatureDefinitions.appInstallerRows
        val pkg = SharedFeatureDefinitions.packageInstallerRows
        assertEquals(InstallerCleanupSettings.appKeys.toList(), app.map { it.first })
        assertEquals(InstallerCleanupSettings.packageKeys.toList(), pkg.map { it.first })
        assertTrue(app.map { it.first }.intersect(pkg.map { it.first }.toSet()).isEmpty())
        assertTrue(app.all { it.second.page == FeaturePageMode.AppInstaller })
        assertTrue(pkg.all { it.second.page == FeaturePageMode.PackageInstaller })
        assertEquals(listOf(R.string.installer_skip_detection_title, R.string.installer_remove_ads_title,
            R.string.installer_hide_collection_notice_title, R.string.installer_block_network_title), app.map { it.second.title })
        assertEquals(R.string.installer_native_ui_title, pkg.single().second.title)
        assertEquals(setOf(R.string.package_installer_title, R.string.installer_native_ui_title),
            featureSearchEntries(SystemAdaptationVersion.ColorOs17).filter { it.page == FeaturePageMode.PackageInstaller }.map { it.title }.toSet())
        val actions = EnabledFeatureActions()
        (app + pkg).forEach { (_, definition) ->
            assertEquals(definition.page, featureSearchEntries(SystemAdaptationVersion.ColorOs17).single { it.title == definition.title }.page)
            assertEquals(definition.page, EnabledFeatureCatalog.entries.single { it.title == definition.title }.page)
            assertTrue(actions.supports(definition.title))
        }
    }

    @Test fun replacementAndCorePatchBelongToPackageInstallerWithoutChangingTheirRuntimeScopes() {
        assertEquals(listOf(FeaturePageMode.Installer, FeaturePageMode.CorePatch), SharedFeatureDefinitions.installerSubPages.map { it.first })
        assertEquals(FeaturePageMode.PackageInstaller, EnabledFeatureCatalog.group(FeaturePageMode.Installer))
        assertEquals(FeaturePageMode.PackageInstaller, EnabledFeatureCatalog.group(FeaturePageMode.CorePatch))
        assertEquals(listOf("android", "system"), featureRestartPackages(FeaturePageMode.Installer))
        assertTrue(featureRestartPackages(FeaturePageMode.CorePatch).isEmpty())
    }

    @Test fun colorOs16KeepsExistingSubpagesUnderTheNewParentWithoutAdvertisingUnauditedHooks() {
        val c16 = SystemAdaptationVersion.ColorOs16
        assertFalse(ColorOs16FeaturePolicy.isPageAvailable(FeaturePageMode.AppInstaller))
        assertEquals(listOf(FeaturePageMode.Installer, FeaturePageMode.CorePatch), ColorOs16FeaturePolicy.categories[FeaturePageMode.PackageInstaller])
        assertTrue(FeaturePageMode.PackageInstaller in ColorOs16FeaturePolicy.categories.getValue(FeaturePageMode.Main))
        assertTrue(featureSearchEntries(c16).any { it.title == R.string.package_installer_title })
        assertFalse(enabledFeatureEntries(c16).any { it.page in setOf(FeaturePageMode.AppInstaller, FeaturePageMode.PackageInstaller) })
        assertFalse(featureSearchEntries(c16).any { it.title == R.string.installer_native_ui_title })
        assertTrue(featureRestartPackages(FeaturePageMode.PackageInstaller, c16).isEmpty())
    }
}
