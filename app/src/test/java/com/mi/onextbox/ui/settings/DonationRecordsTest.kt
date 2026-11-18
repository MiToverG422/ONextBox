package com.mi.onextbox.ui.settings

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DonationRecordsTest {
    @Test fun amountAndDateOrderNumericallyAndIndependentlyWithoutChangingTheSource() {
        val entries = listOf(
            DonationRecord("A", 2000, LocalDate.of(2026, 10, 20)),
            DonationRecord("B", 900, LocalDate.of(2026, 11, 1)),
            DonationRecord("C", 1100, LocalDate.of(2026, 1, 1)),
        )
        assertEquals(listOf("A", "C", "B"), entries.sortedFor(DonationSort.AmountDescending).map { it.name })
        assertEquals(listOf("B", "C", "A"), entries.sortedFor(DonationSort.AmountAscending).map { it.name })
        assertEquals(listOf("B", "A", "C"), entries.sortedFor(DonationSort.NewestFirst).map { it.name })
        assertEquals(listOf("C", "A", "B"), entries.sortedFor(DonationSort.OldestFirst).map { it.name })
        assertEquals(listOf("A", "B", "C"), entries.map { it.name })
    }

    @Test fun amountsKeepExactCentPrecisionAndDatesKeepTheRequestedFormat() {
        val record = DonationRecord("A", 2001, LocalDate.of(2026, 1, 2))
        assertEquals("20.01 CNY", record.amountText)
        assertEquals("2026/01/02", record.dateText)
        assertEquals("20 CNY", record.copy(amountCnyFen = 2000).amountText)
    }
}
