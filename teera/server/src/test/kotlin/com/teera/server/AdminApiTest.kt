package com.teera.server

import com.teera.core.ClientMessage
import com.teera.core.ErrorCode
import com.teera.core.Protocol
import com.teera.core.Rules
import com.teera.core.ServerMessage
import com.teera.room.AdminState
import com.teera.room.gameServer
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.h2.jdbcx.JdbcDataSource

/** The developer's controls: what the page can see and do, and only with the password. */
class AdminApiTest {
    private val key = "secret123"

    private fun books() = SqlRecords(
        JdbcDataSource().apply { setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1") },
    )

    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.say(message: ClientMessage) =
        send(Protocol.encode(message))

    private suspend inline fun <reified T : ServerMessage> io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.await(
        crossinline accept: (T) -> Boolean = { true },
    ): T = withTimeout(10_000) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val message = Protocol.decodeServer(frame.readText())
            if (message is T && accept(message)) return@withTimeout message
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    @Test
    fun `nothing answers without the password`() = testApplication {
        application { gameServer(adminKey = key) }
        assertEquals(403, client.get("/admin/api/overview").status.value)
        assertEquals(403, client.get("/admin/api/overview") { header("X-Admin-Key", "wrong-one") }.status.value)
        assertEquals(403, client.post("/admin/api/announce") { setBody("""{"text":"hi"}""") }.status.value)
        assertEquals(200, client.get("/admin/api/overview") { header("X-Admin-Key", key) }.status.value)
    }

    @Test
    fun `a problem sent from the app reaches the page, and can be marked done`() = testApplication {
        val books = books()
        application { gameServer(records = books, adminKey = key) }
        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.await<ServerMessage.Welcome>()
        ali.say(ClientMessage.Feedback("idea", "  ضيفوا لعبة الدومنة الإنگليزية  "))
        assertTrue(ali.await<ServerMessage.FeedbackReceived>().ok)
        ali.say(ClientMessage.Feedback("problem", "x"))
        assertTrue(!ali.await<ServerMessage.FeedbackReceived>().ok, "a single letter is not a report")
        books.flush()

        val list = Protocol.json.parseToJsonElement(
            client.get("/admin/api/feedback") { header("X-Admin-Key", key) }.bodyAsText(),
        ).jsonArray
        assertEquals(1, list.size)
        val item = list[0].jsonObject
        assertEquals("ضيفوا لعبة الدومنة الإنگليزية", item["text"]!!.jsonPrimitive.content)
        assertEquals("idea", item["kind"]!!.jsonPrimitive.content)
        assertEquals(99, item["appVersion"]!!.jsonPrimitive.int)

        val id = item["id"]!!.jsonPrimitive.content
        client.post("/admin/api/feedback/$id/done?done=true") { header("X-Admin-Key", key) }
        books.flush()
        val after = Protocol.json.parseToJsonElement(
            client.get("/admin/api/feedback") { header("X-Admin-Key", key) }.bodyAsText(),
        ).jsonArray[0].jsonObject
        assertTrue(after["done"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `a barred player is let go at once and turned away after`() = testApplication {
        val books = books()
        val admin = AdminState()
        application { gameServer(records = books, adminKey = key, admin = admin) }
        val ws = createClient { install(WebSockets) }
        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", playerId = "sara", appVersion = 99))
        sara.await<ServerMessage.Welcome>()

        client.post("/admin/api/players/sara/ban") { header("X-Admin-Key", key); setBody("""{"reason":"سب وشتم"}""") }
        val told = sara.await<ServerMessage.Failure>()
        assertEquals(ErrorCode.INTERNAL, told.code)
        assertTrue("انحظرت" in told.message)

        val again = ws.webSocketSession("/ws")
        again.say(ClientMessage.Hello("Sara", playerId = "sara", appVersion = 99))
        assertTrue("انحظرت" in again.await<ServerMessage.Failure>().message)

        books.flush()
        assertEquals("سب وشتم", books.bannedPlayers()["sara"], "kept for after a restart")
        client.post("/admin/api/players/sara/unban") { header("X-Admin-Key", key) }
        val back = ws.webSocketSession("/ws")
        back.say(ClientMessage.Hello("Sara", playerId = "sara", appVersion = 99))
        back.await<ServerMessage.Welcome>()
    }

    @Test
    fun `the page can make old apps update, and say something to everyone`() = testApplication {
        val books = books()
        application { gameServer(records = books, adminKey = key) }
        client.post("/admin/api/settings") { header("X-Admin-Key", key); setBody("""{"minAppVersion":25,"remindersOn":false}""") }
        books.flush()
        assertEquals(mapOf("minAppVersion" to "25", "remindersOn" to "false"), books.loadSettings())

        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 20))
        assertTrue(ali.await<ServerMessage.UpdateAvailable>().required, "build 20 is under the 25 set on the page")

        val said = client.post("/admin/api/announce") { header("X-Admin-Key", key); setBody("""{"text":"بطولة الجمعة!"}""") }
        assertTrue("\"reached\":1" in said.bodyAsText())
        assertEquals("بطولة الجمعة!", ali.await<ServerMessage.Announcement>().text)
    }

    @Test
    fun `live rooms are listed, and one can be closed`() = testApplication {
        application { gameServer(adminKey = key) }
        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = ali.await<ServerMessage.RoomUpdate>().room.code

        val rooms = Protocol.json.parseToJsonElement(
            client.get("/admin/api/rooms") { header("X-Admin-Key", key) }.bodyAsText(),
        ).jsonArray
        assertEquals(code, rooms.single().jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(listOf("Ali"), rooms.single().jsonObject["players"]!!.jsonArray.map { it.jsonPrimitive.content })

        client.post("/admin/api/rooms/$code/say") { header("X-Admin-Key", key); setBody("""{"text":"يلا خلصوا"}""") }
        val line = ali.await<ServerMessage.ChatMessage>()
        assertEquals("الإدارة" to "يلا خلصوا", line.name to line.text)

        client.post("/admin/api/rooms/$code/close") { header("X-Admin-Key", key) }
        assertEquals(ErrorCode.KICKED, ali.await<ServerMessage.Failure>().code)
        val left = client.get("/admin/api/rooms") { header("X-Admin-Key", key) }.bodyAsText()
        assertEquals("[]", left)
    }

    @Test
    fun `the page draws with the first numbers already in it`() = testApplication {
        application { gameServer(adminKey = key) }
        val page = client.get("/admin?key=$key").bodyAsText()
        assertTrue("\"live\":{" in page && "/*INIT*/" !in page, "the overview is put into the page")
        assertTrue("{{LATEST}}" !in page)
    }

    @Test
    fun `a report can bring a picture, and only the page with the password sees it`() = testApplication {
        val books = books()
        application { gameServer(records = books, adminKey = key) }
        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.await<ServerMessage.Welcome>()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        ali.say(ClientMessage.Feedback("problem", "الكتابة طالعة فوگ البولات", image = java.util.Base64.getEncoder().encodeToString(jpeg)))
        assertTrue(ali.await<ServerMessage.FeedbackReceived>().ok)
        // Not a picture: the words still arrive, without it.
        ali.say(ClientMessage.Feedback("idea", "ضيفوا بطولات", image = java.util.Base64.getEncoder().encodeToString("hello".toByteArray())))
        assertTrue(ali.await<ServerMessage.FeedbackReceived>().ok)
        books.flush()

        val items = Protocol.json.parseToJsonElement(client.get("/admin/api/feedback") { header("X-Admin-Key", key) }.bodyAsText()).jsonArray
        val withPicture = items.first { it.jsonObject["kind"]!!.jsonPrimitive.content == "problem" }.jsonObject
        val without = items.first { it.jsonObject["kind"]!!.jsonPrimitive.content == "idea" }.jsonObject
        assertTrue(withPicture["hasImage"]!!.jsonPrimitive.boolean)
        assertTrue(without["hasImage"]?.jsonPrimitive?.boolean != true)

        val id = withPicture["id"]!!.jsonPrimitive.content
        assertEquals(403, client.get("/admin/api/feedback/$id/image").status.value)
        val got = client.get("/admin/api/feedback/$id/image?key=$key")
        assertEquals("image/jpeg", got.headers["Content-Type"])
        assertTrue(jpeg.contentEquals(got.body<ByteArray>()))
    }
}
