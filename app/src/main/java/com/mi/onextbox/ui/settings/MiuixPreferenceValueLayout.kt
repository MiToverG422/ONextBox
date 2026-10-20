package com.mi.onextbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun miuixPreferenceValueFitsInline(width: Int, titleWidth: Int, valueWidth: Int, gap: Int): Boolean =
    width > 0 && titleWidth.toLong() + valueWidth + gap <= width

internal data class MiuixPreferenceVerticalPlacement(
    val height: Int,
    val titleTop: Int,
    val summaryTop: Int,
    val trailingTop: Int,
)

internal fun miuixPreferenceVerticalPlacement(
    titleHeight: Int, summaryHeight: Int?, trailingHeight: Int,
    stacked: Boolean, summaryGap: Int, stackedGap: Int,
): MiuixPreferenceVerticalPlacement {
    val summaryOffset = titleHeight + if (summaryHeight != null) summaryGap else 0
    val textHeight = summaryOffset + (summaryHeight ?: 0)
    val height = if (stacked) textHeight + stackedGap + trailingHeight else maxOf(textHeight, trailingHeight)
    val textTop = if (stacked) 0 else (height - textHeight) / 2
    return MiuixPreferenceVerticalPlacement(
        height, textTop, textTop + summaryOffset,
        if (stacked) textHeight + stackedGap else (height - trailingHeight) / 2,
    )
}

/** Prefer a trailing value; move it to its own line instead of truncating either label. */
@Composable
internal fun MiuixPreferenceValueLayout(
    title: String,
    summary: String?,
    value: String?,
    enabled: Boolean,
    endAction: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Layout(content = {
        if (endAction != null) Box(Modifier.layoutId("action")) { endAction() }
        Text(
            text = title,
            modifier = Modifier.layoutId("title"),
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = if (enabled) colors.onSurface else colors.disabledOnSecondaryVariant,
        )
        if (!value.isNullOrBlank()) Text(
            text = value,
            modifier = Modifier.layoutId("value"),
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            textAlign = TextAlign.End,
            color = if (enabled) colors.onSurfaceVariantActions else colors.disabledOnSecondaryVariant,
        )
        if (!summary.isNullOrBlank()) Text(
            text = summary,
            modifier = Modifier.layoutId("summary"),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = if (enabled) colors.onSurfaceVariantSummary else colors.disabledOnSecondaryVariant,
        )
    }) { measurables, constraints ->
        val titleMeasurable = measurables.first { it.layoutId == "title" }
        val valueMeasurable = measurables.firstOrNull { it.layoutId == "value" }
        val summaryMeasurable = measurables.firstOrNull { it.layoutId == "summary" }
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val action = measurables.firstOrNull { it.layoutId == "action" }?.measure(loose)
        val actionGap = if (action != null) 8.dp.roundToPx() else 0
        val textWidth = (width - (action?.width ?: 0) - actionGap).coerceAtLeast(0)
        val textConstraints = loose.copy(maxWidth = textWidth)
        val gap = 12.dp.roundToPx()
        val valueWidth = valueMeasurable?.maxIntrinsicWidth(constraints.maxHeight) ?: 0
        val inline = valueMeasurable != null && miuixPreferenceValueFitsInline(
            textWidth, titleMeasurable.maxIntrinsicWidth(constraints.maxHeight), valueWidth, gap,
        )
        val titlePlaceable = titleMeasurable.measure(
            textConstraints.copy(maxWidth = if (inline) (textWidth - valueWidth - gap).coerceAtLeast(0) else textWidth),
        )
        val valuePlaceable = valueMeasurable?.measure(textConstraints)
        val wrappedValue = valuePlaceable != null && !inline
        // The summary belongs to the left column, not beneath both columns.
        val summaryPlaceable = summaryMeasurable?.measure(
            loose.copy(maxWidth = when {
                wrappedValue -> width
                inline -> (textWidth - valueWidth - gap).coerceAtLeast(0)
                else -> textWidth
            }),
        )
        val trailingHeight = maxOf(valuePlaceable?.height ?: 0, action?.height ?: 0)
        val placement = miuixPreferenceVerticalPlacement(
            titlePlaceable.height, summaryPlaceable?.height, trailingHeight, wrappedValue,
            summaryGap = 2.dp.roundToPx(), stackedGap = 4.dp.roundToPx(),
        )
        val height = constraints.constrainHeight(placement.height)
        layout(width, height) {
            val offset = ((height - placement.height) / 2).coerceAtLeast(0)
            titlePlaceable.placeRelative(0, offset + placement.titleTop)
            valuePlaceable?.let {
                it.placeRelative(textWidth - it.width, offset + placement.trailingTop + (trailingHeight - it.height) / 2)
            }
            action?.placeRelative(width - action.width, offset + placement.trailingTop + (trailingHeight - action.height) / 2)
            summaryPlaceable?.placeRelative(0, offset + placement.summaryTop)
        }
    }
}
