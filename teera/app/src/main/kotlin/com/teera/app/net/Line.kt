package com.teera.app.net

/**
 * A websocket to the room server, as each platform opens one (OkHttp on the phone and the
 * computer, the system's own on the iPhone). [WebSocketGameClient] does the rest the same
 * everywhere: the wake-up, the handshake, reconnecting.
 */
interface Line {
    /** Queued in order if the line is not open yet; false if it is already closed. */
    fun send(text: String): Boolean
    fun send(bytes: ByteArray): Boolean

    /** Bytes waiting to go out. */
    val queued: Long

    fun close(code: Int, reason: String?)
}

/** What a [Line] says happened. Called on the line's own threads. */
interface LineListener {
    fun onOpen(line: Line)
    fun onText(line: Line, text: String)
    fun onBytes(line: Line, bytes: ByteArray)

    /** The server is closing it. */
    fun onClosing(line: Line)

    /** Closed or broken: either way it is gone. */
    fun onDown(line: Line)
}
