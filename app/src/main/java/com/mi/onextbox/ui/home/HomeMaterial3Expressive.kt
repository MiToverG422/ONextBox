@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R

private data class HomeDetail(
    val title: String,
    val detail: String,
)

@Composable
internal fun Material3ExpressiveHomeInfoCard(
    rootGranted: Boolean,
    versionInfo: HomeVersionInfo,
) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.home_info_unknown)
    val region = remember(context) { detectHomeRegionText(context) }
    val kernelVersion = remember {
        System.getProperty("os.version")
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
    }
    val marketName = rememberDeviceMarketName()
    val deviceName = if (marketName.equals(Build.MODEL, ignoreCase = true)) {
        Build.MODEL
    } else {
        "$marketName · ${Build.MODEL}"
    }
    val rows = listOf(
        HomeDetail(
            stringResource(R.string.home_info_root),
            if (rootGranted) versionInfo.rootManager ?: unknown
                else stringResource(R.string.home_status_root_missing),
        ),
        HomeDetail(
            stringResource(R.string.home_status_lsp),
            when {
                !versionInfo.lsposedModuleEnabled -> stringResource(R.string.lsp_status_module_disabled)
                versionInfo.lsposedReady -> versionInfo.lsposed ?: unknown
                else -> stringResource(R.string.lsp_status_missing_scope)
            },
        ),
        HomeDetail(stringResource(R.string.home_info_region), region),
        HomeDetail(
            stringResource(R.string.home_info_android_api),
            stringResource(R.string.home_dash_android_api_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
        ),
        HomeDetail(stringResource(R.string.info_device_model), deviceName),
        HomeDetail(
            stringResource(R.string.home_info_system_version),
            Build.DISPLAY.ifBlank { Build.VERSION.INCREMENTAL }.ifBlank { unknown },
        ),
        HomeDetail(
            stringResource(R.string.home_info_system_architecture),
            Build.SUPPORTED_ABIS.joinToString(" / ").ifBlank { unknown },
        ),
        HomeDetail(
            stringResource(R.string.home_info_system_fingerprint),
            Build.FINGERPRINT.ifBlank { unknown },
        ),
        HomeDetail(
            stringResource(R.string.home_info_kernel_version),
            kernelVersion.ifBlank { unknown },
        ),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { index, row ->
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                supportingContent = { Text(row.detail) },
            ) {
                Text(row.title)
            }
        }
    }
}
