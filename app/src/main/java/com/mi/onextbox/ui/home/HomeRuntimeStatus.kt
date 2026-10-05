package com.mi.onextbox.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.mi.onextbox.ui.common.rememberHapticClick
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class HomeVersionInfo(
    val rootManager: String? = null,
    val lsposed: String? = null,
    val lsposedModuleEnabled: Boolean = false,
    val lsposedReady: Boolean = false,
)

@Composable
fun rememberHomeVersionInfo(rootGranted: Boolean, refreshKey: Int = 0): HomeVersionInfo {
    val context = LocalContext.current
    val initialVersionInfo = remember(context) {
        buildCachedHomeVersionInfo(context)
    }
    val versionInfo by produceState(
        initialValue = initialVersionInfo,
        key1 = context,
        key2 = rootGranted,
        key3 = refreshKey,
    ) {
        if (refreshKey == 0) {
            delay(1_200)
        }
        value = withContext(Dispatchers.IO) {
            val lsposedSnapshot = runCatching {
                LsposedScopeRequester.snapshot(context)
            }.getOrNull()
            val cachedRootManager = readCachedRootAccessInfo(context)?.managerVersion
            HomeVersionInfo(
                rootManager = cachedRootManager ?: value.rootManager,
                lsposed = lsposedSnapshot?.frameworkVersionText ?: value.lsposed,
                lsposedModuleEnabled = lsposedSnapshot?.moduleEnabled == true,
                lsposedReady = lsposedSnapshot?.moduleEnabled == true,
            )
        }
    }
    return versionInfo
}

private fun buildCachedHomeVersionInfo(context: Context): HomeVersionInfo {
    val lsposedSnapshot = runCatching {
        LsposedScopeRequester.cachedSnapshot(context)
    }.getOrNull()
    return HomeVersionInfo(
        rootManager = readCachedRootAccessInfo(context)?.managerVersion,
        lsposed = lsposedSnapshot?.frameworkVersionText,
        lsposedModuleEnabled = lsposedSnapshot?.moduleEnabled == true,
        lsposedReady = lsposedSnapshot?.moduleEnabled == true,
    )
}

@Composable
fun HomeRuntimeStatusCards(
    rootGranted: Boolean,
    versionInfo: HomeVersionInfo,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RuntimeStatusCard(
            title = stringResource(R.string.home_info_root),
            detail = if (rootGranted) {
                versionInfo.rootManager ?: stringResource(R.string.home_info_unknown)
            } else {
                stringResource(R.string.home_status_root_missing)
            },
            modifier = Modifier
                .weight(1f)
                .requiredHeight(90.dp),
        )
        RuntimeStatusCard(
            title = stringResource(R.string.home_status_lsp),
            detail = when {
                !versionInfo.lsposedModuleEnabled -> stringResource(R.string.lsp_status_module_disabled)
                versionInfo.lsposedReady -> versionInfo.lsposed ?: stringResource(R.string.home_info_unknown)
                else -> stringResource(R.string.lsp_status_missing_scope)
            },
            modifier = Modifier
                .weight(1f)
                .requiredHeight(90.dp),
        )
    }
}

@Composable
private fun RuntimeStatusCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val hapticClick = rememberHapticClick()

    Card(
        modifier = modifier,
        cornerRadius = AppUiTokens.CardCornerRadius,
        insideMargin = PaddingValues(14.dp),
        onClick = onClick?.let {
            {
                hapticClick()
                it()
            }
        },
        showIndication = onClick != null,
    ) {
        Column {
            Text(
                text = title,
                style = COUITheme.textStyles.title3,
                color = COUITheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                style = COUITheme.textStyles.body1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
