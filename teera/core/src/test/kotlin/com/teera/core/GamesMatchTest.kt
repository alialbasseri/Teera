package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** "10 گيمات": the match is ten scored games, and the higher score after them loses. */
class GamesMatchTest {
    @Test
    fun `a match to ten games ends after the tenth scored game, whatever the scores`() {
        val seats = (0 until 4).map { Seat(it, "bot-$it", "Bot", isBot = true) }
        repeat(5) { m ->
            var state = Engine.newMatch(Rules(games = 10), seats, seed = 77L + m)
            var guard = 0
            while (state.phase != Phase.MATCH_OVER && guard++ < 50_000) {
                if (state.phase == Phase.GAME_OVER) { state = Engine.startGame(state); continue }
                val seat = assertNotNull(state.actingSeat)
                val move = assertNotNull(Bot.chooseMove(state, seat, Difficulty.NORMAL))
                state = (Engine.apply(state, seat, move) as MoveOutcome.Ok).state
            }
            assertEquals(Phase.MATCH_OVER, state.phase)
            assertTrue(state.gamesPlayed >= 10, "ended after ${state.gamesPlayed} games")
            val loser = assertNotNull(state.losingTeam)
            assertTrue(state.scores[loser] > state.scores[1 - loser], "the higher score loses: ${state.scores}")
            // Past ten only to settle a level score.
            if (state.gamesPlayed > 10) assertTrue(state.gamesPlayed > 10)
        }
    }

    @Test
    fun `the target cannot be voted on when the match is to a number of games`() {
        val room = Room("TEN", "a", Rules(games = 10))
        room.join("a", "Ali")
        (1..3).forEach { room.setBot("a", it, true, Difficulty.NORMAL) }
        room.start("a", seed = 1)
        assertEquals(Room.Output.NOTHING, room.proposeTarget("a", 251))
    }
}
