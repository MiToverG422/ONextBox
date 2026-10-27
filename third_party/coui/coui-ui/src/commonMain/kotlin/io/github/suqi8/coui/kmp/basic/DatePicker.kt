// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A plain calendar date holder used by [DatePicker].
 *
 * Values are stored as simple integers so no date/time library is required.
 * Out-of-range values passed to [DatePicker] are coerced (e.g. day 31 in a
 * 30-day month is clamped to 30).
 *
 * @param year The calendar year (e.g. 2026).
 * @param month The month of the year, 1-based (1 = January .. 12 = December).
 * @param day The day of the month, 1-based.
 */
@Immutable
data class DateValue(
    val year: Int,
    val month: Int,
    val day: Int,
)

/**
 * A [DatePicker] component with COUI style.
 *
 * Three scroll wheels (year / month / day) arranged like the COUI date picker:
 * the columns are centered as a group, the day range follows the selected
 * year and month (leap years included), and the day is clamped when a shorter
 * month is selected. All columns wrap around, matching COUIDatePicker.
 *
 * @param value The currently selected date. Out-of-range fields are coerced.
 * @param onValueChange The callback invoked when the selected date changes.
 * @param modifier The modifier to be applied to the [DatePicker].
 * @param enabled Whether the [DatePicker] is enabled for user interaction.
 * @param yearRange The selectable year range.
 * @param yearLabel Converts a year to its display string.
 * @param monthLabel Converts a month (1..12) to its display string.
 * @param dayLabel Converts a day of month to its display string.
 * @param yearColumnWidth The width of the year column.
 * @param monthColumnWidth The width of the month column.
 * @param dayColumnWidth The width of the day column.
 * @param colors The [NumberPickerColors] for the wheels.
 * @param textStyle The [TextStyle] for the wheel items.
 * @param itemHeight The height of each wheel item.
 * @param insideMargin The padding around the wheel columns.
 */
@Composable
fun DatePicker(
    value: DateValue,
    onValueChange: (DateValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    yearRange: IntRange = DatePickerDefaults.YearRange,
    yearLabel: (Int) -> String = DatePickerDefaults.YearLabel,
    monthLabel: (Int) -> String = DatePickerDefaults.MonthLabel,
    dayLabel: (Int) -> String = DatePickerDefaults.DayLabel,
    yearColumnWidth: Dp = DatePickerDefaults.YearColumnWidth,
    monthColumnWidth: Dp = DatePickerDefaults.MonthColumnWidth,
    dayColumnWidth: Dp = DatePickerDefaults.DayColumnWidth,
    colors: NumberPickerColors = NumberPickerDefaults.colors(),
    textStyle: TextStyle = COUITheme.textStyles.title3,
    itemHeight: Dp = NumberPickerDefaults.ItemHeight,
    insideMargin: PaddingValues = DatePickerDefaults.InsideMargin,
) {
    require(yearRange.first <= yearRange.last) {
        "yearRange must not be empty"
    }

    val coercedYear = value.year.coerceIn(yearRange)
    val coercedMonth = value.month.coerceIn(1, 12)
    val daysOfMonth = daysInMonth(coercedYear, coercedMonth)
    val coercedDay = value.day.coerceIn(1, daysOfMonth)

    // Latest state readable from the remember-cached per-column callbacks below.
    val currentValue by rememberUpdatedState(DateValue(coercedYear, coercedMonth, coercedDay))
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val onYearChange = remember {
        { newYear: Int ->
            val date = currentValue
            val day = date.day.coerceAtMost(daysInMonth(newYear, date.month))
            currentOnValueChange(DateValue(newYear, date.month, day))
        }
    }
    val onMonthChange = remember {
        { newMonth: Int ->
            val date = currentValue
            val day = date.day.coerceAtMost(daysInMonth(date.year, newMonth))
            currentOnValueChange(DateValue(date.year, newMonth, day))
        }
    }
    val onDayChange = remember {
        { newDay: Int ->
            currentOnValueChange(currentValue.copy(day = newDay))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(insideMargin),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberPicker(
            value = coercedYear,
            onValueChange = onYearChange,
            modifier = Modifier.width(yearColumnWidth),
            enabled = enabled,
            range = yearRange,
            label = yearLabel,
            wrapAround = true,
            colors = colors,
            textStyle = textStyle,
            itemHeight = itemHeight,
        )
        NumberPicker(
            value = coercedMonth,
            onValueChange = onMonthChange,
            modifier = Modifier.width(monthColumnWidth),
            enabled = enabled,
            range = 1..12,
            label = monthLabel,
            wrapAround = true,
            colors = colors,
            textStyle = textStyle,
            itemHeight = itemHeight,
        )
        NumberPicker(
            value = coercedDay,
            onValueChange = onDayChange,
            modifier = Modifier.width(dayColumnWidth),
            enabled = enabled,
            range = 1..daysOfMonth,
            label = dayLabel,
            wrapAround = true,
            colors = colors,
            textStyle = textStyle,
            itemHeight = itemHeight,
        )
    }
}

/**
 * Contains default values used by [DatePicker].
 */
object DatePickerDefaults {

    /** Default selectable year range (COUI: 1900..2100). */
    val YearRange: IntRange = 1900..2100

    /** Width of the year column (COUI coui_datepicker_width_year). */
    val YearColumnWidth = 76.dp

    /** Width of the month column (COUI coui_datepicker_width_month). */
    val MonthColumnWidth = 96.dp

    /** Width of the day column (COUI coui_number_picker_width_small). */
    val DayColumnWidth = 62.dp

    /** Padding around the wheel columns (COUI coui_number_picker_view_padding_vertical). */
    val InsideMargin = PaddingValues(vertical = 12.dp)

    /** Default year label: the plain year number. */
    val YearLabel: (Int) -> String = { it.toString() }

    /** Default month label: abbreviated English month names, as COUI shows in English. */
    val MonthLabel: (Int) -> String = { MonthAbbreviations[it - 1] }

    /** Default day label: zero-padded two digits, as COUI shows in English. */
    val DayLabel: (Int) -> String = { it.toString().padStart(2, '0') }

    private val MonthAbbreviations = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 31
}
