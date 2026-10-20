package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class NativeLiveUpdateChipRelayTest {
    private data class NativeBundle(val active: Int)

    @Test fun disabledModeDoesNotWriteToNativeFlow() {
        val writes = mutableListOf<Any>()
        val relay = NativeLiveUpdateChipRelay(NativeBundle(0)) { _, value -> writes.add(value) }
        relay.accept(Any(), NativeBundle(1), false)
        assertTrue(writes.isEmpty())
    }

    @Test fun forwardsExactNativeBundleAndResetsToNativeDefaultOnDisable() {
        val empty = NativeBundle(0)
        val active = NativeBundle(1)
        val writes = mutableListOf<Any>()
        val relay = NativeLiveUpdateChipRelay(empty) { _, value -> writes.add(value) }
        val sink = Any()
        relay.accept(sink, active, true)
        assertSame(active, writes.last())
        repeat(20) {
            relay.reconcile(false)
            assertSame(empty, writes.last())
            relay.reconcile(true)
            assertSame(active, writes.last())
        }
    }

    @Test fun hotReloadTransfersOnlyNativeObjectsAndUsesLatestBundle() {
        val sink = Any()
        val latest = NativeBundle(2)
        val old = NativeLiveUpdateChipRelay(NativeBundle(0)) { _, _ -> }
        old.accept(sink, NativeBundle(1), true)
        old.accept(sink, latest, true)
        val state = old.capture()
        old.clear()
        val writes = mutableListOf<Pair<Any, Any>>()
        val replacement = NativeLiveUpdateChipRelay(NativeBundle(0)) { target, bundle -> writes.add(target to bundle) }
        replacement.restore(state) { it === sink }
        replacement.reconcile(true)
        assertEquals(1, writes.size)
        assertSame(sink, writes.single().first)
        assertSame(latest, writes.single().second)
    }

    @Test fun invalidTransferredStateCannotReachNativeSink() {
        val writes = mutableListOf<Any>()
        val relay = NativeLiveUpdateChipRelay(NativeBundle(0)) { _, value -> writes.add(value) }
        relay.restore(listOf(arrayOf(Any(), "foreign bundle"), arrayOf(null, NativeBundle(1)), "bad pair")) { true }
        relay.restore(listOf(arrayOf(Any(), NativeBundle(1)))) { false }
        relay.reconcile(true)
        assertTrue(writes.isEmpty())
    }
}
