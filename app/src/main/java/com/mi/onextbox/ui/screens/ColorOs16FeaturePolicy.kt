package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.SystemAdaptationVersion
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.R

internal object ColorOs16FeaturePolicy {
    private val candidateCategories = linkedMapOf(
        FeaturePageMode.Desktop to listOf(FeaturePageMode.DesktopLayout, FeaturePageMode.DesktopIcons,
            FeaturePageMode.DesktopRecent, FeaturePageMode.DesktopPages),
        FeaturePageMode.SystemUi to listOf(
            FeaturePageMode.SystemUiNative,
            FeaturePageMode.SystemUiStatusBar,
            FeaturePageMode.SystemUiFluidCloud,
            FeaturePageMode.SystemUiNotificationCenter,
            FeaturePageMode.SystemUiControlCenter,
            FeaturePageMode.SystemUiVolume,
            FeaturePageMode.SystemUiSmallWindow,
            FeaturePageMode.SystemUiLockScreen,
            FeaturePageMode.SystemUiNavigationBar,
            FeaturePageMode.SystemUiDynamicColor,
        ),
        FeaturePageMode.Settings to listOf(
            FeaturePageMode.SettingsAppearance,
            FeaturePageMode.SettingsRegion,
            FeaturePageMode.SettingsApps,
            FeaturePageMode.SettingsPermissions,
            FeaturePageMode.SettingsAboutDevice,
            FeaturePageMode.SettingsRealme,
        ),
    )

    val featureTitles: Map<FeaturePageMode, Set<Int>> by lazy {
        FeaturePageMode.entries.mapNotNull { page ->
            ColorOs16FeatureRegistry.titlesForPage(page).takeIf { it.isNotEmpty() }?.let { page to it }
        }.toMap()
    }

    val categories: Map<FeaturePageMode, List<FeaturePageMode>> by lazy {
            val groups = candidateCategories.mapValues { (_, children) -> children.filter { it in featureTitles } }
                .filterValues { it.isNotEmpty() }
            val nested = groups.values.flatten().toSet() + setOf(FeaturePageMode.SystemUiIconOrder,
                FeaturePageMode.SystemUiCornerSettings, FeaturePageMode.SystemUiPercentageSettings,
                FeaturePageMode.SystemUiSideVolumePercentageSettings, FeaturePageMode.SystemUiFluidCloudMediaBlacklist,
                FeaturePageMode.DesktopHiddenApps, FeaturePageMode.EsimDiagnostics, FeaturePageMode.NotificationRemoval,
                FeaturePageMode.Installer, FeaturePageMode.CorePatch)
            val main = groups.keys.toList() + FeaturePageMode.entries.filter { page ->
                page in featureTitles && page !in nested && page !in groups && page != FeaturePageMode.EnabledFeatures
            }
            linkedMapOf(FeaturePageMode.Main to main) + groups
        }

    fun isPageAvailable(page: FeaturePageMode): Boolean =
        page in categories || page in featureTitles || page in setOf(FeaturePageMode.EnabledFeatures, FeaturePageMode.Updates)

    fun isFeatureAvailable(version: SystemAdaptationVersion, page: FeaturePageMode, title: Int): Boolean =
        version != SystemAdaptationVersion.ColorOs16 || title in featureTitles[page].orEmpty()

    fun isAutoStartVisible(internationalEnabled: Boolean): Boolean = internationalEnabled
}

internal fun featureSearchEntries(version: SystemAdaptationVersion): List<FeatureSearchIndex.Entry> =
    (FeatureSearchIndex.entries + if (version == SystemAdaptationVersion.ColorOs16) listOf(
        FeatureSearchIndex.Entry(FeaturePageMode.MobileNetwork, R.string.feature_mobile_network_hide_smart_cloud_acceleration_title,
            R.string.feature_mobile_network_hide_smart_cloud_acceleration_summary)) else emptyList())
        .filter { ColorOs16FeaturePolicy.isFeatureAvailable(version, it.page, it.title) }

internal fun enabledFeatureEntries(version: SystemAdaptationVersion): List<EnabledFeatureCatalog.Entry> =
    (EnabledFeatureCatalog.entries + if (version == SystemAdaptationVersion.ColorOs16) colorOs16AdditionalCatalogEntries else emptyList())
        .filter { ColorOs16FeaturePolicy.isFeatureAvailable(version, it.page, it.title) }

internal val colorOs16AdditionalCatalogEntries = listOf(
    EnabledFeatureCatalog.Entry(R.string.feature_mobile_network_hide_smart_cloud_acceleration_title, FeaturePageMode.MobileNetwork) {
        LspConfig.isMobileNetworkHideSmartCloudAccelerationEnabled(it)
    },
    EnabledFeatureCatalog.Entry(R.string.feature_permission_export_pages_title, FeaturePageMode.SecurityPermission) {
        LspConfig.isPermissionFeatureEnabled(it, LspConfig.PermissionFeature.ExportPermissionPages)
    },
    EnabledFeatureCatalog.Entry(R.string.feature_permission_native_dialogs_title, FeaturePageMode.SecurityPermission) {
        LspConfig.isPermissionFeatureEnabled(it, LspConfig.PermissionFeature.NativePermissionDialogs)
    },
)

internal val colorOs16MobileNetworkTitles = setOf(R.string.network_identity_title,
    R.string.feature_mobile_network_hide_ai_link_boost_title, R.string.feature_mobile_network_hide_roaming_service_title,
    R.string.feature_mobile_network_hide_high_data_sim_card_title, R.string.feature_mobile_network_hide_phone_number_title,
    R.string.feature_mobile_network_force_carrier_options_title, R.string.feature_mobile_network_hide_smart_cloud_acceleration_title)

internal val colorOs16SecurityPermissionTitles = setOf(R.string.app_lock_delay_title,
    R.string.feature_permission_export_pages_title, R.string.feature_permission_native_dialogs_title,
    R.string.feature_permission_old_dialog_title, R.string.feature_permission_always_allow_title,
    R.string.feature_permission_unlock_restricted_title, R.string.feature_permission_disable_malicious_title)

internal fun colorOs16PageHasAll(page: FeaturePageMode, titles: Set<Int>) =
    ColorOs16FeatureRegistry.titlesForPage(page).containsAll(titles)

internal fun featureRestartPackages(mode: FeaturePageMode, version: SystemAdaptationVersion): List<String> {
    if (version != SystemAdaptationVersion.ColorOs16) return featureRestartPackages(mode)
    if (!ColorOs16FeaturePolicy.isPageAvailable(mode)) return emptyList()
    if (mode in setOf(FeaturePageMode.Main, FeaturePageMode.EnabledFeatures, FeaturePageMode.Updates, FeaturePageMode.CorePatch)) return emptyList()
    return (ColorOs16FeatureRegistry.packagesForPage(mode) +
        ColorOs16FeaturePolicy.categories[mode].orEmpty().flatMap(ColorOs16FeatureRegistry::packagesForPage)).distinct()
}
