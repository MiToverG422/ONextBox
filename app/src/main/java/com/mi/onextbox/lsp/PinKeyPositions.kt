package com.mi.onextbox.lsp

import java.util.Random

/** Changes only the native number-key cells; each key keeps its label, listener and identity. */
internal class PinKeyPositions private constructor(
    private val cells: List<IntArray>,
    private val original: List<IntArray>,
) {
    companion object {
        fun capture(grid: Map<*, *>, numberIds: List<Int>): PinKeyPositions {
            require(numberIds.size == 10 && numberIds.distinct().size == 10)
            val cells = numberIds.map { requireNotNull(grid[it] as? IntArray) }
            require(cells.all { it.size == 2 } && cells.distinct().size == 10)
            val expected = (0..2).flatMap { row -> (0..2).map { column -> row to column } }.toSet() + (3 to 1)
            require(cells.map { it[0] to it[1] }.toSet() == expected) { "Unknown PIN number-key grid" }
            return PinKeyPositions(cells, cells.map(IntArray::copyOf))
        }

        fun randomOrder(random: Random, previous: IntArray? = null): IntArray {
            val native = IntArray(10) { it }
            var order: IntArray
            do {
                order = native.copyOf()
                for (i in 9 downTo 1) {
                    val j = random.nextInt(i + 1)
                    val value = order[i]; order[i] = order[j]; order[j] = value
                }
            } while (order.contentEquals(native) || order.contentEquals(previous))
            return order
        }
    }

    fun apply(order: IntArray) {
        require(order.size == 10 && order.toSet() == (0..9).toSet())
        cells.forEachIndexed { index, cell -> original[order[index]].copyInto(cell) }
    }

    fun restore() = apply(IntArray(10) { it })
}
