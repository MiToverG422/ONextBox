package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.EsimDiagnosticsSnapshot
import com.mi.onextbox.lsp.EsimDiagnosticsStore
import com.mi.onextbox.lsp.EsimProfileDiagnostic
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsTokens
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentContentCard
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun EsimFeaturesPage(onOpenSubPage: (FeaturePageMode) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var regionRestrictionOverrideEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isEsimRegionRestrictionOverrideEnabled(context))
    }
    val regionRestrictionCountryRestricted =
        LspConfig.isEsimRegionRestrictionCountryRestricted(context)
    val regionRestrictionBypassAvailable =
        regionRestrictionOverrideEnabled || !regionRestrictionCountryRestricted
    var regionRestrictionBypassEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isEsimRegionRestrictionBypassEnabled(context))
    }
    var confirmationCodePromptEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isEsimConfirmationCodePromptEnabled(context))
    }
    var profileLimitBypassEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isEsimProfileLimitBypassEnabled(context))
    }
    val warningColor = if (COUITheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFFFF6B64)
    } else {
        Color(0xFFC62828)
    }

    LaunchedEffect(regionRestrictionBypassAvailable) {
        if (!regionRestrictionBypassAvailable) {
            regionRestrictionBypassEnabled = false
            withContext(Dispatchers.IO) {
                LspConfig.setEsimRegionRestrictionBypassEnabled(context, false)
            }
        }
    }

    SettingsGroup {
        if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
            Material3ExpressiveSegmentContentCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    MaterialText(
                        text = stringResource(R.string.feature_esim_legal_warning_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    MaterialText(
                        text = stringResource(R.string.feature_esim_legal_warning_summary),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SettingsTokens.RowInsideMargin),
        ) {
            Text(
                text = stringResource(R.string.feature_esim_legal_warning_title),
                fontSize = COUITheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = warningColor,
            )
            Text(
                text = stringResource(R.string.feature_esim_legal_warning_summary),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = COUITheme.textStyles.body2.fontSize,
                color = warningColor,
            )
        }
        }
    }

    SettingsSection(title = stringResource(R.string.feature_group_esim_download))
    SettingsGroup {
        FeatureSegmentPosition(index = 0, count = 3) {
        SettingsToggleRow(
            title = stringResource(R.string.feature_esim_region_bypass_title),
            summary = if (regionRestrictionBypassAvailable) {
                stringResource(R.string.feature_esim_region_bypass_summary)
            } else {
                stringResource(R.string.feature_esim_region_bypass_unavailable_summary)
            },
            checked = regionRestrictionBypassEnabled,
            enabled = regionRestrictionBypassAvailable,
            onCheckedChange = { enabled ->
                val effectiveEnabled = enabled &&
                    (regionRestrictionOverrideEnabled || !regionRestrictionCountryRestricted)
                regionRestrictionBypassEnabled = effectiveEnabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setEsimRegionRestrictionBypassEnabled(context, effectiveEnabled)
                    }
                }
            },
            hiddenHoldDurationMillis = 10_000L,
            onHiddenHold = {
                val enableOverride = !regionRestrictionOverrideEnabled
                regionRestrictionOverrideEnabled = enableOverride
                regionRestrictionBypassEnabled = enableOverride
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setEsimRegionRestrictionHiddenOverride(
                            context,
                            enableOverride,
                        )
                    }
                    Toast.makeText(
                        context,
                        if (enableOverride) {
                            R.string.feature_esim_region_override_enabled
                        } else {
                            R.string.feature_esim_region_override_disabled
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        }
        SettingsDivider()
        FeatureSegmentPosition(index = 1, count = 3) {
        SettingsToggleRow(
            title = stringResource(R.string.feature_esim_profile_limit_bypass_title),
            summary = stringResource(R.string.feature_esim_profile_limit_bypass_summary),
            checked = profileLimitBypassEnabled,
            onCheckedChange = { enabled ->
                profileLimitBypassEnabled = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setEsimProfileLimitBypassEnabled(context, enabled)
                    }
                }
            },
        )
        }
        SettingsDivider()
        FeatureSegmentPosition(index = 2, count = 3) {
        SettingsToggleRow(
            title = stringResource(R.string.feature_esim_confirmation_prompt_title),
            summary = stringResource(R.string.feature_esim_confirmation_prompt_summary),
            checked = confirmationCodePromptEnabled,
            onCheckedChange = { enabled ->
                confirmationCodePromptEnabled = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setEsimConfirmationCodePromptEnabled(context, enabled)
                    }
                }
            },
        )
        }
    }

    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.feature_group_esim_diagnostics),
            summary = stringResource(R.string.feature_esim_diagnostics_entry_summary),
            onClick = { onOpenSubPage(FeaturePageMode.EsimDiagnostics) },
            showArrow = true,
        )
    }
}

@Composable
internal fun EsimDiagnosticsPage() {
    val context = LocalContext.current
    var diagnosticsSnapshot by remember {
        mutableStateOf(EsimDiagnosticsStore.read(context))
    }
    var diagnosticsRefreshNonce by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(diagnosticsRefreshNonce) {
        EsimDiagnosticsStore.requestRefresh(context)
        repeat(16) { attempt ->
            delay(if (attempt == 0) 300L else 750L)
            EsimDiagnosticsStore.read(context)?.let { snapshot ->
                diagnosticsSnapshot = snapshot
            }
        }
    }

    SettingsGroup {
        EsimDiagnosticsContent(
            snapshot = diagnosticsSnapshot,
            regionRestrictionBypassEnabled = LspConfig.isEsimRegionRestrictionBypassEnabled(context),
            profileLimitBypassEnabled = LspConfig.isEsimProfileLimitBypassEnabled(context),
            confirmationCodePromptEnabled = LspConfig.isEsimConfirmationCodePromptEnabled(context),
            onRefresh = { diagnosticsRefreshNonce += 1 },
        )
    }
}

@Composable
internal fun EsimDiagnosticsContent(
    snapshot: EsimDiagnosticsSnapshot?,
    regionRestrictionBypassEnabled: Boolean,
    profileLimitBypassEnabled: Boolean,
    confirmationCodePromptEnabled: Boolean,
    onRefresh: () -> Unit,
) {
    val unknown = stringResource(R.string.feature_esim_diag_unknown)
    val on = stringResource(R.string.feature_esim_diag_on)
    val off = stringResource(R.string.feature_esim_diag_off)
    val yes = stringResource(R.string.feature_esim_diag_yes)
    val no = stringResource(R.string.feature_esim_diag_no)
    val normal = stringResource(R.string.feature_esim_diag_normal)
    val notRecorded = stringResource(R.string.feature_esim_diag_not_recorded)
    fun textOrUnknown(value: String?): String = value?.takeIf { it.isNotBlank() } ?: unknown
    fun state(value: Boolean?): String = when (value) {
        true -> on
        false -> off
        null -> unknown
    }
    fun support(value: Boolean?): String = when (value) {
        true -> yes
        false -> no
        null -> unknown
    }
    val segmentCount = if (snapshot == null || !snapshot.hookReady) {
        2
    } else {
        snapshot.profiles.size + 5
    }

    if (snapshot == null || !snapshot.hookReady) {
            FeatureSegmentPosition(index = 0, count = segmentCount) {
            SettingsCardRow(
                title = stringResource(R.string.feature_esim_diagnostics_waiting_title),
                summary = stringResource(R.string.feature_esim_diagnostics_waiting_summary),
            )
            }
        } else {
            val power = when (snapshot.powerState) {
                "on" -> on
                "off" -> off
                else -> unknown
            }
            val availableMemory = buildString {
                append("NV ")
                append(snapshot.freeNonVolatileMemory?.toString() ?: unknown)
                append(" · V ")
                append(snapshot.freeVolatileMemory?.toString() ?: unknown)
            }
            FeatureSegmentPosition(index = 0, count = segmentCount) {
            SettingsCardRow(
                title = stringResource(R.string.feature_esim_diagnostics_euicc_title),
                summary = stringResource(
                    R.string.feature_esim_diagnostics_euicc_summary,
                    power,
                    textOrUnknown(snapshot.eidMasked),
                    textOrUnknown(snapshot.firmwareVersion),
                    textOrUnknown(snapshot.ppVersion),
                    textOrUnknown(snapshot.svn),
                    support(snapshot.mepSupported),
                    availableMemory,
                ),
            )
            }
            SettingsDivider()
            FeatureSegmentPosition(index = 1, count = segmentCount) {
            SettingsCardRow(
                title = stringResource(R.string.feature_esim_diagnostics_profiles_title),
                summary = stringResource(
                    R.string.feature_esim_diagnostics_profiles_summary,
                    snapshot.profileCount?.toString() ?: unknown,
                    snapshot.operationalProfileCount?.toString() ?: unknown,
                    snapshot.enabledProfileCount?.toString() ?: unknown,
                    snapshot.profileLimit?.toString() ?: unknown,
                    if (profileLimitBypassEnabled) on else off,
                ),
            )
            }
            snapshot.profiles.forEachIndexed { index, profile ->
                SettingsDivider()
                FeatureSegmentPosition(index = index + 2, count = segmentCount) {
                EsimProfileDiagnosticRow(
                    index = index,
                    profile = profile,
                    unknown = unknown,
                    enabledText = on,
                    disabledText = off,
                )
                }
            }
            SettingsDivider()
            val binding = snapshot.bindingResult.takeIf { it.isNotBlank() }?.let { code ->
                if (code == "9000") normal else stringResource(
                    R.string.feature_esim_diag_abnormal_code,
                    code,
                )
            } ?: unknown
            FeatureSegmentPosition(index = snapshot.profiles.size + 2, count = segmentCount) {
            SettingsCardRow(
                title = stringResource(R.string.feature_esim_diagnostics_environment_title),
                summary = stringResource(
                    R.string.feature_esim_diagnostics_environment_summary,
                    state(snapshot.networkAvailable),
                    textOrUnknown(snapshot.networkCountries),
                    state(snapshot.locationEnabled),
                    binding,
                    textOrUnknown(snapshot.smdpAddress),
                ),
            )
            }
            SettingsDivider()
            val downloadResult = when (snapshot.lastDownloadResult) {
                null -> notRecorded
                0 -> normal
                -1 -> stringResource(R.string.feature_esim_diag_result_invalid_code)
                1020 -> stringResource(R.string.feature_esim_diag_result_no_network)
                1021 -> stringResource(R.string.feature_esim_diag_result_unknown_smdp)
                1022 -> stringResource(R.string.feature_esim_diag_result_domestic_profile_abroad)
                1023 -> stringResource(R.string.feature_esim_diag_result_location_permission)
                1024 -> stringResource(R.string.feature_esim_diag_result_location_unknown)
                1025 -> stringResource(R.string.feature_esim_diag_result_location_off)
                1026 -> stringResource(R.string.feature_esim_diag_result_profile_limit)
                1027 -> stringResource(R.string.feature_esim_diag_result_profile_query)
                1028 -> stringResource(R.string.feature_esim_diag_result_euicc_open)
                1030 -> stringResource(R.string.feature_esim_diag_result_country_check)
                1031 -> stringResource(R.string.feature_esim_diag_result_binding)
                else -> stringResource(
                    R.string.feature_esim_diag_result_code,
                    snapshot.lastDownloadResult,
                )
            }
            FeatureSegmentPosition(index = snapshot.profiles.size + 3, count = segmentCount) {
            SettingsCardRow(
                title = stringResource(R.string.feature_esim_diagnostics_module_title),
                summary = stringResource(
                    R.string.feature_esim_diagnostics_module_summary,
                    if (regionRestrictionBypassEnabled) on else off,
                    if (confirmationCodePromptEnabled) on else off,
                    downloadResult,
                ),
            )
            }
        }
        SettingsDivider()
        val updatedAt = snapshot?.capturedAtMillis
            ?.takeIf { it > 0L }
            ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it)) }
            ?: unknown
        FeatureSegmentPosition(index = segmentCount - 1, count = segmentCount) {
        SettingsCardRow(
            title = stringResource(R.string.feature_esim_diagnostics_refresh_title),
            summary = stringResource(R.string.feature_esim_diagnostics_last_update, updatedAt),
            onClick = onRefresh,
        )
        }
}

@Composable
internal fun EsimProfileDiagnosticRow(
    index: Int,
    profile: EsimProfileDiagnostic,
    unknown: String,
    enabledText: String,
    disabledText: String,
) {
    val profileType = when (profile.profileClass) {
        0 -> stringResource(R.string.feature_esim_diag_profile_test)
        1 -> stringResource(R.string.feature_esim_diag_profile_provisioning)
        2 -> stringResource(R.string.feature_esim_diag_profile_operational)
        else -> unknown
    }
    fun textOrUnknown(value: String): String = value.takeIf { it.isNotBlank() } ?: unknown
    SettingsCardRow(
        title = stringResource(R.string.feature_esim_diagnostics_profile_item_title, index + 1),
        summary = stringResource(
            R.string.feature_esim_diagnostics_profile_item_summary,
            textOrUnknown(profile.displayName),
            textOrUnknown(profile.serviceProviderName),
            textOrUnknown(profile.iccidMasked),
            if (profile.enabled) enabledText else disabledText,
            profileType,
            profile.portIndex?.toString() ?: unknown,
        ),
    )
}
