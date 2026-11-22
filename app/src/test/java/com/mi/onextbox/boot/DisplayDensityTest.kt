package com.mi.onextbox.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DisplayDensityTest {
    @Test fun aPreviousOverrideIsDistinctFromThePhysicalDensity() {
        assertEquals(640 to 480, DisplayDensity.parse(listOf("Physical density: 640", "Override density: 480")))
        assertEquals(640 to null, DisplayDensity.parse(listOf("Physical density: 640")))
    }

    @Test fun unavailableDensityCannotBecomeARestorePoint() {
        assertThrows(IllegalStateException::class.java) { DisplayDensity.parse(listOf("Permission denial")) }
        assertThrows(IllegalStateException::class.java) {
            DisplayDensity.parse(listOf("Physical density: 640", "Override density: unavailable"))
        }
    }

    @Test fun rebootRestoresPreviousOverrideWhenTheRomClearsTheRequestedDensity() {
        assertEquals(DisplayDensity.Reconciliation(480, false),
            DisplayDensity.reconcile(480, 520, null, onBoot = true, keep = false))
        assertEquals(DisplayDensity.Reconciliation(520, true),
            DisplayDensity.reconcile(480, 520, null, onBoot = true, keep = true))
        assertEquals(DisplayDensity.Reconciliation(null, false),
            DisplayDensity.reconcile(-1, 520, null, onBoot = true, keep = false))
    }

    @Test fun anExternalDensityWinsEvenWhenKeepOnBootWasEnabled() {
        for (keep in listOf(true, false)) {
            for (onBoot in listOf(true, false)) {
                assertEquals(DisplayDensity.Reconciliation(null, false),
                    DisplayDensity.reconcile(480, 520, 540, onBoot, keep))
            }
        }
        assertEquals(DisplayDensity.Reconciliation(null, false),
            DisplayDensity.reconcile(480, 520, null, onBoot = false, keep = true))
    }

    @Test fun noWriteUnlessTheModuleChangedTheDensity() {
        for (keep in listOf(true, false)) {
            assertEquals(null, DisplayDensity.reconcile(480, 480, null, onBoot = true, keep = keep).write)
            assertEquals(null, DisplayDensity.reconcile(480, 480, 480, onBoot = true, keep = keep).write)
        }
        assertEquals(DisplayDensity.Reconciliation(null, true),
            DisplayDensity.reconcile(480, 520, 520, onBoot = true, keep = true))
        assertEquals(DisplayDensity.Reconciliation(-1, false),
            DisplayDensity.reconcile(-1, 520, 520, onBoot = false, keep = true))
    }
}
