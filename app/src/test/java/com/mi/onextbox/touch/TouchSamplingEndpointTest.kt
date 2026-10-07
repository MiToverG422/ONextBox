package com.mi.onextbox.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchSamplingEndpointTest {
    private val service = "vendor.oplus.hardware.touch.IOplusTouch/default"
    private val endpoint = OplusTouchEndpoint(service, 0)
    private val profile = TouchConfigProfile(
        panelIndex = 0,
        presets = listOf(
            TouchRatePreset(1, 120, 2, false),
            TouchRatePreset(2, 240, 4, false),
        ),
        defaultChipValue = 1,
    )

    @Test fun generatesOnlyKnownAidlCommands() {
        val prefix = "/system/bin/service call '$service'"
        assertEquals("$prefix 2 i32 0 i32 182", endpoint.supportCommand())
        assertEquals("$prefix 3 i32 0 i32 182", endpoint.readCommand())
        assertEquals("$prefix 4 i32 0 i32 182 s16 0", endpoint.writeCommand(0))
        assertEquals("$prefix 4 i32 0 i32 182 s16 255", endpoint.writeCommand(255))
        assertEquals("$prefix 3 i32 1 i32 182", endpoint.copy(panelIndex = 1).readCommand())
    }

    @Test fun rejectsServiceInjectionAndOtherSchemas() {
        for (instance in listOf("", "x';id", "../default", "..", "x\n", "x".repeat(65))) {
            expectRejected { OplusTouchEndpoint("vendor.oplus.hardware.touch.IOplusTouch/$instance", 0) }
        }
        expectRejected { OplusTouchEndpoint("vendor.oplus.hardware.touch@1.0::IOplusTouch/default", 0) }
    }

    @Test fun rejectsUnknownPanelsAndNodes() {
        for (panel in listOf(-1, 2, 255)) expectRejected { endpoint.copy(panelIndex = panel) }
        for (node in listOf(0, 181, 183)) expectRejected { endpoint.copy(node = node) }
    }

    @Test fun boundsWriteIndex() {
        for (index in listOf(-1, 256, Int.MAX_VALUE)) expectRejected { endpoint.writeCommand(index) }
    }

    @Test fun bindingIsStableAcrossSourcePathsAndPresetOrdering() {
        val binding = endpoint.bindingId(profile)
        assertEquals(binding, endpoint.bindingId(profile.copy(sourceLabel = "/vendor/etc/new.xml")))
        assertEquals(binding, endpoint.bindingId(profile.copy(presets = profile.presets.reversed())))
        assertEquals(binding, endpoint.bindingId(profile.copy(panelIndex = null)))
        assertTrue(binding.startsWith("oplus:$service:panel=0:node=182:"))
        assertTrue(binding.substringAfterLast(':').matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun changedChipMappingsAndDefaultChangeBinding() {
        val binding = endpoint.bindingId(profile)
        assertNotEquals(binding, endpoint.bindingId(profile.copy(defaultChipValue = 9)))
        assertNotEquals(binding, endpoint.bindingId(profile.copy(presets = listOf(
            profile.presets[0].copy(chipValue = 8), profile.presets[1],
        ))))
    }

    @Test fun changedLabelsAndIstModeChangeBinding() {
        val binding = endpoint.bindingId(profile)
        assertNotEquals(binding, endpoint.bindingId(profile.copy(presets = listOf(
            profile.presets[0].copy(hz = null), profile.presets[1],
        ))))
        assertNotEquals(binding, endpoint.bindingId(profile.copy(presets = listOf(
            profile.presets[0].copy(isIstMode = true), profile.presets[1],
        ))))
    }

    @Test fun changedEndpointChangesBinding() {
        val binding = endpoint.bindingId(profile)
        assertNotEquals(binding, endpoint.copy(panelIndex = 1).bindingId(profile.copy(panelIndex = 1)))
        assertNotEquals(binding, endpoint.copy(service = service.replace("default", "panel0")).bindingId(profile))
    }

    @Test fun rejectsMismatchedProfile() {
        expectRejected { endpoint.bindingId(profile.copy(panelIndex = 1)) }
        expectRejected { endpoint.bindingId(profile.copy(node = 183)) }
    }

    @Test fun rejectsMissingDefaultOrIncompleteMapping() {
        expectRejected { endpoint.bindingId(profile.copy(defaultChipValue = null)) }
        expectRejected { endpoint.bindingId(profile.copy(defaultChipValue = -1)) }
        expectRejected { endpoint.bindingId(profile.copy(presets = emptyList())) }
        expectRejected { endpoint.bindingId(profile.copy(presets = listOf(profile.presets[1]))) }
        expectRejected { endpoint.bindingId(profile.copy(presets = listOf(profile.presets[0], profile.presets[0]))) }
    }

    private fun expectRejected(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }
}
