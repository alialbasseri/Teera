package com.teera.core

import kotlin.test.Test
import kotlin.test.assertTrue

/** The levels have to mean something: a hard pair beats a normal pair, and a normal one an easy pair. */
class BotLevelsTest {
    private fun winsFor(strong: Difficulty, weak: Difficulty, matches: Int): Int {
        var wins = 0
        repeat(matches) { m ->
            val seats = (0 until 4).map { Seat(it, "bot-$it", "Bot", isBot = true) }
            var state = Engine.newMatch(Rules(target = 101), seats, seed = 1_000L + m)
            var guard = 0
            while (state.phase != Phase.MATCH_OVER && guard++ < 50_000) {
                if (state.phase == Phase.GAME_OVER) { state = Engine.startGame(state); continue }
                val seat = state.actingSeat ?: break
                val level = if (teamOf(seat) == 0) strong else weak
                val move = Bot.chooseMove(state, seat, level) ?: break
                state = (Engine.apply(state, seat, move) as? MoveOutcome.Ok)?.state ?: break
            }
            if (state.losingTeam == 1) wins++
        }
        return wins
    }

    @Test
    fun `hard beats normal, and normal beats easy, more often than not`() {
        val n = 60
        val hard = winsFor(Difficulty.HARD, Difficulty.NORMAL, n)
        val normal = winsFor(Difficulty.NORMAL, Difficulty.EASY, n)
        println("BOTS hard-vs-normal $hard/$n, normal-vs-easy $normal/$n")
        assertTrue(hard > n * 0.55, "hard won $hard of $n against normal")
        assertTrue(normal > n * 0.55, "normal won $normal of $n against easy")
    }
}
