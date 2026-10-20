package com.mi.onextbox.ui.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspStatus
import com.mi.onextbox.ui.common.LspMissingScopesNotice
import com.mi.onextbox.ui.common.lspStatusText
import com.mi.onextbox.ui.common.rememberHapticLongPress
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun MiuixHomeContent(rootGranted: Boolean, versionInfo: HomeVersionInfo, onHeroLongPress: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val ready = rootGranted && versionInfo.lsposedReady
    val checking = versionInfo.lsposedStatus == LspStatus.CHECKING
    val title = when {
        ready -> stringResource(R.string.onboarding_runtime_ready)
        checking -> stringResource(R.string.app_name)
        !rootGranted -> stringResource(R.string.home_status_root_missing)
        else -> lspStatusText(versionInfo.lsposedStatus)
    }
    val hapticLongPress = rememberHapticLongPress()
    val versionName = rememberAppVersionName()
    val marketName = rememberDeviceMarketName()
    val dark = colors.background.luminance() < 0.5f
    val statusBackground = when {
        ready && !MiuixTheme.isDynamicColor -> if (dark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
        ready -> colors.secondaryContainer
        checking -> colors.surfaceContainer
        else -> colors.errorContainer
    }
    val statusForeground = when {
        ready && !MiuixTheme.isDynamicColor -> if (dark) Color(0xFFB8E8C5) else Color(0xFF164A29)
        ready -> colors.onSecondaryContainer
        checking -> colors.onSurfaceContainer
        else -> colors.onErrorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = statusBackground,
            contentColor = statusForeground,
        ),
        pressFeedbackType = PressFeedbackType.Tilt,
        onLongPress = { hapticLongPress(); onHeroLongPress() },
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
            // Decorative status mark is clipped by the native card, not laid out beside the text.
            Icon(
                imageVector = when { ready -> Icons.Rounded.CheckCircleOutline; checking -> Icons.Rounded.HourglassEmpty; else -> Icons.Rounded.ErrorOutline },
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 27.dp, y = 31.dp).size(110.dp),
                tint = when {
                    ready && !MiuixTheme.isDynamicColor -> Color(0xFF36D167)
                    ready -> colors.primary.copy(alpha = 0.8f)
                    checking -> colors.primary.copy(alpha = 0.35f)
                    else -> colors.error.copy(alpha = 0.65f)
                },
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = statusForeground)
                Text(stringResource(R.string.about_version, versionName), fontSize = 15.sp, color = statusForeground)
                Text(marketName,
                    modifier = Modifier.padding(top = 16.dp, end = 72.dp),
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, color = statusForeground)
            }
        }
    }
    LspMissingScopesNotice(versionInfo.lsposedStatus, versionInfo.missingScopes)
    val context = LocalContext.current
    val unknown = stringResource(R.string.home_info_unknown)
    val region = remember(context) { detectHomeRegionText(context) }
    val kernel = remember { System.getProperty("os.version").orEmpty().lineSequence().firstOrNull().orEmpty() }
    val device = if (marketName.equals(Build.MODEL, ignoreCase = true)) Build.MODEL else "$marketName · ${Build.MODEL}"
    val rows = listOf(
        Triple(Icons.Rounded.Security, stringResource(R.string.home_info_root),
            if (rootGranted) versionInfo.rootManager ?: unknown else stringResource(R.string.home_status_root_missing)),
        Triple(Icons.Rounded.Extension, stringResource(R.string.home_status_lsp),
            lspStatusText(versionInfo.lsposedStatus, versionInfo.lsposed, showChecking = false)),
        Triple(Icons.Rounded.Public, stringResource(R.string.home_info_region), region),
        Triple(Icons.Rounded.Android, stringResource(R.string.home_info_android_api), stringResource(R.string.home_dash_android_api_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)),
        Triple(Icons.Rounded.Smartphone, stringResource(R.string.info_device_model), device),
        Triple(Icons.Rounded.Settings, stringResource(R.string.home_info_system_version), Build.DISPLAY.ifBlank { Build.VERSION.INCREMENTAL }.ifBlank { unknown }),
        Triple(Icons.Rounded.Memory, stringResource(R.string.home_info_system_architecture), Build.SUPPORTED_ABIS.joinToString(" / ").ifBlank { unknown }),
        Triple(Icons.Rounded.Fingerprint, stringResource(R.string.home_info_system_fingerprint), Build.FINGERPRINT.ifBlank { unknown }),
        Triple(Icons.Rounded.DeveloperBoard, stringResource(R.string.home_info_kernel_version), kernel.ifBlank { unknown }),
    )
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            rows.forEach { (icon, label, detail) -> MiuixHomeInfoRow(icon, label, detail) }
        }
    }
}

@Composable
private fun MiuixHomeInfoRow(icon: ImageVector, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = MiuixTheme.textStyles.headline1.fontSize, fontWeight = FontWeight.Medium)
            // Fingerprints, kernel versions and framework names must remain fully readable.
            Text(detail, fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
