package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeFiveGaUiPolicyTest {
    private fun matches(enabled: Boolean = true, source: String? = "phone-A", expectedSource: String = "phone-A",
        phone: Int = 0, sub: Int = 1, epoch: Long = 2, proofPhone: Int = 0, proofSub: Int = 1, proofEpoch: Long = 2,
        ready: Boolean = true, qualified: Boolean = true, network: Int = 20, override: Int = 5, service: Boolean = true) =
        NativeFiveGaUiPolicy.matches(enabled, source, expectedSource, phone, sub, epoch, proofPhone, proofSub,
            proofEpoch, ready, qualified, network, override, service)

    @Test fun acceptsOnlyCurrentOriginalQualification() { assertTrue(matches()) }
    @Test fun rejectsGenericNrAdvancedWithoutProof() { assertFalse(matches(qualified = false)) }
    @Test fun rejectsPhoneDeathOrReplacement() {
        assertFalse(matches(source = null)); assertFalse(matches(source = "phone-B"))
    }
    @Test fun rejectsChangedSimAndConfig() {
        assertFalse(matches(proofSub = 2)); assertFalse(matches(proofPhone = 1)); assertFalse(matches(proofEpoch = 3))
    }
    @Test fun rejectsDisabledUnreadyAndOutOfService() {
        assertFalse(matches(enabled = false)); assertFalse(matches(ready = false)); assertFalse(matches(service = false))
    }
    @Test fun rejectsUncertainIdentitiesAndOrdinaryNr() {
        assertFalse(matches(phone = -1)); assertFalse(matches(sub = -1)); assertFalse(matches(epoch = 0))
        assertFalse(matches(network = 13)); assertFalse(matches(override = 0))
    }
    @Test fun pulseAlwaysChangesIncludingOverflow() {
        assertEquals(1L, NativeFiveGaUiPolicy.nextRevision(0)); assertEquals(0L, NativeFiveGaUiPolicy.nextRevision(Long.MAX_VALUE))
    }

    @Test fun equalCachedNativeModelCanFollowRequalifiedEpochOnlyAfterNativeValidation() {
        val lineage = NativeFiveGaUiLineage(0, 1)
        assertTrue(lineage.renew("phone-A", 2, 0, 1, true))
        val cachedModelLineage = lineage
        assertFalse(matches(epoch = cachedModelLineage.current()!!.epoch, proofEpoch = 3))
        assertFalse(lineage.renew("phone-A", 3, 0, 1, false))
        assertEquals(2L, cachedModelLineage.current()!!.epoch)
        assertTrue(lineage.renew("phone-A", 3, 0, 1, true))
        assertTrue(matches(epoch = cachedModelLineage.current()!!.epoch, proofEpoch = 3))
    }

    @Test fun renewedLineageNeverCrossesRepositorySubscriptionOrPhone() {
        val lineage = NativeFiveGaUiLineage(0, 1)
        assertTrue(lineage.renew("phone-A", 2, 0, 1, true))
        assertFalse(lineage.renew("phone-B", 3, 1, 1, true))
        assertFalse(lineage.renew("phone-B", 3, 0, 2, true))
        assertEquals(NativeFiveGaUiLineage.Certificate("phone-A", 2), lineage.current())
    }

    @Test fun newPhoneProcessNeedsNativeRecertificationBeforeOldCachedModelCanResume() {
        val lineage = NativeFiveGaUiLineage(0, 1)
        assertTrue(lineage.renew("phone-A", 2, 0, 1, true))
        assertFalse(matches(source = "phone-B", expectedSource = lineage.current()!!.source))
        assertFalse(lineage.renew("phone-B", 2, 0, 1, false))
        assertTrue(lineage.renew("phone-B", 2, 0, 1, true))
        assertTrue(matches(source = "phone-B", expectedSource = lineage.current()!!.source))
        assertFalse(matches(source = null, expectedSource = lineage.current()!!.source))
    }
}
