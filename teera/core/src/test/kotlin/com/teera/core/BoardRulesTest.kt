package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Rules 12 to 14: the opening double is played on from four sides, and only it. */
class FourSidesTest {

    private val openedOnSixes = rig(
        hands = listOf(
            listOf(d(6, 0), d(5, 5)),
            listOf(d(6, 1), d(4, 4)),
            listOf(d(6, 2), d(3, 3)),
            listOf(d(6, 3), d(2, 2)),
        ),
        board = Board().open(d(6, 6)),
        turn = 1,
        gameNumber = 1,
    )

    @Test
    fun `all four sides show the opening number until they are closed`() {
        val board = openedOnSixes.board
        assertTrue(board.inOpeningPhase)
        assertEquals(0, board.openedArms)
        Arm.ALL.forEach { assertEquals(6, board.endOf(it)) }
    }

    @Test
    fun `only a tile matching the opening double may be played while sides are open`() {
        val moves = Engine.legalMoves(openedOnSixes, 1)
        assertEquals(
            listOf(d(6, 1)),
            moves.filterIsInstance<Move.Play>().map { it.domino },
            "4|4 does not touch a six, so it is not playable yet",
        )
    }

    @Test
    fun `each of the first four turns opens a side nobody has touched`() {
        var state = openedOnSixes
        val order = listOf(1 to d(6, 1), 2 to d(6, 2), 3 to d(6, 3), 0 to d(6, 0))

        order.forEachIndexed { index, (seat, tile) ->
            assertEquals(seat, state.turn, "turn should have come round to seat $seat")
            state = assertIs<MoveOutcome.Ok>(Engine.apply(state, seat, Move.Play(tile, null))).state
            assertEquals(index + 1, state.board.openedArms)
        }

        assertFalse(state.board.inOpeningPhase, "all four sides are closed")
        assertEquals(setOf(1, 2, 3, 0), Arm.ALL.map { state.board.endOf(it) }.toSet())
    }

    @Test
    fun `play spreads to any end once the four sides are closed`() {
        val board = openedBoard(d(6, 6), d(6, 1), d(6, 2), d(6, 3), d(6, 0))
        val state = rig(
            hands = listOf(listOf(d(2, 5), d(4, 4)), listOf(d(1, 1)), listOf(d(0, 4)), listOf(d(3, 5))),
            board = board,
            turn = 0,
        )
        val plays = Engine.legalMoves(state, 0).filterIsInstance<Move.Play>()
        assertEquals(listOf(d(2, 5)), plays.map { it.domino }.distinct())
        assertEquals(listOf(Arm.EAST), plays.map { it.arm }, "the two end is the only one that answers")
    }

    @Test
    fun `a double played later keeps its number live and opens nothing`() {
        val board = openedBoard(d(6, 6), d(6, 1), d(6, 2), d(6, 3), d(6, 0))
        val state = rig(
            hands = listOf(listOf(d(2, 2)), listOf(d(1, 1)), listOf(d(0, 0)), listOf(d(3, 3))),
            board = board,
            turn = 0,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(2, 2), Arm.EAST))).state

        assertEquals(2, after.board.endOf(Arm.EAST), "rule 14: the end still shows a two")
        assertEquals(4, after.board.openedArms, "a later double does not add a side")
        assertEquals(2, after.board.tilesOn(Arm.EAST).size)
    }

    @Test
    fun `a tile that matches nothing is refused`() {
        val board = openedBoard(d(6, 6), d(6, 1), d(6, 2), d(6, 3), d(6, 0))
        val state = rig(
            hands = listOf(listOf(d(4, 5)), listOf(d(1, 1)), listOf(d(0, 4)), listOf(d(3, 5))),
            board = board,
            turn = 0,
        )
        val outcome = Engine.apply(state, 0, Move.Play(d(4, 5), Arm.EAST))
        assertEquals(Rejection.TILE_DOES_NOT_MATCH, assertIs<MoveOutcome.Rejected>(outcome).reason)
    }
}

class BoardGeometryTest {

    @Test
    fun `an empty table lays out to nothing`() {
        assertTrue(BoardLayout.compute(Board()).placements.isEmpty())
    }

    @Test
    fun `the opening double sits in the middle with arms running off it`() {
        val board = openedBoard(d(6, 6), d(6, 1), d(6, 2), d(6, 3), d(6, 0))
        val layout = BoardLayout.compute(board)

        assertEquals(5, layout.placements.size)
        val centre = layout.placements.single { it.isCentre }
        assertEquals(0f, centre.centreX)
        assertEquals(0f, centre.centreY)

        val byArm = layout.placements.filter { !it.isCentre }.associateBy { it.arm }
        assertTrue(byArm.getValue(Arm.EAST).centreX > 0f)
        assertTrue(byArm.getValue(Arm.WEST).centreX < 0f)
        assertTrue(byArm.getValue(Arm.NORTH).centreY < 0f)
        assertTrue(byArm.getValue(Arm.SOUTH).centreY > 0f)
    }

    @Test
    fun `tiles lie along their arm and doubles sit across it`() {
        val board = openedBoard(d(6, 6), d(6, 1), d(6, 2), d(6, 3), d(6, 0))
            .place(d(2, 2), Arm.EAST)
            .place(d(1, 1), Arm.NORTH)
        val layout = BoardLayout.compute(board)

        val eastTiles = layout.placements.filter { it.arm == Arm.EAST }
        assertEquals(0f, eastTiles[0].rotationDegrees, "the east arm runs left to right")
        assertEquals(90f, eastTiles[1].rotationDegrees, "its double stands across the arm")

        val northTiles = layout.placements.filter { it.arm == Arm.NORTH }
        assertEquals(90f, northTiles[0].rotationDegrees, "the north arm runs up the table")
        assertEquals(0f, northTiles[1].rotationDegrees)
    }

    @Test
    fun `an arm grows outwards without tiles overlapping`() {
        var board = openedBoard(d(6, 6), d(6, 1), d(6, 2), d(6, 3), d(6, 0))
        board = board.place(d(2, 5), Arm.EAST).place(d(5, 4), Arm.EAST).place(d(4, 3), Arm.EAST)
        val east = BoardLayout.compute(board).placements.filter { it.arm == Arm.EAST }

        assertEquals(4, east.size)
        east.zipWithNext { a, b ->
            assertTrue(b.centreX > a.centreX, "each tile sits further out than the last")
            assertEquals(BoardLayout.TILE_LENGTH, b.centreX - a.centreX, 0.001f)
        }
    }

    @Test
    fun `the bounding box covers every tile on the table`() {
        val board = openedBoard(d(6, 6), d(6, 1), d(6, 2), d(6, 3), d(6, 0))
            .place(d(1, 5), Arm.NORTH)
            .place(d(2, 4), Arm.EAST)
        val layout = BoardLayout.compute(board)

        layout.placements.forEach {
            assertTrue(it.centreX in layout.minX..layout.maxX)
            assertTrue(it.centreY in layout.minY..layout.maxY)
        }
        assertTrue(layout.width > 0f && layout.height > 0f)
    }
}
