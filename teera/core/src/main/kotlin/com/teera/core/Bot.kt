package com.teera.core

import kotlin.random.Random

@kotlinx.serialization.Serializable
enum class Difficulty { EASY, NORMAL, HARD }

/**
 * Fills an empty seat and covers for a player who has walked away.
 *
 * Heuristic rather than a search: a bot that plays perfectly is not fun, and the point
 * of the app is the four people. Choices are deterministic for a given state, so a
 * match replays identically from its seed.
 */
object Bot {

    fun chooseMove(state: GameState, seat: Int, difficulty: Difficulty = Difficulty.NORMAL): Move? {
        val moves = Engine.legalMoves(state, seat)
        if (moves.isEmpty()) return null

        return when (state.phase) {
            Phase.REDEAL_OFFER -> redealChoice(state, seat)
            Phase.TEERA_OFFER -> teeraChoice(state, seat)
            Phase.RESCUE -> rescueChoice(state, seat, moves, difficulty)
            Phase.IN_PLAY -> bestPlay(state, seat, moves.filterIsInstance<Move.Play>(), difficulty)
            else -> moves.first()
        }
    }

    /** Four doubles is a heavy, awkward hand; take the fresh deal when it is also heavy. */
    private fun redealChoice(state: GameState, seat: Int): Move =
        if (state.handOf(seat).pipTotal() >= HEAVY_HAND) Move.Redeal else Move.KeepHand

    /**
     * Rule 21: the teera is certain but small, the X is worth 25. Play on only when the
     * last tile is already sitting on a live end.
     */
    private fun teeraChoice(state: GameState, seat: Int): Move {
        val hand = state.handOf(seat)
        val canFinishNow = hand.size == 1 && state.board.acceptsAnywhere(hand.first())
        return if (canFinishNow) Move.CarryOn else Move.Teera
    }

    private fun rescueChoice(
        state: GameState,
        seat: Int,
        moves: List<Move>,
        difficulty: Difficulty,
    ): Move {
        val hand = state.handOf(seat)
        val gives = moves.filterIsInstance<Move.Give>()
        val plays = moves.filterIsInstance<Move.PlayFor>()

        // Rule 18: handing over your last tile empties your hand and wins the game.
        if (hand.size == 1 && gives.isNotEmpty()) return gives.first()

        if (plays.isNotEmpty() && difficulty != Difficulty.EASY) {
            val best = plays.maxBy { score(state, seat, it.domino, it.arm, difficulty) }
            return best
        }
        // Nothing to play, so rule 17 applies: hand over a tile, and make it a heavy one
        // so the stuck opponent carries the weight if the game ends on a count.
        return gives.maxByOrNull { it.domino.pips } ?: moves.first()
    }

    private fun bestPlay(
        state: GameState,
        seat: Int,
        plays: List<Move.Play>,
        difficulty: Difficulty,
    ): Move? {
        if (plays.isEmpty()) return null
        plays.singleOrNull()?.let { return it }

        val rng = Random(state.seed + state.board.size * 131 + seat)
        if (difficulty == Difficulty.EASY) return plays[rng.nextInt(plays.size)]

        return plays.maxBy {
            score(state, seat, it.domino, it.arm, difficulty) + rng.nextDouble(0.5)
        }
    }

    private fun score(
        state: GameState,
        seat: Int,
        domino: Domino,
        arm: Arm?,
        difficulty: Difficulty,
    ): Double {
        var score = 0.0

        // Shed weight: whatever is left in hand is what the team pays.
        score += domino.pips * 1.5

        // Doubles are awkward to place later, and only one number answers them.
        if (domino.isDouble) score += 6.0

        if (difficulty == Difficulty.HARD && arm != null && !state.board.inOpeningPhase) {
            val after = state.board.place(domino, arm)
            val remaining = state.handOf(seat).filterNot { it == domino }

            // Keep ends this hand can still answer.
            val cover = Arm.ALL.count { a -> remaining.any { after.accepts(it, a) } }
            score += cover * 3.0

            // Holding the only tiles of a number is how a guaranteed tile is made.
            val end = after.endOf(arm) ?: -1
            score += remaining.count { it.matches(end) } * 1.5
        }

        // A hard bot reads the table as a sharp player would: it leaves the next opponent
        // as little as possible to answer with, and its partner as much as possible.
        if (difficulty == Difficulty.HARD && arm != null) {
            val after = if (state.board.isEmpty) state.board.open(domino) else state.board.place(domino, arm)
            fun answers(other: Int) = state.handOf(other).count { tile -> Arm.ALL.any { after.accepts(tile, it) } }
            score -= answers((seat + 1) % Pack.PLAYERS) * 0.45
            score -= answers((seat + 3) % Pack.PLAYERS) * 0.15
            score += answers((seat + 2) % Pack.PLAYERS) * 0.25
            if (answers((seat + 1) % Pack.PLAYERS) == 0) score += 1.5
        }

        return score
    }

    private const val HEAVY_HAND = 50
}
