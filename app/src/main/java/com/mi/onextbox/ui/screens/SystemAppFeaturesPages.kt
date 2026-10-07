package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AndroidSystemFeaturesPage(
    onOpenSubPage: (FeaturePageMode) -> Unit,
    gmsRegionRestrictionBypassEnabled: Boolean,
    onGmsRegionRestrictionBypassEnabledChange: (Boolean) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.feature_group_google_services))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_settings_gms_region_bypass_title),
            summary = stringResource(R.string.feature_settings_gms_region_bypass_summary),
            checked = gmsRegionRestrictionBypassEnabled,
            onCheckedChange = onGmsRegionRestrictionBypassEnabledChange,
        )
    }
    SettingsSection(title = stringResource(R.string.installer_group))
    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.installer_title),
            summary = stringResource(R.string.installer_entry_summary),
            onClick = { onOpenSubPage(FeaturePageMode.Installer) },
            showArrow = true,
        )
    }
}

@Composable
internal fun AppMarketFeaturesPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var regionRestrictionBypass by rememberSaveable {
        mutableStateOf(LspConfig.isAppMarketRegionRestrictionBypassEnabled(context))
    }
    var simplifyRecommendations by rememberSaveable {
        mutableStateOf(LspConfig.isAppMarketSimplifyRecommendationsEnabled(context))
    }

    // A legacy install may have only some of the former switches enabled. Collapse that mixed
    // state into the single visible setting without losing any previously enabled cleanup.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            LspConfig.setAppMarketSimplifyRecommendationsEnabled(
                context,
                simplifyRecommendations,
            )
        }
    }

    SettingsSection(title = stringResource(R.string.feature_group_app_market_region))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_app_market_region_bypass_title),
            summary = stringResource(R.string.feature_app_market_region_bypass_summary),
            checked = regionRestrictionBypass,
            onCheckedChange = { enabled ->
                regionRestrictionBypass = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setAppMarketRegionRestrictionBypassEnabled(context, enabled)
                    }
                }
            },
        )
    }

    SettingsSection(title = stringResource(R.string.feature_group_app_market_recommendations))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_app_market_simplify_recommendations_title),
            summary = stringResource(R.string.feature_app_market_simplify_recommendations_summary),
            checked = simplifyRecommendations,
            onCheckedChange = { enabled ->
                simplifyRecommendations = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setAppMarketSimplifyRecommendationsEnabled(context, enabled)
                    }
                }
            },
        )
    }
}

@Composable
internal fun MobileNetworkFeaturesPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hideAiLinkBoost by rememberSaveable {
        mutableStateOf(LspConfig.isMobileNetworkHideAiLinkBoostEnabled(context))
    }
    var hideRoamingService by rememberSaveable {
        mutableStateOf(LspConfig.isMobileNetworkHideRoamingServiceEnabled(context))
    }
    var hideHighDataSimCard by rememberSaveable {
        mutableStateOf(LspConfig.isMobileNetworkHideHighDataSimCardEnabled(context))
    }
    var hideSmartCloudAcceleration by rememberSaveable {
        mutableStateOf(LspConfig.isMobileNetworkHideSmartCloudAccelerationEnabled(context))
    }
    var hidePhoneNumber by rememberSaveable {
        mutableStateOf(LspConfig.isMobileNetworkHidePhoneNumberEnabled(context))
    }
    var forceCarrierOptions by rememberSaveable {
        mutableStateOf(LspConfig.isMobileNetworkForceCarrierOptionsEnabled(context))
    }

    SettingsSection(title = stringResource(R.string.feature_group_mobile_network_privacy))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_mobile_network_hide_phone_number_title),
            summary = stringResource(R.string.feature_mobile_network_hide_phone_number_summary),
            checked = hidePhoneNumber,
            onCheckedChange = { enabled ->
                hidePhoneNumber = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setMobileNetworkHidePhoneNumberEnabled(context, enabled)
                    }
                }
            },
        )
    }

    SettingsSection(title = stringResource(R.string.feature_group_mobile_network_carrier))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_mobile_network_force_carrier_options_title),
            summary = stringResource(
                R.string.feature_mobile_network_force_carrier_options_summary,
            ),
            checked = forceCarrierOptions,
            onCheckedChange = { enabled ->
                forceCarrierOptions = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setMobileNetworkForceCarrierOptionsEnabled(context, enabled)
                    }
                }
            },
        )
    }

    SettingsSection(title = stringResource(R.string.feature_group_mobile_network_entries))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_mobile_network_hide_ai_link_boost_title),
            summary = stringResource(R.string.feature_mobile_network_hide_ai_link_boost_summary),
            checked = hideAiLinkBoost,
            onCheckedChange = { enabled ->
                hideAiLinkBoost = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setMobileNetworkHideAiLinkBoostEnabled(context, enabled)
                    }
                }
            },
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_mobile_network_hide_roaming_service_title),
            summary = stringResource(R.string.feature_mobile_network_hide_roaming_service_summary),
            checked = hideRoamingService,
            onCheckedChange = { enabled ->
                hideRoamingService = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setMobileNetworkHideRoamingServiceEnabled(context, enabled)
                    }
                }
            },
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_mobile_network_hide_high_data_sim_card_title),
            summary = stringResource(R.string.feature_mobile_network_hide_high_data_sim_card_summary),
            checked = hideHighDataSimCard,
            onCheckedChange = { enabled ->
                hideHighDataSimCard = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setMobileNetworkHideHighDataSimCardEnabled(context, enabled)
                    }
                }
            },
            hasDividerAbove = true,
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(
                R.string.feature_mobile_network_hide_smart_cloud_acceleration_title,
            ),
            summary = stringResource(
                R.string.feature_mobile_network_hide_smart_cloud_acceleration_summary,
            ),
            checked = hideSmartCloudAcceleration,
            onCheckedChange = { enabled ->
                hideSmartCloudAcceleration = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setMobileNetworkHideSmartCloudAccelerationEnabled(
                            context,
                            enabled,
                        )
                    }
                }
            },
            hasDividerAbove = true,
        )
    }
}
