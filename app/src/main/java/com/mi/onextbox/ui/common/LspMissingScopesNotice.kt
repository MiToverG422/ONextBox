package com.mi.onextbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspStatus
import com.mi.onextbox.lsp.LsposedScopeRequester
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun shouldShowLspMissingScopes(status: LspStatus, scopes: Set<String>): Boolean =
    status == LspStatus.MISSING_SCOPE && scopes.isNotEmpty()

internal fun orderedLspMissingScopes(scopes: Set<String>): List<String> = scopes.sortedWith(
    compareBy<String> {
        when (it) {
            "system" -> 0
            "android" -> 1
            "com.android.systemui" -> 2
            else -> 3
        }
    }.thenBy { it },
)

@Composable
internal fun LspMissingScopesNotice(
    status: LspStatus,
    missingScopes: Set<String>,
    modifier: Modifier = Modifier,
) {
    if (!shouldShowLspMissingScopes(status, missingScopes)) return
    val context = LocalContext.current
    val material = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
    val scopes = remember(missingScopes) { orderedLspMissingScopes(missingScopes) }
    val labels by produceState<Map<String, String>>(emptyMap(), context, scopes) {
        value = withContext(Dispatchers.IO) {
            scopes.take(3).associateWith { packageName ->
                runCatching {
                    val info = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                        .lineSequence().firstOrNull().orEmpty().trim().take(80)
                }.getOrDefault(packageName).ifBlank { packageName }
            }
        }
    }
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    val onOpen = {
        opening = true
        openFailed = false
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LsposedScopeRequester.openManager(context) }
                openFailed = !result.isSuccess
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                openFailed = true
            } finally {
                opening = false
            }
        }
        Unit
    }
    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MissingScopeText(stringResource(R.string.lsp_status_missing_scope), 16.sp, FontWeight.Medium)
            MissingScopeText(stringResource(R.string.lsp_missing_scopes_summary), 13.sp)
            scopes.take(3).forEach { packageName ->
                val label = when (packageName) {
                    "system" -> stringResource(R.string.lsp_scope_system_title)
                    "android" -> stringResource(R.string.feature_android_system_title)
                    else -> labels[packageName] ?: packageName
                }
                MissingScopeText(
                    if (label == packageName) packageName else "$label ($packageName)",
                    13.sp,
                    maxLines = 2,
                )
            }
            if (scopes.size > 3) {
                MissingScopeText(stringResource(R.string.lsp_missing_scopes_more, scopes.size - 3), 13.sp)
            }
            if (material) {
                androidx.compose.material3.TextButton(
                    onClick = onOpen,
                    enabled = !opening,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = .38f),
                    ),
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.onboarding_open_lsposed))
                }
            } else {
                Button(
                    onClick = onOpen,
                    enabled = !opening,
                    minHeight = 40.dp,
                    cornerRadius = AppUiTokens.CardCornerRadius,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.onboarding_open_lsposed), fontSize = 14.sp)
                }
            }
            if (openFailed) {
                MissingScopeText(stringResource(R.string.onboarding_lsposed_open_failed), 13.sp)
            }
        }
    }
    if (material) {
        Surface(
            modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = AppUiTokens.CardCornerRadius,
            insideMargin = PaddingValues(0.dp),
            content = { content() },
        )
    }
}

@Composable
private fun MissingScopeText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        androidx.compose.material3.Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            color = COUITheme.colorScheme.onSurface,
        )
    }
}
