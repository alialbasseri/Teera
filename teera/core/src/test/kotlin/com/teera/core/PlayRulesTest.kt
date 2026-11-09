package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The board every rescue and scoring test starts from: ends showing 3, 2, 1 and 0. */
private fun ends3210() = openedBoard(d(6, 6), d(6, 3), d(6, 2), d(6, 1), d(6, 0))

/** Rules 15 to 18: there is no boneyard, so a stuck seat is rescued by the seat before it. */
class RescueTest {

    private fun stuckTable() = rig(
        hands = listOf(
            listOf(d(3, 5), d(2, 2), d(4, 6)),
            listOf(d(4, 4)),                      // nothing answers 3, 2, 1 or 0
            listOf(d(4, 5), d(5, 2)),
            listOf(d(0, 2), d(1, 1)),
        ),
        board = ends3210(),
        turn = 0,
    )

    @Test
    fun `the seat before a stuck player is handed the decision`() {
        val state = stuckTable()
        // Seat 0 plays 3|5, which leaves the ends showing 5, 2, 1, 0 — seat 1 holds only 4|4.
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(3, 5), Arm.NORTH))).state

        assertEquals(Phase.RESCUE, after.phase)
        assertEquals(1, after.stuckSeat)
        assertEquals(0, after.rescuer, "the player before them, who is always an opponent")
        assertEquals(0, after.actingSeat)
        assertTrue(after.events.any { it is GameEvent.Stuck })
    }

    @Test
    fun `the rescuer may give a tile or play in their place, from their own hand`() {
        val after = assertIs<MoveOutcome.Ok>(
            Engine.apply(stuckTable(), 0, Move.Play(d(3, 5), Arm.NORTH))
        ).state

        val moves = Engine.legalMoves(after, 0)
        assertEquals(
            setOf(d(2, 2), d(4, 6)),
            moves.filterIsInstance<Move.Give>().map { it.domino }.toSet(),
            "every tile in the rescuer's own hand may be handed over",
        )
        assertEquals(
            listOf(Move.PlayFor(d(2, 2), Arm.EAST)),
            moves.filterIsInstance<Move.PlayFor>(),
            "and anything they could legally lay may be played instead",
        )
        assertTrue(
            moves.filterIsInstance<Move.Give>().none { it.domino == d(4, 4) },
            "the rescuer never plays out of the stuck player's hand",
        )
    }

    @Test
    fun `a tile that fits nowhere is only carried, and play moves past the stuck seat`() {
        val stuck = assertIs<MoveOutcome.Ok>(
            Engine.apply(stuckTable(), 0, Move.Play(d(3, 5), Arm.NORTH))
        ).state
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(stuck, 0, Move.Give(d(4, 6)))).state

        assertEquals(listOf(d(2, 2)), after.handOf(0))
        assertEquals(setOf(d(4, 4), d(4, 6)), after.handOf(1).toSet())
        assertEquals(2, after.turn, "rule 16: 4|6 fits nowhere, so play carries on past the stuck seat")
        assertEquals(Phase.IN_PLAY, after.phase)
        assertTrue(after.events.any { it is GameEvent.Gave })
    }

    @Test
    fun `a tile that fits is the stuck player's to lay, straight away`() {
        val stuck = assertIs<MoveOutcome.Ok>(
            Engine.apply(stuckTable(), 0, Move.Play(d(3, 5), Arm.NORTH))
        ).state
        // Ends show 5, 2, 1, 0: the 2|2 handed over goes on the 2.
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(stuck, 0, Move.Give(d(2, 2)))).state

        assertEquals(Phase.IN_PLAY, after.phase)
        assertEquals(1, after.turn, "rule 16: the stuck player plays the tile they were given")
        assertEquals(listOf(Move.Play(d(2, 2), Arm.EAST)), Engine.legalMoves(after, 1))
        val laid = assertIs<MoveOutcome.Ok>(Engine.apply(after, 1, Move.Play(d(2, 2), Arm.EAST))).state
        assertEquals(listOf(d(4, 4)), laid.handOf(1))
        assertEquals(2, laid.turn, "and then play goes on as usual")
    }

    @Test
    fun `a given tile that fits in two places may go in either`() {
        val state = rig(
            hands = listOf(
                listOf(d(5, 2), d(3, 3)),
                listOf(d(4, 4)),                      // nothing answers 5, 2, 1 or 0
                listOf(d(4, 5), d(5, 5)),
                listOf(d(0, 4), d(1, 1)),
            ),
            board = openedBoard(d(6, 6), d(6, 5), d(6, 2), d(6, 1), d(6, 0)),
            turn = 0,
            phase = Phase.RESCUE,
            stuckSeat = 1,
            rescuer = 0,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Give(d(5, 2)))).state
        assertEquals(1, after.turn)
        val arms = Engine.legalMoves(after, 1).filterIsInstance<Move.Play>().filter { it.domino == d(5, 2) }.map { it.arm }
        assertEquals(setOf(Arm.NORTH, Arm.EAST), arms.toSet(), "5|2 answers the 5 and the 2: their choice")
        val laid = assertIs<MoveOutcome.Ok>(Engine.apply(after, 1, Move.Play(d(5, 2), Arm.NORTH))).state
        assertEquals(2, laid.board.endOf(Arm.NORTH))
    }

    @Test
    fun `playing in their place puts the rescuer's own tile on the table`() {
        val stuck = assertIs<MoveOutcome.Ok>(
            Engine.apply(stuckTable(), 0, Move.Play(d(3, 5), Arm.NORTH))
        ).state
        val after = assertIs<MoveOutcome.Ok>(
            Engine.apply(stuck, 0, Move.PlayFor(d(2, 2), Arm.EAST))
        ).state

        assertEquals(2, after.board.endOf(Arm.EAST))
        assertEquals(listOf(d(4, 6)), after.handOf(0))
        assertEquals(listOf(d(4, 4)), after.handOf(1), "the stuck hand is untouched")
        assertEquals(2, after.turn)
    }

    @Test
    fun `a rescuer with nothing playable is forced to give`() {
        // Seat 3 is stuck, and seat 2 — the player before them — cannot play either.
        val state = rig(
            hands = listOf(
                listOf(d(3, 5)),
                listOf(d(1, 4)),
                listOf(d(4, 4), d(5, 5)),         // no 3, 2, 1 or 0
                listOf(d(4, 6)),                  // likewise
            ),
            board = ends3210(),
            turn = 2,
            phase = Phase.RESCUE,
            stuckSeat = 3,
            rescuer = 2,
        )
        val moves = Engine.legalMoves(state, 2)
        assertTrue(moves.all { it is Move.Give }, "rule 17 leaves no other option")
        assertEquals(2, moves.size)
    }

    @Test
    fun `handing over your last tile empties your hand and wins the game`() {
        val state = rig(
            hands = listOf(
                listOf(d(3, 5)),
                listOf(d(1, 4)),
                listOf(d(4, 6)),                  // one tile, and nothing to play it on
                listOf(d(4, 4), d(5, 5)),         // stuck
            ),
            board = ends3210(),
            turn = 2,
            phase = Phase.RESCUE,
            stuckSeat = 3,
            rescuer = 2,
            scores = listOf(10, 10),
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 2, Move.Give(d(4, 6)))).state
        val result = assertNotNull(after.lastGame)

        assertEquals(GameEnd.X, result.end, "rule 18: an empty hand is an X however it emptied")
        assertEquals(2, result.winnerSeat)
        assertTrue(after.handOf(2).isEmpty())
        assertEquals(-25, result.scored[0], "the X takes 25 off seat 2's team")
        // Seat 1 holds 1|4, and seat 3 holds 4|4 and 5|5 plus the 4|6 just pushed on them:
        // handing over your last tile wins the game and weighs the other team down with it.
        assertEquals(5 + 8 + 10 + 10, result.scored[1])
    }
}

/** Rules 19 to 21: the X, the teera, and the choice between them. */
class EndingTest {

    @Test
    fun `emptying your hand takes the X and charges the other team their spots`() {
        val state = rig(
            hands = listOf(
                listOf(d(3, 5)),                  // seat 0 goes out
                listOf(d(6, 6)),                  // 12
                listOf(d(5, 5)),                  // partner: 10, must not count
                listOf(d(4, 4)),                  // 8
            ),
            board = ends3210(),
            turn = 0,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(3, 5), Arm.NORTH))).state
        val result = assertNotNull(after.lastGame)

        assertEquals(GameEnd.X, result.end)
        assertEquals(listOf(-25, 20), result.scored, "12 + 8 from the losing team; the partner's 10 is ignored")
        assertEquals(listOf(-25, 20), after.scores)
        assertEquals(Phase.GAME_OVER, after.phase)
        assertEquals(0, result.nextOpener, "rule 11: the winner opens the next game")
    }

    @Test
    fun `dropping to a single spot offers the teera rather than taking it`() {
        val state = rig(
            hands = listOf(
                listOf(d(3, 5), d(0, 0), d(0, 1)),
                listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4)),
            ),
            board = ends3210(),
            turn = 0,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(3, 5), Arm.NORTH))).state

        assertEquals(Phase.TEERA_OFFER, after.phase)
        assertEquals(0, after.teeraSeat)
        assertEquals(listOf(Move.Teera, Move.CarryOn), Engine.legalMoves(after, 0))
        assertNull(after.lastGame, "nothing is settled until the player decides")
    }

    @Test
    fun `calling the teera ends the game and charges you your own spots`() {
        val offered = assertIs<MoveOutcome.Ok>(
            Engine.apply(
                rig(
                    hands = listOf(
                        listOf(d(3, 5), d(0, 0), d(0, 1)),   // 0|0 and 0|1 total one spot
                        listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4)),
                    ),
                    board = ends3210(),
                    turn = 0,
                ),
                0,
                Move.Play(d(3, 5), Arm.NORTH),
            )
        ).state

        val after = assertIs<MoveOutcome.Ok>(Engine.apply(offered, 0, Move.Teera)).state
        val result = assertNotNull(after.lastGame)

        assertEquals(GameEnd.TEERA, result.end)
        assertEquals(
            listOf(1, 20),
            result.scored,
            "no X: the declarer's own single spot counts against them, the other team pays 12 + 8",
        )
        assertTrue(after.events.any { it is GameEvent.TeeraCalled })
    }

    @Test
    fun `a teera with nothing left but the double blank costs nothing`() {
        val offered = assertIs<MoveOutcome.Ok>(
            Engine.apply(
                rig(
                    hands = listOf(
                        listOf(d(3, 5), d(0, 0)),
                        listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4)),
                    ),
                    board = ends3210(),
                    turn = 0,
                ),
                0,
                Move.Play(d(3, 5), Arm.NORTH),
            )
        ).state
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(offered, 0, Move.Teera)).state
        assertEquals(0, assertNotNull(after.lastGame).scored[0], "flown with a zero")
    }

    @Test
    fun `turning the teera down plays on for the X`() {
        val offered = assertIs<MoveOutcome.Ok>(
            Engine.apply(
                rig(
                    hands = listOf(
                        listOf(d(3, 5), d(0, 1)),
                        listOf(d(6, 6), d(1, 2)), listOf(d(5, 5)), listOf(d(4, 4)),
                    ),
                    board = ends3210(),
                    turn = 0,
                ),
                0,
                Move.Play(d(3, 5), Arm.NORTH),
            )
        ).state

        val after = assertIs<MoveOutcome.Ok>(Engine.apply(offered, 0, Move.CarryOn)).state
        assertNull(after.lastGame)
        assertEquals(1, after.turn, "play simply carries on round the table")
        assertEquals(listOf(d(0, 1)), after.handOf(0))
    }

    @Test
    fun `a locked table scores nothing and is dealt again by whoever locked it`() {
        // Ends show 0, 2, 3 and 4; nobody holds any of those numbers except seat 3's
        // double four, which keeps the end on a four and shuts the game down.
        val board = openedBoard(d(1, 1), d(1, 0), d(1, 2), d(1, 3), d(1, 4))
        val state = rig(
            hands = listOf(
                listOf(d(5, 5)),
                listOf(d(5, 6)),
                listOf(d(6, 6)),
                listOf(d(4, 4), d(1, 5)),
            ),
            board = board,
            turn = 3,
            scores = listOf(30, 40),
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 3, Move.Play(d(4, 4), Arm.WEST))).state
        val result = assertNotNull(after.lastGame)

        assertEquals(GameEnd.BLOCKED, result.end)
        assertNull(result.winnerSeat)
        assertEquals(listOf(0, 0), result.scored, "rule 22: a locked game is worth nothing to anybody")
        assertEquals(listOf(30, 40), after.scores)
        assertEquals(3, result.nextOpener, "the player who locked it opens the next game")
        assertEquals(Phase.GAME_OVER, after.phase)
    }
}

/** Rule 20 reaches the rescue too: you may hand the heavy tile over and fly on the light one. */
class TeeraAfterGivingTest {

    @Test
    fun `handing over the heavy tile can leave you flying`() {
        val state = rig(
            hands = listOf(
                listOf(d(3, 5)),
                listOf(d(1, 4)),
                listOf(d(4, 6), d(0, 1)),         // one ordinary tile and one spot
                listOf(d(4, 4), d(5, 5)),         // stuck on ends of 3, 2, 1 and 0
            ),
            board = openedBoard(d(6, 6), d(6, 3), d(6, 2), d(6, 1), d(6, 0)),
            turn = 2,
            phase = Phase.RESCUE,
            stuckSeat = 3,
            rescuer = 2,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 2, Move.Give(d(4, 6)))).state

        assertEquals(Phase.TEERA_OFFER, after.phase, "giving leaves a single spot, so the teera is on offer")
        assertEquals(2, after.teeraSeat)

        val flown = assertIs<MoveOutcome.Ok>(Engine.apply(after, 2, Move.Teera)).state
        val result = assertNotNull(flown.lastGame)
        assertEquals(GameEnd.TEERA, result.end)
        assertEquals(2, result.winnerSeat)
        assertEquals(1, result.scored[teamOf(2)], "the spot still in hand counts against them")
    }

    @Test
    fun `a heavy hand after giving simply plays on`() {
        val state = rig(
            hands = listOf(
                listOf(d(3, 5)),
                listOf(d(1, 4)),
                listOf(d(4, 6), d(5, 5)),
                listOf(d(4, 4), d(4, 5)),         // truly stuck on 3, 2, 1 and 0
            ),
            board = openedBoard(d(6, 6), d(6, 3), d(6, 2), d(6, 1), d(6, 0)),
            turn = 2,
            phase = Phase.RESCUE,
            stuckSeat = 3,
            rescuer = 2,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 2, Move.Give(d(4, 6)))).state
        assertEquals(Phase.IN_PLAY, after.phase)
        assertEquals(0, after.turn)
    }
}
