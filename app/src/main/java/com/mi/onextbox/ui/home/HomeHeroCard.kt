package com.mi.onextbox.ui.home

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.ui.settings.AppUpdateRules
import com.mi.onextbox.lsp.LspStatus
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.LocalColorOsMonetEnabled
import com.mi.onextbox.ui.common.LocalPreviewTextPlaceholders
import com.mi.onextbox.ui.common.ONextBoxLogo
import com.mi.onextbox.ui.common.PreviewTextLine
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

    val hapticLongPress = rememberHapticLongPress()
    val redactText = LocalPreviewTextPlaceholders.current
    val appVersionName = if (redactText) "" else rememberAppVersionName()
    val deviceMarketName = if (redactText) "" else rememberDeviceMarketName()
    val themed = rootGranted && lsposedReady && !LocalColorOsMonetEnabled.current
    val heroColor = when {
        themed -> COUITheme.colorScheme.primary
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
        HomeHeroCardContent(appVersionName, deviceMarketName)
    }
}

@Composable
internal fun HomeHeroCardContent(appVersionName: String, deviceMarketName: String) {
    if (LocalPreviewTextPlaceholders.current) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
            PreviewTextLine(142.dp, 24.dp, Color.White.copy(alpha = .90f))
            Spacer(Modifier.height(12.dp))
            PreviewTextLine(87.dp, 8.dp, Color.White.copy(alpha = .66f))
            Spacer(Modifier.height(7.dp))
            PreviewTextLine(154.dp, 8.dp, Color.White.copy(alpha = .55f))
        }
        return
    }
    val ci = AppUpdateRules.isCiVersion(appVersionName)
    val detailColor = Color.White.copy(alpha = 0.92f)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            ONextBoxLogo(
                modifier = Modifier.testTag("homeHeroTitle"),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(Modifier.testTag("homeHeroDetails")) {
                Text(
                    text = stringResource(R.string.about_version, appVersionName),
                    style = COUITheme.textStyles.title3,
                    color = detailColor,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deviceMarketName,
                    style = COUITheme.textStyles.title3,
                    color = detailColor,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ci) HomeCiBadge()
        },
    ) { measurables, constraints ->
        val contentInset = 22.dp.roundToPx()
        val badgeGap = 10.dp.roundToPx()
        val textWidth = (constraints.maxWidth - contentInset * 2).coerceAtLeast(0)
        val badge = measurables.getOrNull(2)?.measure(Constraints(
            maxWidth = constraints.maxWidth,
            maxHeight = constraints.maxHeight,
        ))
        val titleWidth = if (badge == null) textWidth else minOf(textWidth,
            (constraints.maxWidth - contentInset - badge.width - badgeGap).coerceAtLeast(0))
        val title = measurables[0].measure(Constraints(maxWidth = titleWidth, maxHeight = constraints.maxHeight))
        val lineGap = 10.dp.roundToPx().coerceAtMost((constraints.maxHeight - title.height).coerceAtLeast(0))
        val details = measurables[1].measure(Constraints(maxWidth = textWidth,
            maxHeight = (constraints.maxHeight - title.height - lineGap).coerceAtLeast(0)))
        val textTop = (constraints.maxHeight - title.height - lineGap - details.height) / 2
        layout(constraints.maxWidth, constraints.maxHeight) {
            title.placeRelative(contentInset, textTop)
            details.placeRelative(contentInset, textTop + title.height + lineGap)
            badge?.placeRelative(constraints.maxWidth - badge.width, 0)
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
