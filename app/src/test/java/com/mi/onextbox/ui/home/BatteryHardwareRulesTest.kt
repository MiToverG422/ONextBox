package com.mi.onextbox.ui.home

import org.junit.Assert.*
import org.junit.Test

class BatteryHardwareRulesTest {
    @Test fun convertsCapacityUnitsWithoutMixingMicroampHoursAndMilliampHours() {
        assertEquals(7050.0, BatteryHardwareRules.capacityMah("7050000", micro = true)!!, 0.001)
        assertEquals(7049.0, BatteryHardwareRules.capacityMah("7049")!!, 0.001)
        assertNull(BatteryHardwareRules.capacityMah("0"))
        assertNull(BatteryHardwareRules.capacityMah(Int.MIN_VALUE.toString(), micro = true))
        assertNull(BatteryHardwareRules.capacityMah("NaN"))
    }

    @Test fun acceptsZeroCyclesButRejectsUnsupportedSentinels() {
        assertEquals(0L, BatteryHardwareRules.cycles("0"))
        assertEquals(160L, BatteryHardwareRules.cycles("160"))
        listOf("-1", "65535", Int.MAX_VALUE.toString(), "160 cycles").forEach {
            assertNull(it, BatteryHardwareRules.cycles(it))
        }
    }

    @Test fun dateFormatsDoNotInterpretMissingDatesAs1970OrDeviceBootTime() {
        assertEquals("2026-07-04", BatteryHardwareRules.date("20260704"))
        assertEquals("2026-07-04", BatteryHardwareRules.date("2026/7/4"))
        assertEquals("2026-07-04", BatteryHardwareRules.date("2026-07-04"))
        listOf("", "0", "-1", "1970-01-10", "20260230", "65535", "unknown", Long.MIN_VALUE.toString()).forEach {
            assertNull(it, BatteryHardwareRules.date(it))
        }
    }

    @Test fun androidDateSecondsAndMillisecondsResolveToTheSameUtcDate() {
        assertEquals("2026-07-04", BatteryHardwareRules.date("1783123200"))
        assertEquals("2026-07-04", BatteryHardwareRules.date("1783123200000"))
    }

    @Test fun percentageAndPowerFormattingPreservesZerosSignsAndPrecision() {
        assertEquals("0", BatteryHardwareRules.format(0.0))
        assertEquals("-125.5", BatteryHardwareRules.format(-125.5))
        assertEquals("100", BatteryHardwareRules.format(100.0, 0))
        assertEquals("99.99", BatteryHardwareRules.format(BatteryHardwareRules.ratio(7049.0, 7050.0)!!))
        assertNull(BatteryHardwareRules.ratio(7049.0, 0.0))
        assertNull(BatteryHardwareRules.percent("0"))
        assertNull(BatteryHardwareRules.percent("101"))
    }

    @Test fun readsSelectedUsbTypeAndOemChemistryIdentifier() {
        assertEquals("SDP", BatteryHardwareRules.selectedUsbType("Unknown [SDP] DCP PD PD_PPS"))
        assertEquals("PD", BatteryHardwareRules.selectedUsbType("PD"))
        assertEquals("617074", BatteryHardwareRules.chemistryId("[0][gauge-virtual:0]36 31 37 30 37 34 0 0\n,"))
        assertEquals("617074", BatteryHardwareRules.chemistryId("617074"))
    }

    @Test fun rootResultsKeepMultilineSerialsAndEmptyDateFieldsSeparate() {
        val result = BatteryHardwareRules.parseFields(listOf("root notice", "ONB_BAT=serial", "cell-A", "cell-B",
            "ONB_BAT=manufactured", "", "ONB_BAT=fcc", "7049"))
        assertEquals("cell-A\ncell-B", result["serial"])
        assertEquals("", result["manufactured"])
        assertEquals("7049", result["fcc"])
        assertNull(BatteryHardwareRules.text(result["manufactured"]))
    }
}
