package com.mi.onextbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.CardDefaults

object AppUiTokens {
    val CardCornerRadius: Dp
        @Composable @ReadOnlyComposable get() = when (LocalAppUiStyle.current) {
            AppUiStyle.ColorOs -> 20.dp
            AppUiStyle.ColorOs16 -> CardDefaults.CornerRadius
        }
}
