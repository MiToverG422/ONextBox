package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryHealthInfoRulesTest {
    @Test fun standardDriverCapacityIsConvertedFromMicroampHours() {
        assertEquals(7050, BatteryHealthInfoRules.microampHours("7050000\n"))
        assertEquals(4821, BatteryHealthInfoRules.microampHours("4821000"))
        assertEquals(4822, BatteryHealthInfoRules.microampHours("4821500"))
        assertEquals(7050, BatteryHealthInfoRules.milliampHours("7050\n"))
    }

    @Test fun invalidAndUnavailableReadingsDoNotBecomeZeroCapacity() {
        listOf(null, "", "-1", "0", "unknown", "7050 mAh", Long.MAX_VALUE.toString()).forEach {
            assertNull(BatteryHealthInfoRules.microampHours(it))
            assertNull(BatteryHealthInfoRules.milliampHours(it))
        }
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 100_001.0).forEach {
            assertNull(BatteryHealthInfoRules.capacity(it))
        }
    }

    @Test fun ratedAndTypicalCapacityKeepTheirOwnValues() {
        val info = BatteryHealthInfo(
            cycleCount = 0, health = 2,
            fullChargeCapacity = BatteryHealthInfoRules.microampHours("6619000"),
            ratedCapacity = BatteryHealthInfoRules.capacity(6850.0),
            typicalCapacity = BatteryHealthInfoRules.capacity(7050.0),
        )
        assertEquals(0, info.cycleCount)
        assertEquals(6619, info.fullChargeCapacity)
        assertEquals(6850, info.ratedCapacity)
        assertEquals(7050, info.typicalCapacity)
        assertNull(BatteryHealthInfo().ratedCapacity)
    }

    @Test fun healthAcceptsAllNativeStatesAndRejectsMissingValues() {
        (1..7).forEach { assertEquals(it, BatteryHealthInfoRules.health(it)) }
        listOf(null, -1, 0, 8, Int.MAX_VALUE).forEach { assertNull(BatteryHealthInfoRules.health(it)) }
    }

    @Test fun explicitRatedCapacityWinsAndMissingProfilesUseDriverDesignCapacity() {
        assertEquals(6850, BatteryHealthInfoRules.ratedCapacity(6850.0, "7050", "7050000"))
        assertEquals(7050, BatteryHealthInfoRules.ratedCapacity(0.0, "7050", "7000000"))
        assertEquals(7000, BatteryHealthInfoRules.ratedCapacity(null, "-1", "7000000"))
        assertNull(BatteryHealthInfoRules.ratedCapacity(null, "0", "-1"))
    }

    @Test fun vendorLearnedFullChargeCapacityWinsOverTheStandardReportedValue() {
        assertEquals(7034, BatteryHealthInfoRules.fullChargeCapacity("7034", "7050000", "6800"))
        assertEquals(7050, BatteryHealthInfoRules.fullChargeCapacity("-1", "7050000", "6800"))
        assertEquals(6800, BatteryHealthInfoRules.fullChargeCapacity(null, "0", "6800"))
        assertNull(BatteryHealthInfoRules.fullChargeCapacity(null, "-1", "0"))
    }

    @Test fun calculatedHealthUsesFullChargeCapacityRelativeToTheRatedCapacity() {
        assertEquals(0.9977304964539007, BatteryHealthInfoRules.healthRatio(7034, 7050)!!, 0.000000000001)
        assertEquals(0.9, BatteryHealthInfoRules.healthRatio(4500, 5000)!!, 0.000000000001)
        assertEquals(1.0, BatteryHealthInfoRules.healthRatio(7050, 7050)!!, 0.000000000001)
    }

    @Test fun learnedCapacityAboveTheRatingIsNotClampedToOneHundredPercent() {
        assertEquals(1.02, BatteryHealthInfoRules.healthRatio(5100, 5000)!!, 0.000000000001)
    }

    @Test fun missingOrInvalidCapacityDoesNotProduceAFabricatedHealthValue() {
        listOf(null, -1, 0).forEach {
            assertNull(BatteryHealthInfoRules.healthRatio(it, 7050))
            assertNull(BatteryHealthInfoRules.healthRatio(7034, it))
        }
    }
}
