package com.teera.app

import com.teera.core.ClientMessage
import com.teera.core.Protocol
import com.teera.core.ServerMessage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Against the real server, only when asked (-Pteera.live): the Windows app opens a room
 * and a phone-like client joins it with the code.
 */
class LiveServerTest {

    @Test
    fun `a room opened on Windows can be joined by a phone`() {
        if (System.getProperty("teera.live") != "true") return
        val model = GameViewModel(DesktopApp(kotlin.io.path.createTempDirectory("teera-live").toFile()))
        javax.swing.SwingUtilities.invokeAndWait { model.setName("ويندوز"); model.hostOnline() }
        var code: String? = null
        repeat(180) {
            code = model.state.value.room?.code
            if (code != null) return@repeat
            Thread.sleep(500)
        }
        val room = assertNotNull(code, "no room after 90 s: ${model.state.value.notice}")
        assertEquals(Screen.Lobby, model.state.value.screen)

        // A phone joins with the code.
        val inbox = LinkedBlockingQueue<ServerMessage>()
        val phone = OkHttpClient().newWebSocket(
            Request.Builder().url(AppInfo.SERVER_URL).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { Protocol.decodeServer(text) }.getOrNull()?.let(inbox::add)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = Unit
            },
        )
        phone.send(Protocol.encode(ClientMessage.Hello("تلفون", "live-phone-${System.nanoTime()}", appVersion = 30)))
        phone.send(Protocol.encode(ClientMessage.JoinRoom(room)))
        var joined = false
        repeat(60) {
            val message = inbox.poll(1, TimeUnit.SECONDS)
            if (message is ServerMessage.RoomUpdate && message.room.code == room) joined = true
            if (joined) return@repeat
        }
        assertTrue(joined, "the phone never got into room $room")
        repeat(20) {
            if (model.state.value.room?.seats?.any { it.name == "تلفون" } == true) return@repeat
            Thread.sleep(500)
        }
        assertTrue(model.state.value.room?.seats?.any { it.name == "تلفون" } == true, "Windows never saw the phone sit down")
        println("LIVE OK: room $room, seats ${model.state.value.room?.seats?.map { it.name }}")
        phone.close(1000, null)
        javax.swing.SwingUtilities.invokeAndWait { model.leave(); model.close() }
    }
}
