package com.dominoes.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire protocol between a phone and the room server.
 *
 * The server is authoritative: a client asks to move, the server runs [Engine] and
 * broadcasts the result. Clients never see a full [GameState] — only [PlayerView],
 * which hides other people's hands and the order of the boneyard.
 */
object Protocol {
    const val VERSION = 1

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "t"
    }

    fun encode(message: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), message)
    fun encode(message: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), message)
    fun decodeClient(text: String): ClientMessage = json.decodeFromString(ClientMessage.serializer(), text)
    fun decodeServer(text: String): ServerMessage = json.decodeFromString(ServerMessage.serializer(), text)
}

/** A seat as the lobby shows it, before a match starts. */
@Serializable
data class RoomInfo(
    val code: String,
    val hostId: String,
    val rules: RuleSet,
    val seats: List<Seat>,
    val ready: List<String> = emptyList(),
    val started: Boolean = false,
)

/**
 * One player's redacted view of the table. Everything needed to draw a frame and
 * nothing that would let a modified client cheat.
 */
@Serializable
data class PlayerView(
    val roomCode: String,
    val rules: RuleSet,
    val seats: List<Seat>,
    val yourSeat: Int,
    val hand: List<Domino>,
    /** Tile counts for every seat, indexed by seat. */
    val handCounts: List<Int>,
    val board: Board,
    val turn: Int,
    val scores: List<Int>,
    val phase: Phase,
    val boneyardCount: Int,
    val roundNumber: Int,
    val forcedOpening: Domino? = null,
    val legalMoves: List<Move> = emptyList(),
    val lastRound: RoundResult? = null,
    val matchWinner: Int? = null,
    val events: List<GameEvent> = emptyList(),
    /** Wall-clock deadline for the seat on turn, or null when untimed. */
    val turnDeadlineEpochMs: Long? = null,
) {
    val isYourTurn: Boolean get() = turn == yourSeat && phase == Phase.IN_ROUND
    fun seatInfo(seat: Int): Seat? = seats.firstOrNull { it.seat == seat }
}

/** Builds the redacted view a given seat is allowed to see. */
fun GameState.viewFor(seat: Int, roomCode: String, turnDeadlineEpochMs: Long? = null) = PlayerView(
    roomCode = roomCode,
    rules = rules,
    seats = seats,
    yourSeat = seat,
    hand = handOf(seat),
    handCounts = (0 until rules.playerCount).map { handOf(it).size },
    board = board,
    turn = turn,
    scores = scores,
    phase = phase,
    boneyardCount = boneyard.size,
    roundNumber = roundNumber,
    forcedOpening = forcedOpening,
    legalMoves = Engine.legalMoves(this, seat),
    lastRound = lastRound,
    matchWinner = matchWinner,
    events = events,
    turnDeadlineEpochMs = turnDeadlineEpochMs,
)

@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("hello")
    data class Hello(val name: String, val playerId: String? = null, val version: Int = Protocol.VERSION) : ClientMessage

    @Serializable @SerialName("createRoom")
    data class CreateRoom(val rules: RuleSet = RuleSet.CLASSIC_DRAW) : ClientMessage

    @Serializable @SerialName("joinRoom")
    data class JoinRoom(val code: String) : ClientMessage

    @Serializable @SerialName("leaveRoom")
    data object LeaveRoom : ClientMessage

    /** Host only. Rejected once the match is running. */
    @Serializable @SerialName("setRules")
    data class SetRules(val rules: RuleSet) : ClientMessage

    /** Host only: drop a bot into an empty seat, or clear one out. */
    @Serializable @SerialName("setBot")
    data class SetBot(val seat: Int, val enabled: Boolean, val difficulty: Difficulty = Difficulty.NORMAL) : ClientMessage

    @Serializable @SerialName("ready")
    data class Ready(val ready: Boolean) : ClientMessage

    /** Host only. */
    @Serializable @SerialName("startMatch")
    data object StartMatch : ClientMessage

    @Serializable @SerialName("move")
    data class MakeMove(val move: Move) : ClientMessage

    /** Deal the next round once everyone has seen the scores. */
    @Serializable @SerialName("nextRound")
    data object NextRound : ClientMessage

    @Serializable @SerialName("chat")
    data class Chat(val text: String) : ClientMessage

    @Serializable @SerialName("ping")
    data object Ping : ClientMessage
}

@Serializable
enum class ErrorCode {
    ROOM_NOT_FOUND, ROOM_FULL, ROOM_ALREADY_STARTED, NOT_HOST, NOT_IN_ROOM,
    INVALID_RULES, NOT_ENOUGH_PLAYERS, VERSION_MISMATCH, BAD_MESSAGE, INTERNAL,
}

@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("welcome")
    data class Welcome(val playerId: String, val version: Int = Protocol.VERSION) : ServerMessage

    @Serializable @SerialName("room")
    data class RoomUpdate(val room: RoomInfo) : ServerMessage

    @Serializable @SerialName("view")
    data class GameUpdate(val view: PlayerView) : ServerMessage

    /** A move the server would not accept; the client should roll back its prediction. */
    @Serializable @SerialName("rejected")
    data class MoveRejected(val reason: Rejection, val view: PlayerView) : ServerMessage

    @Serializable @SerialName("error")
    data class Failure(val code: ErrorCode, val message: String) : ServerMessage

    @Serializable @SerialName("chat")
    data class ChatMessage(val seat: Int, val name: String, val text: String, val atEpochMs: Long) : ServerMessage

    @Serializable @SerialName("pong")
    data object Pong : ServerMessage
}
