package com.teera.core

import kotlin.random.Random

/** Why a move was turned down. The client shows these, so they name the rule. */
@kotlinx.serialization.Serializable
enum class Rejection {
    NOT_YOUR_TURN,
    WRONG_PHASE,
    TILE_NOT_IN_HAND,
    MUST_OPEN_WITH_A_DOUBLE,
    MUST_OPEN_WITH_THE_SIX,
    MUST_OPEN_A_FRESH_SIDE,
    TILE_DOES_NOT_MATCH,
    NO_ARM_GIVEN,
    MUST_PLAY_IF_ABLE,
    CANNOT_PLAY_FOR_THEM,
    TOO_HEAVY_FOR_A_TEERA,
}

sealed interface MoveOutcome {
    data class Ok(val state: GameState) : MoveOutcome
    data class Rejected(val reason: Rejection) : MoveOutcome
}

/**
 * The referee for Teera.
 *
 * Pure functions over [GameState]: the same state and the same move always give the
 * same result, which is what lets the server hold the truth while a phone predicts
 * moves locally without the two drifting apart.
 *
 * Rule numbers in the comments refer to RULES.md.
 */
object Engine {

    private const val MAX_DEAL_ATTEMPTS = 200

    // -- starting ----------------------------------------------------------

    fun newMatch(rules: Rules, seats: List<Seat>, seed: Long = Random.nextLong()): GameState {
        val problems = rules.validate()
        require(problems.isEmpty()) { "invalid rules: ${problems.joinToString("; ")}" }
        require(seats.size == Pack.PLAYERS) { "Teera needs exactly ${Pack.PLAYERS} players" }

        val state = GameState(
            rules = rules,
            seats = seats.sortedBy { it.seat },
            scores = listOf(0, 0),
            alerts = listOf(0, 0),
            seed = seed,
        )
        return startGame(state)
    }

    /** Deals the next game. The opener carries over from how the last one ended. */
    fun startGame(state: GameState): GameState = deal(state, state.gameNumber + 1, attempt = 1)

    /**
     * Shuffles and deals until the hands are ones the rules will allow (rules 23 and 24),
     * then works out who opens and with what (rules 10 and 11).
     */
    private fun deal(state: GameState, gameNumber: Int, attempt: Int): GameState {
        var tries = attempt
        var hands: List<List<Domino>>
        var forced: RedealReason? = null

        while (true) {
            val rng = Random(state.seed * 31 + gameNumber * 7919L + tries)
            val pack = Pack.tiles.shuffled(rng)
            hands = (0 until Pack.PLAYERS).map { seat ->
                pack.subList(seat * Pack.HAND_SIZE, (seat + 1) * Pack.HAND_SIZE).sortedDescending()
            }

            forced = mandatoryRedeal(hands)
            if (forced == null || tries - attempt >= MAX_DEAL_ATTEMPTS) break
            tries++
        }

        val (opener, mustOpenWith) = decideOpener(state, hands, gameNumber)

        // A hand of four doubles may ask for a fresh deal before anything is played.
        val offers = (0 until Pack.PLAYERS)
            .map { (opener + it) % Pack.PLAYERS }
            .filter { hands[it].count { tile -> tile.isDouble } == FOUR_DOUBLES }

        val events = buildList<GameEvent> {
            if (tries > attempt) add(GameEvent.Redealt(forced ?: RedealReason.FIVE_DOUBLES, null))
            add(GameEvent.Dealt(gameNumber, opener, mustOpenWith))
        }

        return state.copy(
            hands = hands,
            board = Board(),
            turn = opener,
            phase = if (offers.isEmpty()) Phase.IN_PLAY else Phase.REDEAL_OFFER,
            gameNumber = gameNumber,
            dealAttempt = tries,
            opener = opener,
            mustOpenWith = mustOpenWith,
            lastToPlay = null,
            stuckSeat = null,
            rescuer = null,
            teeraSeat = null,
            turnAfterTeera = null,
            redealOffers = offers,
            events = events,
        )
    }

    /** Rules 23 and 24: hands nobody has to play with. */
    private fun mandatoryRedeal(hands: List<List<Domino>>): RedealReason? {
        for (hand in hands) {
            if (hand.count { it.isDouble } >= FIVE_DOUBLES) return RedealReason.FIVE_DOUBLES
            for (face in 0..Pack.MAX_FACE) {
                if (hand.count { it.matches(face) } >= SIX_OF_A_NUMBER) {
                    return RedealReason.SIX_OF_A_NUMBER
                }
            }
        }
        return null
    }

    /**
     * Rule 10: the first game of a match is opened with the six, by whoever holds it.
     * Rule 11: afterwards the winner opens, with a double of their choosing, and if they
     * hold no double at all the right passes to the player behind them.
     */
    private fun decideOpener(
        state: GameState,
        hands: List<List<Domino>>,
        gameNumber: Int,
    ): Pair<Int, Domino?> {
        if (gameNumber == 1) {
            val seat = hands.indexOfFirst { Pack.OPENING_DOUBLE in it }
            return seat to Pack.OPENING_DOUBLE
        }
        val from = state.lastGame?.nextOpener ?: state.opener
        for (step in 0 until Pack.PLAYERS) {
            val seat = (from + step) % Pack.PLAYERS
            if (hands[seat].any { it.isDouble }) return seat to null
        }
        // Impossible with seven doubles across four hands, but never deal an unplayable game.
        return from to null
    }

    // -- what a seat may do ------------------------------------------------

    fun legalMoves(state: GameState, seat: Int): List<Move> {
        if (state.actingSeat != seat) return emptyList()
        return when (state.phase) {
            Phase.REDEAL_OFFER -> listOf(Move.Redeal, Move.KeepHand)
            Phase.TEERA_OFFER -> listOf(Move.Teera, Move.CarryOn)
            Phase.IN_PLAY -> playsFor(state, seat)
            Phase.RESCUE -> rescueMoves(state, seat)
            Phase.GAME_OVER, Phase.MATCH_OVER -> emptyList()
        }
    }

    /** The tiles [seat] could lay, and where. */
    private fun playsFor(state: GameState, seat: Int): List<Move.Play> {
        val hand = state.handOf(seat)
        val board = state.board

        if (board.isEmpty) {
            // Opening the game: a double, and the first game's is fixed at 6|6.
            val openers = state.mustOpenWith
                ?.let { forced -> hand.filter { it == forced } }
                ?: hand.filter { it.isDouble }
            return openers.map { Move.Play(it, null) }
        }

        if (board.inOpeningPhase) {
            // Rule 12: the next bare side gets closed; which one is bookkeeping, not a choice.
            val face = board.centreFace ?: return emptyList()
            return hand.filter { it.matches(face) }.map { Move.Play(it, null) }
        }

        return hand.flatMap { tile ->
            Arm.ALL.filter { board.accepts(tile, it) }.map { Move.Play(tile, it) }
        }
    }

    /** Rule 15: give one of your own tiles, or play one in their place. */
    private fun rescueMoves(state: GameState, rescuer: Int): List<Move> {
        val giving = state.handOf(rescuer).map { Move.Give(it) }
        val playing = playsFor(state, rescuer).mapNotNull { play ->
            play.arm?.let { Move.PlayFor(play.domino, it) }
                ?: openingArm(state.board, state.stuckSeat ?: rescuer)?.let { Move.PlayFor(play.domino, it) }
        }
        return playing + giving
    }

    /** Whether a seat holds anything it could legally lay right now. */
    fun hasPlayable(state: GameState, seat: Int): Boolean {
        val board = state.board
        if (board.isEmpty) return false
        val hand = state.handOf(seat)
        if (board.inOpeningPhase) {
            val face = board.centreFace ?: return false
            return hand.any { it.matches(face) }
        }
        return hand.any { board.acceptsAnywhere(it) }
    }

    private fun firstFreeArm(board: Board): Arm? = Arm.ALL.firstOrNull { !board.isOpened(it) }

    /**
     * The side of the opening double that faces [seat]: south for seat 0, then round the
     * table the way play goes (east, north, west). While the four sides are being closed,
     * each player's tile goes down in front of them, as across a real table.
     */
    fun facing(seat: Int): Arm = when (((seat % 4) + 4) % 4) {
        0 -> Arm.SOUTH
        1 -> Arm.EAST
        2 -> Arm.NORTH
        else -> Arm.WEST
    }

    /** The side a tile laid by (or for) [seat] goes on while the four sides are open. */
    private fun openingArm(board: Board, seat: Int): Arm? =
        facing(seat).takeIf { !board.isOpened(it) } ?: firstFreeArm(board)

    // -- applying a move ---------------------------------------------------

    fun apply(state: GameState, seat: Int, move: Move): MoveOutcome {
        if (state.isOver) return MoveOutcome.Rejected(Rejection.WRONG_PHASE)
        if (state.actingSeat != seat) return MoveOutcome.Rejected(Rejection.NOT_YOUR_TURN)

        return when (move) {
            is Move.Play -> if (state.phase == Phase.IN_PLAY) applyPlay(state, seat, move)
            else MoveOutcome.Rejected(Rejection.WRONG_PHASE)

            is Move.Give -> if (state.phase == Phase.RESCUE) applyGive(state, seat, move)
            else MoveOutcome.Rejected(Rejection.WRONG_PHASE)

            is Move.PlayFor -> if (state.phase == Phase.RESCUE) applyPlayFor(state, seat, move)
            else MoveOutcome.Rejected(Rejection.WRONG_PHASE)

            Move.Teera -> if (state.phase == Phase.TEERA_OFFER) applyTeera(state, seat)
            else MoveOutcome.Rejected(Rejection.WRONG_PHASE)

            Move.CarryOn -> if (state.phase == Phase.TEERA_OFFER) {
                MoveOutcome.Ok(advanceTo(state.copy(teeraSeat = null), state.turnAfterTeera ?: nextSeat(seat)))
            } else MoveOutcome.Rejected(Rejection.WRONG_PHASE)

            Move.Redeal -> if (state.phase == Phase.REDEAL_OFFER) {
                MoveOutcome.Ok(
                    deal(state, state.gameNumber, state.dealAttempt + 1).let {
                        it.copy(events = listOf(GameEvent.Redealt(RedealReason.FOUR_DOUBLES_BY_CHOICE, seat)) + it.events)
                    }
                )
            } else MoveOutcome.Rejected(Rejection.WRONG_PHASE)

            Move.KeepHand -> if (state.phase == Phase.REDEAL_OFFER) {
                val remaining = state.redealOffers.drop(1)
                MoveOutcome.Ok(
                    state.copy(
                        redealOffers = remaining,
                        phase = if (remaining.isEmpty()) Phase.IN_PLAY else Phase.REDEAL_OFFER,
                        turn = state.opener,
                        events = emptyList(),
                    )
                )
            } else MoveOutcome.Rejected(Rejection.WRONG_PHASE)
        }
    }

    private fun applyPlay(state: GameState, seat: Int, move: Move.Play): MoveOutcome {
        val hand = state.handOf(seat)
        if (move.domino !in hand) return MoveOutcome.Rejected(Rejection.TILE_NOT_IN_HAND)

        // Opening the table.
        if (state.board.isEmpty) {
            state.mustOpenWith?.let { forced ->
                if (move.domino != forced) return MoveOutcome.Rejected(Rejection.MUST_OPEN_WITH_THE_SIX)
            }
            if (!move.domino.isDouble) return MoveOutcome.Rejected(Rejection.MUST_OPEN_WITH_A_DOUBLE)
            val opened = state.copy(
                board = state.board.open(move.domino),
                hands = state.hands.replacing(seat, hand - move.domino),
                lastToPlay = seat,
                mustOpenWith = null,
                events = listOf(GameEvent.Opened(seat, move.domino)),
            )
            // Rule 25: if neither opponent can answer the opening double, deal again.
            if (opponentsCannotAnswer(opened, seat, move.domino)) {
                val fresh = deal(state, state.gameNumber, state.dealAttempt + 1)
                return MoveOutcome.Ok(
                    fresh.copy(
                        events = listOf(
                            GameEvent.Opened(seat, move.domino),
                            GameEvent.Redealt(RedealReason.OPPONENTS_CANNOT_ANSWER, seat),
                        ) + fresh.events,
                    )
                )
            }
            return MoveOutcome.Ok(afterShedding(opened, seat, nextSeat(seat)))
        }

        // Rule 12: while the four sides are being closed, the tile goes on a bare one: the
        // one in front of whoever lays it.
        val arm = if (state.board.inOpeningPhase) {
            openingArm(state.board, seat) ?: return MoveOutcome.Rejected(Rejection.MUST_OPEN_A_FRESH_SIDE)
        } else {
            move.arm ?: return MoveOutcome.Rejected(Rejection.NO_ARM_GIVEN)
        }
        if (!state.board.accepts(move.domino, arm)) {
            return MoveOutcome.Rejected(Rejection.TILE_DOES_NOT_MATCH)
        }

        val played = state.copy(
            board = state.board.place(move.domino, arm),
            hands = state.hands.replacing(seat, hand - move.domino),
            lastToPlay = seat,
            events = listOf(GameEvent.Played(seat, move.domino, arm)),
        )
        return MoveOutcome.Ok(afterShedding(played, seat, nextSeat(seat)))
    }

    private fun applyGive(state: GameState, rescuer: Int, move: Move.Give): MoveOutcome {
        val stuck = state.stuckSeat ?: return MoveOutcome.Rejected(Rejection.WRONG_PHASE)
        val hand = state.handOf(rescuer)
        if (move.domino !in hand) return MoveOutcome.Rejected(Rejection.TILE_NOT_IN_HAND)

        val hands = state.hands
            .replacing(rescuer, hand - move.domino)
            .replacing(stuck, (state.handOf(stuck) + move.domino).sortedDescending())

        val given = state.copy(
            hands = hands,
            stuckSeat = null,
            rescuer = null,
            events = listOf(GameEvent.Gave(rescuer, stuck)),
        )

        // Rule 18: handing over your last tile empties your hand, which wins the game.
        // Rule 20: and if what is left weighs a spot or nothing, the teera is on offer,
        // so a player can hand over the heavy tile and fly on the light one.
        return MoveOutcome.Ok(afterShedding(given, rescuer, nextSeat(stuck)))
    }

    private fun applyPlayFor(state: GameState, rescuer: Int, move: Move.PlayFor): MoveOutcome {
        val stuck = state.stuckSeat ?: return MoveOutcome.Rejected(Rejection.WRONG_PHASE)
        val hand = state.handOf(rescuer)
        if (move.domino !in hand) return MoveOutcome.Rejected(Rejection.TILE_NOT_IN_HAND)

        // Laid for the stuck player, so in front of them.
        val arm = if (state.board.inOpeningPhase) {
            openingArm(state.board, stuck) ?: return MoveOutcome.Rejected(Rejection.MUST_OPEN_A_FRESH_SIDE)
        } else {
            move.arm
        }
        if (!state.board.accepts(move.domino, arm)) {
            return MoveOutcome.Rejected(Rejection.CANNOT_PLAY_FOR_THEM)
        }

        val played = state.copy(
            board = state.board.place(move.domino, arm),
            hands = state.hands.replacing(rescuer, hand - move.domino),
            lastToPlay = rescuer,
            stuckSeat = null,
            rescuer = null,
            events = listOf(GameEvent.PlayedFor(rescuer, stuck, move.domino, arm)),
        )
        return MoveOutcome.Ok(afterShedding(played, rescuer, nextSeat(stuck)))
    }

    private fun applyTeera(state: GameState, seat: Int): MoveOutcome {
        val pips = state.handOf(seat).pipTotal()
        if (pips > TEERA_LIMIT) return MoveOutcome.Rejected(Rejection.TOO_HEAVY_FOR_A_TEERA)
        return MoveOutcome.Ok(
            endGame(
                state.copy(events = state.events + GameEvent.TeeraCalled(seat, pips)),
                GameEnd.TEERA,
                seat,
            )
        )
    }

    /**
     * What happens after a player parts with a tile, whether they laid it or handed it
     * over: an empty hand wins, a hand down to a single spot may call teera (rule 20),
     * and otherwise play moves on.
     */
    private fun afterShedding(state: GameState, seat: Int, nextTurn: Int): GameState {
        val hand = state.handOf(seat)
        if (hand.isEmpty()) return endGame(state, GameEnd.X, seat)

        if (hand.pipTotal() <= TEERA_LIMIT) {
            return state.copy(
                phase = Phase.TEERA_OFFER,
                teeraSeat = seat,
                turnAfterTeera = nextTurn,
            )
        }
        return advanceTo(state, nextTurn)
    }

    /**
     * Hands the turn on, covering the two things that can happen instead: the table
     * locking up entirely (rule 22), or the next seat being stuck (rule 15).
     */
    private fun advanceTo(state: GameState, seat: Int): GameState {
        if ((0 until Pack.PLAYERS).none { hasPlayable(state, it) }) {
            return endGame(state, GameEnd.BLOCKED, null)
        }
        if (hasPlayable(state, seat)) {
            return state.copy(phase = Phase.IN_PLAY, turn = seat, stuckSeat = null, rescuer = null)
        }
        val rescuer = previousSeat(seat)
        return state.copy(
            phase = Phase.RESCUE,
            turn = seat,
            stuckSeat = seat,
            rescuer = rescuer,
            events = state.events + GameEvent.Stuck(seat, rescuer),
        )
    }

    private fun nextSeat(from: Int): Int = (from + 1) % Pack.PLAYERS

    private fun previousSeat(of: Int): Int = (of + Pack.PLAYERS - 1) % Pack.PLAYERS

    /** Rule 25, checked once the opening double is on the table. */
    private fun opponentsCannotAnswer(state: GameState, opener: Int, double: Domino): Boolean {
        val opponents = seatsOfTeam(otherTeam(teamOf(opener)))
        return opponents.none { seat -> state.handOf(seat).any { it.matches(double.high) } }
    }

    // -- scoring -----------------------------------------------------------

    /**
     * Settles the game and the match.
     *
     * Scoring runs backwards in Teera: what a team takes is added to it, and reaching
     * the target loses. The X is simply a negative take.
     */
    private fun endGame(state: GameState, end: GameEnd, winnerSeat: Int?): GameState {
        val pips = (0 until Pack.PLAYERS).map { state.handOf(it).pipTotal() }
        val scored = intArrayOf(0, 0)

        if (winnerSeat != null) {
            val mine = teamOf(winnerSeat)
            val theirs = otherTeam(mine)
            // The losing team always pays for what it is still holding; a partner's
            // tiles never count against the team that finished.
            scored[theirs] += seatsOfTeam(theirs).sumOf { pips[it] }
            when (end) {
                GameEnd.X -> scored[mine] += state.rules.xBonus
                GameEnd.TEERA -> scored[mine] += pips[winnerSeat]
                GameEnd.BLOCKED -> Unit
            }
        }

        val scores = listOf(state.scores[0] + scored[0], state.scores[1] + scored[1])
        val nextOpener = winnerSeat ?: state.lastToPlay ?: state.opener

        val result = GameResult(
            gameNumber = state.gameNumber,
            end = end,
            winnerSeat = winnerSeat,
            handPips = pips,
            scored = scored.toList(),
            nextOpener = nextOpener,
        )

        val loser = losingTeam(scores, state.rules.target)
        val events = state.events + GameEvent.GameEnded(result) +
            listOfNotNull(loser?.let { GameEvent.MatchEnded(it, scores) })

        return state.copy(
            scores = scores,
            phase = if (loser != null) Phase.MATCH_OVER else Phase.GAME_OVER,
            lastGame = result,
            losingTeam = loser,
            stuckSeat = null,
            rescuer = null,
            teeraSeat = null,
            turnAfterTeera = null,
            events = events,
        )
    }

    /**
     * A team that reaches the target loses. Both can cross it in the same game, since a
     * teera scores on both sides, and then the heavier score is the losing one; dead
     * level means another game settles it.
     */
    private fun losingTeam(scores: List<Int>, target: Int): Int? {
        val crossed = scores.indices.filter { scores[it] >= target }
        return when (crossed.size) {
            0 -> null
            1 -> crossed.first()
            else -> if (scores[0] == scores[1]) null else scores.indices.maxByOrNull { scores[it] }
        }
    }

    /**
     * Records an alert the table has voted through (rules 27 to 30). Every third one
     * costs the offending team fifty.
     */
    fun upholdAlert(state: GameState, againstTeam: Int): GameState {
        val alerts = state.alerts.toMutableList()
        alerts[againstTeam] = alerts[againstTeam] + 1
        val count = alerts[againstTeam]

        var scores = state.scores
        val events = mutableListOf<GameEvent>()
        var penalty = 0
        if (count % state.rules.alertsPerPenalty == 0) {
            penalty = state.rules.alertPenalty
            scores = scores.toMutableList().also { it[againstTeam] = it[againstTeam] + penalty }
        }
        events += GameEvent.AlertUpheld(againstTeam, count, penalty)

        val loser = losingTeam(scores, state.rules.target)
        if (loser != null) events += GameEvent.MatchEnded(loser, scores)

        return state.copy(
            alerts = alerts,
            scores = scores,
            losingTeam = loser,
            phase = if (loser != null) Phase.MATCH_OVER else state.phase,
            // Only what this changed: the tiles laid before it were already reported.
            events = events,
        )
    }

    private const val TEERA_LIMIT = 1
    private const val FIVE_DOUBLES = 5
    private const val FOUR_DOUBLES = 4
    private const val SIX_OF_A_NUMBER = 6
}

private fun <T> List<List<T>>.replacing(index: Int, value: List<T>): List<List<T>> =
    mapIndexed { i, existing -> if (i == index) value else existing }
