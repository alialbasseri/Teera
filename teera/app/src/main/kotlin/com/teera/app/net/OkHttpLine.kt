package com.teera.app.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/* The line to the server on the JVM (the phone and the computer): OkHttp. */

private val http: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}

private val knocker: OkHttpClient by lazy {
    http.newBuilder()
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .build()
}

/** Opens a websocket to [url]; null when [url] is not an address at all. */
fun openLine(url: String, listener: LineListener): Line? {
    val request = try {
        Request.Builder().url(url).build()
    } catch (e: IllegalArgumentException) {
        return null
    }
    return OkHttpLine(request, listener)
}

/** One GET of [url]: true if it answered OK, false if not (yet), null if it is not an address. */
suspend fun knock(url: String): Boolean? = withContext(Dispatchers.IO) {
    try {
        knocker.newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful }
    } catch (e: IOException) {
        false
    } catch (e: IllegalArgumentException) {
        null
    }
}

private class OkHttpLine(request: Request, private val listener: LineListener) : Line {
    private val socket: WebSocket = http.newWebSocket(
        request,
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen(this@OkHttpLine)
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onText(this@OkHttpLine, text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = listener.onBytes(this@OkHttpLine, bytes.toByteArray())
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = listener.onClosing(this@OkHttpLine)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onDown(this@OkHttpLine)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onDown(this@OkHttpLine)
        },
    )

    override fun send(text: String) = socket.send(text)
    override fun send(bytes: ByteArray) = socket.send(bytes.toByteString())
    override val queued: Long get() = socket.queueSize()
    override fun close(code: Int, reason: String?) {
        socket.close(code, reason)
    }
}
