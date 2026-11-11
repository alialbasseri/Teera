package com.teera.app.net

import com.teera.app.toByteArray
import com.teera.app.toInt8Array
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.ARRAYBUFFER
import org.w3c.dom.BinaryType
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

/* The line to the server in a browser: its own WebSocket. */

private fun isText(data: JsAny?): Boolean = js("typeof data === 'string'")

private fun jsKnock(url: String): Promise<JsBoolean> =
    js("fetch(url, { mode: 'no-cors', cache: 'no-store' }).then(() => true, () => false)")

fun openLine(url: String, listener: LineListener): Line? {
    if (!url.startsWith("ws://") && !url.startsWith("wss://")) return null
    return runCatching { BrowserLine(url, listener) }.getOrNull()
}

/** Any answer at all counts: the browser hides the details of another site's reply. */
suspend fun knock(url: String): Boolean? {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    return jsKnock(url).await<JsBoolean>().toBoolean()
}

private class BrowserLine(url: String, private val listener: LineListener) : Line {
    private val socket = WebSocket(url)
    private var open = false
    private var down = false

    /** A browser's socket refuses to send before it opens: hold the words until then. */
    private val waiting = ArrayList<String>()

    init {
        socket.binaryType = BinaryType.ARRAYBUFFER
        socket.onopen = {
            open = true
            waiting.forEach { socket.send(it) }
            waiting.clear()
            listener.onOpen(this)
        }
        socket.onmessage = { event: MessageEvent ->
            val data = event.data
            if (isText(data)) {
                listener.onText(this, data.toString())
            } else if (data != null) {
                listener.onBytes(this, Int8Array(data.unsafeCast<ArrayBuffer>()).toByteArray())
            }
        }
        socket.onclose = { gone() }
        socket.onerror = { gone() }
    }

    private fun gone() {
        if (down) return
        down = true
        open = false
        listener.onDown(this)
    }

    override fun send(text: String): Boolean {
        if (down) return false
        if (open) socket.send(text) else waiting += text
        return true
    }

    override fun send(bytes: ByteArray): Boolean {
        if (!open) return false
        socket.send(bytes.toInt8Array())
        return true
    }

    override val queued: Long get() = 0

    override fun close(code: Int, reason: String?) {
        runCatching { socket.close(code.toShort(), reason.orEmpty()) }
    }
}
