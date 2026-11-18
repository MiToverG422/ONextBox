package com.mi.onextbox.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeBetaBadge() {
    Layout(
        modifier = Modifier.size(90.dp).clipToBounds().testTag("homeBetaBadge"),
        content = { GoldBetaRibbon() },
    ) { measurables, constraints ->
        val ribbonWidth = 128.dp.roundToPx()
        val ribbon = measurables.single().measure(Constraints(
            minWidth = ribbonWidth, maxWidth = ribbonWidth, maxHeight = constraints.maxHeight,
        ))
        layout(constraints.maxWidth, constraints.maxHeight) {
            ribbon.placeRelative(
                (constraints.maxWidth * .6f - ribbon.width / 2f).toInt(),
                (constraints.maxHeight * .4f - ribbon.height / 2f).toInt(),
            )
        }
    }
}

@Composable
private fun GoldBetaRibbon() {
    val density = LocalDensity.current
    BasicText(
        text = "BETA",
        maxLines = 1,
        style = TextStyle(
            color = Color(0xFF503006),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            letterSpacing = .9.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color(0xFFFFF2B6), Offset(0f, with(density) { .55.dp.toPx() }), .3f),
        ),
        modifier = Modifier
            .testTag("homeBetaRibbon")
            .graphicsLayer { rotationZ = 45f }
            .shadow(2.dp, RectangleShape, clip = false)
            .drawWithCache {
                val edge = .7.dp.toPx()
                val gold = Brush.linearGradient(
                    0f to Color(0xFFFFF3B0),
                    .16f to Color(0xFFE8B944),
                    .34f to Color(0xFFBD861F),
                    .48f to Color(0xFFFFD86B),
                    .55f to Color(0xFFFFF4BB),
                    .67f to Color(0xFFE7B63F),
                    .88f to Color(0xFFC28C24),
                    1f to Color(0xFFFFDE82),
                    start = Offset(size.width * .16f, 0f),
                    end = Offset(size.width * .82f, size.height),
                )
                val rim = Brush.verticalGradient(listOf(Color(0xFFFFF5BE), Color(0xFF9E6A12)))
                val grain = Path().apply {
                    val step = .85.dp.toPx()
                    var y = step
                    while (y < size.height + 2.dp.toPx()) {
                        moveTo(0f, y)
                        lineTo(size.width, y - 2.dp.toPx())
                        y += step
                    }
                }
                onDrawBehind {
                    drawRect(gold)
                    drawPath(grain, Color(0xFFFFF5CC).copy(alpha = .12f), style = Stroke(.25.dp.toPx()))
                    drawRect(rim, topLeft = Offset(edge / 2, edge / 2),
                        size = Size(size.width - edge, size.height - edge),
                        style = Stroke(edge))
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
