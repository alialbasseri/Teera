package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** While the four sides of the opening double are closed, each tile goes down in front of its player. */
class FacingTest {

    @Test
    fun `the opening sides are filled in front of whoever plays them, over many deals`() {
        var checked = 0
        for (seed in 1L..60L) {
            val room = Room("KHAN", hostId = "a").apply {
                listOf("a", "b", "c", "d").forEach { join(it, it) }
                start("a", seed = seed)
            }
            val ids = listOf("a", "b", "c", "d")
            var guard = 0
            while (room.game!!.board.let { it.isEmpty || it.inOpeningPhase } && !room.game!!.isOver && guard++ < 40) {
                val state = room.game!!
                val seat = state.actingSeat ?: break
                val move = Engine.legalMoves(state, seat).first()
                room.move(ids[seat], move)
                // In front of the player whenever that side is still bare; a player who
                // already closed theirs and is played for later gets whichever is left.
                fun check(owner: Int, arm: Arm) {
                    if (!state.board.isOpened(Engine.facing(owner))) {
                        assertEquals(Engine.facing(owner), arm, "seed $seed")
                        checked++
                    }
                }
                for (event in room.game!!.events) {
                    when (event) {
                        is GameEvent.Played -> if (state.board.inOpeningPhase) check(event.seat, event.arm)
                        is GameEvent.PlayedFor -> if (state.board.inOpeningPhase) check(event.stuck, event.arm)
                        else -> Unit
                    }
                }
            }
        }
        assertTrue(checked > 80, "enough opening plays seen: $checked")
    }

    @Test
    fun `turning the table moves every tile and keeps the shape`() {
        val board = Board().open(Domino.of(6, 6)).place(Domino.of(6, 2), Arm.EAST).place(Domino.of(6, 1), Arm.SOUTH)
        val layout = BoardLayout.compute(board)
        val turned = layout.turned(1)
        assertEquals(layout.width, turned.height, 0.001f)
        assertEquals(layout.height, turned.width, 0.001f)
        val east = layout.placements.first { it.arm == Arm.EAST }
        val eastTurned = turned.placements.first { it.arm == Arm.EAST }
        // A tile to the right of the centre ends up below it after a quarter turn clockwise.
        assertEquals(-east.centreY, eastTurned.centreX, 0.001f)
        assertEquals(east.centreX, eastTurned.centreY, 0.001f)
        assertEquals(layout.placements, layout.turned(4).placements, "four turns is no turn")
    }
}
