package com.mi.onextbox.ui.settings

import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test fun githubTableSupportsUtf8ExactAmountsAndAnEmptyList() {
        val records = parseDonationRecords(
            """[{"name":"Coolapk@测试","amountCny":20.01,"date":"2026-10-20"},
                {"name":"支持者","amountCny":9,"date":"2026-11-19"}]""",
        )
        assertEquals(listOf("Coolapk@测试", "支持者"), records.map { it.name })
        assertEquals(listOf(2001L, 900L), records.map { it.amountCnyFen })
        assertEquals("2026/11/19", records.last().dateText)
        assertEquals(emptyList<DonationRecord>(), parseDonationRecords("[]"))
    }

    @Test fun githubTableRejectsInvalidDatesAndAmountsInsteadOfReturningAPartialList() {
        assertThrows(DateTimeParseException::class.java) {
            parseDonationRecords(
                """[{"name":"A","amountCny":20,"date":"2026-10-20"},
                    {"name":"B","amountCny":1,"date":"2026-02-30"}]""",
            )
        }
        assertThrows(ArithmeticException::class.java) {
            parseDonationRecords("""[{"name":"A","amountCny":0.001,"date":"2026-10-20"}]""")
        }
    }
}
