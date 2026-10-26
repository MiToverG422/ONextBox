// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A plain time-of-day holder used by [TimePicker].
 *
 * The hour is always stored in 24-hour form (0..23) regardless of the
 * display mode of the picker; out-of-range values are coerced.
 *
 * @param hour The hour of the day in 24-hour form (0..23).
 * @param minute The minute of the hour (0..59).
 */
@Immutable
data class TimeValue(
    val hour: Int,
    val minute: Int,
)

/**
 * A [TimePicker] component with COUI style.
 *
 * Hour and minute scroll wheels arranged like the COUI time picker
 * (COUITimeLimitPicker). In 12-hour mode an AM/PM wheel is added; COUI places
 * it before the hour wheel for locales whose time pattern starts with the
 * day period (e.g. Chinese) and after the minute wheel otherwise, which is
 * controlled here by [amPmFirst]. Optional unit labels (e.g. "h" / "min")
 * are drawn beside the selected row, matching the COUI unit text.
 *
 * @param value The currently selected time. The hour is always in 24-hour form.
 * @param onValueChange The callback invoked when the selected time changes.
 * @param modifier The modifier to be applied to the [TimePicker].
 * @param enabled Whether the [TimePicker] is enabled for user interaction.
 * @param is24Hour Whether the hour wheel uses the 24-hour format. When false,
 *   hours display as 1..12 with an AM/PM wheel.
 * @param amPmFirst Whether the AM/PM wheel is placed before the hour wheel
 *   (only used when [is24Hour] is false).
 * @param amLabel The display string for AM on the AM/PM wheel.
 * @param pmLabel The display string for PM on the AM/PM wheel.
 * @param hourUnit The unit label drawn beside the selected hour; empty to hide.
 * @param minuteUnit The unit label drawn beside the selected minute; empty to hide.
 * @param hourColumnWidth The width of the hour column.
 * @param minuteColumnWidth The width of the minute column.
 * @param amPmColumnWidth The width of the AM/PM column.
 * @param colors The [NumberPickerColors] for the wheels.
 * @param textStyle The [TextStyle] for the wheel items.
 * @param itemHeight The height of each wheel item.
 * @param insideMargin The padding around the wheel columns.
 */
@Composable
fun TimePicker(
    value: TimeValue,
    onValueChange: (TimeValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    is24Hour: Boolean = true,
    amPmFirst: Boolean = false,
    amLabel: String = TimePickerDefaults.AmLabel,
    pmLabel: String = TimePickerDefaults.PmLabel,
    hourUnit: String = TimePickerDefaults.HourUnit,
    minuteUnit: String = TimePickerDefaults.MinuteUnit,
    hourColumnWidth: Dp = TimePickerDefaults.HourColumnWidth,
    minuteColumnWidth: Dp = TimePickerDefaults.MinuteColumnWidth,
    amPmColumnWidth: Dp = TimePickerDefaults.AmPmColumnWidth,
    colors: NumberPickerColors = NumberPickerDefaults.colors(),
    textStyle: TextStyle = COUITheme.textStyles.title3,
    itemHeight: Dp = NumberPickerDefaults.ItemHeight,
    insideMargin: PaddingValues = TimePickerDefaults.InsideMargin,
) {
    val coercedHour = value.hour.coerceIn(0, 23)
    val coercedMinute = value.minute.coerceIn(0, 59)

    // Latest state readable from the remember-cached per-wheel callbacks below.
    val currentValue by rememberUpdatedState(TimeValue(coercedHour, coercedMinute))
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentIs24Hour by rememberUpdatedState(is24Hour)

    val onHourChange = remember {
        { newHour: Int ->
            val time = currentValue
            // Wheel shows 1..12; map back to 24-hour form keeping the current half of the day.
            val hour = when {
                currentIs24Hour -> newHour
                time.hour < 12 -> newHour % 12
                else -> newHour % 12 + 12
            }
            currentOnValueChange(TimeValue(hour, time.minute))
        }
    }
    val onMinuteChange = remember {
        { newMinute: Int ->
            currentOnValueChange(currentValue.copy(minute = newMinute))
        }
    }
    val onAmPmChange = remember {
        { newAmPm: Int ->
            val time = currentValue
            val hour = if (newAmPm == 0) time.hour % 12 else time.hour % 12 + 12
            if (hour != time.hour) {
                currentOnValueChange(TimeValue(hour, time.minute))
            }
        }
    }

    val amPmLabel = remember(amLabel, pmLabel) {
        { amPm: Int -> if (amPm == 0) amLabel else pmLabel }
    }
    // COUI zero-pads hours only in 24-hour mode; minutes are always two digits.
    val hourLabel = if (is24Hour) TwoDigitLabel else PlainLabel
    val hourRange = if (is24Hour) 0..23 else 1..12
    val displayHour = when {
        is24Hour -> coercedHour
        coercedHour % 12 == 0 -> 12
        else -> coercedHour % 12
    }
    val showAmPm = !is24Hour

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(insideMargin),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAmPm && amPmFirst) {
            AmPmWheel(
                value = if (coercedHour < 12) 0 else 1,
                onValueChange = onAmPmChange,
                enabled = enabled,
                label = amPmLabel,
                columnWidth = amPmColumnWidth,
                colors = colors,
                textStyle = textStyle,
                itemHeight = itemHeight,
            )
        }
        WheelWithUnit(
            value = displayHour,
            onValueChange = onHourChange,
            enabled = enabled,
            range = hourRange,
            label = hourLabel,
            columnWidth = hourColumnWidth,
            unit = hourUnit,
            colors = colors,
            textStyle = textStyle,
            itemHeight = itemHeight,
        )
        WheelWithUnit(
            value = coercedMinute,
            onValueChange = onMinuteChange,
            enabled = enabled,
            range = 0..59,
            label = TwoDigitLabel,
            columnWidth = minuteColumnWidth,
            unit = minuteUnit,
            colors = colors,
            textStyle = textStyle,
            itemHeight = itemHeight,
        )
        if (showAmPm && !amPmFirst) {
            AmPmWheel(
                value = if (coercedHour < 12) 0 else 1,
                onValueChange = onAmPmChange,
                enabled = enabled,
                label = amPmLabel,
                columnWidth = amPmColumnWidth,
                colors = colors,
                textStyle = textStyle,
                itemHeight = itemHeight,
            )
        }
    }
}

@Composable
private fun WheelWithUnit(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    range: IntRange,
    label: (Int) -> String,
    columnWidth: Dp,
    unit: String,
    colors: NumberPickerColors,
    textStyle: TextStyle,
    itemHeight: Dp,
) {
    Box {
        NumberPicker(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(columnWidth),
            enabled = enabled,
            range = range,
            label = label,
            wrapAround = true,
            colors = colors,
            textStyle = textStyle,
            itemHeight = itemHeight,
        )
        if (unit.isNotEmpty()) {
            // COUI draws the unit at the selected value's trailing edge, offset from the wheel center.
            Text(
                text = unit,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = columnWidth / 2 + TimePickerDefaults.UnitTextOffset),
                color = colors.selectedTextColor(enabled),
                fontSize = TimePickerDefaults.UnitFontSize,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
@NonRestartableComposable
private fun AmPmWheel(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    label: (Int) -> String,
    columnWidth: Dp,
    colors: NumberPickerColors,
    textStyle: TextStyle,
    itemHeight: Dp,
) {
    NumberPicker(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.width(columnWidth),
        enabled = enabled,
        range = 0..1,
        label = label,
        wrapAround = false,
        colors = colors,
        textStyle = textStyle,
        itemHeight = itemHeight,
    )
}

/**
 * Contains default values used by [TimePicker].
 */
object TimePickerDefaults {

    /** Width of the hour column (COUI coui_number_picker_width_middle). */
    val HourColumnWidth = 76.dp

    /** Width of the minute column (COUI coui_number_picker_width_middle). */
    val MinuteColumnWidth = 76.dp

    /** Width of the AM/PM column (COUI coui_number_picker_width_small). */
    val AmPmColumnWidth = 62.dp

    /** Padding around the wheel columns (COUI coui_number_picker_view_padding_vertical). */
    val InsideMargin = PaddingValues(vertical = 12.dp)

    /** Offset of the unit label from the wheel center: selected value width / 2 + 4dp margin. */
    val UnitTextOffset = 13.dp

    /** Font size of the unit label (COUI coui_numberpicker_unit_textSize). */
    val UnitFontSize = 14.sp

    /** Default AM label, as COUI shows in English. */
    val AmLabel = "AM"

    /** Default PM label, as COUI shows in English. */
    val PmLabel = "PM"

    /** Default hour unit label (hidden). */
    val HourUnit = ""

    /** Default minute unit label (hidden). */
    val MinuteUnit = ""
}

private val TwoDigitLabel: (Int) -> String = { it.toString().padStart(2, '0') }

private val PlainLabel: (Int) -> String = { it.toString() }
