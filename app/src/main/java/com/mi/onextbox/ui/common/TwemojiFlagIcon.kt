package com.mi.onextbox.ui.common

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.res.ResourcesCompat
import com.mi.onextbox.R

internal class CountryFlagGlyph(val value: String, val paint: Paint, val bounds: Rect) {
    val aspectRatio: Float = bounds.width().toFloat() / bounds.height()
}

@Composable
internal fun rememberCountryFlagGlyph(flag: String): CountryFlagGlyph {
    val context = LocalContext.current
    return remember(context, flag) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.discord_twemoji_flags)
            textSize = 128f
            color = android.graphics.Color.WHITE
            alpha = 255
        }
        val bounds = Rect()
        paint.getTextBounds(flag, 0, flag.length, bounds)
        CountryFlagGlyph(flag, paint, bounds)
    }
}

@Composable
internal fun TwemojiFlagIcon(glyph: CountryFlagGlyph, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { text = AnnotatedString(glyph.value) }) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val save = native.save()
            native.scale(size.width / glyph.bounds.width(), size.height / glyph.bounds.height())
            native.translate(-glyph.bounds.left.toFloat(), -glyph.bounds.top.toFloat())
            native.drawText(glyph.value, 0f, 0f, glyph.paint)
            native.restoreToCount(save)
        }
    }
}
