package com.mi.onextbox.lsp

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class PinKeyPositionsTest {
    private fun nativeGrid() = (0..9).associateWith { digit ->
        if (digit == 0) intArrayOf(3, 1) else intArrayOf((digit - 1) / 3, (digit - 1) % 3)
    } + mapOf(10 to intArrayOf(3, 0), 11 to intArrayOf(3, 2))

    @Test fun everyOrderContainsEachDigitOnceAndChangesBetweenSessions() {
        val random = Random(12345)
        val native = IntArray(10) { it }
        var previous: IntArray? = null
        repeat(1000) {
            val order = PinKeyPositions.randomOrder(random, previous)
            assertEquals((0..9).toList(), order.sorted())
            assertFalse(order.contentEquals(native))
            assertFalse(order.contentEquals(previous))
            previous = order
        }
    }

    @Test fun movesAllTenNativeKeysWhileLeavingBothSideButtonsAndIdentitiesIntact() {
        val grid = nativeGrid()
        val original = grid.mapValues { it.value.copyOf() }
        val identities = grid.toMap()
        val positions = PinKeyPositions.capture(grid, (0..9).toList())
        val order = intArrayOf(7, 0, 5, 3, 9, 2, 8, 4, 1, 6)
        positions.apply(order)
        for (digit in 0..9) {
            assertSame(identities[digit], grid[digit])
            assertArrayEquals(original[order[digit]], grid[digit])
        }
        for (side in listOf(10, 11)) assertArrayEquals(original[side], grid[side])
        assertEquals(10, (0..9).map { requireNotNull(grid[it]).toList() }.toSet().size)
        positions.restore()
        for (id in grid.keys) assertArrayEquals(original[id], grid[id])
    }

    @Test fun repeatedLayoutAndRestoreNeverAccumulateOrLoseCoordinates() {
        val grid = nativeGrid()
        val original = grid.mapValues { it.value.copyOf() }
        val positions = PinKeyPositions.capture(grid, (0..9).toList())
        val order = PinKeyPositions.randomOrder(Random(8))
        repeat(3) { positions.apply(order) }
        repeat(3) { positions.restore() }
        for (id in grid.keys) assertArrayEquals(original[id], grid[id])
    }

    @Test fun malformedOrdersAndUnsupportedKeyboardsCannotPartiallyChangeTheNativeGrid() {
        val grid = nativeGrid()
        val original = grid.mapValues { it.value.copyOf() }
        val positions = PinKeyPositions.capture(grid, (0..9).toList())
        assertTrue(runCatching { positions.apply(IntArray(10)) }.isFailure)
        assertTrue(runCatching { positions.apply(IntArray(9) { it }) }.isFailure)
        assertTrue(runCatching { PinKeyPositions.capture(grid, (0..8).toList()) }.isFailure)
        assertTrue(runCatching { PinKeyPositions.capture(grid, List(10) { 0 }) }.isFailure)
        assertTrue(runCatching { PinKeyPositions.capture(grid, (1..10).toList()) }.isFailure)
        for (id in grid.keys) assertArrayEquals(original[id], grid[id])
    }
}
