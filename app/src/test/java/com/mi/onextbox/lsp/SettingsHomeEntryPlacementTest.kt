package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsHomeEntryPlacementTest {
    @Test fun positionChoicesUseNativeGroupsAndPutBottomBeforeTheFooter() {
        val groups = listOf(0, 1, 2, 3, 4, 5, 6)
        assertEquals(1, SettingsHomeEntryPlacement.orderFor(SettingsEntryPosition.Top, 1, groups, 5100))
        assertEquals(4, SettingsHomeEntryPlacement.orderFor(SettingsEntryPosition.Middle, 1, groups, 5100))
        assertEquals(5100, SettingsHomeEntryPlacement.orderFor(SettingsEntryPosition.Bottom, 1, groups, 5100))
    }

    @Test fun placementsHandleUnsortedGroupsAndMissingFooters() {
        assertEquals(4, SettingsHomeEntryPlacement.orderFor(SettingsEntryPosition.Middle, 1, listOf(9, 1, 4, -101), null))
        assertEquals(10, SettingsHomeEntryPlacement.orderFor(SettingsEntryPosition.Bottom, 1, listOf(9, 1, 4), null))
        assertEquals(1, SettingsHomeEntryPlacement.orderFor(SettingsEntryPosition.Middle, 1, emptyList(), null))
        assertThrows(IllegalArgumentException::class.java) {
            SettingsHomeEntryPlacement.orderFor(SettingsEntryPosition.Disabled, 1, emptyList(), null)
        }
    }

    private class NativeGroup {
        var hasPreferenceManager = false
        var hasEntry = false
        fun addEntry(): Boolean {
            check(hasPreferenceManager) { "PreferenceManager is null" }
            hasEntry = true
            return true
        }
    }

    @Test fun parentIsAttachedBeforeAddingTheEntry() {
        val group = NativeGroup()
        SettingsHomeEntryPlacement.attach(
            attachGroup = { group.hasPreferenceManager = true; true },
            attachEntry = group::addEntry,
            rollback = { fail("Successful insertion must not roll back") },
        )
        assertTrue(group.hasPreferenceManager)
        assertTrue(group.hasEntry)
    }

    @Test fun failureToAttachTheGroupDoesNotTryToAddAChild() {
        var rolledBack = false
        assertThrows(IllegalStateException::class.java) {
            SettingsHomeEntryPlacement.attach(
                attachGroup = { false },
                attachEntry = { fail("Child must not be added to an unattached group"); true },
                rollback = { rolledBack = true },
            )
        }
        assertTrue(rolledBack)
    }

    @Test fun childInsertionFailureRemovesTheEmptyGroupAndRestoresOrders() {
        val group = NativeGroup()
        var networkOrder = 2
        assertThrows(IllegalStateException::class.java) {
            SettingsHomeEntryPlacement.attach(
                attachGroup = { networkOrder++; group.hasPreferenceManager = true; true },
                attachEntry = { false },
                rollback = { group.hasPreferenceManager = false; networkOrder-- },
            )
        }
        assertFalse(group.hasPreferenceManager)
        assertEquals(2, networkOrder)
    }

    @Test fun exceptionDuringPartialGroupInsertionAlsoRollsBack() {
        var order = 5
        val error = IllegalArgumentException("Native attach failed")
        assertSame(error, assertThrows(IllegalArgumentException::class.java) {
            SettingsHomeEntryPlacement.attach(
                attachGroup = { order++; throw error },
                attachEntry = { fail("Unexpected child insertion"); true },
                rollback = { order-- },
            )
        })
        assertEquals(5, order)
    }

    @Test fun rollbackFailureDoesNotHideTheOriginalFailure() {
        val original = IllegalArgumentException("Child failed")
        val rollbackError = IllegalStateException("Rollback failed")
        assertSame(original, assertThrows(IllegalArgumentException::class.java) {
            SettingsHomeEntryPlacement.attach(
                attachGroup = { true }, attachEntry = { throw original }, rollback = { throw rollbackError },
            )
        })
        assertEquals(listOf(rollbackError), original.suppressed.toList())
    }

    @Test fun onlyFollowingExplicitOrdersAreShifted() {
        assertFalse(SettingsHomeEntryPlacement.shouldShift(0, 2))
        assertTrue(SettingsHomeEntryPlacement.shouldShift(2, 2))
        assertTrue(SettingsHomeEntryPlacement.shouldShift(5100, 2))
        assertFalse(SettingsHomeEntryPlacement.shouldShift(Int.MAX_VALUE, 2))
    }

    @Test fun removalRestoresOnlyOurOwnOrderChange() {
        assertTrue(SettingsHomeEntryPlacement.shouldRestore(3, 2))
        assertTrue(SettingsHomeEntryPlacement.shouldRestore(5101, 5100))
        assertFalse(SettingsHomeEntryPlacement.shouldRestore(2, 2))
        assertFalse(SettingsHomeEntryPlacement.shouldRestore(7, 2))
        assertFalse(SettingsHomeEntryPlacement.shouldRestore(Int.MIN_VALUE, Int.MAX_VALUE))
    }
}
