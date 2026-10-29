package com.mi.onextbox.ui.home

import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.runtime.getValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mi.onextbox.ui.platform.findActivity
import com.mi.onextbox.ui.common.LspMissingScopesNotice
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun HomePage(
    rootGranted: Boolean,
    onHeroLongPress: () -> Unit,
    onOpenDeviceDetails: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) {
        context.findActivity() as? LifecycleOwner
    }
    var lspRefreshKey by remember { mutableIntStateOf(0) }
    val versionInfo = rememberHomeVersionInfo(
        rootGranted = rootGranted,
        refreshKey = lspRefreshKey,
    )

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            var skipCurrentResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (skipCurrentResume) skipCurrentResume = false
                    else lspRefreshKey += 1
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }


    HomeHeroCard(
        rootGranted = rootGranted,
        lsposedReady = versionInfo.lsposedReady,
        lsposedStatus = versionInfo.lsposedStatus,
        onLongPress = onHeroLongPress,
    )
    LspMissingScopesNotice(versionInfo.lsposedStatus, versionInfo.missingScopes)

    HomeRuntimeStatusCards(
        rootGranted = rootGranted,
        versionInfo = versionInfo,
    )
    HomeInfoCard()
    io.github.suqi8.coui.kmp.basic.Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        cornerRadius = com.mi.onextbox.ui.common.AppUiTokens.CardCornerRadius,
    ) {
        com.mi.onextbox.ui.settings.SettingsCardRow("设备详细信息", "运行内存、闪存与屏幕", showArrow = true,
            onClick = onOpenDeviceDetails)
    }
}
