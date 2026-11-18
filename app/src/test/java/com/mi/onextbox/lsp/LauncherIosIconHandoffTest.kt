package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class LauncherIosIconHandoffTest {
    @Test fun deferredFrameIsConsumedExactlyOnceByItsOwnTransaction() {
        val handoff = LauncherIosIconHandoff<Any>()
        val transaction = Any()
        val target = Any()
        handoff.capture(transaction, target)
        handoff.deferred = true
        assertNull(handoff.take(Any()))
        assertSame(target, handoff.take(transaction))
        assertNull(handoff.take(transaction))
        assertNull(handoff.abandon())
    }

    @Test fun newCaptureReplacesTheOldTargetWithoutReplayingItIntoANewTransaction() {
        val handoff = LauncherIosIconHandoff<Any>()
        val olderTransaction = Any()
        val newerTransaction = Any()
        val older = Any()
        val newer = Any()
        assertNull(handoff.capture(olderTransaction, older))
        assertSame(older, handoff.capture(newerTransaction, newer))
        assertNull(handoff.take(olderTransaction))
        assertSame(newer, handoff.take(newerTransaction))
    }

    @Test fun cancellationReturnsThePendingTargetForVisibilityRollback() {
        val handoff = LauncherIosIconHandoff<Any>()
        val transaction = Any()
        val target = Any()
        handoff.capture(transaction, target)
        assertSame(target, handoff.abandon())
        assertNull(handoff.take(transaction))
        assertNull(handoff.abandon())
    }
}
