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
    /** People watching rather than playing; their seat numbers start at 4. */
    val spectators: List<Seat> = emptyList(),
    /** A player let the table show in the list of games being played, for anyone to watch. */
    val listed: Boolean = false,
    /** Only people invited from inside, or already here, may come in. */
    val locked: Boolean = false,
    /** A tournament match: the two teams' names, for seats 0 and 2 and for seats 1 and 3. */
    val teamNames: List<String> = emptyList(),
    /** Which tournament and round, as the lobby says it. */
    val tournament: String? = null,
)

/** A game being played that its players let anyone watch, as the list shows it. */
@Serializable
data class LiveGame(
    val code: String,
    /** Names by seat: 0 and 2 against 1 and 3. */
    val names: List<String>,
    val avatars: List<String> = emptyList(),
    val scores: List<Int>,
    val target: Int,
    val gameNumber: Int,
    val watchers: Int,
    /** Played to this many games rather than to [target]; 0 when to the target. */
    val games: Int = 0,
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
    /** Games that ended in a score, for a match played to a number of games. */
    val gamesPlayed: Int = 0,
    val mustOpenWith: Domino? = null,
    val stuckSeat: Int? = null,
    val rescuer: Int? = null,
    val teeraSeat: Int? = null,
    val legalMoves: List<Move> = emptyList(),
    val lastGame: GameResult? = null,
    val losingTeam: Int? = null,
    val events: List<GameEvent> = emptyList(),
    val turnDeadlineEpochMs: Long? = null,
    /** A "ناخذ خاوة" being voted on right now, if any. */
    val alertVote: AlertVote? = null,
    /**
     * Watching, not playing: [yourSeat] is then the seat being watched from, and [hand]
     * is that player's only if they chose to show it.
     */
    val spectating: Boolean = false,
    val spectators: List<Seat> = emptyList(),
    /** Seats whose players let the watchers see their tiles. */
    val showingHands: List<Int> = emptyList(),
    /**
     * For a watcher: every hand whose player lets it be seen, indexed by seat (empty for
     * the rest), so they see all of them round the table and not only the one in front.
     */
    val revealedHands: List<List<Domino>> = emptyList(),
    /** A change of target being voted on right now, if any. */
    val targetVote: TargetVote? = null,
    /** Whether the table shows in the list of games being played. */
    val listed: Boolean = false,
) {
    /** A hand a watcher may see, at [seat]; empty when it is not shown. */
    fun revealedAt(seat: Int): List<Domino> = revealedHands.getOrElse(seat) { emptyList() }

    val yourTeam: Int get() = teamOf(yourSeat)
    val isYourMove: Boolean get() = !spectating && actingSeat == yourSeat
    fun seatInfo(seat: Int): Seat? = seats.firstOrNull { it.seat == seat }

    /** The spots in your hand, which is what a teera is judged on. */
    val handPips: Int get() = hand.pipTotal()
}

/**
 * "ناخذ خاوة": one seat says the other team broke the rules (a signal, a hint over the
 * mic) and the table votes. Seats, not ids, so every phone can draw it.
 */
@Serializable
data class AlertVote(
    val raisedBy: Int,
    val againstTeam: Int,
    /** Who gets a say: the people at the table, not the bots. */
    val voters: List<Int>,
    val yes: List<Int>,
    val no: List<Int>,
    val deadlineEpochMs: Long,
) {
    /** Passes on more than half of the voters agreeing, so a team cannot carry it alone. */
    val needed: Int get() = voters.size / 2 + 1
}

/**
 * Changing what the match is played to, mid-match (201 to 251, say). Every person at the
 * table has to agree: it changes the game for all of them.
 */
@Serializable
data class TargetVote(
    val proposedBy: Int,
    val target: Int,
    val voters: List<Int>,
    val yes: List<Int>,
    val no: List<Int>,
    val deadlineEpochMs: Long,
)

/** Scores a match can be played to. */
val TARGET_CHOICES = listOf(101, 151, 201, 251, 301, 351, 401)

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
    gamesPlayed = gamesPlayed,
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
        /** The app's own build number, so the server can say when a newer one is out. 0 from apps before 1.4.1. */
        val appVersion: Int = 0,
    ) : ClientMessage

    @Serializable @SerialName("createRoom")
    data class CreateRoom(val rules: Rules = Rules.DEFAULT) : ClientMessage

    @Serializable @SerialName("joinRoom")
    data class JoinRoom(
        val code: String,
        /**
         * Coming back to a room this player was in, or arriving by its link: if the server
         * has lost it (a restart), it is opened again under the same code, so everyone
         * following the same link still ends up at the same table.
         */
        val rejoin: Boolean = false,
    ) : ClientMessage

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

    /** A player lets the watchers see their tiles, or stops. */
    @Serializable @SerialName("showHand")
    data class ShowHand(val shown: Boolean) : ClientMessage

    /** A watcher moves to another player's shoulder. */
    @Serializable @SerialName("watch")
    data class Watch(val seat: Int) : ClientMessage

    /** "ناخذ خاوة" on the other team. */
    @Serializable @SerialName("raiseAlert")
    data object RaiseAlert : ClientMessage

    @Serializable @SerialName("voteAlert")
    data class VoteAlert(val yes: Boolean) : ClientMessage

    /** Which of these players have the app open right now. */
    @Serializable @SerialName("whoIsOnline")
    data class WhoIsOnline(val playerIds: List<String>) : ClientMessage

    /** Looks a player up by the short code on their profile, to add them to your people. */
    @Serializable @SerialName("findPlayer")
    data class FindPlayer(val shortCode: String) : ClientMessage

    /** Asks a player, wherever they are in the app, to come to a room. */
    @Serializable @SerialName("invite")
    data class Invite(val playerId: String, val code: String) : ClientMessage

    /** Asks the table to play the match to [target] instead. */
    @Serializable @SerialName("proposeTarget")
    data class ProposeTarget(val target: Int) : ClientMessage

    @Serializable @SerialName("voteTarget")
    data class VoteTarget(val yes: Boolean) : ClientMessage

    /** This phone just took a screenshot of the table; everyone else is told. */
    @Serializable @SerialName("screenCaptured")
    data object ScreenCaptured : ClientMessage

    /** A watcher plays a dropped player's (or a bot's) chair; null gives it back. */
    @Serializable @SerialName("standIn")
    data class StandIn(val seat: Int?) : ClientMessage

    /** The host sends somebody out of the room, for good. */
    @Serializable @SerialName("kick")
    data class Kick(val playerId: String) : ClientMessage

    /** The host closes the room to everybody not invited from inside it, or opens it again. */
    @Serializable @SerialName("setLocked")
    data class SetLocked(val locked: Boolean) : ClientMessage

    /** Lets anyone watch this table from the list of games being played, or stops it. */
    @Serializable @SerialName("setListed")
    data class SetListed(val listed: Boolean) : ClientMessage

    /** This phone's address for notifications with the app closed. */
    @Serializable @SerialName("pushToken")
    data class PushToken(val token: String) : ClientMessage

    /** The app went out of sight (or came back), so the table may need to call them back. */
    @Serializable @SerialName("away")
    data class Away(val away: Boolean) : ClientMessage

    /** A knockout of [teams] (4, 8 or 16 pairs, by name), drawn in the order given. */
    @Serializable @SerialName("createTournament")
    data class CreateTournament(val name: String, val teams: List<String>) : ClientMessage

    @Serializable @SerialName("tournament")
    data class AskTournament(val id: String) : ClientMessage

    /** Opens (or joins) the room a tournament match is played in. */
    @Serializable @SerialName("playTournamentMatch")
    data class PlayTournamentMatch(val id: String, val round: Int, val index: Int) : ClientMessage

    /** The organiser settles a match by hand. */
    @Serializable @SerialName("tournamentWinner")
    data class SetTournamentWinner(val id: String, val round: Int, val index: Int, val team: Int) : ClientMessage

    /** The ranking. */
    @Serializable @SerialName("leaderboard")
    data object AskLeaderboard : ClientMessage

    /** One match from the books, game by game. */
    @Serializable @SerialName("match")
    data class AskMatch(val matchId: String) : ClientMessage

    /** The games being played that anyone may watch. */
    @Serializable @SerialName("liveGames")
    data object ListLiveGames : ClientMessage

    /** This player's record from the server's books: totals and their latest matches. */
    @Serializable @SerialName("myRecord")
    data object MyRecord : ClientMessage
}

/** A player's short code: what friends type to add them. Six letters and digits of their id. */
fun shortCodeOf(playerId: String): String =
    playerId.uppercase().filter { it.isLetterOrDigit() }.take(6)

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
    /** "سبلوش!" */
    SPLOOSH,
    /** "والله دنيييكم" */
    WALLAH,
    ;

    companion object {
        /** What the soundboard offers, in order. */
        val BOARD = listOf(GULP, NO_WAY, SHEEL, SPLOOSH, WALLAH, CHAT, DRUMROLL, SAD)
    }
}

@Serializable
enum class ErrorCode {
    ROOM_NOT_FOUND, ROOM_FULL, ROOM_ALREADY_STARTED, NOT_HOST, NOT_IN_ROOM,
    SEAT_TAKEN, INVALID_RULES, NOT_ENOUGH_PLAYERS, VERSION_MISMATCH, BAD_MESSAGE, INTERNAL,
    /** The room is locked to people invited from inside it. */
    ROOM_LOCKED,
    /** The host sent this player out of the room. */
    KICKED,
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

    /** The players asked about in [ClientMessage.WhoIsOnline] who have the app open. */
    @Serializable @SerialName("online")
    data class Online(val playerIds: List<String>) : ServerMessage

    /** Who a short code belongs to; null [playerId] if nobody with it is online. */
    @Serializable @SerialName("found")
    data class PlayerFound(val shortCode: String, val playerId: String? = null, val name: String = "", val avatar: String = "") : ServerMessage

    /** Somebody wants you at their table. */
    @Serializable @SerialName("invited")
    data class Invited(val fromId: String, val fromName: String, val fromAvatar: String, val code: String) : ServerMessage

    /** How a "ناخذ خاوة" vote ended. [count] is the team's total after it. */
    @Serializable @SerialName("alertResult")
    data class AlertResult(val againstTeam: Int, val upheld: Boolean, val yes: Int, val needed: Int, val count: Int, val penalty: Int) : ServerMessage

    /** Whether an invite reached anyone: false when they do not have the app open. */
    @Serializable @SerialName("inviteSent")
    data class InviteSent(val playerId: String, val delivered: Boolean, val pushed: Boolean = false) : ServerMessage

    /** Somebody's own picture, sent once when either of you joins rather than with every view. */
    @Serializable @SerialName("avatar")
    data class Avatar(val playerId: String, val photo: String) : ServerMessage

    /** Somebody at the table took a screenshot: [seat] as their voice and chat carry it. */
    @Serializable @SerialName("screenshot")
    data class ScreenshotTaken(val seat: Int, val name: String) : ServerMessage

    /**
     * A newer app is out: [url] downloads it. [required] when this one is too old to keep
     * playing with the rest.
     */
    @Serializable @SerialName("update")
    data class UpdateAvailable(val versionCode: Int, val versionName: String, val url: String, val required: Boolean = false) : ServerMessage

    @Serializable @SerialName("liveGames")
    data class LiveGames(val games: List<LiveGame>) : ServerMessage

    /** A tournament as it stands; null if there is no such tournament. */
    @Serializable @SerialName("tournament")
    data class TournamentIs(val id: String, val tournament: Tournament?) : ServerMessage

    /** The ranking; null where the server keeps no books. */
    @Serializable @SerialName("leaderboard")
    data class LeaderboardIs(val board: Leaderboard?) : ServerMessage

    /** A match from the books; null if there is no such match or no books. */
    @Serializable @SerialName("match")
    data class MatchIs(val matchId: String, val detail: MatchDetail?) : ServerMessage

    /** How a vote to change the target went. */
    @Serializable @SerialName("targetResult")
    data class TargetResult(val target: Int, val passed: Boolean) : ServerMessage

    /** The answer to [ClientMessage.MyRecord]; null where the server keeps no books. */
    @Serializable @SerialName("record")
    data class Record(val record: PlayerRecord?) : ServerMessage
}

/** What the server's books say about one player. */
@Serializable
data class PlayerRecord(
    /** Matches played to the end, and how they went. */
    val matches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    /** Games finished in all, and how many this player ended themselves. */
    val games: Int = 0,
    val finishes: Int = 0,
    val teeras: Int = 0,
    val firstSeenEpochMs: Long? = null,
    /** Their latest matches, newest first. */
    val recent: List<MatchSummary> = emptyList(),
    /** Their place in the ranking, once they have a ranked match. */
    val rating: Int? = null,
    val rank: Int? = null,
    /** Badges earned (first win, five teeras…), and titles held (ملك الطيرة…). */
    val badges: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
)

/**
 * The ranking. A player's rating starts at [Ranking.START] and moves after each match
 * with people on both sides: more for beating a stronger pair, less for a weaker one.
 */
@Serializable
data class Leaderboard(
    val players: List<RankedPlayer>,
    val pairs: List<RankedPair>,
    val titles: List<TitleHolder> = emptyList(),
)

@Serializable
data class RankedPlayer(
    val playerId: String,
    val name: String,
    val avatar: String,
    val rating: Int,
    val matches: Int,
    val wins: Int,
)

/** Two partners and how they have done together. */
@Serializable
data class RankedPair(
    val names: List<String>,
    val avatars: List<String>,
    val matches: Int,
    val wins: Int,
)

/** One match from the books, game by game, each with its steps. */
@Serializable
data class MatchDetail(
    val id: String,
    val roomCode: String,
    val startedAtEpochMs: Long,
    /** By seat: 0 and 2 against 1 and 3. */
    val names: List<String>,
    val scores: List<Int>,
    val target: Int,
    /** Null while it is being played, or if it was left unfinished. */
    val losingTeam: Int?,
    val games: List<PlayedGame>,
)

@Serializable
data class PlayedGame(
    val gameNumber: Int,
    val end: GameEnd,
    val winnerSeat: Int?,
    /** What each team took from this game, and where the match stood after it. */
    val scored: List<Int>,
    val scores: List<Int>,
    val steps: List<GameEvent>,
)

/** One match as this player saw it. */
@Serializable
data class MatchSummary(
    val startedAtEpochMs: Long,
    val roomCode: String,
    /** Null while it is still being played, or if it was left unfinished. */
    val won: Boolean?,
    val partner: String,
    val opponents: List<String>,
    val ourScore: Int,
    val theirScore: Int,
    val games: Int,
    /** For opening it game by game. */
    val id: String = "",
    /** Which chair this player had in it. */
    val seat: Int = 0,
)
