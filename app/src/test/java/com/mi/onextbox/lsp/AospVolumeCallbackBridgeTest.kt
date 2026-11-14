package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class AospVolumeCallbackBridgeTest {
    @Test fun onlyNativeProducerReceivesNonNullState() {
        assertTrue(AospVolumeCallbackBridge.shouldForward(true, true))
        assertFalse(AospVolumeCallbackBridge.shouldForward(false, true))
        assertFalse(AospVolumeCallbackBridge.shouldForward(true, false))
        assertFalse(AospVolumeCallbackBridge.shouldForward(false, false))
    }

    private fun targets(callbacks: Class<*>, producer: Class<*>) = AospVolumeCallbackMethods.bridgeTargets(
        callbacks, AospVolumeCallbackFixtures.State::class.java, producer, AospVolumeCallbackFixtures.Dispatcher::class.java)

    @Test fun currentRomWithoutTwoArgumentCallbackBridgesThreeArguments() {
        val methods = targets(AospVolumeCallbackFixtures.Current::class.java, AospVolumeCallbackFixtures.CurrentProducer::class.java)
        assertEquals(listOf(3), methods.map { it.parameterCount })
    }
    @Test fun oldRomStillBridgesTwoArguments() {
        val methods = targets(AospVolumeCallbackFixtures.Old::class.java, AospVolumeCallbackFixtures.OldProducer::class.java)
        assertEquals(listOf(2), methods.map { it.parameterCount })
    }
    @Test fun existingNativeOverrideDoesNotReceiveDuplicateState() {
        assertTrue(targets(AospVolumeCallbackFixtures.Current::class.java, AospVolumeCallbackFixtures.NativeOverride::class.java).isEmpty())
    }
    @Test fun stockInterfaceWithoutOemOverloadNeedsNoBridge() {
        assertTrue(targets(AospVolumeCallbackFixtures.Stock::class.java, AospVolumeCallbackFixtures.StockProducer::class.java).isEmpty())
    }
}
