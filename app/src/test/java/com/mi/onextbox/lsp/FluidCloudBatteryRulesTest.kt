package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidCloudBatteryRulesTest {
    @Test fun outsidePercentageIsPreservedForExistingBatteryIcons() {
        for (style in listOf(0, 1, 3, 4, 5)) {
            assertTrue(FluidCloudBatteryRules.keepOutsidePercentage(true, 2, style))
            assertEquals(0, FluidCloudBatteryRules.forceShowStyle(1, true, 2, style))
            assertFalse(FluidCloudBatteryRules.capsuleShowing(true, true, 2, style))
        }
    }

    @Test fun noIconStyleIsNotChanged() {
        assertFalse(FluidCloudBatteryRules.keepOutsidePercentage(true, 2, 2))
        assertEquals(1, FluidCloudBatteryRules.forceShowStyle(1, true, 2, 2))
        assertTrue(FluidCloudBatteryRules.capsuleShowing(true, true, 2, 2))
    }

    @Test fun insidePercentageAndHiddenPercentageKeepNativeBehavior() {
        for (percentStyle in listOf(0, 1)) {
            assertFalse(FluidCloudBatteryRules.keepOutsidePercentage(true, percentStyle, 1))
            assertEquals(1, FluidCloudBatteryRules.forceShowStyle(1, true, percentStyle, 1))
            assertTrue(FluidCloudBatteryRules.capsuleShowing(true, true, percentStyle, 1))
        }
    }

    @Test fun disabledFeatureKeepsNativeBehavior() {
        assertFalse(FluidCloudBatteryRules.keepOutsidePercentage(false, 2, 1))
        assertEquals(1, FluidCloudBatteryRules.forceShowStyle(1, false, 2, 1))
        assertTrue(FluidCloudBatteryRules.capsuleShowing(true, false, 2, 1))
    }

    @Test fun hiddenAndNormalForceStylesAreNeverOverridden() {
        for (forceStyle in listOf(0, 2, -1, 3)) {
            assertEquals(forceStyle, FluidCloudBatteryRules.forceShowStyle(forceStyle, true, 2, 1))
        }
    }

    @Test fun absentCapsuleStaysAbsent() {
        assertFalse(FluidCloudBatteryRules.capsuleShowing(false, true, 2, 1))
        assertFalse(FluidCloudBatteryRules.capsuleShowing(false, true, 1, 1))
    }

    @Test fun missingAndUnknownStylesKeepNativeBehavior() {
        for (style in listOf(null, -1, 6, Int.MAX_VALUE)) {
            assertFalse(FluidCloudBatteryRules.keepOutsidePercentage(true, 2, style))
            assertEquals(1, FluidCloudBatteryRules.forceShowStyle(1, true, 2, style))
            assertTrue(FluidCloudBatteryRules.capsuleShowing(true, true, 2, style))
        }
        assertFalse(FluidCloudBatteryRules.keepOutsidePercentage(true, null, 1))
        assertFalse(FluidCloudBatteryRules.keepOutsidePercentage(true, 3, 1))
    }
}
