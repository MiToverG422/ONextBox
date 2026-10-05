package io.github.suqi8.coui.kmp.basic

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.window.WindowListPopup

@Composable
fun ListPopup(
    show: MutableState<Boolean>,
    popupPositionProvider: PopupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    transformOriginAnchorX: Float? = null,
    onDismissRequest: (() -> Unit)? = null,
    minWidth: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
    cornerRadius: Dp = 16.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    WindowListPopup(
        show = show.value,
        popupModifier = Modifier
            .then(if (minWidth != Dp.Unspecified) Modifier.widthIn(min = minWidth) else Modifier)
            .then(if (maxHeight != Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier),
        popupPositionProvider = popupPositionProvider,
        alignment = alignment,
        enableWindowDim = false,
        onDismissRequest = {
            show.value = false
            onDismissRequest?.invoke()
        },
        minWidth = if (minWidth != Dp.Unspecified) minWidth else ListPopupDefaults.MinWidth,
        maxHeight = maxHeight.takeIf { it != Dp.Unspecified },
        content = {
            SyncListPopupWindowAppearance()
            content()
        },
    )
}

@Composable
private fun SyncListPopupWindowAppearance() {
    val view = LocalView.current
    val useDarkSystemBarIcons = COUITheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply {
            dimAmount = 0f
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkSystemBarIcons
            isAppearanceLightNavigationBars = useDarkSystemBarIcons
        }
    }
}
