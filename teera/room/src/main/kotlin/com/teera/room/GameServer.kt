package com.teera.room

import com.teera.core.ClientMessage
import com.teera.core.ErrorCode
import com.teera.core.Phase
import com.teera.core.Protocol
import com.teera.core.Room
import com.teera.core.ServerMessage
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.host
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameServer")

/** How long a bot "thinks" before playing, so the table does not snap forward. */
private const val BOT_THINK_MS = 1_500L

/** Up to this much longer on some turns, the way a person takes longer over some. */
private const val BOT_THINK_SPREAD_MS = 700L

private fun botThink(): Long = BOT_THINK_MS + kotlin.random.Random.nextLong(BOT_THINK_SPREAD_MS)
private const val TICK_MS = 250L
private const val IDLE_ROOM_MS = 30 * 60 * 1000L

/** How often the cloud server looks for a newer app. */
private const val LATEST_CHECK_MS = 5 * 60 * 1000L

fun Application.gameServer(
    registry: RoomRegistry = RoomRegistry(),
    records: Records = Records.NONE,
    /** Where to check for the newest app (the cloud server); null keeps the built-in numbers. */
    latestFrom: String? = null,
    pusher: Pusher = Pusher.NONE,
) {
    val tournaments = Tournaments(records)
    if (latestFrom != null) {
        launch {
            while (isActive) {
                val text = runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val connection = java.net.URL(latestFrom).openConnection().apply {
                            connectTimeout = 10_000
                            readTimeout = 10_000
                        }
                        connection.getInputStream().use { it.readBytes().decodeToString() }
                    }
                }.getOrNull()
                if (text != null && !LatestApp.update(text)) log.warn("could not read the newest version")
                delay(LATEST_CHECK_MS)
            }
        }
    }

    install(WebSockets) {
        pingPeriodMillis = 20_000
        timeoutMillis = 45_000
    }

    // One ticker for the whole server: it moves bots, covers players who have run out
    // of clock, and clears rooms out once everyone has gone home.
    launch {
        while (isActive) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            for (holder in registry.all()) {
                if (!holder.lock.tryLock()) continue
                try {
                    var output = holder.room.expireTurnIfDue() + holder.room.expireAlertIfDue()
                    if (holder.room.awaitingBot() && now >= holder.nextBotMoveAt) {
                        output += holder.room.stepBot()
                        holder.nextBotMoveAt = now + botThink()
                    }
                    if (output != Room.Output.NOTHING) publish(holder, output, records)
                    // The table is waiting on somebody who is not looking: call them back,
                    // once per turn.
                    val absent = holder.room.absentActing()
                    val state = holder.room.game
                    if (absent != null && state != null) {
                        val turn = "${state.gameNumber}:${state.board.size}:${state.actingSeat}:${state.phase}"
                        if (holder.calledBack != turn) {
                            holder.calledBack = turn
                            pusher.notify(
                                absent, "دورك بطيرة!", "الطاولة تنتظرك، ارجع العب",
                                mapOf("code" to holder.room.code),
                            )
                        }
                    }
                } finally {
                    holder.lock.unlock()
                }
            }
            registry.evictIdle(IDLE_ROOM_MS)
        }
    }

    routing {
        get("/health") {
            // Whether the books and notifications are set up, so a deploy can be checked from outside.
            val books = if (records === Records.NONE) "off" else "on"
            val push = if (pusher === Pusher.NONE) "off" else "on"
            call.respondText("ok rooms=${registry.size()} records=$books push=$push")
        }

        // Rooms still waiting for players. A phone hosting on the Wi-Fi only ever has one,
        // so a friend who typed the host's address can find it without a code.
        roomLinks()

        get("/rooms") {
            val open = registry.all().filter { !it.room.isStarted }.map { it.room.code }
            call.respondText(
                Protocol.json.encodeToString(ListSerializer(String.serializer()), open),
                ContentType.Application.Json,
            )
        }

        webSocket("/ws") {
            var playerId: String? = null
            var name = "Player"
            var avatar = ""
            var photo: String? = null
            var holder: RoomRegistry.Holder? = null

            suspend fun fail(code: ErrorCode, message: String) =
                send(Protocol.encode(ServerMessage.Failure(code, message)))

            val voiceAllowance = VoiceAllowance()

            try {
                incoming.consumeEach { frame ->
                    // Table talk: binary, passed straight on without being opened.
                    if (frame is Frame.Binary) {
                        relayVoice(holder, playerId, frame.data, voiceAllowance)
                        return@consumeEach
                    }
                    val text = (frame as? Frame.Text)?.readText() ?: return@consumeEach
                    val message = try {
                        Protocol.decodeClient(text)
                    } catch (e: Exception) {
                        log.debug("bad frame: {}", e.message)
                        fail(ErrorCode.BAD_MESSAGE, "Could not read that message")
                        return@consumeEach
                    }

                    // Everything except the handshake needs an identified player.
                    if (message !is ClientMessage.Hello && playerId == null) {
                        fail(ErrorCode.BAD_MESSAGE, "Say hello first")
                        return@consumeEach
                    }
                    val me = playerId

                    when (message) {
                        is ClientMessage.Hello -> {
                            if (message.version != Protocol.VERSION) {
                                fail(ErrorCode.VERSION_MISMATCH, "Update the app to keep playing")
                                close(CloseReason(CloseReason.Codes.NORMAL, "version"))
                                return@consumeEach
                            }
                            // The client keeps its id between runs so a reconnect finds its seat.
                            playerId = message.playerId?.takeIf { it.isNotBlank() }
                                ?: UUID.randomUUID().toString()
                            name = message.name.trim().take(24).ifEmpty { "Player" }
                            avatar = message.avatar.take(12).filter { it.isLetterOrDigit() || it == ':' }
                            photo = message.photo?.takeIf { avatar == "photo" && it.length <= MAX_PHOTO_CHARS }
                            if (avatar == "photo" && photo == null) avatar = ""
                            registry.arrived(playerId!!, name, avatar, this)
                            records.playerSeen(playerId!!, name, avatar)
                            send(Protocol.encode(ServerMessage.Welcome(playerId!!)))
                            when {
                                // Apps before 1.4.1 cannot show an update window, but they do
                                // show the words of an error: that is how they hear of it.
                                message.appVersion == 0 -> trySend(
                                    ServerMessage.Failure(
                                        ErrorCode.INTERNAL,
                                        "نزلت نسخة جديدة من طيرة (${LatestApp.name})! نزّلها من: ${call.request.host()}/download",
                                    ),
                                )
                                message.appVersion < LatestApp.code -> trySend(
                                    ServerMessage.UpdateAvailable(
                                        LatestApp.code, LatestApp.name, APK_URL,
                                        required = message.appVersion < LatestApp.minimum,
                                    ),
                                )
                            }
                        }

                        is ClientMessage.CreateRoom -> {
                            val problems = message.rules.validate()
                            if (problems.isNotEmpty()) {
                                fail(ErrorCode.INVALID_RULES, problems.joinToString("; "))
                                return@consumeEach
                            }
                            holder?.let { detach(it, me!!) }
                            val created = registry.create(me!!, message.rules)
                            holder = created
                            created.sessions[me] = this
                            created.lock.withLock {
                                publish(created, created.room.join(me, name, avatar), records)
                            }
                            swapPhotos(created, me, photo)
                        }

                        is ClientMessage.JoinRoom -> {
                            val target = registry.find(message.code)
                                ?: if (message.rejoin && registry.isCode(message.code)) registry.reopen(message.code, me!!) else null
                            if (target == null) {
                                fail(ErrorCode.ROOM_NOT_FOUND, "No room with the code ${message.code.uppercase()}")
                                return@consumeEach
                            }
                            val refused = target.lock.withLock { target.room.refusal(me!!) }
                            if (refused != null) {
                                fail(
                                    refused,
                                    if (refused == ErrorCode.KICKED) "صاحب الغرفة طلّعك من هالغرفة"
                                    else "الغرفة مقفولة، تحتاج عزيمة من واحد بيها",
                                )
                                return@consumeEach
                            }
                            holder?.let { detach(it, me!!) }
                            holder = target
                            target.sessions[me!!] = this
                            target.lock.withLock { publish(target, target.room.join(me, name, avatar), records) }
                            swapPhotos(target, me!!, photo)
                        }

                        // Answered anywhere, in a room or not: the app pings to keep a
                        // free host from putting the server to sleep mid-match.
                        ClientMessage.Ping -> send(Protocol.encode(ServerMessage.Pong))

                        // Friends: who is around, who a code belongs to, and invitations.
                        is ClientMessage.WhoIsOnline -> trySend(
                            ServerMessage.Online(message.playerIds.take(200).filter { registry.online.containsKey(it) }),
                        )
                        is ClientMessage.FindPlayer -> {
                            val found = registry.byShortCode(message.shortCode)
                            trySend(
                                if (found == null) ServerMessage.PlayerFound(message.shortCode.uppercase())
                                else ServerMessage.PlayerFound(message.shortCode.uppercase(), found.first, found.second.name, found.second.avatar),
                            )
                        }
                        is ClientMessage.Invite -> {
                            val target = registry.online[message.playerId]
                            val code = message.code.uppercase().filter(Char::isLetterOrDigit).take(8)
                            // Invited from inside the room: a locked room lets them in.
                            holder?.takeIf { it.room.code == code }?.let { here ->
                                here.lock.withLock { here.room.invited(message.playerId) }
                            }
                            val invite = ServerMessage.Invited(me!!, name, avatar, code)
                            target?.sessions?.forEach { it.trySend(invite) }
                            // Not in the app: their phone hears of it anyway, if it can.
                            val pushed = target == null && pusher.canReach(message.playerId)
                            if (pushed) {
                                pusher.notify(message.playerId, "عزيمة على طيرة", "$name يعزمك على طاولته", mapOf("code" to code))
                            }
                            trySend(ServerMessage.InviteSent(message.playerId, delivered = target != null, pushed = pushed))
                        }
                        is ClientMessage.PushToken -> if (message.token.length in 20..4096) pusher.remember(me!!, message.token)

                        // Tournaments: a bracket of pairs, each match played in its own room.
                        is ClientMessage.CreateTournament -> {
                            val made = com.teera.core.Tournament.create(
                                tournaments.newId(), message.name, me!!, message.teams, System.currentTimeMillis(),
                            )
                            if (made == null) {
                                fail(ErrorCode.BAD_MESSAGE, "البطولة لازم تكون 4 أو 8 أو 16 فريق، وكل فريق إله اسم")
                            } else {
                                tournaments.put(made)
                                trySend(ServerMessage.TournamentIs(made.id, made))
                            }
                        }
                        is ClientMessage.AskTournament -> trySend(ServerMessage.TournamentIs(message.id.uppercase(), tournaments.get(message.id)))
                        is ClientMessage.SetTournamentWinner -> {
                            val t = tournaments.get(message.id)
                            val updated = t?.takeIf { it.creatorId == me }?.withWinner(message.round, message.index, message.team)
                            if (updated != null) tournaments.put(updated)
                            trySend(ServerMessage.TournamentIs(message.id.uppercase(), updated ?: t))
                        }
                        is ClientMessage.PlayTournamentMatch -> {
                            val t = tournaments.get(message.id)
                            val match = t?.rounds?.getOrNull(message.round)?.getOrNull(message.index)
                            if (t == null || match?.a == null || match.b == null || match.winner != null) {
                                fail(ErrorCode.BAD_MESSAGE, "هالمباراة مو جاهزة تنلعب")
                                return@consumeEach
                            }
                            // Already being played: go to that room.
                            val open = match.roomCode?.let { registry.find(it) }
                            val target = open ?: registry.create(me!!, com.teera.core.Rules.DEFAULT).also { made ->
                                made.lock.withLock {
                                    made.room.teamNames = listOf(t.teams[match.a!!], t.teams[match.b!!])
                                    made.room.tournamentLabel = "${t.name} · ${t.roundName(message.round)}"
                                }
                                val (id, round, index) = Triple(t.id, message.round, message.index)
                                made.onMatchFinished = { losing -> tournaments.matchFinished(id, round, index, losing) }
                                t.withRoom(message.round, message.index, made.room.code)?.let(tournaments::put)
                            }
                            holder?.let { detach(it, me!!) }
                            holder = target
                            target.sessions[me!!] = this
                            target.lock.withLock { publish(target, target.room.join(me, name, avatar), records) }
                            swapPhotos(target, me, photo)
                            tournaments.get(t.id)?.let { trySend(ServerMessage.TournamentIs(it.id, it)) }
                        }
                        ClientMessage.MyRecord -> trySend(ServerMessage.Record(records.recordOf(me!!)))
                        ClientMessage.AskLeaderboard -> trySend(ServerMessage.LeaderboardIs(records.leaderboard()))
                        is ClientMessage.AskMatch -> trySend(
                            ServerMessage.MatchIs(message.matchId, records.matchDetail(message.matchId.take(36))),
                        )
                        ClientMessage.ListLiveGames -> trySend(
                            ServerMessage.LiveGames(
                                registry.all().mapNotNull { it.lock.withLock { it.room.liveGame() } }.take(50),
                            ),
                        )

                        else -> {
                            val current = holder
                            if (current == null) {
                                fail(ErrorCode.NOT_IN_ROOM, "Join a room first")
                                return@consumeEach
                            }
                            current.lastActivity = System.currentTimeMillis()
                            current.lock.withLock {
                                handleInRoom(current, me!!, message)?.let { output ->
                                    publish(current, output, records)
                                    // Told they were sent out, they stop hearing the table.
                                    if (message is ClientMessage.Kick && output != Room.Output.NOTHING) {
                                        current.sessions.remove(message.playerId)
                                    }
                                }
                            }
                            if (message is ClientMessage.LeaveRoom) {
                                current.sessions.remove(me)
                                holder = null
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("socket failed", e)
            } finally {
                playerId?.let { registry.left(it, this) }
                val current = holder
                val me = playerId
                // A phone that reconnected has a new socket at the table by now; the old one
                // closing late (a dead mobile line can take most of a minute to notice) must
                // not mark them gone or take their new socket's place.
                if (current != null && me != null && current.sessions.remove(me, this)) {
                    current.lock.withLock { publish(current, current.room.disconnect(me), records) }
                }
            }
        }
    }
}

/** A player's own picture: a small JPEG, base64. About 12 KB is what the app sends. */
private const val MAX_PHOTO_CHARS = 48_000

/**
 * Pictures go round once, not with every view: a newcomer gets everyone else's, and
 * everyone else gets theirs.
 */
private suspend fun WebSocketSession.swapPhotos(holder: RoomRegistry.Holder, me: String, photo: String?) {
    if (photo != null) {
        holder.photos[me] = photo
        val mine = ServerMessage.Avatar(me, photo)
        for ((id, session) in holder.sessions) if (id != me) session.trySend(mine)
    }
    for ((id, theirs) in holder.photos) if (id != me) trySend(ServerMessage.Avatar(id, theirs))
}

/** The most a phone may send in one voice packet; a 40 ms one is about 330 bytes. */
private const val MAX_VOICE_BYTES = 2_048

/**
 * About 25 voice packets a second is a person talking. Allows a burst after a stall,
 * and stops a broken or hostile client from flooding the table.
 */
private class VoiceAllowance(private val perSecond: Double = 40.0, private val burst: Double = 60.0) {
    private var tokens = burst
    private var last = System.nanoTime()

    fun take(): Boolean {
        val now = System.nanoTime()
        tokens = minOf(burst, tokens + (now - last) / 1e9 * perSecond)
        last = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }
}

/**
 * Passes a voice packet to everybody else in the room, with the speaker's seat in front
 * so their phones know whose ring to light. A listener on a slow line has packets
 * dropped rather than holding the speaker up: late speech is worse than missing speech.
 */
private suspend fun relayVoice(
    holder: RoomRegistry.Holder?,
    playerId: String?,
    payload: ByteArray,
    allowance: VoiceAllowance,
) {
    if (holder == null || playerId == null) return
    if (payload.isEmpty() || payload.size > MAX_VOICE_BYTES || !allowance.take()) return
    val seat = holder.lock.withLock { holder.room.voiceSeatOf(playerId) } ?: return
    val relayed = ByteArray(payload.size + 1)
    relayed[0] = seat.toByte()
    payload.copyInto(relayed, 1)
    for ((id, session) in holder.sessions) {
        if (id != playerId) session.outgoing.trySend(Frame.Binary(true, relayed))
    }
}

/** Handles the messages that only make sense once a player is seated. */
private fun handleInRoom(holder: RoomRegistry.Holder, playerId: String, message: ClientMessage): Room.Output? {
    val room = holder.room
    return when (message) {
        is ClientMessage.SetRules -> room.setRules(playerId, message.rules)
        is ClientMessage.SetBot -> room.setBot(playerId, message.seat, message.enabled, message.difficulty)
        is ClientMessage.Ready -> room.setReady(playerId, message.ready)
        is ClientMessage.TakeSeat -> room.takeSeat(playerId, message.seat)
        ClientMessage.StartMatch -> {
            holder.nextBotMoveAt = System.currentTimeMillis() + botThink()
            room.start(playerId)
        }
        is ClientMessage.MakeMove -> room.move(playerId, message.move)
        ClientMessage.NextGame -> room.nextGame(playerId)
        ClientMessage.LeaveRoom -> room.leave(playerId)
        is ClientMessage.Chat -> room.chat(playerId, message.text)?.let { chat ->
            Room.Output(direct = room.humanIds().map { it to chat })
        }
        ClientMessage.Ping -> Room.Output(direct = listOf(playerId to ServerMessage.Pong))
        is ClientMessage.SendEmote -> room.emote(playerId, message.emote)
        ClientMessage.RaiseAlert -> room.raiseAlert(playerId)
        is ClientMessage.ShowHand -> room.showHand(playerId, message.shown)
        is ClientMessage.Watch -> room.watch(playerId, message.seat)
        is ClientMessage.VoteAlert -> room.voteAlert(playerId, message.yes)
        is ClientMessage.StandIn -> room.standIn(playerId, message.seat)
        is ClientMessage.ProposeTarget -> room.proposeTarget(playerId, message.target)
        ClientMessage.ScreenCaptured -> room.screenCaptured(playerId)
        is ClientMessage.VoteTarget -> room.voteTarget(playerId, message.yes)
        is ClientMessage.SetListed -> room.setListed(playerId, message.listed)
        is ClientMessage.Away -> room.setAway(playerId, message.away)
        is ClientMessage.Kick -> room.kick(playerId, message.playerId)
        is ClientMessage.SetLocked -> room.setLocked(playerId, message.locked)
        is ClientMessage.Hello, is ClientMessage.CreateRoom, is ClientMessage.JoinRoom,
        is ClientMessage.WhoIsOnline, is ClientMessage.FindPlayer, is ClientMessage.Invite,
        ClientMessage.MyRecord, ClientMessage.ListLiveGames, ClientMessage.AskLeaderboard,
        is ClientMessage.AskMatch, is ClientMessage.PushToken, is ClientMessage.CreateTournament,
        is ClientMessage.AskTournament, is ClientMessage.PlayTournamentMatch, is ClientMessage.SetTournamentWinner -> null
    }
}

private suspend fun detach(holder: RoomRegistry.Holder, playerId: String) {
    holder.sessions.remove(playerId)
    holder.lock.withLock { holder.room.disconnect(playerId) }
}

/**
 * Puts a room's output on the wire. Views are built per player, so nobody is ever sent
 * a payload containing somebody else's hand.
 */
private suspend fun publish(holder: RoomRegistry.Holder, output: Room.Output, records: Records) {
    val room = holder.room
    for (happening in room.takeHappenings()) {
        if (happening is Room.Happening.MatchStarted) holder.matchId = UUID.randomUUID().toString()
        holder.matchId?.let { records.happened(it, room.code, happening) }
        if (happening is Room.Happening.MatchFinished) holder.onMatchFinished?.invoke(happening.losingTeam)
    }
    if (output == Room.Output.NOTHING) return

    for ((target, message) in output.direct) {
        holder.sessions[target]?.trySend(message)
    }
    if (output.roomChanged) {
        val info = ServerMessage.RoomUpdate(room.info())
        holder.sessions.values.forEach { it.trySend(info) }
    }
    if (output.viewChanged) {
        for ((target, session) in holder.sessions) {
            room.viewFor(target)?.let { session.trySend(ServerMessage.GameUpdate(it)) }
        }
    }
    if (room.game?.phase == Phase.MATCH_OVER) holder.lastActivity = System.currentTimeMillis()
}

/** A dead socket must never take a room down with it. */
private suspend fun WebSocketSession.trySend(message: ServerMessage) {
    try {
        send(Protocol.encode(message))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.debug("dropping frame for a closed socket: {}", e.message)
    }
}
