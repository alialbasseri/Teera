package com.dominoes.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardLayoutTest {
    private val rules = RuleSet.CLASSIC_DRAW

    private fun chain(count: Int): Board {
        // A long alternating run: 0|1, 1|2, 2|3 ... which never needs the boneyard.
        var board = Board().place(Domino.of(0, 1), End.RIGHT, rules)
        var face = 1
        repeat(count - 1) {
            val next = Domino.of(face, (face + 1).coerceAtMost(6))
            board = board.place(next, End.RIGHT, rules)
            face = board.rightEnd!!
        }
        return board
    }

    @Test
    fun `an empty table lays out to nothing`() {
        val layout = BoardLayout.compute(emptyList())
        assertTrue(layout.placements.isEmpty())
        assertEquals(0f, layout.width)
    }

    @Test
    fun `tiles sit end to end without overlapping`() {
        val layout = BoardLayout.compute(chain(5).tiles, maxRowUnits = 100f)
        assertEquals(5, layout.placements.size)
        val gaps = layout.placements.zipWithNext { a, b -> b.centreX - a.centreX }
        gaps.forEach { assertEquals(BoardLayout.TILE_LENGTH, it, 0.001f) }
        assertTrue(layout.placements.all { it.centreY == 0f }, "a short line stays in one row")
    }

    @Test
    fun `doubles are laid crossways and take less room`() {
        val board = Board()
            .place(Domino.of(3, 6), End.RIGHT, rules)
            .place(Domino(6, 6), End.RIGHT, rules)
            .place(Domino.of(6, 4), End.RIGHT, rules)
        val layout = BoardLayout.compute(board.tiles, maxRowUnits = 100f)

        assertEquals(90f, layout.placements[1].rotationDegrees)
        assertEquals(0f, layout.placements[0].rotationDegrees)
        // The double eats one unit of the row rather than two.
        assertEquals(1.5f, layout.placements[1].centreX - layout.placements[0].centreX, 0.001f)
    }

    @Test
    fun `a long line folds into rows instead of running off the table`() {
        val layout = BoardLayout.compute(chain(14).tiles, maxRowUnits = 8f)
        val rows = layout.placements.map { it.centreY }.distinct()
        assertTrue(rows.size > 1, "the line should have folded")
        assertTrue(layout.width <= 8f + BoardLayout.TILE_LENGTH, "no row runs past the limit")

        // Rows alternate direction, so the run reverses after each fold.
        val firstRow = layout.placements.filter { it.centreY == rows[0] }
        val secondRow = layout.placements.filter { it.centreY == rows[1] }
        val firstGoesRight = firstRow.last().centreX > firstRow.first().centreX
        val secondGoesRight = secondRow.last().centreX > secondRow.first().centreX
        assertTrue(firstGoesRight != secondGoesRight, "consecutive rows run opposite ways")
    }

    @Test
    fun `the bounding box covers every tile`() {
        val layout = BoardLayout.compute(chain(20).tiles, maxRowUnits = 10f)
        layout.placements.forEach { placement ->
            assertTrue(abs(placement.centreX) <= abs(layout.maxX) + abs(layout.minX))
            assertTrue(placement.centreY in layout.minY..layout.maxY)
        }
        assertTrue(layout.height > 0f)
    }
}
