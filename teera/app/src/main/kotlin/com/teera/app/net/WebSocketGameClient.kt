package com.teera.app.net

import com.teera.core.ClientMessage
import com.teera.core.Protocol
import com.teera.core.ServerMessage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Talks to the room server.
 *
 * Reconnects on its own and replays the handshake, because the server holds a seat open
 * for a player who drops: walking through a tunnel should not cost you the match.
 */
class WebSocketGameClient(
    private val url: String,
    private val scope: CoroutineScope,
    private val playerId: String,
    private val playerName: String,
    private val http: OkHttpClient = defaultHttpClient(),
) : GameClient {

    // A small replay covers the gap between connecting and the screen subscribing.
    private val events = MutableSharedFlow<ServerMessage>(
        replay = 4,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming: Flow<ServerMessage> = events.asSharedFlow()

    private var socket: WebSocket? = null
    private var closed = false
    private var attempt = 0

    /** The room to rejoin after a drop, remembered from whatever we last asked for. */
    @Volatile
    private var rejoinCode: String? = null

    init {
        open()
    }

    private fun open() {
        if (closed) return
        val request = Request.Builder().url(url).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = try {
                    Protocol.decodeServer(text)
                } catch (e: Exception) {
                    return
                }
                if (message is ServerMessage.RoomUpdate) rejoinCode = message.room.code
                events.tryEmit(message)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.tryEmit(connectionLost(t.message))
                retry()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed) retry()
            }
        }).also { fresh ->
            // OkHttp queues anything sent before the socket opens, in order. The hello has
            // to be first in that queue: the server refuses everything until it has one,
            // so sending it from onOpen let a "create room" sent straight away overtake it.
            fresh.send(encode(ClientMessage.Hello(playerName, playerId)))
            rejoinCode?.let { fresh.send(encode(ClientMessage.JoinRoom(it))) }
        }
    }

    private fun retry() {
        if (closed) return
        val wait = BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)]
        attempt++
        scope.launch {
            delay(wait)
            open()
        }
    }

    override fun send(message: ClientMessage) {
        if (message is ClientMessage.JoinRoom) rejoinCode = message.code
        if (message is ClientMessage.LeaveRoom) rejoinCode = null
        socket?.send(encode(message))
    }

    override fun close() {
        closed = true
        socket?.close(1000, "bye")
        socket = null
    }

    companion object {
        private val BACKOFF_MS = longArrayOf(500, 1_000, 2_000, 4_000, 8_000)

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
