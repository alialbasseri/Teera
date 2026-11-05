package com.teera.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Seat(
    val seat: Int,
    val playerId: String,
    val name: String,
    val isBot: Boolean = false,
    val connected: Boolean = true,
    /**
     * The player's picture: "p:3" for one of the app's drawn pictures, "photo" when they
     * chose their own (sent separately, see [ServerMessage.Avatar]), empty for none.
     */
    val avatar: String = "",
)

/** Everything a player can be asked to do. */
@Serializable
sealed interface Move {

    /** Lay a tile. [arm] is left out for the opening double, which goes in the middle. */
    @Serializable @SerialName("play")
    data class Play(val domino: Domino, val arm: Arm? = null) : Move

    /** Hand one of your own tiles to the player stuck after you (rule 15). */
    @Serializable @SerialName("give")
    data class Give(val domino: Domino) : Move

    /** Play one of your own tiles in place of the player stuck after you (rule 15). */
    @Serializable @SerialName("playFor")
    data class PlayFor(val domino: Domino, val arm: Arm) : Move

    /** End the game holding nothing heavier than a single spot (rule 20). */
    @Serializable @SerialName("teera")
    data object Teera : Move

    /** Turn down the teera and play on for the X (rule 21). */
    @Serializable @SerialName("carryOn")
    data object CarryOn : Move

    /** Take up the redeal a hand of four doubles is entitled to (rule 26). */
    @Serializable @SerialName("redeal")
    data object Redeal : Move

    @Serializable @SerialName("keepHand")
    data object KeepHand : Move
}

/** Who the table is waiting on, and what for. */
@Serializable
enum class Phase {
    /** Somebody with four doubles is deciding whether to redeal. */
    REDEAL_OFFER,

    /** Ordinary turn: the seat in [GameState.turn] is to play. */
    IN_PLAY,

    /** A seat is stuck, and the player before them must give or play for them. */
    RESCUE,

    /** A player just got light enough to declare a teera, and is deciding. */
    TEERA_OFFER,

    GAME_OVER,
    MATCH_OVER,
}

@Serializable
enum class GameEnd {
    /** Somebody emptied their hand: their team takes the X. */
    X,

    /** Somebody declared a teera: no X, and their own spots count against them. */
    TEERA,

    /** Nobody could play: nothing is scored and the game is dealt again. */
    BLOCKED,
}

@Serializable
data class GameResult(
    val gameNumber: Int,
    val end: GameEnd,
    /** Whoever finished or declared; null for a blocked game. */
    val winnerSeat: Int?,
    /** Spots left in each hand, indexed by seat. */
    val handPips: List<Int>,
    /** What each team took from this game, indexed by team. Can be negative. */
    val scored: List<Int>,
    /** Who opens the next game (rules 11 and 22). */
    val nextOpener: Int,
)

@Serializable
sealed interface GameEvent {
    @Serializable @SerialName("dealt")
    data class Dealt(val gameNumber: Int, val opener: Int, val mustOpenWith: Domino?) : GameEvent

    @Serializable @SerialName("redealt")
    data class Redealt(val reason: RedealReason, val seat: Int?) : GameEvent

    @Serializable @SerialName("opened")
    data class Opened(val seat: Int, val domino: Domino) : GameEvent

    @Serializable @SerialName("played")
    data class Played(val seat: Int, val domino: Domino, val arm: Arm) : GameEvent

    /** A tile changed hands because the next seat was stuck. */
    @Serializable @SerialName("gave")
    data class Gave(val from: Int, val to: Int) : GameEvent

    @Serializable @SerialName("playedFor")
    data class PlayedFor(val rescuer: Int, val stuck: Int, val domino: Domino, val arm: Arm) : GameEvent

    @Serializable @SerialName("stuck")
    data class Stuck(val seat: Int, val rescuer: Int) : GameEvent

    @Serializable @SerialName("teera")
    data class TeeraCalled(val seat: Int, val pips: Int) : GameEvent

    @Serializable @SerialName("alert")
    data class AlertUpheld(val againstTeam: Int, val count: Int, val penalty: Int) : GameEvent

    @Serializable @SerialName("gameEnded")
    data class GameEnded(val result: GameResult) : GameEvent

    @Serializable @SerialName("matchEnded")
    data class MatchEnded(val losingTeam: Int, val scores: List<Int>) : GameEvent
}

@Serializable
enum class RedealReason {
    /** Five doubles in one hand (rule 23). */
    FIVE_DOUBLES,

    /** Six tiles of the same number in one hand (rule 24). */
    SIX_OF_A_NUMBER,

    /** Neither opponent can answer the opening double (rule 25). */
    OPPONENTS_CANNOT_ANSWER,

    /** A hand of four doubles asked for it (rule 26). */
    FOUR_DOUBLES_BY_CHOICE,
}

/**
 * The whole table, as data.
 *
 * The engine never changes one of these in place: every move returns a new state, which
 * is what lets the server stay the referee and a client replay a game from its seed.
 */
@Serializable
data class GameState(
    val rules: Rules,
    val seats: List<Seat>,
    /** Indexed by seat. */
    val hands: List<List<Domino>> = emptyList(),
    val board: Board = Board(),
    val turn: Int = 0,
    /** Indexed by team. Reaching [Rules.target] loses. */
    val scores: List<Int> = listOf(0, 0),
    /** Alerts upheld against each team, running for the whole match (rule 30). */
    val alerts: List<Int> = listOf(0, 0),
    val phase: Phase = Phase.IN_PLAY,
    val gameNumber: Int = 0,
    /** Which attempt at dealing this game is, so a redeal shuffles differently. */
    val dealAttempt: Int = 0,
    /** Who opened this game, and what the rules force them to open with. */
    val opener: Int = 0,
    val mustOpenWith: Domino? = null,
    /** Last seat to actually lay a tile: they open the next game if this one locks up. */
    val lastToPlay: Int? = null,
    /** During [Phase.RESCUE]. */
    val stuckSeat: Int? = null,
    val rescuer: Int? = null,
    /** During [Phase.TEERA_OFFER]: who is deciding, and whose turn follows if they play on. */
    val teeraSeat: Int? = null,
    val turnAfterTeera: Int? = null,
    /** During [Phase.REDEAL_OFFER]: seats still to be asked, the first one being asked now. */
    val redealOffers: List<Int> = emptyList(),
    val lastGame: GameResult? = null,
    val losingTeam: Int? = null,
    val seed: Long = 0L,
    /** Produced by the move that created this state, for the client to animate. */
    val events: List<GameEvent> = emptyList(),
) {
    fun handOf(seat: Int): List<Domino> = hands.getOrElse(seat) { emptyList() }

    fun seatOf(playerId: String): Int? = seats.firstOrNull { it.playerId == playerId }?.seat

    fun scoreOfSeat(seat: Int): Int = scores.getOrElse(teamOf(seat)) { 0 }

    /** Whoever the table is waiting on right now, if anyone. */
    val actingSeat: Int?
        get() = when (phase) {
            Phase.IN_PLAY -> turn
            Phase.RESCUE -> rescuer
            Phase.TEERA_OFFER -> teeraSeat
            Phase.REDEAL_OFFER -> redealOffers.firstOrNull()
            Phase.GAME_OVER, Phase.MATCH_OVER -> null
        }

    val isOver: Boolean get() = phase == Phase.GAME_OVER || phase == Phase.MATCH_OVER
}
