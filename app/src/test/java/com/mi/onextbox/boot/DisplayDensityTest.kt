package com.mi.onextbox.boot

import com.mi.onextbox.lsp.DisplayDensitySettings
import com.mi.onextbox.lsp.DisplayDensitySettings.Action
import com.mi.onextbox.lsp.DisplayDensitySettings.Reconciliation
import com.mi.onextbox.lsp.DisplayDensitySettings.Request
import com.mi.onextbox.lsp.DisplayDensitySettings.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayDensityTest {
    private val request = Request("00000000-0000-0000-0000-000000000001", 0, 25, 480, 310, true, Action.Adjust)

    @Test fun aSinglePreferenceCarriesTheOriginalAndRequestedDensity() {
        assertEquals(request, DisplayDensitySettings.request(request.encode()))
        assertNull(DisplayDensitySettings.request(request.copy(target = 0).encode()))
        assertEquals(480, DisplayDensitySettings.overrideFor(480, 640))
        assertEquals(-1, DisplayDensitySettings.overrideFor(640, 640))
    }

    @Test fun applyIsIdempotentAcrossHookReloadAndRunsAgainOnTheNextBoot() {
        val applied = Result(request.id, 25, active = true, success = true)
        assertEquals(Reconciliation(310, true), DisplayDensitySettings.decide(request, 25, null, 480))
        assertNull(DisplayDensitySettings.decide(request, 25, applied, 540))
        assertEquals(Reconciliation(310, true), DisplayDensitySettings.decide(request, 26, applied, 480))
    }

    @Test fun aRetiredSettingRemainsInactiveOnLaterBoots() {
        val retired = Result(request.id, 26, active = false, success = true)
        val disabled = request.copy(keep = false)
        assertEquals(Reconciliation(480, false), DisplayDensitySettings.decide(disabled, 26, null, 310))
        assertEquals(Reconciliation(null, false), DisplayDensitySettings.decide(request, 27, retired, 480))
    }

    @Test fun rebootRestoresPreviousOverrideWhenTheRomClearsTheRequestedDensity() {
        assertEquals(Reconciliation(480, false),
            DisplayDensitySettings.reconcile(480, 520, null, onBoot = true, keep = false))
        assertEquals(Reconciliation(520, true),
            DisplayDensitySettings.reconcile(480, 520, null, onBoot = true, keep = true))
        assertEquals(Reconciliation(null, false),
            DisplayDensitySettings.reconcile(-1, 520, null, onBoot = true, keep = false))
    }

    @Test fun anExternalDensityWinsEvenWhenKeepOnBootWasEnabled() {
        for (keep in listOf(true, false)) {
            for (onBoot in listOf(true, false)) {
                assertEquals(Reconciliation(null, false),
                    DisplayDensitySettings.reconcile(480, 520, 540, onBoot, keep))
            }
        }
        assertEquals(Reconciliation(null, false),
            DisplayDensitySettings.reconcile(480, 520, null, onBoot = false, keep = true))
    }

    @Test fun rebootKeepsTheTargetWhenTheRomRestoresThePreviousOverride() {
        assertEquals(Reconciliation(310, true),
            DisplayDensitySettings.reconcile(480, 310, 480, onBoot = true, keep = true))
        assertEquals(Reconciliation(null, false),
            DisplayDensitySettings.reconcile(480, 310, 480, onBoot = true, keep = false))
        assertEquals(Reconciliation(null, false),
            DisplayDensitySettings.reconcile(480, 310, 480, onBoot = false, keep = true))
    }

    @Test fun noWriteUnlessTheModuleChangedTheDensity() {
        for (keep in listOf(true, false)) {
            assertEquals(null, DisplayDensitySettings.reconcile(480, 480, null, onBoot = true, keep = keep).write)
            assertEquals(null, DisplayDensitySettings.reconcile(480, 480, 480, onBoot = true, keep = keep).write)
        }
        assertEquals(Reconciliation(null, true),
            DisplayDensitySettings.reconcile(480, 520, 520, onBoot = true, keep = true))
        assertEquals(Reconciliation(-1, false),
            DisplayDensitySettings.reconcile(-1, 520, 520, onBoot = false, keep = true))
    }
}
