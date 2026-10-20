package com.mi.onextbox.lsp.hyperos

import org.junit.Assert.*
import org.junit.Test

class HyperOsPerformanceRulesTest {
    class Idle(@JvmField var timeoutMillis: Int)
    class Range(@JvmField var min: Float = 60f, @JvmField var max: Float = 120f)
    class Ranges {
        @JvmField var physical = Range()
        @JvmField var render = Range()
    }
    class Spec {
        @JvmField var baseModeId = 2
        @JvmField var allowGroupSwitching = true
        @JvmField var primary = Ranges()
        @JvmField var appRequest = Ranges()
        @JvmField var mIdleScreenRefreshRateConfig = Idle(1000)
    }

    private fun runtime(refresh: Boolean, idle: Boolean): Force185 {
        val runtime = Force185(refresh, false, idle)
        Force185::class.java.getDeclaredField("ready").apply { isAccessible = true }.setBoolean(runtime, true)
        val modeType = Class.forName("com.mi.onextbox.lsp.hyperos.Force185\$Mode")
        val mode = modeType.declaredConstructors.single().apply { isAccessible = true }.newInstance(7, 8, 185f)
        Force185::class.java.getDeclaredField("highMode").apply { isAccessible = true }.set(runtime, mode)
        return runtime
    }

    private fun apply(runtime: Force185, spec: Spec) {
        Force185::class.java.getDeclaredMethod("lockDirectorSpec", Any::class.java)
            .apply { isAccessible = true }.invoke(runtime, spec)
    }

    @Test fun exactDeviceAndRomOnly() {
        assertTrue(Force185.supported("songyuan", 36, "OS3.0.310.0.WGNCNXM"))
        assertFalse(Force185.supported("other", 36, "OS3.0.310.0.WGNCNXM"))
        assertFalse(Force185.supported("songyuan", 37, "OS3.0.310.0.WGNCNXM"))
        assertFalse(Force185.supported("songyuan", 36, "OS3.0.311.0.WGNCNXM"))
    }

    @Test fun idleOnlyDoesNotForce185OrChangeModeRanges() {
        val spec = Spec()
        apply(runtime(false, true), spec)
        assertEquals(0, spec.mIdleScreenRefreshRateConfig.timeoutMillis)
        assertEquals(2, spec.baseModeId)
        assertTrue(spec.allowGroupSwitching)
        assertEquals(60f, spec.primary.physical.min)
        assertEquals(120f, spec.appRequest.render.max)
    }

    @Test fun refreshOnlyDoesNotDisableIdle() {
        val spec = Spec()
        apply(runtime(true, false), spec)
        assertEquals(1000, spec.mIdleScreenRefreshRateConfig.timeoutMillis)
        assertEquals(7, spec.baseModeId)
        assertFalse(spec.allowGroupSwitching)
        for (ranges in listOf(spec.primary, spec.appRequest)) {
            for (range in listOf(ranges.physical, ranges.render)) {
                assertEquals(185f, range.min)
                assertEquals(185f, range.max)
            }
        }
    }

    @Test fun disabledDisplayOptionsLeavePolicyUnchanged() {
        val spec = Spec()
        apply(runtime(false, false), spec)
        assertEquals(1000, spec.mIdleScreenRefreshRateConfig.timeoutMillis)
        assertEquals(2, spec.baseModeId)
        assertEquals(120f, spec.primary.physical.max)
    }

    @Test fun touchValuesOnlyAlterKnownRateModes() {
        assertEquals(1, TouchRateLock.forceValue(0, 0))
        assertEquals(1, TouchRateLock.forceValue(202, 0))
        assertEquals(0, TouchRateLock.forceValue(1116, 1))
        assertEquals(3, TouchRateLock.forceValue(10110, 1))
        assertEquals(42, TouchRateLock.forceValue(42, 42))
        assertEquals(-1, TouchRateLock.forceValue(10110, -1))
        TouchRateLock(false, {}, { throw AssertionError(it) }).schedule()
    }
}
