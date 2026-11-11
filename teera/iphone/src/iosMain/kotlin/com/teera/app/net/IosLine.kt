package com.teera.app.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/* The line to the server on an iPhone: Ktor over the system's own networking. */

internal val client: HttpClient by lazy {
    HttpClient(Darwin) {
        install(WebSockets) { pingInterval = 20_000 }
        install(HttpTimeout) {
            requestTimeoutMillis = 50_000
            connectTimeoutMillis = 20_000
        }
    }
}

fun openLine(url: String, listener: LineListener): Line? {
    if (!url.startsWith("ws://") && !url.startsWith("wss://")) return null
    return KtorLine(url, listener)
}

suspend fun knock(url: String): Boolean? {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    return try {
        client.get(url).status.isSuccess()
    } catch (e: Exception) {
        false
    }
}

private class KtorLine(url: String, private val listener: LineListener) : Line {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outgoing = Channel<Frame>(Channel.UNLIMITED)

    @Volatile private var waiting = 0L
    @Volatile private var shut = false

    init {
        scope.launch {
            try {
                client.webSocket(urlString = url) {
                    listener.onOpen(this@KtorLine)
                    val writer = launch {
                        for (frame in outgoing) {
                            send(frame)
                            waiting = (waiting - frame.data.size).coerceAtLeast(0)
                            if (frame is Frame.Close) break
                        }
                    }
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> listener.onText(this@KtorLine, frame.readText())
                            is Frame.Binary -> listener.onBytes(this@KtorLine, frame.readBytes())
                            is Frame.Close -> listener.onClosing(this@KtorLine)
                            else -> Unit
                        }
                    }
                    writer.cancel()
                }
            } catch (e: Exception) {
                // Refused, dropped, or no signal: the client decides what to do next.
            } finally {
                shut = true
                listener.onDown(this@KtorLine)
                scope.cancel()
            }
        }
    }

    private fun queue(frame: Frame): Boolean {
        if (shut) return false
        waiting += frame.data.size
        return outgoing.trySend(frame).isSuccess
    }

    override fun send(text: String) = queue(Frame.Text(text))
    override fun send(bytes: ByteArray) = queue(Frame.Binary(true, bytes))
    override val queued: Long get() = waiting

    override fun close(code: Int, reason: String?) {
        queue(Frame.Close(CloseReason(code.toShort(), reason.orEmpty())))
        outgoing.close()
    }
}
