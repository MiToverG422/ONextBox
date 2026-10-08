package com.mi.onextbox.ui.home

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspStatus
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.ONextBoxLogo
import com.mi.onextbox.ui.common.lspStatusText
import com.mi.onextbox.ui.common.rememberHapticLongPress
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeHeroCard(
    rootGranted: Boolean,
    lsposedReady: Boolean,
    lsposedStatus: LspStatus,
    onLongPress: () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveHomeHeroCard(rootGranted, lsposedReady, lsposedStatus, onLongPress)
        return
    }
    val hapticLongPress = rememberHapticLongPress()
    val appVersionName = rememberAppVersionName()
    val deviceMarketName = rememberDeviceMarketName()
    val heroColor = when {
        rootGranted && lsposedReady -> Color(0xFF147DF5)
        lsposedStatus == LspStatus.CHECKING -> Color(0xFF64758A)
        !rootGranted || lsposedStatus == LspStatus.DISABLED -> Color(0xFFD9443F)
        else -> Color(0xFF64758A)
    }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 520f,
        ),
        label = "home_hero_press_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1128f / 430f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .squircleSurface(
                color = heroColor,
                cornerRadius = AppUiTokens.CardCornerRadius,
            )
            .pointerInput(onLongPress) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val releasedBeforeLongPress = withTimeoutOrNull(800L) {
                        waitForUpOrCancellation()
                    }
                    if (releasedBeforeLongPress == null) {
                        hapticLongPress()
                        onLongPress()
                        waitForUpOrCancellation()
                    }
                    pressed = false
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 22.dp, end = 22.dp),
            ) {
                ONextBoxLogo(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.about_version, appVersionName),
                    style = COUITheme.textStyles.title3,
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deviceMarketName,
                    style = COUITheme.textStyles.title3,
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Material3ExpressiveHomeHeroCard(
    rootGranted: Boolean,
    lsposedReady: Boolean,
    lsposedStatus: LspStatus,
    onLongPress: () -> Unit,
) {
    val hapticLongPress = rememberHapticLongPress()
    val successful = rootGranted && lsposedReady
    val failed = lsposedStatus != LspStatus.CHECKING &&
        (!rootGranted || lsposedStatus == LspStatus.DISABLED)
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val container = when {
        successful -> colors.secondaryContainer
        failed -> colors.errorContainer
        else -> colors.surfaceContainerHigh
    }
    val foreground = when {
        successful -> colors.onSecondaryContainer
        failed -> colors.onErrorContainer
        else -> colors.onSurface
    }
    val title = when {
        successful -> stringResource(R.string.onboarding_runtime_ready)
        lsposedStatus == LspStatus.CHECKING -> stringResource(R.string.app_name)
        !rootGranted -> stringResource(R.string.home_status_root_missing)
        else -> lspStatusText(lsposedStatus)
    }
    val supporting = stringResource(R.string.about_version, rememberAppVersionName())
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onLongPress) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val released = withTimeoutOrNull(800L) { waitForUpOrCancellation() }
                    if (released == null) {
                        hapticLongPress()
                        onLongPress()
                        waitForUpOrCancellation()
                    }
                }
            },
        shape = androidx.compose.material3.MaterialTheme.shapes.large,
        color = container,
        contentColor = foreground,
    ) {
        androidx.compose.material3.ListItem(
            supportingContent = { androidx.compose.material3.Text(supporting) },
            leadingContent = {
                androidx.compose.material3.Icon(
                    imageVector = if (successful) com.mi.onextbox.ui.common.AppIcons.Check else com.mi.onextbox.ui.common.AppIcons.InfoFilled,
                    contentDescription = null,
                    modifier = Modifier.rotate(
                        if (rootGranted && lsposedStatus == LspStatus.MISSING_SCOPE) 180f else 0f,
                    ),
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = container,
                contentColor = foreground,
                leadingContentColor = foreground,
                supportingContentColor = foreground.copy(alpha = 0.7f),
            ),
        ) {
            androidx.compose.material3.Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

@Composable
fun rememberAppVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
    }
}

@Composable
fun rememberDeviceMarketName(): String {
    return remember {
        readHomeSystemProperty("ro.vendor.oplus.market.name")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: Build.MODEL
    }
}
