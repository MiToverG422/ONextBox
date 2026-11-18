package com.mi.onextbox.ui.common

import org.junit.Assert.*
import org.junit.Test

class AppLogHistoryTest {
    private fun entry(id: Long, tag: String = "App") = AppLogEntry(id, id, AppLogLevel.INFO, tag, "message$id")

    @Test fun loadingDoesNotReplaceLiveEntriesOrReuseTheirIds() {
        val live = listOf(entry(1), entry(2))
        val merged = mergeAppLogHistory(listOf(entry(1), entry(2)), live, false, emptySet(), 500)
        assertEquals(live, merged.takeLast(2))
        assertEquals(4, merged.map { it.id }.toSet().size)
        assertTrue(merged.take(2).all { it.id < 0 })
    }

    @Test fun clearDuringLoadingCannotRestoreHistory() {
        val live = listOf(entry(3))
        assertEquals(live, mergeAppLogHistory(listOf(entry(1)), live, true, emptySet(), 500))
    }

    @Test fun removalsAndCapacityAreRespectedDuringLoading() {
        val history = listOf(entry(1, "Hook/old"), entry(2, "Removed/tag"), entry(3), entry(4))
        val merged = mergeAppLogHistory(history, listOf(entry(5)), false, setOf("Removed/"), 2)
        assertEquals(listOf(4L, 5L), merged.map { it.timestampMs })
    }
}
