package com.teera.core

import kotlin.random.Random

/** Why a move was refused. Surfaced to the client so it can explain itself. */
@kotlinx.serialization.Serializable
enum class Rejection {
    NOT_YOUR_TURN,
    ROUND_NOT_RUNNING,
    TILE_NOT_IN_HAND,
    TILE_DOES_NOT_MATCH,
    MUST_PLAY_OPENING_TILE,
    MUST_PLAY_IF_ABLE,
    MUST_DRAW_FIRST,
    BONEYARD_EMPTY,
    DRAW_LIMIT_REACHED,
}

sealed interface MoveOutcome {
    data class Ok(val state: GameState) : MoveOutcome
    data class Rejected(val reason: Rejection) : MoveOutcome
}

/**
 * The referee. Pure functions over [GameState]: same state plus same move always gives
 * the same result, which is what lets the server stay authoritative and the client
 * predict moves locally without the two drifting apart.
 */
object Engine {

    fun newMatch(rules: RuleSet, seats: List<Seat>, seed: Long = Random.nextLong()): GameState {
        val problems = rules.validate()
        require(problems.isEmpty()) { "invalid rules: ${problems.joinToString("; ")}" }
        require(seats.size == rules.playerCount) {
            "expected ${rules.playerCount} seats, got ${seats.size}"
        }
        val state = GameState(
            rules = rules,
            seats = seats.sortedBy { it.seat },
            scores = List(rules.teamCount) { 0 },
            phase = Phase.WAITING,
            seed = seed,
            dealer = 0,
        )
        return startRound(state)
    }

    /** Shuffle, deal, and work out who leads. */
    fun startRound(state: GameState): GameState {
        val rules = state.rules
        val roundNumber = state.roundNumber + 1
        val rng = Random(state.seed * 31 + roundNumber)
        val pack = rules.set.tiles().shuffled(rng)

        val hands = (0 until rules.playerCount).map { seat ->
            pack.subList(seat * rules.handSize, (seat + 1) * rules.handSize).sortedDescending()
        }
        val boneyard = pack.drop(rules.playerCount * rules.handSize)

        val (starter, forced) = decideOpening(state, hands, roundNumber)

        return state.copy(
            hands = hands,
            boneyard = boneyard,
            board = Board(),
            turn = starter,
            phase = Phase.IN_ROUND,
            roundNumber = roundNumber,
            consecutivePasses = 0,
            drawsThisTurn = 0,
            forcedOpening = forced,
            events = listOf(GameEvent.Dealt(roundNumber, starter, forced)),
        )
    }

    /** Returns the seat that leads and, when the rules force it, the tile they must lead. */
    private fun decideOpening(
        state: GameState,
        hands: List<List<Domino>>,
        roundNumber: Int,
    ): Pair<Int, Domino?> {
        val rules = state.rules
        val previous = state.lastRound

        if (roundNumber > 1) {
            when (rules.subsequentOpening) {
                SubsequentOpening.PREVIOUS_WINNER ->
                    previous?.winnerSeat?.let { return it to null }
                SubsequentOpening.PREVIOUS_LOSER ->
                    previous?.winnerSeat?.let { winner ->
                        val loser = previous.handPips.withIndex()
                            .filter { it.index != winner }
                            .maxByOrNull { it.value }?.index
                        if (loser != null) return loser to null
                    }
                SubsequentOpening.ROTATE_LEFT ->
                    return (state.dealer + roundNumber) % rules.playerCount to null
                SubsequentOpening.SAME_AS_FIRST -> Unit // fall through and recompute
            }
        }

        fun holderOf(tile: Domino): Int? = hands.indexOfFirst { tile in it }.takeIf { it >= 0 }
        fun heaviest(): Pair<Int, Domino> {
            var best = 0 to hands[0].max()
            hands.forEachIndexed { seat, hand ->
                val top = hand.max()
                if (top > best.second) best = seat to top
            }
            return best
        }
        fun highestDouble(): Pair<Int, Domino>? {
            var best: Pair<Int, Domino>? = null
            hands.forEachIndexed { seat, hand ->
                hand.filter { it.isDouble }.maxOrNull()?.let { top ->
                    if (best == null || top > best!!.second) best = seat to top
                }
            }
            return best
        }

        return when (rules.opening) {
            OpeningRule.HIGHEST_DOUBLE -> highestDouble() ?: heaviest()
            OpeningRule.HEAVIEST_TILE -> heaviest()
            OpeningRule.FIXED_TILE ->
                holderOf(rules.openingTile)?.let { it to rules.openingTile }
                    ?: (highestDouble() ?: heaviest())
            OpeningRule.DEALER_LEFT_ANY ->
                (state.dealer + 1) % rules.playerCount to null
        }
    }

    // -- move generation ----------------------------------------------------

    fun legalMoves(state: GameState, seat: Int): List<Move> {
        if (state.phase != Phase.IN_ROUND || state.turn != seat) return emptyList()
        val hand = state.handOf(seat)

        val plays = if (state.board.isEmpty) {
            val openers = state.forcedOpening?.let { forced -> hand.filter { it == forced } } ?: hand
            openers.map { Move.Play(it, End.RIGHT) }
        } else {
            hand.flatMap { tile ->
                End.entries.filter { state.board.accepts(tile, it, state.rules) }
                    .map { Move.Play(tile, it) }
            }
        }
        if (plays.isNotEmpty()) return plays

        return buildList {
            if (canDraw(state)) add(Move.Draw)
            if (canPass(state)) add(Move.Pass)
        }
    }

    private fun canDraw(state: GameState): Boolean {
        val rules = state.rules
        if (!rules.boneyardEnabled || state.boneyardDrawable <= 0) return false
        if (rules.mustDrawUntilPlayable) return true
        return rules.maxDrawsPerTurn == 0 || state.drawsThisTurn < rules.maxDrawsPerTurn
    }

    /** A seat may only give up once the rules stop insisting they draw. */
    private fun canPass(state: GameState): Boolean =
        !(state.rules.mustDrawUntilPlayable && canDraw(state))

    // -- move application ---------------------------------------------------

    fun apply(state: GameState, seat: Int, move: Move): MoveOutcome {
        if (state.phase != Phase.IN_ROUND) return MoveOutcome.Rejected(Rejection.ROUND_NOT_RUNNING)
        if (state.turn != seat) return MoveOutcome.Rejected(Rejection.NOT_YOUR_TURN)

        return when (move) {
            is Move.Play -> applyPlay(state, seat, move)
            Move.Draw -> applyDraw(state, seat)
            Move.Pass -> applyPass(state, seat)
        }
    }

    private fun applyPlay(state: GameState, seat: Int, move: Move.Play): MoveOutcome {
        val hand = state.handOf(seat)
        if (move.domino !in hand) return MoveOutcome.Rejected(Rejection.TILE_NOT_IN_HAND)
        if (state.board.isEmpty) {
            val forced = state.forcedOpening
            if (forced != null && move.domino != forced) {
                return MoveOutcome.Rejected(Rejection.MUST_PLAY_OPENING_TILE)
            }
        } else if (!state.board.accepts(move.domino, move.end, state.rules)) {
            return MoveOutcome.Rejected(Rejection.TILE_DOES_NOT_MATCH)
        }

        val board = state.board.place(move.domino, move.end, state.rules)
        val hands = state.hands.replacing(seat, hand.minusOne(move.domino))
        val events = mutableListOf<GameEvent>(GameEvent.Played(seat, move.domino, move.end))

        var scores = state.scores
        if (state.rules.scoring == ScoringMode.PEG_ON_ENDS) {
            val total = board.openEndTotal(state.rules)
            if (total > 0 && total % state.rules.pegStep == 0) {
                val points = if (state.rules.pegDividedByStep) total / state.rules.pegStep else total
                val key = state.rules.scoreKeyOf(seat)
                scores = scores.adding(key, points)
                events += GameEvent.Pegged(seat, key, points, total)
            }
        }

        val next = state.copy(
            board = board,
            hands = hands,
            scores = scores,
            consecutivePasses = 0,
            drawsThisTurn = 0,
            forcedOpening = null,
            events = events,
        )

        if (hands[seat].isEmpty()) {
            return MoveOutcome.Ok(endRound(next, RoundEndReason.DOMINO, seat, move.domino))
        }
        return MoveOutcome.Ok(next.copy(turn = nextSeat(next, seat)))
    }

    private fun applyDraw(state: GameState, seat: Int): MoveOutcome {
        if (legalMoves(state, seat).any { it is Move.Play }) {
            return MoveOutcome.Rejected(Rejection.MUST_PLAY_IF_ABLE)
        }
        if (state.boneyardDrawable <= 0 || !state.rules.boneyardEnabled) {
            return MoveOutcome.Rejected(Rejection.BONEYARD_EMPTY)
        }
        if (!canDraw(state)) return MoveOutcome.Rejected(Rejection.DRAW_LIMIT_REACHED)

        val tile = state.boneyard.first()
        val hands = state.hands.replacing(seat, (state.handOf(seat) + tile).sortedDescending())
        val boneyard = state.boneyard.drop(1)
        return MoveOutcome.Ok(
            state.copy(
                hands = hands,
                boneyard = boneyard,
                drawsThisTurn = state.drawsThisTurn + 1,
                events = listOf(GameEvent.Drew(seat, boneyard.size)),
            )
        )
    }

    private fun applyPass(state: GameState, seat: Int): MoveOutcome {
        if (legalMoves(state, seat).any { it is Move.Play }) {
            return MoveOutcome.Rejected(Rejection.MUST_PLAY_IF_ABLE)
        }
        if (!canPass(state)) return MoveOutcome.Rejected(Rejection.MUST_DRAW_FIRST)

        val passes = state.consecutivePasses + 1
        val next = state.copy(
            consecutivePasses = passes,
            drawsThisTurn = 0,
            events = listOf(GameEvent.Passed(seat)),
        )
        if (passes >= state.rules.playerCount) {
            return MoveOutcome.Ok(endRound(next, RoundEndReason.BLOCKED, null, null))
        }
        return MoveOutcome.Ok(next.copy(turn = nextSeat(next, seat)))
    }

    private fun nextSeat(state: GameState, from: Int): Int =
        (from + 1) % state.rules.playerCount

    // -- round and match scoring -------------------------------------------

    private fun endRound(
        state: GameState,
        reason: RoundEndReason,
        winner: Int?,
        goingOutTile: Domino?,
    ): GameState {
        val rules = state.rules
        val pips = (0 until rules.playerCount).map { state.handOf(it).pipTotal() }

        val takerSeat: Int? = winner ?: lightestSeat(state, pips)
        var points = 0
        if (takerSeat != null) {
            val opponents = opposingSeats(rules, takerSeat)
            val opponentPips = opponents.sumOf { pips[it] }
            points = when {
                reason == RoundEndReason.DOMINO -> opponentPips
                rules.blocked == BlockedResolution.LOWEST_PIPS_TAKES_ALL -> opponentPips
                rules.blocked == BlockedResolution.LOWEST_PIPS_TAKES_DIFFERENCE ->
                    (opponentPips - teamPips(rules, takerSeat, pips)).coerceAtLeast(0)
                else -> 0
            }
            if (rules.roundPipCountToStep) points = roundToStep(points, rules.pegStep)
            if (reason == RoundEndReason.DOMINO && goingOutTile?.isDouble == true) {
                points += rules.bonusForGoingOutOnDouble
            }
        }

        val scoreKey = takerSeat?.let { rules.scoreKeyOf(it) }
        var scores = state.scores
        if (scoreKey != null && points > 0) {
            val updated = scores[scoreKey] + points
            val overshoots = rules.winMustBeExact &&
                rules.matchFormat == MatchFormat.FIRST_TO_TARGET &&
                updated > rules.targetScore
            if (!overshoots) scores = scores.adding(scoreKey, points)
        }

        val result = RoundResult(
            roundNumber = state.roundNumber,
            reason = reason,
            winnerSeat = takerSeat,
            scoreKey = scoreKey,
            points = points,
            handPips = pips,
        )

        val matchWinner = matchWinner(rules, scores, state.roundNumber)
        val events = state.events + GameEvent.RoundEnded(result) +
            listOfNotNull(matchWinner?.let { GameEvent.MatchEnded(it, scores) })

        return state.copy(
            scores = scores,
            phase = if (matchWinner != null) Phase.MATCH_OVER else Phase.ROUND_OVER,
            lastRound = result,
            matchWinner = matchWinner,
            dealer = (state.dealer + 1) % rules.playerCount,
            events = events,
        )
    }

    /** Lightest hand takes a blocked round; a dead heat scores nothing. */
    private fun lightestSeat(state: GameState, pips: List<Int>): Int? {
        val rules = state.rules
        if (rules.blocked == BlockedResolution.NO_SCORE) return null

        val byKey = (0 until rules.teamCount).associateWith { key ->
            rules.seatsFor(key).sumOf { pips[it] }
        }
        val best = byKey.minOf { it.value }
        val winners = byKey.filterValues { it == best }.keys
        if (winners.size > 1) return null
        // Credit the lightest hand within the winning team, so "who went out" reads sensibly.
        return rules.seatsFor(winners.first()).minByOrNull { pips[it] }
    }

    private fun opposingSeats(rules: RuleSet, seat: Int): List<Int> {
        val key = rules.scoreKeyOf(seat)
        return (0 until rules.playerCount).filter {
            if (rules.partners && rules.opposingTeamPipsOnly) rules.scoreKeyOf(it) != key
            else it != seat
        }
    }

    private fun teamPips(rules: RuleSet, seat: Int, pips: List<Int>): Int =
        rules.seatsFor(rules.scoreKeyOf(seat)).sumOf { pips[it] }

    private fun matchWinner(rules: RuleSet, scores: List<Int>, roundNumber: Int): Int? = when (rules.matchFormat) {
        MatchFormat.FIRST_TO_TARGET -> {
            val leader = scores.withIndex().maxByOrNull { it.value }
            val hit = if (rules.winMustBeExact) leader?.value == rules.targetScore
            else (leader?.value ?: 0) >= rules.targetScore
            if (hit && scores.count { it == leader!!.value } == 1) leader!!.index else null
        }
        MatchFormat.FIXED_ROUNDS -> {
            if (roundNumber < rules.rounds) null
            else {
                val leader = scores.withIndex().maxByOrNull { it.value }
                if (leader != null && scores.count { it == leader.value } == 1) leader.index else null
            }
        }
    }

    private fun roundToStep(value: Int, step: Int): Int {
        if (step <= 1) return value
        val remainder = value % step
        return if (remainder * 2 >= step) value + (step - remainder) else value - remainder
    }
}

// -- small list helpers, kept private to the engine's own use ---------------

private fun <T> List<List<T>>.replacing(index: Int, value: List<T>): List<List<T>> =
    mapIndexed { i, existing -> if (i == index) value else existing }

private fun List<Int>.adding(index: Int, delta: Int): List<Int> =
    mapIndexed { i, value -> if (i == index) value + delta else value }

/** Removes a single copy; a hand never holds duplicates, but this keeps intent obvious. */
private fun List<Domino>.minusOne(tile: Domino): List<Domino> {
    val at = indexOf(tile)
    return if (at < 0) this else filterIndexed { i, _ -> i != at }
}
