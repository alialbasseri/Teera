package com.teera.server

import com.teera.core.ClientMessage
import com.teera.core.ErrorCode
import com.teera.core.Phase
import com.teera.core.Protocol
import com.teera.core.Room
import com.teera.core.ServerMessage
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameServer")

/** How long a bot "thinks" before playing, so the table does not snap forward. */
private const val BOT_THINK_MS = 900L
private const val TICK_MS = 250L
private const val IDLE_ROOM_MS = 30 * 60 * 1000L

fun Application.gameServer(registry: RoomRegistry = RoomRegistry()) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(20)
        timeout = Duration.ofSeconds(45)
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
                    var output = holder.room.expireTurnIfDue()
                    if (holder.room.awaitingBot() && now >= holder.nextBotMoveAt) {
                        output += holder.room.stepBot()
                        holder.nextBotMoveAt = now + BOT_THINK_MS
                    }
                    if (output != Room.Output.NOTHING) publish(holder, output)
                } finally {
                    holder.lock.unlock()
                }
            }
            registry.evictIdle(IDLE_ROOM_MS)
        }
    }

    routing {
        get("/health") {
            call.respondText("ok rooms=${registry.size()}")
        }

        webSocket("/ws") {
            var playerId: String? = null
            var name = "Player"
            var holder: RoomRegistry.Holder? = null

            suspend fun fail(code: ErrorCode, message: String) =
                send(Protocol.encode(ServerMessage.Failure(code, message)))

            try {
                incoming.consumeEach { frame ->
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
                            send(Protocol.encode(ServerMessage.Welcome(playerId!!)))
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
                                publish(created, created.room.join(me, name))
                            }
                        }

                        is ClientMessage.JoinRoom -> {
                            val target = registry.find(message.code)
                            if (target == null) {
                                fail(ErrorCode.ROOM_NOT_FOUND, "No room with the code ${message.code.uppercase()}")
                                return@consumeEach
                            }
                            holder?.let { detach(it, me!!) }
                            holder = target
                            target.sessions[me!!] = this
                            target.lock.withLock { publish(target, target.room.join(me, name)) }
                        }

                        else -> {
                            val current = holder
                            if (current == null) {
                                fail(ErrorCode.NOT_IN_ROOM, "Join a room first")
                                return@consumeEach
                            }
                            current.lastActivity = System.currentTimeMillis()
                            current.lock.withLock {
                                handleInRoom(current, me!!, message)?.let { publish(current, it) }
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
                val current = holder
                val me = playerId
                if (current != null && me != null) {
                    current.sessions.remove(me)
                    current.lock.withLock { publish(current, current.room.disconnect(me)) }
                }
            }
        }
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
            holder.nextBotMoveAt = System.currentTimeMillis() + BOT_THINK_MS
            room.start(playerId)
        }
        is ClientMessage.MakeMove -> room.move(playerId, message.move)
        ClientMessage.NextGame -> room.nextGame(playerId)
        ClientMessage.LeaveRoom -> room.leave(playerId)
        is ClientMessage.Chat -> room.chat(playerId, message.text)?.let { chat ->
            Room.Output(direct = room.humanIds().map { it to chat })
        }
        ClientMessage.Ping -> Room.Output(direct = listOf(playerId to ServerMessage.Pong))
        is ClientMessage.Hello, is ClientMessage.CreateRoom, is ClientMessage.JoinRoom -> null
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
private suspend fun publish(holder: RoomRegistry.Holder, output: Room.Output) {
    if (output == Room.Output.NOTHING) return
    val room = holder.room

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
