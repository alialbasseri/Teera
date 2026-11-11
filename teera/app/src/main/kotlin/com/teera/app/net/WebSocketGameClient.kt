package com.teera.app.net

import com.teera.app.locked
import com.teera.core.ClientMessage
import com.teera.core.ErrorCode
import com.teera.core.Protocol
import com.teera.core.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Talks to the room server.
 *
 * Friends playing from their own houses are on mobile data and home Wi-Fi, which drop for
 * a few seconds all the time. So this client:
 *  - wakes the server first when asked to ([wakeFirst]): a free host puts it to sleep when
 *    nobody is playing, and the first visit after that takes about a minute;
 *  - reconnects on its own, replaying the handshake and the room, because the server
 *    holds a dropped player's seat open;
 *  - reports how the line is doing through [link] rather than as a stream of errors.
 */
class WebSocketGameClient(
    private val url: String,
    private val scope: CoroutineScope,
    private val playerId: String,
    private val playerName: String,
    private val wakeFirst: Boolean = false,
    private val wakeTimeoutMillis: Long = WAKE_TIMEOUT_MS,
    /** This player's picture, as [com.teera.core.Seat.avatar] carries it. */
    private val avatar: String = "",
    /** Their own photo, base64 JPEG, when [avatar] is "photo". */
    private val photo: String? = null,
) : GameClient {

    // A small replay covers the gap between connecting and the screen subscribing.
    private val events = MutableSharedFlow<ServerMessage>(
        replay = 4,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming: Flow<ServerMessage> = events.asSharedFlow()

    // Voice is only worth anything live: a slow reader loses the oldest, never blocks.
    private val voiceIn = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val voice: Flow<ByteArray> = voiceIn.asSharedFlow()

    private val _link = MutableStateFlow(if (wakeFirst) Link.WAKING else Link.CONNECTING)
    override val link: StateFlow<Link> = _link.asStateFlow()

    /** Guards the fields below: the line calls back on its own threads. */
    private val lock = com.teera.app.Lock()

    private var socket: Line? = null
    private var closed = false
    private var attempt = 0
    private var everUp = false

    /**
     * The room to (re)enter on every fresh connection: whatever was last asked for, and
     * once the server has answered, simply "join this code".
     */
    private var roomRequest: ClientMessage? = null

    /** Messages sent before there was any socket at all, delivered once there is one. */
    private val pending = ArrayList<String>()

    private val keepAlive: Job

    /** Knocks on the server's health page until it answers, which is what wakes it. */
    private suspend fun wake(): Boolean {
        val health = healthUrlFor(url)
        val deadline = com.teera.app.nowMillis() + wakeTimeoutMillis
        while (!closed && com.teera.app.nowMillis() < deadline) {
            // Not up yet, or no signal (false): knock again in a moment.
            when (knock(health)) {
                true -> return true
                null -> return false // not an address at all
                false -> Unit
            }
            delay(2_000)
        }
        return false
    }

    private fun open() = lock.locked {
        if (closed) return@locked
        if (_link.value != Link.LOST) _link.value = Link.CONNECTING
        val fresh = openLine(url, listener)
        if (fresh == null) {
            giveUp()
            return@locked
        }
        // The line queues anything sent before it opens, in order. The hello has to be
        // first in that queue: the server refuses everything until it has one.
        fresh.send(encode(ClientMessage.Hello(playerName, playerId, avatar = avatar, photo = photo, appVersion = com.teera.app.AppInfo.VERSION_CODE, platform = com.teera.app.AppInfo.PLATFORM)))
        roomRequest?.let { fresh.send(encode(it)) }
        pending.forEach { fresh.send(it) }
        pending.clear()
        socket = fresh
    }

    private val listener = object : LineListener {

        override fun onOpen(line: Line) {
            lock.locked { if (line === socket) attempt = 0 }
        }

        override fun onText(line: Line, text: String) {
            val message = try {
                Protocol.decodeServer(text)
            } catch (e: Exception) {
                return
            }
            lock.locked {
                when (message) {
                    is ServerMessage.Welcome -> {
                        everUp = true
                        _link.value = Link.UP
                    }
                    is ServerMessage.RoomUpdate -> if (roomRequest != null) {
                        // Coming back after a drop: if the server restarted meanwhile, the
                        // room opens again under its code rather than leaving us stranded.
                        roomRequest = ClientMessage.JoinRoom(message.room.code, rejoin = true)
                    }
                    // A room that has gone is gone: asking for it again on every reconnect
                    // would only repeat the same refusal.
                    is ServerMessage.Failure -> when (message.code) {
                        ErrorCode.ROOM_NOT_FOUND -> roomRequest = null
                        ErrorCode.VERSION_MISMATCH -> closed = true
                        else -> Unit
                    }
                    else -> Unit
                }
            }
            events.tryEmit(message)
        }

        override fun onBytes(line: Line, bytes: ByteArray) {
            voiceIn.tryEmit(bytes)
        }

        override fun onClosing(line: Line) {
            line.close(1000, null)
        }

        override fun onDown(line: Line) = dropped(line)
    }

    // After the listener: init runs top to bottom, and open() hands the listener over.
    init {
        if (wakeFirst) {
            scope.launch {
                if (wake()) {
                    open()
                } else {
                    giveUp()
                }
            }
        } else {
            open()
        }
        // A free host sleeps after a quarter of an hour without traffic; a quiet table
        // (everyone thinking, or reading the score) must not look like an empty one.
        keepAlive = scope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_MS)
                lock.locked {
                    if (_link.value == Link.UP) socket?.send(encode(ClientMessage.Ping))
                }
            }
        }
    }

    private fun dropped(line: Line) = lock.locked {
        // A socket we already replaced (or closed on purpose) has nothing more to say.
        if (line !== socket || closed) return@locked
        socket = null
        if (everUp) _link.value = Link.LOST
        retry()
    }

    private fun retry() {
        if (closed) return
        // Before the first welcome, a handful of failures means there is nothing there.
        if (!everUp && attempt >= FIRST_CONNECT_ATTEMPTS) return giveUp()
        val wait = BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)]
        attempt++
        scope.launch {
            delay(wait)
            open()
        }
    }

    private fun giveUp() {
        closed = true
        _link.value = Link.UNREACHABLE
    }

    override fun send(message: ClientMessage) = lock.locked {
        when (message) {
            is ClientMessage.CreateRoom, is ClientMessage.JoinRoom, is ClientMessage.PlayTournamentMatch -> roomRequest = message
            ClientMessage.LeaveRoom -> roomRequest = null
            else -> Unit
        }
        val current = socket
        when {
            current != null -> current.send(encode(message))
            // Replayed by open() anyway; queueing it too would ask twice.
            message is ClientMessage.CreateRoom || message is ClientMessage.JoinRoom ||
                message is ClientMessage.PlayTournamentMatch -> Unit
            else -> pending += encode(message)
        }
        Unit
    }

    override fun sendVoice(packet: ByteArray) = lock.locked {
        val current = socket ?: return@locked
        // A line that cannot keep up would only deliver speech late; skip rather than queue.
        if (_link.value == Link.UP && current.queued < MAX_VOICE_BACKLOG) {
            current.send(packet)
        }
        Unit
    }

    override fun close() = lock.locked {
        closed = true
        keepAlive.cancel()
        socket?.close(1000, "bye")
        socket = null
    }

    companion object {
        private val BACKOFF_MS = longArrayOf(500, 1_000, 2_000, 4_000, 8_000)
        private const val FIRST_CONNECT_ATTEMPTS = 6
        private const val WAKE_TIMEOUT_MS = 100_000L
        private const val KEEP_ALIVE_MS = 4 * 60_000L
        private const val MAX_VOICE_BACKLOG = 8_000L

        /** wss://host/ws → https://host/health */
        fun healthUrlFor(socketUrl: String): String = socketUrl
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .removeSuffix("/")
            .removeSuffix("/ws") + "/health"
    }
}
