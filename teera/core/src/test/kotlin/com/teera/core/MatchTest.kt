package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun ends3210() = openedBoard(d(6, 6), d(6, 3), d(6, 2), d(6, 1), d(6, 0))

/** Rules 5 to 8: the score runs backwards, and reaching the target loses. */
class MatchScoringTest {

    @Test
    fun `reaching the target loses the match`() {
        val state = rig(
            hands = listOf(
                listOf(d(3, 5)),
                listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4)),
            ),
            board = ends3210(),
            turn = 0,
            scores = listOf(0, 190),
            rules = Rules(target = 201),
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(3, 5), Arm.NORTH))).state

        assertEquals(listOf(-25, 210), after.scores)
        assertEquals(1, after.losingTeam, "the team that got there loses")
        assertEquals(Phase.MATCH_OVER, after.phase)
        assertTrue(after.events.any { it is GameEvent.MatchEnded })
    }

    @Test
    fun `a teera on 200 loses you the match yourself`() {
        // Ali's own example: your team is on 200, you fly with a one, and that is that.
        val opened = rig(
            hands = listOf(
                listOf(d(3, 5), d(0, 1)),
                listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4)),
            ),
            board = ends3210(),
            turn = 0,
            scores = listOf(200, 0),
            rules = Rules(target = 201),
        )
        val offered = assertIs<MoveOutcome.Ok>(Engine.apply(opened, 0, Move.Play(d(3, 5), Arm.NORTH))).state
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(offered, 0, Move.Teera)).state

        assertEquals(201, after.scores[0])
        assertEquals(0, after.losingTeam, "the single spot took you over the line")
        assertEquals(Phase.MATCH_OVER, after.phase)
    }

    @Test
    fun `the X can take a score below zero`() {
        val state = rig(
            hands = listOf(listOf(d(3, 5)), listOf(d(1, 1)), listOf(d(5, 5)), listOf(d(2, 2))),
            board = ends3210(),
            turn = 0,
            scores = listOf(-75, 40),
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(3, 5), Arm.NORTH))).state
        assertEquals(-100, after.scores[0], "four X's in a row is minus a hundred")
        assertNull(after.losingTeam)
    }

    @Test
    fun `251 is the longer match`() {
        val state = rig(
            hands = listOf(listOf(d(3, 5)), listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4))),
            board = ends3210(),
            turn = 0,
            scores = listOf(0, 230),
            rules = Rules(target = 251),
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(3, 5), Arm.NORTH))).state
        assertEquals(250, after.scores[1])
        assertNull(after.losingTeam, "one short, so the match goes on")
    }

    @Test
    fun `the next game is dealt from the same match score`() {
        val state = rig(
            hands = listOf(listOf(d(3, 5)), listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4))),
            board = ends3210(),
            turn = 0,
            scores = listOf(10, 10),
        ).copy(seed = 3L)
        val over = assertIs<MoveOutcome.Ok>(Engine.apply(state, 0, Move.Play(d(3, 5), Arm.NORTH))).state
        val next = Engine.startGame(over)

        assertEquals(over.scores, next.scores, "the score carries across games")
        assertEquals(2, next.gameNumber)
        assertEquals(Pack.HAND_SIZE, next.handOf(0).size)
        assertTrue(next.phase == Phase.IN_PLAY || next.phase == Phase.REDEAL_OFFER)
    }
}

/** Rules 27 to 30. */
class AlertTest {

    private fun table() = rig(
        hands = listOf(listOf(d(3, 5)), listOf(d(6, 6)), listOf(d(5, 5)), listOf(d(4, 4))),
        board = ends3210(),
        turn = 0,
        scores = listOf(0, 0),
    )

    @Test
    fun `every third alert costs the offending team fifty`() {
        var state = table()
        state = Engine.upholdAlert(state, againstTeam = 1)
        assertEquals(listOf(0, 1), state.alerts)
        assertEquals(listOf(0, 0), state.scores, "one alert on its own costs nothing")

        state = Engine.upholdAlert(state, againstTeam = 1)
        assertEquals(listOf(0, 0), state.scores)

        state = Engine.upholdAlert(state, againstTeam = 1)
        assertEquals(listOf(0, 3), state.alerts)
        assertEquals(listOf(0, 50), state.scores)

        val penalty = state.events.filterIsInstance<GameEvent.AlertUpheld>().last()
        assertEquals(50, penalty.penalty)
    }

    @Test
    fun `alerts run for the whole match and each team keeps its own count`() {
        var state = table()
        repeat(2) { state = Engine.upholdAlert(state, againstTeam = 0) }
        repeat(3) { state = Engine.upholdAlert(state, againstTeam = 1) }

        assertEquals(listOf(2, 3), state.alerts)
        assertEquals(listOf(0, 50), state.scores)

        val next = Engine.startGame(state.copy(phase = Phase.GAME_OVER, seed = 5L))
        assertEquals(listOf(2, 3), next.alerts, "rule 30: the count is not wiped between games")
    }

    @Test
    fun `a penalty can lose the match`() {
        var state = table().copy(scores = listOf(0, 160))
        repeat(3) { state = Engine.upholdAlert(state, againstTeam = 1) }
        assertEquals(210, state.scores[1])
        assertEquals(1, state.losingTeam)
        assertEquals(Phase.MATCH_OVER, state.phase)
    }
}

class FullMatchTest {

    @Test
    fun `a match between unevenly matched pairs reaches a target`() {
        // Teera only ends when one side keeps losing: the X takes 25 off whoever goes
        // out while the losers pay what is left in their hands, so two pairs playing
        // alike drift together and the match runs very long. Make them play differently
        // and a score climbs to the target.
        var finishes = 0
        for (seed in 1L..8L) {
            val finished = playOut(
                rules = Rules(target = 201),
                seed = seed,
                strength = { seat -> if (teamOf(seat) == 0) Difficulty.HARD else Difficulty.EASY },
            )
            if (finished.phase != Phase.MATCH_OVER) continue
            finishes++
            val loser = assertNotNull(finished.losingTeam)
            assertTrue(finished.scores[loser] >= 201)
            assertTrue(finished.scores[1 - loser] < 201)
        }
        assertTrue(finishes >= 6, "only $finishes of 8 matches reached a target")
    }

    @Test
    fun `games keep ending cleanly however long the match runs`() {
        // The engine must never reach a position where the table is waiting on a seat
        // that has nothing it may legally do.
        var ended = 0
        for (seed in 1L..12L) {
            var state = Engine.newMatch(Rules(target = 100_000), seats(), seed)
            var moves = 0
            while (ended < 400 && moves < 8_000) {
                if (state.phase == Phase.GAME_OVER) {
                    ended++
                    assertNotNull(state.lastGame)
                    state = Engine.startGame(state)
                    continue
                }
                val seat = assertNotNull(state.actingSeat, "phase ${state.phase} waits on nobody")
                val move = assertNotNull(
                    Bot.chooseMove(state, seat),
                    "seat $seat has nothing legal in phase ${state.phase}",
                )
                state = assertIs<MoveOutcome.Ok>(Engine.apply(state, seat, move)).state
                moves++
            }
        }
        assertTrue(ended >= 400, "only $ended games finished")
    }

    @Test
    fun `every tile stays on the table for the whole match`() {
        playOut(seed = 99L) { state ->
            val accounted = state.hands.flatten() + state.board.tiles()
            assertEquals(Pack.SIZE, accounted.size, "a tile went missing or was duplicated")
            assertEquals(Pack.SIZE, accounted.toSet().size, "the same tile is in two places")
        }
    }

    @Test
    fun `games end the way the rules allow and nothing else`() {
        val ends = mutableMapOf<GameEnd, Int>()
        for (seed in 50L..70L) {
            playOut(seed = seed) { state ->
                state.lastGame?.let { result ->
                    if (state.events.any { it is GameEvent.GameEnded }) {
                        ends[result.end] = (ends[result.end] ?: 0) + 1
                        when (result.end) {
                            GameEnd.X -> {
                                assertEquals(0, result.handPips[assertNotNull(result.winnerSeat)])
                                assertTrue(result.scored.contains(-25))
                            }
                            GameEnd.TEERA -> {
                                val seat = assertNotNull(result.winnerSeat)
                                assertTrue(result.handPips[seat] <= 1, "a teera is flown on a spot or none")
                            }
                            GameEnd.BLOCKED -> assertEquals(listOf(0, 0), result.scored)
                        }
                    }
                }
            }
        }
        assertTrue(ends.isNotEmpty())
        assertTrue(GameEnd.X in ends || GameEnd.TEERA in ends, "games should be being won")
    }

    @Test
    fun `a seeded match replays identically`() {
        assertEquals(playOut(seed = 123L).scores, playOut(seed = 123L).scores)
    }

    @Test
    fun `a stuck seat is always rescued by the opponent before it`() {
        playOut(seed = 31L) { state ->
            if (state.phase == Phase.RESCUE) {
                val stuck = assertNotNull(state.stuckSeat)
                val rescuer = assertNotNull(state.rescuer)
                assertEquals((stuck + 3) % 4, rescuer)
                assertFalse(teamOf(stuck) == teamOf(rescuer), "the seat before you is always an opponent")
                assertFalse(Engine.hasPlayable(state, stuck), "only a stuck seat is rescued")
            }
        }
    }
}
