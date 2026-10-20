package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsFixedRefreshIdleConfigTest {
    class NativeBean() {
        var enabled = true
        var hardware = true
        var software = true
        var adaptive = true
        var brightnessLimit = 35
        var minimumRate = 60
        constructor(source: NativeBean) : this() {
            enabled = source.enabled
            hardware = source.hardware
            software = source.software
            adaptive = source.adaptive
            brightnessLimit = source.brightnessLimit
            minimumRate = source.minimumRate
        }
        fun setEnable(value: Boolean) { enabled = value }
        fun setHwEnable(value: Boolean) { hardware = value }
        fun setSwEnable(value: Boolean) { software = value }
        fun setAdfrEnable(value: Boolean) { adaptive = value }
    }

    private val config = SettingsFixedRefreshIdleConfig(NativeBean::class.java)

    @Test fun inactiveModeForwardsTheOriginalByIdentity() {
        val original = NativeBean()
        assertSame(original, config.outgoing(original, false))
    }

    @Test fun blockingIdleClonesAndDisablesAllThreeIdlePaths() {
        val original = NativeBean()
        val outgoing = config.outgoing(original, true) as NativeBean
        assertNotSame(original, outgoing)
        assertFalse(outgoing.enabled)
        assertFalse(outgoing.hardware)
        assertFalse(outgoing.software)
        assertFalse(outgoing.adaptive)
        assertTrue(original.enabled && original.hardware && original.software && original.adaptive)
    }

    @Test fun brightnessAndOtherNativeParametersAreNotOverridden() {
        val original = NativeBean().apply { brightnessLimit = 78; minimumRate = 30 }
        val outgoing = config.outgoing(original, true) as NativeBean
        assertEquals(78, outgoing.brightnessLimit)
        assertEquals(30, outgoing.minimumRate)
    }

    @Test fun disablingAfterAnOverrideRestoresTheNativeConfigurationNotThePreviousForcedCopy() {
        val original = NativeBean()
        val overridden = config.outgoing(original, true) as NativeBean
        original.software = false
        original.brightnessLimit = 91
        val restored = config.outgoing(original, false) as NativeBean
        assertSame(original, restored)
        assertTrue(restored.enabled)
        assertFalse(restored.software)
        assertEquals(91, restored.brightnessLimit)
        assertFalse(overridden.enabled)
    }

    @Test fun repeatedOverridesCannotAccumulateChangesInTheRetainedNativeBean() {
        val original = NativeBean()
        repeat(10) {
            val outgoing = config.outgoing(original, true) as NativeBean
            outgoing.minimumRate = 144
            assertEquals(60, original.minimumRate)
            assertSame(original, config.outgoing(original, false))
        }
        assertTrue(original.enabled)
    }

    @Test fun nativeDisabledFlagsRemainDisabledWhenTheModuleReleasesItsOverride() {
        val original = NativeBean().apply { enabled = false; hardware = false; adaptive = false }
        config.outgoing(original, true)
        val restored = config.outgoing(original, false) as NativeBean
        assertFalse(restored.enabled)
        assertFalse(restored.hardware)
        assertFalse(restored.adaptive)
        assertTrue(restored.software)
    }

    @Test fun aMissingCopyContractRejectsBeforeAnyNativeConfigurationCanBeMutated() {
        assertThrows(NoSuchMethodException::class.java) { SettingsFixedRefreshIdleConfig(Any::class.java) }
    }
}
