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
    )

    private fun seatList(): List<Seat> = (0 until Pack.PLAYERS).map { seat ->
        val member = members.values.firstOrNull { it.seat == seat }
        when {
            member != null -> Seat(seat, member.playerId, member.name, isBot = false, connected = member.connected)
            bots.containsKey(seat) -> Seat(seat, "bot-$seat", "Bot ${seat + 1}", isBot = true)
            else -> Seat(seat, "", "", isBot = false, connected = false)
        }
    }

    /** Teera needs all four seats filled, by people or bots (rule 1). */
    fun isFull(): Boolean = (0 until Pack.PLAYERS).all { seat ->
        members.values.any { it.seat == seat } || bots.containsKey(seat)
    }

    fun seatOf(playerId: String): Int? = members[playerId]?.seat

    fun humanIds(): List<String> = members.values.filter { it.connected }.map { it.playerId }

    fun join(playerId: String, name: String): Output {
        members[playerId]?.let { existing ->
            existing.connected = true
            existing.goneSince = null
            existing.name = name
            return Output(roomChanged = true, viewChanged = isStarted)
        }
        if (isStarted) return Output.error(playerId, ErrorCode.ROOM_ALREADY_STARTED, "That match has already started")

        val seat = freeSeat() ?: return Output.error(playerId, ErrorCode.ROOM_FULL, "That room is full")
        members[playerId] = Member(playerId, name, seat)
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

    fun viewFor(playerId: String): PlayerView? {
        val state = game ?: return null
        val seat = seatOf(playerId) ?: return null
        // The deal fixed who sits where; whether each of them is still connected is live.
        val seats = state.seats.map { it.copy(connected = it.isBot || members[it.playerId]?.connected == true) }
        return state.viewFor(seat, code, turnDeadline).copy(seats = seats)
    }

    fun chat(playerId: String, text: String): ServerMessage.ChatMessage? {
        val member = members[playerId] ?: return null
        val trimmed = text.trim().take(200)
        if (trimmed.isEmpty()) return null
        return ServerMessage.ChatMessage(member.seat, member.name, trimmed, clock())
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

        /** How long the table waits for somebody whose connection dropped before covering for them. */
        const val RECONNECT_GRACE_MS = 25_000L
    }
}
