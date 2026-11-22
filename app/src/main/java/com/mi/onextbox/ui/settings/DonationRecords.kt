package com.mi.onextbox.ui.settings

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal data class DonationRecord(val name: String, val amountCnyFen: Long, val date: LocalDate) {
    val amountText: String get() = BigDecimal.valueOf(amountCnyFen, 2).stripTrailingZeros().toPlainString() + " CNY"
    val dateText: String get() = date.format(DonationDateFormat)
}

private val DonationDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)

internal enum class DonationSort { AmountDescending, AmountAscending, NewestFirst, OldestFirst }

@Serializable
private data class DonationTableRow(val name: String, val amountCny: JsonPrimitive, val date: String)

private val donationTableJson = Json { ignoreUnknownKeys = true }

internal fun parseDonationRecords(text: String): List<DonationRecord> =
    donationTableJson.decodeFromString<List<DonationTableRow>>(text.removePrefix("\uFEFF")).map { row ->
        val name = row.name.trim()
        require(name.isNotEmpty() && !row.amountCny.isString)
        val amountCnyFen = row.amountCny.content.toBigDecimal().movePointRight(2).longValueExact()
        require(amountCnyFen > 0)
        DonationRecord(name, amountCnyFen, LocalDate.parse(row.date))
    }

internal fun List<DonationRecord>.sortedFor(sort: DonationSort): List<DonationRecord> = sortedWith(
    when (sort) {
        DonationSort.AmountDescending -> compareByDescending<DonationRecord> { it.amountCnyFen }.thenByDescending { it.date }
        DonationSort.AmountAscending -> compareBy<DonationRecord> { it.amountCnyFen }.thenByDescending { it.date }
        DonationSort.NewestFirst -> compareByDescending<DonationRecord> { it.date }.thenByDescending { it.amountCnyFen }
        DonationSort.OldestFirst -> compareBy<DonationRecord> { it.date }.thenByDescending { it.amountCnyFen }
    }.thenBy { it.name },
)
