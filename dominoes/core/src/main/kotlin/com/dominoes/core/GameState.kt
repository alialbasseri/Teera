package com.dominoes.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Seat(
    val seat: Int,
    val playerId: String,
    val name: String,
    val isBot: Boolean = false,
    val connected: Boolean = true,
)

@Serializable
sealed interface Move {
    @Serializable
    @SerialName("play")
    data class Play(val domino: Domino, val end: End) : Move

    @Serializable
    @SerialName("draw")
    data object Draw : Move

    @Serializable
    @SerialName("pass")
    data object Pass : Move
}

@Serializable
enum class Phase { WAITING, IN_ROUND, ROUND_OVER, MATCH_OVER }

@Serializable
enum class RoundEndReason { DOMINO, BLOCKED }

@Serializable
data class RoundResult(
    val roundNumber: Int,
    val reason: RoundEndReason,
    /** Seat that took the round, or null when a blocked round scored nothing. */
    val winnerSeat: Int?,
    val scoreKey: Int?,
    val points: Int,
    /** Spots left in each hand, indexed by seat. */
    val handPips: List<Int>,
)

@Serializable
sealed interface GameEvent {
    @Serializable @SerialName("dealt")
    data class Dealt(val roundNumber: Int, val starterSeat: Int, val forced: Domino?) : GameEvent

    @Serializable @SerialName("played")
    data class Played(val seat: Int, val domino: Domino, val end: End) : GameEvent

    @Serializable @SerialName("drew")
    data class Drew(val seat: Int, val remaining: Int) : GameEvent

    @Serializable @SerialName("passed")
    data class Passed(val seat: Int) : GameEvent

    @Serializable @SerialName("pegged")
    data class Pegged(val seat: Int, val scoreKey: Int, val points: Int, val endTotal: Int) : GameEvent

    @Serializable @SerialName("roundEnded")
    data class RoundEnded(val result: RoundResult) : GameEvent

    @Serializable @SerialName("matchEnded")
    data class MatchEnded(val scoreKey: Int, val scores: List<Int>) : GameEvent
}

/**
 * The whole table. Every field is plain data so a state can be serialised, sent, replayed
 * or diffed; the engine never mutates one in place.
 */
@Serializable
data class GameState(
    val rules: RuleSet,
    val seats: List<Seat>,
    /** Indexed by seat. */
    val hands: List<List<Domino>> = emptyList(),
    val boneyard: List<Domino> = emptyList(),
    val board: Board = Board(),
    val turn: Int = 0,
    /** Indexed by score key: two entries in a partners game, one per seat otherwise. */
    val scores: List<Int> = emptyList(),
    val phase: Phase = Phase.WAITING,
    val roundNumber: Int = 0,
    val dealer: Int = 0,
    val consecutivePasses: Int = 0,
    val drawsThisTurn: Int = 0,
    /** Set when the opening rule forces a particular tile onto the table. */
    val forcedOpening: Domino? = null,
    val lastRound: RoundResult? = null,
    val matchWinner: Int? = null,
    val seed: Long = 0L,
    /** Produced by the move that created this state, for the client to animate. */
    val events: List<GameEvent> = emptyList(),
) {
    val boneyardDrawable: Int get() = (boneyard.size - rules.boneyardReserve).coerceAtLeast(0)

    fun handOf(seat: Int): List<Domino> = hands.getOrElse(seat) { emptyList() }

    fun seatOf(playerId: String): Int? = seats.firstOrNull { it.playerId == playerId }?.seat

    fun scoreOf(seat: Int): Int = scores.getOrElse(rules.scoreKeyOf(seat)) { 0 }
}
