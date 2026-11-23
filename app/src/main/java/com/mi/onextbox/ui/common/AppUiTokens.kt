package com.mi.onextbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.CardDefaults

object AppUiTokens {
    private val cardCorners: AppCardCornerStyle
        @Composable @ReadOnlyComposable get() =
            AppCardCornerStyle.resolve(LocalAppUiStyle.current, LocalSmootherCardCorners.current)

    val CardCornerRadius: Dp
        @Composable @ReadOnlyComposable get() = cardCorners.cardRadius

    internal val PreferenceCardCornerRadius: Dp
        @Composable @ReadOnlyComposable get() = cardCorners.preferenceRadius
}

internal enum class AppCardCornerStyle(val cardRadius: Dp, val preferenceRadius: Dp) {
    ColorOs17(17.dp, 24.dp),
    SmootherColorOs17(20.dp, 20.dp),
    ColorOs16(CardDefaults.CornerRadius, CardDefaults.CornerRadius);

    companion object {
        fun resolve(style: AppUiStyle, smoother: Boolean): AppCardCornerStyle = when {
            style == AppUiStyle.ColorOs16 -> ColorOs16
            smoother -> SmootherColorOs17
            else -> ColorOs17
        }
    }
}
