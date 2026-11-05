package com.teera.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a phone and the room server say to each other.
 *
 * The server is the referee: a client asks to move, the server runs [Engine] and sends
 * back a [PlayerView]. Clients never see a [GameState], so a modified app cannot read
 * another hand or play a tile it does not hold.
 */
object Protocol {
    const val VERSION = 2

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

/** The room before the deal: who is sitting where, and what the table has agreed on. */
@Serializable
data class RoomInfo(
    val code: String,
    val hostId: String,
    val rules: Rules,
    val seats: List<Seat>,
    val ready: List<String> = emptyList(),
    val started: Boolean = false,
)

/**
 * One player's view of the table: their own hand, everyone's tile counts, and the moves
 * the referee will actually accept from them right now.
 */
@Serializable
data class PlayerView(
    val roomCode: String,
    val rules: Rules,
    val seats: List<Seat>,
    val yourSeat: Int,
    val hand: List<Domino>,
    /** Tile counts for every seat, indexed by seat. */
    val handCounts: List<Int>,
    val board: Board,
    val turn: Int,
    /** Who the table is waiting on, which is not always whose turn it is. */
    val actingSeat: Int?,
    val phase: Phase,
    /** Indexed by team: seats 0 and 2 are team 0, seats 1 and 3 are team 1. */
    val scores: List<Int>,
    val alerts: List<Int>,
    val gameNumber: Int,
    val mustOpenWith: Domino? = null,
    val stuckSeat: Int? = null,
    val rescuer: Int? = null,
    val teeraSeat: Int? = null,
    val legalMoves: List<Move> = emptyList(),
    val lastGame: GameResult? = null,
    val losingTeam: Int? = null,
    val events: List<GameEvent> = emptyList(),
    val turnDeadlineEpochMs: Long? = null,
) {
    val yourTeam: Int get() = teamOf(yourSeat)
    val isYourMove: Boolean get() = actingSeat == yourSeat
    fun seatInfo(seat: Int): Seat? = seats.firstOrNull { it.seat == seat }

    /** The spots in your hand, which is what a teera is judged on. */
    val handPips: Int get() = hand.pipTotal()
}

/** Builds the redacted view a seat is allowed to see. */
fun GameState.viewFor(seat: Int, roomCode: String, turnDeadlineEpochMs: Long? = null) = PlayerView(
    roomCode = roomCode,
    rules = rules,
    seats = seats,
    yourSeat = seat,
    hand = handOf(seat),
    handCounts = (0 until Pack.PLAYERS).map { handOf(it).size },
    board = board,
    turn = turn,
    actingSeat = actingSeat,
    phase = phase,
    scores = scores,
    alerts = alerts,
    gameNumber = gameNumber,
    mustOpenWith = mustOpenWith,
    stuckSeat = stuckSeat,
    rescuer = rescuer,
    teeraSeat = teeraSeat,
    legalMoves = Engine.legalMoves(this, seat),
    lastGame = lastGame,
    losingTeam = losingTeam,
    events = events,
    turnDeadlineEpochMs = turnDeadlineEpochMs,
)

@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("hello")
    data class Hello(
        val name: String,
        val playerId: String? = null,
        val version: Int = Protocol.VERSION,
        /** See [Seat.avatar]. */
        val avatar: String = "",
        /** The player's own picture, a small JPEG in base64, when [avatar] is "photo". */
        val photo: String? = null,
    ) : ClientMessage

    @Serializable @SerialName("createRoom")
    data class CreateRoom(val rules: Rules = Rules.DEFAULT) : ClientMessage

    @Serializable @SerialName("joinRoom")
    data class JoinRoom(val code: String) : ClientMessage

    @Serializable @SerialName("leaveRoom")
    data object LeaveRoom : ClientMessage

    /** Players arrange the teams themselves by choosing where to sit (rule 4). */
    @Serializable @SerialName("takeSeat")
    data class TakeSeat(val seat: Int) : ClientMessage

    /** Host only, and only before the deal. */
    @Serializable @SerialName("setRules")
    data class SetRules(val rules: Rules) : ClientMessage

    @Serializable @SerialName("setBot")
    data class SetBot(val seat: Int, val enabled: Boolean, val difficulty: Difficulty = Difficulty.NORMAL) : ClientMessage

    @Serializable @SerialName("ready")
    data class Ready(val ready: Boolean) : ClientMessage

    @Serializable @SerialName("startMatch")
    data object StartMatch : ClientMessage

    @Serializable @SerialName("move")
    data class MakeMove(val move: Move) : ClientMessage

    /** Deal the next game once everyone has seen the score. */
    @Serializable @SerialName("nextGame")
    data object NextGame : ClientMessage

    @Serializable @SerialName("chat")
    data class Chat(val text: String) : ClientMessage

    @Serializable @SerialName("ping")
    data object Ping : ClientMessage

    /** A button on the soundboard, heard by everybody at the table. */
    @Serializable @SerialName("emote")
    data class SendEmote(val emote: Emote) : ClientMessage
}

/**
 * The soundboard. Each one is a sound and, where it has one, a line said with it —
 * "ابلع" being what you tell somebody as you push a tile onto them.
 */
@Serializable
enum class Emote {
    GULP, DRUMROLL, SAD,
    /** "خذ": retired from the board, kept so an older phone's press still plays. */
    SLAP,
    /** "بالله ما طيغ!" */
    NO_WAY,
    /** "يلا شباب خلي نسولف" */
    CHAT,
    /** "شيل" */
    SHEEL,
    ;

    companion object {
        /** What the soundboard offers, in order. */
        val BOARD = listOf(GULP, NO_WAY, SHEEL, CHAT, DRUMROLL, SAD)
    }
}

@Serializable
enum class ErrorCode {
    ROOM_NOT_FOUND, ROOM_FULL, ROOM_ALREADY_STARTED, NOT_HOST, NOT_IN_ROOM,
    SEAT_TAKEN, INVALID_RULES, NOT_ENOUGH_PLAYERS, VERSION_MISMATCH, BAD_MESSAGE, INTERNAL,
}

@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("welcome")
    data class Welcome(val playerId: String, val version: Int = Protocol.VERSION) : ServerMessage

    @Serializable @SerialName("room")
    data class RoomUpdate(val room: RoomInfo) : ServerMessage

    @Serializable @SerialName("view")
    data class GameUpdate(val view: PlayerView) : ServerMessage

    @Serializable @SerialName("rejected")
    data class MoveRejected(val reason: Rejection, val view: PlayerView) : ServerMessage

    @Serializable @SerialName("error")
    data class Failure(val code: ErrorCode, val message: String) : ServerMessage

    @Serializable @SerialName("chat")
    data class ChatMessage(val seat: Int, val name: String, val text: String, val atEpochMs: Long) : ServerMessage

    @Serializable @SerialName("pong")
    data object Pong : ServerMessage

    @Serializable @SerialName("emote")
    data class EmoteShown(val seat: Int, val emote: Emote) : ServerMessage

    /** Somebody's own picture, sent once when either of you joins rather than with every view. */
    @Serializable @SerialName("avatar")
    data class Avatar(val playerId: String, val photo: String) : ServerMessage
}
