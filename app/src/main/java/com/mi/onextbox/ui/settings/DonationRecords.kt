package com.mi.onextbox.ui.settings

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class DonationRecord(val name: String, val amountCnyFen: Long, val date: LocalDate) {
    val amountText: String get() = BigDecimal.valueOf(amountCnyFen, 2).stripTrailingZeros().toPlainString() + " CNY"
    val dateText: String get() = date.format(DonationDateFormat)
}

private val DonationDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)

internal enum class DonationSort { AmountDescending, AmountAscending, NewestFirst, OldestFirst }

internal val donationRecords = listOf(
    DonationRecord("Coolapk@超级无敌牛批暴龙战士", 2000, LocalDate.of(2026, 10, 20)),
)

internal fun List<DonationRecord>.sortedFor(sort: DonationSort): List<DonationRecord> = sortedWith(
    when (sort) {
        DonationSort.AmountDescending -> compareByDescending<DonationRecord> { it.amountCnyFen }.thenByDescending { it.date }
        DonationSort.AmountAscending -> compareBy<DonationRecord> { it.amountCnyFen }.thenByDescending { it.date }
        DonationSort.NewestFirst -> compareByDescending<DonationRecord> { it.date }.thenByDescending { it.amountCnyFen }
        DonationSort.OldestFirst -> compareBy<DonationRecord> { it.date }.thenByDescending { it.amountCnyFen }
    }.thenBy { it.name },
)
