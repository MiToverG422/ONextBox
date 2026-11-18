package com.mi.onextbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.mi.onextbox.R
import io.github.suqi8.coui.kmp.basic.Text

private val countryFlagFont = FontFamily(Font(R.font.discord_twemoji_flags))

@Composable
internal fun TwemojiFlagText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
) {
    if ('\uD83C' !in text) {
        Text(text = text, modifier = modifier, fontSize = fontSize, color = color)
        return
    }
    val annotated = remember(text) {
        AnnotatedString.Builder(text).apply {
            countryFlagRanges(text).forEach { range ->
                addStyle(
                    SpanStyle(fontFamily = countryFlagFont, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal),
                    range.start,
                    range.end,
                )
            }
        }.toAnnotatedString()
    }
    Text(text = annotated, modifier = modifier, fontSize = fontSize, color = color)
}
