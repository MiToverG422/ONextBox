package com.mi.onextbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ColorOs17DetailTopBar(
    title: String,
    containerColor: Color,
    titleColor: Color,
    dividerColor: Color,
    showDivider: Boolean,
    scrollDistancePx: () -> Float,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val titleSize = with(density) { 18.dp.toSp() }
    val dividerRevealPx = with(density) { ToolbarHeight.toPx() }
    val expandedDividerInsetPx = with(density) { DividerExpandedInset.toPx() }
    val dividerThicknessPx = with(density) { DividerThickness.toPx() }
    val titleStyle = remember(titleSize, titleColor) {
        TextStyle(
            color = titleColor,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight(550),
            fontSize = titleSize,
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        )
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .then(
                if (showDivider) {
                    Modifier.drawWithContent {
                        drawContent()
                        val fraction = (scrollDistancePx() / dividerRevealPx).coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            val inset = expandedDividerInsetPx * (1f - fraction)
                            val y = size.height - dividerThicknessPx / 2f
                            drawLine(
                                color = dividerColor,
                                start = Offset(inset, y),
                                end = Offset(size.width - inset, y),
                                strokeWidth = dividerThicknessPx,
                                alpha = fraction,
                            )
                        }
                    }
                } else {
                    Modifier
                },
            )
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
        content = {
            Box(Modifier.layoutId(NavigationSlot)) {
                navigationIcon()
            }
            BasicText(
                text = title,
                modifier = Modifier.layoutId(TitleSlot),
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Row(
                modifier = Modifier.layoutId(ActionsSlot),
                horizontalArrangement = Arrangement.spacedBy(ActionSpacing),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        },
    ) { measurables, constraints ->
        val barHeight = ToolbarHeight.roundToPx()
        val edge = HorizontalEdge.roundToPx()
        val navTitleGap = NavigationTitleGap.roundToPx()
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = barHeight)

        val navigation = measurables.first { it.layoutId == NavigationSlot }.measure(looseConstraints)
        val actionIcons = measurables.first { it.layoutId == ActionsSlot }.measure(looseConstraints)
        val titleX = if (navigation.width > 0) edge + navigation.width + navTitleGap else edge
        val actionsX = constraints.maxWidth - edge - actionIcons.width
        val titleMaxWidth = (actionsX - titleX).coerceAtLeast(0)
        val titlePlaceable = measurables.first { it.layoutId == TitleSlot }.measure(
            looseConstraints.copy(maxWidth = titleMaxWidth),
        )

        layout(constraints.maxWidth, barHeight) {
            navigation.placeRelative(
                x = edge,
                y = (barHeight - navigation.height) / 2,
            )
            titlePlaceable.placeRelative(
                x = titleX,
                y = ((barHeight - titlePlaceable.height) / 2).coerceAtLeast(0),
            )
            actionIcons.placeRelative(
                x = actionsX,
                y = (barHeight - actionIcons.height) / 2,
            )
        }
    }
}

private const val NavigationSlot = "navigation"
private const val TitleSlot = "title"
private const val ActionsSlot = "actions"

private val ToolbarHeight = 52.dp
private val HorizontalEdge = 16.dp
private val NavigationTitleGap = 10.dp
private val ActionSpacing = 10.dp
private val DividerExpandedInset = 24.dp
private val DividerThickness: Dp = 0.33.dp
