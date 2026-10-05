package com.mi.onextbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

val ONextBoxLogoFontFamily = FontFamily(Font(R.font.opsans_en_regular))

@Composable
fun ONextBoxLogo(
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.onSurface,
    fontSize: TextUnit = 32.sp,
    lineHeight: TextUnit = 38.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    strokeWidth: Dp = 0.6.dp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val text = stringResource(R.string.app_name)
    val style = COUITheme.textStyles.title1.copy(
        fontFamily = ONextBoxLogoFontFamily,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
    )

    Box(modifier = modifier) {
        if (strokeWidthPx > 0f) {
            Text(
                text = text,
                style = style.copy(drawStyle = Stroke(width = strokeWidthPx)),
                color = color,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
