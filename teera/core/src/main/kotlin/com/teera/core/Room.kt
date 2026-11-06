package com.teera.core

/**
 * Everything a room does, with no transport attached.
 *
 * The websocket server owns one of these per room, and the app owns one when hosting an
 * offline table against bots, so a solo game and an online game run through byte for
 * byte the same rules.
 */
class Room(
    val code: String,
    var hostId: String,
    rules: Rules = Rules.DEFAULT,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What the caller should put on the wire after handling an inbound message. */
    data class Output(
        val direct: List<Pair<String, ServerMessage>> = emptyList(),
        val roomChanged: Boolean = false,
        val viewChanged: Boolean = false,
    ) {
        operator fun plus(other: Output) = Output(
            direct + other.direct,
            roomChanged || other.roomChanged,
            viewChanged || other.viewChanged,
        )

        companion object {
            val NOTHING = Output()
            val ROOM = Output(roomChanged = true)
            val VIEW = Output(viewChanged = true)
            fun error(playerId: String, code: ErrorCode, message: String) =
                Output(direct = listOf(playerId to ServerMessage.Failure(code, message)))
        }
    }

    private class Member(
        val playerId: String,
        var name: String,
        var seat: Int,
        var connected: Boolean = true,
        var ready: Boolean = false,
        /** When the connection dropped, so a bot only steps in once they are really gone. */
        var goneSince: Long? = null,
        var avatar: String = "",
    )

    var rules: Rules = rules
        private set

    private val members = LinkedHashMap<String, Member>()
    private val bots = HashMap<Int, Difficulty>()

    var game: GameState? = null
        private set
    private var turnDeadline: Long? = null

    val isStarted: Boolean get() = game != null
    val isEmpty: Boolean get() = members.values.none { it.connected }

    // -- the room ----------------------------------------------------------

    fun info(): RoomInfo = RoomInfo(
        code = code,
        hostId = hostId,
        rules = rules,
        seats = seatList(),
        ready = members.values.filter { it.ready }.map { it.playerId },
        started = isStarted,
        spectators = spectatorSeats(),
    )

    // -- watchers ---------------------------------------------------------------

    private class Watcher(val playerId: String, var name: String, var avatar: String, var watching: Int = 0)

    /** People watching, in the order they came; their voice and chat carry seats 4 and up. */
    private val watchers = LinkedHashMap<String, Watcher>()

    private fun spectatorSeats(): List<Seat> = watchers.values.mapIndexed { index, w ->
        Seat(WATCHER_SEAT_BASE + index, w.playerId, w.name, avatar = w.avatar)
    }

    fun isWatching(playerId: String): Boolean = watchers.containsKey(playerId)

    /** The seat number a player's voice and chat go out under: theirs, or a watcher's. */
    fun voiceSeatOf(playerId: String): Int? =
        seatOf(playerId) ?: watchers.keys.indexOf(playerId).takeIf { it >= 0 }?.let { WATCHER_SEAT_BASE + it }

    private val showingHands = HashSet<String>()

    fun showHand(playerId: String, shown: Boolean): Output {
        if (seatOf(playerId) == null) return Output.NOTHING
        if (shown) showingHands += playerId else showingHands -= playerId
        return Output.VIEW
    }

    fun watch(playerId: String, seat: Int): Output {
        val watcher = watchers[playerId] ?: return Output.NOTHING
        if (seat !in 0 until Pack.PLAYERS) return Output.NOTHING
        watcher.watching = seat
        return Output.VIEW
    }

    private fun seatList(): List<Seat> = (0 until Pack.PLAYERS).map { seat ->
        val member = members.values.firstOrNull { it.seat == seat }
        when {
            member != null -> Seat(seat, member.playerId, member.name, isBot = false, connected = member.connected, avatar = member.avatar)
            bots.containsKey(seat) -> Seat(seat, "bot-$seat", "Bot ${seat + 1}", isBot = true)
            else -> Seat(seat, "", "", isBot = false, connected = false)
        }
    }

    /** Teera needs all four seats filled, by people or bots (rule 1). */
    fun isFull(): Boolean = (0 until Pack.PLAYERS).all { seat ->
        members.values.any { it.seat == seat } || bots.containsKey(seat)
    }

    fun seatOf(playerId: String): Int? = members[playerId]?.seat

    /** Everybody who hears the table: the players with a connection, and the watchers. */
    fun humanIds(): List<String> = members.values.filter { it.connected }.map { it.playerId } + watchers.keys

    fun join(playerId: String, name: String, avatar: String = ""): Output {
        members[playerId]?.let { existing ->
            existing.connected = true
            existing.goneSince = null
            existing.name = name
            existing.avatar = avatar
            return Output(roomChanged = true, viewChanged = isStarted)
        }
        watchers[playerId]?.let { existing ->
            existing.name = name
            existing.avatar = avatar
            return Output(roomChanged = true, viewChanged = isStarted)
        }
        // A full table, or one already playing: come in and watch rather than be turned away.
        val seat = freeSeat()
        if (isStarted || seat == null) {
            watchers[playerId] = Watcher(playerId, name, avatar, watching = firstShowingSeat() ?: 0)
            return Output(roomChanged = true, viewChanged = isStarted)
        }
        members[playerId] = Member(playerId, name, seat, avatar = avatar)
        return Output.ROOM
    }

    private fun freeSeat(): Int? = (0 until Pack.PLAYERS).firstOrNull { seat ->
        members.values.none { it.seat == seat } && !bots.containsKey(seat)
    }

    /**
     * Rule 4: the players arrange the teams between themselves by choosing seats, and
     * may only rearrange once a match is over.
     */
    fun takeSeat(playerId: String, seat: Int): Output {
        // A watcher sits down in a chair that has come free, before the deal.
        if (watchers.containsKey(playerId) && !isStarted && seat in 0 until Pack.PLAYERS &&
            members.values.none { it.seat == seat }
        ) {
            val watcher = watchers.remove(playerId)!!
            bots.remove(seat)
            members[playerId] = Member(playerId, watcher.name, seat, avatar = watcher.avatar)
            return Output.ROOM
        }
        val member = members[playerId] ?: return Output.error(playerId, ErrorCode.NOT_IN_ROOM, "You are not in this room")
        if (isStarted) return Output.error(playerId, ErrorCode.ROOM_ALREADY_STARTED, "Seats are fixed until the match ends")
        if (seat !in 0 until Pack.PLAYERS) return Output.NOTHING
        if (member.seat == seat) return Output.NOTHING
        if (members.values.any { it.seat == seat }) {
            return Output.error(playerId, ErrorCode.SEAT_TAKEN, "Somebody is already sitting there")
        }
        bots.remove(seat)
        member.seat = seat
        return Output.ROOM
    }

    fun disconnect(playerId: String): Output {
        if (watchers.remove(playerId) != null) return Output(roomChanged = true, viewChanged = isStarted)
        val member = members[playerId] ?: return Output.NOTHING
        member.connected = false
        member.goneSince = clock()
        member.ready = false
        if (!isStarted) members.remove(playerId)
        if (hostId == playerId) {
            hostId = members.values.firstOrNull { it.connected }?.playerId ?: hostId
        }
        return Output(roomChanged = true, viewChanged = isStarted)
    }

    fun leave(playerId: String): Output {
        if (watchers.remove(playerId) != null) return Output(roomChanged = true, viewChanged = isStarted)
        showingHands -= playerId
        members.remove(playerId) ?: return Output.NOTHING
        if (hostId == playerId) hostId = members.keys.firstOrNull() ?: hostId
        return Output.ROOM
    }

    // -- before the deal ---------------------------------------------------

    fun setRules(playerId: String, next: Rules): Output {
        if (playerId != hostId) return Output.error(playerId, ErrorCode.NOT_HOST, "Only the host can change the rules")
        if (isStarted) return Output.error(playerId, ErrorCode.ROOM_ALREADY_STARTED, "The match has already started")
        val problems = next.validate()
        if (problems.isNotEmpty()) {
            return Output.error(playerId, ErrorCode.INVALID_RULES, problems.joinToString("; "))
        }
        rules = next
        return Output.ROOM
    }

    fun setBot(playerId: String, seat: Int, enabled: Boolean, difficulty: Difficulty): Output {
        if (playerId != hostId) return Output.error(playerId, ErrorCode.NOT_HOST, "Only the host can add bots")
        if (isStarted) return Output.error(playerId, ErrorCode.ROOM_ALREADY_STARTED, "The match has already started")
        if (seat !in 0 until Pack.PLAYERS) return Output.NOTHING
        if (members.values.any { it.seat == seat }) {
            return Output.error(playerId, ErrorCode.SEAT_TAKEN, "Somebody is already sitting there")
        }
        if (enabled) bots[seat] = difficulty else bots.remove(seat)
        return Output.ROOM
    }

    fun setReady(playerId: String, ready: Boolean): Output {
        members[playerId]?.ready = ready
        return Output.ROOM
    }

    fun start(playerId: String, seed: Long = clock() xor code.hashCode().toLong()): Output {
        if (playerId != hostId) return Output.error(playerId, ErrorCode.NOT_HOST, "Only the host can start")
        if (isStarted) return Output.error(playerId, ErrorCode.ROOM_ALREADY_STARTED, "Already playing")
        if (!isFull()) {
            return Output.error(playerId, ErrorCode.NOT_ENOUGH_PLAYERS, "Teera needs four players")
        }
        game = Engine.newMatch(rules, seatList(), seed)
        armTurnTimer()
        return Output(roomChanged = true, viewChanged = true)
    }

    // -- play --------------------------------------------------------------

    fun move(playerId: String, move: Move): Output {
        val state = game ?: return Output.error(playerId, ErrorCode.NOT_IN_ROOM, "No match is running")
        val seat = seatOf(playerId) ?: return Output.error(playerId, ErrorCode.NOT_IN_ROOM, "You are not seated")

        return when (val outcome = Engine.apply(state, seat, move)) {
            is MoveOutcome.Ok -> {
                game = outcome.state
                armTurnTimer()
                Output.VIEW
            }
            is MoveOutcome.Rejected -> Output(
                direct = listOf(playerId to ServerMessage.MoveRejected(outcome.reason, viewFor(playerId)!!))
            )
        }
    }

    fun nextGame(playerId: String): Output {
        val state = game ?: return Output.NOTHING
        if (state.phase != Phase.GAME_OVER) return Output.NOTHING
        game = Engine.startGame(state)
        armTurnTimer()
        return Output.VIEW
    }

    /**
     * True when the seat the table is waiting on is a bot, or somebody who has been gone
     * longer than [RECONNECT_GRACE_MS]. A phone on mobile data drops for a few seconds
     * all the time, walking between rooms or through a tunnel; a bot playing your tile
     * the moment that happens would be worse than the table waiting a little.
     */
    fun awaitingBot(): Boolean {
        val state = game ?: return false
        val seat = state.actingSeat ?: return false
        val member = members.values.firstOrNull { it.seat == seat } ?: return true
        if (member.connected) return false
        return clock() - (member.goneSince ?: Long.MIN_VALUE / 2) >= RECONNECT_GRACE_MS
    }

    /** Plays one bot move. Callers space these out so the table reads at human speed. */
    fun stepBot(): Output {
        val state = game ?: return Output.NOTHING
        if (!awaitingBot()) return Output.NOTHING
        val seat = state.actingSeat ?: return Output.NOTHING
        val difficulty = bots[seat] ?: Difficulty.NORMAL
        val move = Bot.chooseMove(state, seat, difficulty) ?: return Output.NOTHING
        val outcome = Engine.apply(state, seat, move)
        if (outcome !is MoveOutcome.Ok) return Output.NOTHING
        game = outcome.state
        armTurnTimer()
        return Output.VIEW
    }

    /** A seat that runs out of clock has a move played for it. */
    fun expireTurnIfDue(): Output {
        val deadline = turnDeadline ?: return Output.NOTHING
        val state = game ?: return Output.NOTHING
        val seat = state.actingSeat ?: return Output.NOTHING
        if (clock() < deadline) return Output.NOTHING
        val move = Bot.chooseMove(state, seat, Difficulty.EASY) ?: return Output.NOTHING
        val outcome = Engine.apply(state, seat, move)
        if (outcome !is MoveOutcome.Ok) return Output.NOTHING
        game = outcome.state
        armTurnTimer()
        return Output.VIEW
    }

    private fun armTurnTimer() {
        val state = game
        turnDeadline = if (state != null && !state.isOver && rules.turnSeconds > 0) {
            clock() + rules.turnSeconds * 1000L
        } else {
            null
        }
    }

    // -- ناخذ خاوة ----------------------------------------------------------------

    private var vote: AlertVote? = null

    /** Somebody at the table says the other team broke the rules: the table votes. */
    fun raiseAlert(playerId: String): Output {
        val state = game ?: return Output.NOTHING
        val seat = seatOf(playerId) ?: return Output.NOTHING
        if (vote != null || state.phase == Phase.MATCH_OVER) return Output.NOTHING
        val voters = members.values.filter { it.connected }.map { it.seat }.sorted()
        vote = AlertVote(
            raisedBy = seat,
            againstTeam = 1 - teamOf(seat),
            voters = voters,
            yes = listOf(seat),
            no = emptyList(),
            deadlineEpochMs = clock() + rules.alertVoteSeconds * 1000L,
        )
        return settleVoteIfDecided() ?: Output.VIEW
    }

    fun voteAlert(playerId: String, yes: Boolean): Output {
        val current = vote ?: return Output.NOTHING
        val seat = seatOf(playerId) ?: return Output.NOTHING
        if (seat !in current.voters || seat in current.yes || seat in current.no) return Output.NOTHING
        vote = if (yes) current.copy(yes = current.yes + seat) else current.copy(no = current.no + seat)
        return settleVoteIfDecided() ?: Output.VIEW
    }

    /** Thirty seconds and no answer counts as no. */
    fun expireAlertIfDue(): Output {
        val current = vote ?: return Output.NOTHING
        if (clock() < current.deadlineEpochMs) return Output.NOTHING
        return settle(current)
    }

    /** Settles as soon as the outcome can no longer change, rather than waiting the clock out. */
    private fun settleVoteIfDecided(): Output? {
        val current = vote ?: return null
        val undecided = current.voters.size - current.yes.size - current.no.size
        val decided = current.yes.size >= current.needed || current.yes.size + undecided < current.needed
        return if (decided) settle(current) else null
    }

    private fun settle(current: AlertVote): Output {
        vote = null
        val upheld = current.yes.size >= current.needed
        var count = game?.alerts?.getOrNull(current.againstTeam) ?: 0
        var penalty = 0
        if (upheld) {
            game?.let { state ->
                val after = Engine.upholdAlert(state, current.againstTeam)
                game = after
                count = after.alerts[current.againstTeam]
                penalty = after.events.filterIsInstance<GameEvent.AlertUpheld>().lastOrNull()?.penalty ?: 0
            }
        }
        val result = ServerMessage.AlertResult(current.againstTeam, upheld, current.yes.size, current.needed, count, penalty)
        return Output(direct = humanIds().map { it to result }, viewChanged = true)
    }

    private fun showingSeats(): List<Int> =
        members.values.filter { it.playerId in showingHands }.map { it.seat }.sorted()

    private fun firstShowingSeat(): Int? = showingSeats().firstOrNull()

    fun viewFor(playerId: String): PlayerView? {
        val state = game ?: return null
        // The deal fixed who sits where; whether each of them is still connected is live.
        val seats = state.seats.map { seat ->
            val member = members[seat.playerId]
            seat.copy(connected = seat.isBot || member?.connected == true, avatar = member?.avatar ?: seat.avatar)
        }
        val shared = showingSeats()
        val seat = seatOf(playerId)
        if (seat != null) {
            return state.viewFor(seat, code, turnDeadline)
                .copy(seats = seats, alertVote = vote, spectators = spectatorSeats(), showingHands = shared)
        }
        // A watcher sees the table from a player's shoulder; their tiles only if that
        // player chose to show them, and never a move to make.
        val watcher = watchers[playerId] ?: return null
        val from = watcher.watching
        return state.viewFor(from, code, turnDeadline).copy(
            seats = seats,
            alertVote = vote,
            hand = if (from in shared) state.handOf(from) else emptyList(),
            legalMoves = emptyList(),
            spectating = true,
            spectators = spectatorSeats(),
            showingHands = shared,
        )
    }

    fun chat(playerId: String, text: String): ServerMessage.ChatMessage? {
        val seat = voiceSeatOf(playerId) ?: return null
        val name = members[playerId]?.name ?: watchers[playerId]?.name ?: return null
        val trimmed = text.trim().take(200)
        if (trimmed.isEmpty()) return null
        return ServerMessage.ChatMessage(seat, name, trimmed, clock())
    }

    private val lastEmote = HashMap<String, Long>()

    /**
     * Sends a soundboard button to everybody at the table. One every couple of seconds
     * per player, so a held thumb cannot drown out the game.
     */
    fun emote(playerId: String, emote: Emote): Output {
        val member = members[playerId] ?: return Output.NOTHING
        val now = clock()
        if (now - (lastEmote[playerId] ?: Long.MIN_VALUE / 2) < EMOTE_GAP_MS) return Output.NOTHING
        lastEmote[playerId] = now
        val shown = ServerMessage.EmoteShown(member.seat, emote)
        return Output(direct = humanIds().map { it to shown })
    }

    /** A bot's turn at the soundboard, so an offline table is not silent company. */
    fun botEmote(seat: Int, emote: Emote): Output {
        if (members.values.any { it.seat == seat && it.connected }) return Output.NOTHING
        val shown = ServerMessage.EmoteShown(seat, emote)
        return Output(direct = humanIds().map { it to shown })
    }

    companion object {
        private const val EMOTE_GAP_MS = 1_800L

        /** Watchers' seat numbers, for voice and chat, start after the four chairs. */
        const val WATCHER_SEAT_BASE = 4

        /** How long the table waits for somebody whose connection dropped before covering for them. */
        const val RECONNECT_GRACE_MS = 25_000L
    }
}
