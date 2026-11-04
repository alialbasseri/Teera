package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackTest {
    @Test
    fun `the pack is a double six, dealt out entirely`() {
        assertEquals(28, Pack.tiles.size)
        assertEquals(28, Pack.tiles.toSet().size)
        assertEquals(7, Pack.doubles.size)
        assertEquals(28, Pack.HAND_SIZE * Pack.PLAYERS, "every tile is dealt, so there is no boneyard")
    }

    @Test
    fun `partners sit across the table`() {
        assertEquals(teamOf(0), teamOf(2))
        assertEquals(teamOf(1), teamOf(3))
        assertFalse(teamOf(0) == teamOf(1))
        assertEquals(listOf(0, 2), seatsOfTeam(0))
        assertEquals(listOf(1, 3), seatsOfTeam(1))
    }
}

class OpeningTest {

    @Test
    fun `the first game of a match is opened with the six, by whoever holds it`() {
        val state = Engine.newMatch(Rules.DEFAULT, seats(), seed = 7L)

        assertEquals(Pack.OPENING_DOUBLE, state.mustOpenWith)
        assertTrue(Pack.OPENING_DOUBLE in state.handOf(state.opener))
        assertEquals(state.opener, state.turn)
        assertEquals(
            listOf(Move.Play(Pack.OPENING_DOUBLE, null)),
            Engine.legalMoves(state, state.opener),
            "rule 10 leaves the opener exactly one legal move",
        )
    }

    @Test
    fun `opening with anything but the six is refused in the first game`() {
        val state = Engine.newMatch(Rules.DEFAULT, seats(), seed = 7L)
        val other = state.handOf(state.opener).first { it != Pack.OPENING_DOUBLE }
        val outcome = Engine.apply(state, state.opener, Move.Play(other, null))
        assertEquals(
            Rejection.MUST_OPEN_WITH_THE_SIX,
            assertIs<MoveOutcome.Rejected>(outcome).reason,
        )
    }

    @Test
    fun `a later game may be opened with any double, but never with a plain tile`() {
        val state = rig(
            hands = listOf(
                listOf(d(4, 4), d(5, 5), d(6, 3)),
                listOf(d(1, 2)), listOf(d(0, 3)), listOf(d(2, 5)),
            ),
            gameNumber = 2,
        )
        val moves = Engine.legalMoves(state, 0)
        assertEquals(
            setOf(d(4, 4), d(5, 5)),
            moves.filterIsInstance<Move.Play>().map { it.domino }.toSet(),
            "rule 9: only doubles open a game",
        )

        val refused = Engine.apply(state, 0, Move.Play(d(6, 3), null))
        assertEquals(
            Rejection.MUST_OPEN_WITH_A_DOUBLE,
            assertIs<MoveOutcome.Rejected>(refused).reason,
        )
    }

    @Test
    fun `the opening right passes on when the winner holds no double`() {
        // Seat 2 won the last game but was dealt nothing to open with.
        val previous = rig(hands = emptyList(), gameNumber = 1).copy(
            lastGame = GameResult(
                gameNumber = 1,
                end = GameEnd.X,
                winnerSeat = 2,
                handPips = listOf(0, 5, 0, 7),
                scored = listOf(-25, 12),
                nextOpener = 2,
            ),
        )
        // Deal many games and check the opener always holds a double, and is the first
        // seat from the winner round the table who does.
        for (seed in 1L..40L) {
            val dealt = Engine.startGame(previous.copy(seed = seed))
            assertTrue(dealt.handOf(dealt.opener).any { it.isDouble })
            val skipped = (2 until 2 + Pack.PLAYERS)
                .map { it % Pack.PLAYERS }
                .takeWhile { it != dealt.opener }
            skipped.forEach { seat ->
                assertFalse(
                    dealt.handOf(seat).any { it.isDouble },
                    "seat $seat was skipped but held a double",
                )
            }
        }
    }

    @Test
    fun `a dealt hand never breaks the redeal rules`() {
        for (seed in 1L..60L) {
            val state = Engine.newMatch(Rules.DEFAULT, seats(), seed)
            state.hands.forEachIndexed { seat, hand ->
                assertTrue(hand.count { it.isDouble } < 5, "seat $seat was dealt five doubles (rule 23)")
                for (face in 0..Pack.MAX_FACE) {
                    assertTrue(
                        hand.count { it.matches(face) } < 6,
                        "seat $seat was dealt six ${face}s (rule 24)",
                    )
                }
            }
        }
    }

    @Test
    fun `a hand of four doubles is offered a redeal before anything is played`() {
        val found = (1L..400L).firstNotNullOfOrNull { seed ->
            val state = Engine.newMatch(Rules.DEFAULT, seats(), seed)
            state.takeIf { it.hands.any { hand -> hand.count { t -> t.isDouble } == 4 } }
        }
        val state = assertNotNull(found, "no seed in range dealt a hand of four doubles")

        assertEquals(Phase.REDEAL_OFFER, state.phase)
        val asked = assertNotNull(state.redealOffers.firstOrNull())
        assertEquals(4, state.handOf(asked).count { it.isDouble })
        assertEquals(listOf(Move.Redeal, Move.KeepHand), Engine.legalMoves(state, asked))

        // Turning it down hands the game straight back to the opener.
        val kept = assertIs<MoveOutcome.Ok>(Engine.apply(state, asked, Move.KeepHand)).state
        if (kept.redealOffers.isEmpty()) {
            assertEquals(Phase.IN_PLAY, kept.phase)
            assertEquals(kept.opener, kept.turn)
        }

        // Taking it deals a different game.
        val redealt = assertIs<MoveOutcome.Ok>(Engine.apply(state, asked, Move.Redeal)).state
        assertEquals(state.gameNumber, redealt.gameNumber, "a redeal is the same game, dealt again")
        assertTrue(redealt.dealAttempt > state.dealAttempt)
        assertTrue(redealt.events.any { it is GameEvent.Redealt })
    }

    @Test
    fun `the game is dealt again when neither opponent can answer the opening double`() {
        // Seat 0 opens on fours; seats 1 and 3 hold not a single four between them.
        val state = rig(
            hands = listOf(
                listOf(d(4, 4), d(4, 5), d(6, 6)),
                listOf(d(1, 2), d(0, 3), d(5, 5)),
                listOf(d(4, 6), d(2, 2), d(0, 1)),
                listOf(d(3, 5), d(6, 1), d(0, 0)),
            ),
            gameNumber = 2,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(4, 4), null))).state

        assertTrue(
            after.events.any { it is GameEvent.Redealt && it.reason == RedealReason.OPPONENTS_CANNOT_ANSWER },
            "rule 25 should have forced a fresh deal",
        )
        assertTrue(after.board.isEmpty, "the table is cleared for the new deal")
        assertEquals(Pack.HAND_SIZE, after.handOf(0).size)
    }

    @Test
    fun `an opening the opponents can answer stands`() {
        val state = rig(
            hands = listOf(
                listOf(d(4, 4), d(6, 6)),
                listOf(d(4, 1), d(0, 3)),
                listOf(d(2, 2), d(0, 1)),
                listOf(d(3, 5), d(0, 0)),
            ),
            gameNumber = 2,
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(4, 4), null))).state
        assertEquals(d(4, 4), after.board.centre)
        assertNull(after.events.firstOrNull { it is GameEvent.Redealt })
    }
}
