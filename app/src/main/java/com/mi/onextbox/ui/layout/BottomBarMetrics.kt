package com.mi.onextbox.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val BottomBarHostHeight = 88.dp
internal val BottomBarHeight = 56.dp
internal val BottomBarScreenPadding = 16.dp
internal val BottomBarHorizontalPadding = 12.dp
internal val BottomBarWidthInset = 8.dp
internal val BottomBarItemWidth = 70.dp
internal val BottomBarItemSpacing = 8.dp
internal val BottomBarItemHeight = 48.dp
internal val BottomBarIconSize = 24.dp
internal val BottomBarRouteHiddenOffset = (BottomBarHostHeight + BottomBarHeight) / 2 + 8.dp

internal fun floatingBottomBarWidth(hostWidth: Dp, itemCount: Int): Dp {
    if (itemCount <= 0) return 0.dp
    val desired = BottomBarHorizontalPadding * 2 + BottomBarItemWidth * itemCount +
        BottomBarItemSpacing * (itemCount - 1)
    return (minOf(desired, (hostWidth - BottomBarScreenPadding * 2).coerceAtLeast(0.dp)) -
        BottomBarWidthInset * 2).coerceAtLeast(0.dp)
}
