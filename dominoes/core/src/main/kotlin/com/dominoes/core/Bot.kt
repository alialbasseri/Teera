package com.dominoes.core

import kotlin.random.Random

@kotlinx.serialization.Serializable
enum class Difficulty { EASY, NORMAL, HARD }

/**
 * Fills empty seats and covers for a player who has dropped out.
 *
 * Deliberately heuristic rather than a search: a bot that plays perfectly is not fun,
 * and the whole point of the app is the four humans. Choices are deterministic given a
 * state, so a match replays identically from its seed.
 */
object Bot {

    fun chooseMove(state: GameState, seat: Int, difficulty: Difficulty = Difficulty.NORMAL): Move? {
        val moves = Engine.legalMoves(state, seat)
        if (moves.isEmpty()) return null
        moves.singleOrNull()?.let { return it }

        val plays = moves.filterIsInstance<Move.Play>()
        if (plays.isEmpty()) return moves.first()   // Draw before Pass, as ordered by the engine

        val rng = Random(state.seed + state.board.size * 131 + seat)
        if (difficulty == Difficulty.EASY) return plays[rng.nextInt(plays.size)]

        return plays.maxBy { score(state, seat, it, difficulty) + rng.nextDouble(0.5) }
    }

    private fun score(state: GameState, seat: Int, move: Move.Play, difficulty: Difficulty): Double {
        val rules = state.rules
        val board = state.board.place(move.domino, move.end, rules)
        var score = 0.0

        // Shed weight: the loser pays for what is left in hand.
        score += move.domino.pips * 1.5

        // Doubles are hard to place later, so get rid of them while an end still matches.
        if (move.domino.isDouble) score += 6.0

        // Pegging is immediate, guaranteed points.
        if (rules.scoring == ScoringMode.PEG_ON_ENDS) {
            val total = board.openEndTotal(rules)
            if (total > 0 && total % rules.pegStep == 0) {
                score += (if (rules.pegDividedByStep) total / rules.pegStep else total) * 4.0
            }
        }

        if (difficulty == Difficulty.HARD) {
            val remaining = state.handOf(seat).filterNot { it == move.domino }
            // Leave ends this hand can still answer.
            val cover = listOfNotNull(board.leftEnd, board.rightEnd)
                .count { end -> remaining.any { it.matches(end) } }
            score += cover * 4.0

            // Both ends showing the same face is strong: opponents need that one number.
            if (board.leftEnd == board.rightEnd) score += 3.0

            // Prefer the face this hand is deepest in.
            val suit = remaining.count { it.matches(board.endValue(move.end) ?: -1) }
            score += suit * 1.5
        }

        return score
    }
}
