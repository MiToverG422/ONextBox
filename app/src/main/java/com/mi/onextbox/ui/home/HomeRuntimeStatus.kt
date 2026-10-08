package com.mi.onextbox.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspStatus
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.RootStartupCheck
import com.mi.onextbox.ui.common.homeLspDisplayStatus
import com.mi.onextbox.ui.common.lspStatusText
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.mi.onextbox.ui.common.rememberHapticClick
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HomeVersionInfo(
    val rootManager: String? = null,
    val lsposed: String? = null,
    val lsposedModuleEnabled: Boolean = false,
    val lsposedReady: Boolean = false,
    val lsposedStatus: LspStatus = LspStatus.UNKNOWN,
    val missingScopes: Set<String> = emptySet(),
)

@Composable
fun rememberHomeVersionInfo(rootGranted: Boolean, refreshKey: Int = 0): HomeVersionInfo {
    val context = LocalContext.current
    val lsposedSnapshot by LsposedScopeRequester.states.collectAsState()
    val cachedDisplay by LsposedScopeRequester.homeDisplayStates.collectAsState()
    val rootStartupPending by RootStartupCheck.states.collectAsState()
    var rootManager by remember(context) {
        mutableStateOf(readCachedRootAccessInfo(context)?.managerVersion)
    }
    LaunchedEffect(context, rootGranted, refreshKey) {
        rootManager = withContext(Dispatchers.IO) {
            if (refreshKey == 0) LsposedScopeRequester.snapshot(context)
            else LsposedScopeRequester.refreshSnapshot(context)
            readCachedRootAccessInfo(context)?.managerVersion
        }
    }
    val displayStatus = cachedDisplay?.status ?: homeLspDisplayStatus(
        snapshot = lsposedSnapshot,
        rootStartupPending = rootStartupPending,
    )
    return HomeVersionInfo(
        rootManager = rootManager,
        lsposed = cachedDisplay?.frameworkVersionText ?: lsposedSnapshot.frameworkVersionText,
        lsposedModuleEnabled = lsposedSnapshot.moduleEnabled,
        lsposedReady = displayStatus == LspStatus.READY,
        lsposedStatus = displayStatus,
        missingScopes = cachedDisplay?.missingScopes ?: lsposedSnapshot.missingScopes,
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
            detail = lspStatusText(versionInfo.lsposedStatus, versionInfo.lsposed, showChecking = false),
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
