package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class PreferenceSyncBatchTest {
    @Test fun repeatedKeysAreCoalescedAndLaterChangesAreNotLost() {
        val batch = PreferenceSyncBatch()
        repeat(100) { batch.add("a") }
        batch.add("b")
        assertEquals(PreferenceSyncBatch.Changes(false, setOf("a", "b")), batch.drain())
        assertFalse(batch.isPending())
        batch.add("c")
        assertEquals(setOf("c"), batch.drain().keys)
    }

    @Test fun clearOrImportDominatesIndividualChanges() {
        val batch = PreferenceSyncBatch()
        batch.add("a"); batch.add(null); batch.add("b")
        assertEquals(PreferenceSyncBatch.Changes(true, emptySet()), batch.drain())
        assertFalse(batch.isPending())
    }

    @Test fun deltaIncludesDeletionsAndRespectsTheRequestedKeys() {
        val previous = mapOf("removed" to 1, "changed" to false, "later" to 2, "set" to setOf("a", "b"))
        val desired = mapOf("changed" to true, "added" to 3, "later" to 4, "set" to setOf("b", "a"))
        assertEquals(mapOf("removed" to null, "changed" to true, "added" to 3),
            preferenceDelta(previous, desired, setOf("removed", "changed", "added", "set")))
        assertEquals(mapOf("removed" to null, "changed" to true, "added" to 3, "later" to 4),
            preferenceDelta(previous, desired, null))
    }

    @Test fun confirmedValuesDoNotNeedAnotherRemotePublication() {
        val confirmed = mapOf("enabled" to true, "mode" to 2)
        assertTrue(preferenceDelta(confirmed, confirmed, setOf("enabled", "mode")).isEmpty())
    }

    @Test fun publicationsExcludeProtocolMarkersAndOwnTheirStringSets() {
        val selected = mutableSetOf("one", "two")
        val source = mapOf("selected" to selected,
            com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY to true,
            com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY to 1L)
        val published = frameworkPreferenceValues(source, sideKeyAvailable = true)
        selected += "later"
        assertEquals(setOf("selected"), published.keys)
        assertEquals(setOf("one", "two"), published["selected"])
    }
}
