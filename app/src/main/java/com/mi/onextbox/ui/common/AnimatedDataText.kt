package com.mi.onextbox.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun AnimatedDataText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = COUITheme.textStyles.body2,
    color: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                .using(SizeTransform { _, _ -> tween(280, easing = FastOutSlowInEasing) })
        },
        label = "dataText",
    ) { displayed ->
        if (displayed.isNotEmpty()) Text(
            text = displayed,
            modifier = if (displayed == text) Modifier else Modifier.clearAndSetSemantics {},
            style = style,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
        )
    }
}
