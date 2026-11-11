package com.teera.app

import com.teera.app.net.Link
import com.teera.app.net.WebSocketGameClient
import com.teera.core.ClientMessage
import com.teera.core.Pack
import com.teera.core.PlayerView
import com.teera.core.RoomInfo
import com.teera.core.Rules
import com.teera.core.ServerMessage
import com.teera.room.RoomRegistry
import com.teera.room.gameServer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test

/**
 * The line between a phone and the internet server, over real sockets: waking a server
 * that is asleep, losing it mid-match, and finding the seat still there on the way back.
 */
class ConnectionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registry = RoomRegistry()
    private val port = ServerSocket(0).use { it.localPort }
    private var server: ApplicationEngine? = null

    private fun serverUp() {
        server = embeddedServer(CIO, port = port, host = "127.0.0.1") { gameServer(registry) }.start(wait = false)
    }

    private fun serverDown() {
        server?.stop(0, 500)
        server = null
    }

    @After
    fun tearDown() {
        serverDown()
        scope.cancel()
    }

    private inner class Phone(name: String, wake: Boolean = true, wakeMillis: Long = 20_000) {
        val heard = CopyOnWriteArrayList<ServerMessage>()
        val links = CopyOnWriteArrayList<Link>()
        val voices = CopyOnWriteArrayList<ByteArray>()
        val client = WebSocketGameClient(
            "ws://127.0.0.1:$port/ws", scope, "id-$name", name,
            wakeFirst = wake, wakeTimeoutMillis = wakeMillis,
        )

        init {
            scope.launch { client.incoming.collect { heard += it } }
            scope.launch { client.link.collect { links += it } }
            scope.launch { client.voice.collect { voices += it } }
        }

        val room: RoomInfo? get() = heard.filterIsInstance<ServerMessage.RoomUpdate>().lastOrNull()?.room
        val view: PlayerView? get() = heard.filterIsInstance<ServerMessage.GameUpdate>().lastOrNull()?.view
    }

    private suspend fun until(what: String, millis: Long = 15_000, check: () -> Boolean) {
        withTimeout(millis) { while (!check()) delay(25) }
        assertTrue(check(), what)
    }

    @Test
    fun `a sleeping server is woken before the room is asked for`() = runBlocking {
        // The phone starts knocking before the server is up, as with a free host asleep.
        val ali = Phone("Ali")
        ali.client.send(ClientMessage.CreateRoom(Rules.DEFAULT))
        delay(1_500)
        assertEquals(Link.WAKING, ali.client.link.value, "still knocking, not failing")

        serverUp()
        until("the room was created once the server woke") { ali.room != null }
        assertEquals(Link.UP, ali.client.link.value)
        assertEquals(1, registry.size(), "asked for exactly one room")
        ali.client.close()
    }

    @Test
    fun `a server that never answers is reported, not waited on forever`() = runBlocking {
        val ali = Phone("Ali", wakeMillis = 1_500)
        ali.client.send(ClientMessage.CreateRoom(Rules.DEFAULT))
        until("it gives up") { ali.client.link.value == Link.UNREACHABLE }
    }

    @Test
    fun `a dropped line comes back to the same seat mid-match`() = runBlocking {
        serverUp()
        val ali = Phone("Ali")
        ali.client.send(ClientMessage.CreateRoom(Rules.DEFAULT))
        until("room") { ali.room != null }
        val code = assertNotNull(ali.room).code
        val sara = Phone("Sara")
        sara.client.send(ClientMessage.JoinRoom(code))
        until("Sara seated") { ali.room?.seats?.any { it.name == "Sara" } == true }
        for (seat in 0 until Pack.PLAYERS) {
            if (ali.room!!.seats[seat].playerId.isEmpty()) ali.client.send(ClientMessage.SetBot(seat, true))
        }
        until("table full") { ali.room!!.seats.all { it.playerId.isNotEmpty() } }
        ali.client.send(ClientMessage.StartMatch)
        until("dealt") { sara.view != null }
        val saraSeat = sara.view!!.yourSeat
        val saraHand = sara.view!!.hand.toSet()

        // The whole server goes away for a moment - the harshest drop there is - and
        // comes back with its rooms (as a host's network blip looks from the phone).
        val heardBefore = sara.heard.size
        serverDown()
        until("the phone notices") { sara.client.link.value == Link.LOST }
        serverUp()

        until("Sara is back", 20_000) { sara.client.link.value == Link.UP }
        fun viewSinceDrop() = sara.heard.drop(heardBefore).filterIsInstance<ServerMessage.GameUpdate>().lastOrNull()?.view
        until("and dealt the table as it stands", 20_000) { viewSinceDrop() != null }
        val back = assertNotNull(viewSinceDrop())
        assertEquals(saraSeat, back.yourSeat, "same seat")
        assertTrue(back.hand.toSet().all { it in saraHand }, "same hand, less whatever she played")
        assertTrue(Link.LOST in sara.links, "the screen was told while it was down")
        ali.client.close()
        sara.client.close()
    }

    @Test
    fun `what one player says is heard by the other, from the right seat`() = runBlocking {
        serverUp()
        val ali = Phone("Ali")
        ali.client.send(ClientMessage.CreateRoom(Rules.DEFAULT))
        until("room") { ali.room != null }
        val sara = Phone("Sara")
        sara.client.send(ClientMessage.JoinRoom(ali.room!!.code))
        until("Sara seated") { sara.room?.seats?.any { it.name == "Sara" } == true }
        val aliSeat = ali.room!!.seats.first { it.name == "Ali" }.seat

        val words = ShortArray(com.teera.app.voice.VoiceWire.FRAME_SAMPLES) { (kotlin.math.sin(it / 6.0) * 8_000).toInt().toShort() }
        repeat(5) { ali.client.sendVoice(com.teera.app.voice.VoiceWire.pack(it, words)) }

        until("Sara hears all of it") { sara.voices.size == 5 }
        val heard = assertNotNull(com.teera.app.voice.VoiceWire.unpack(sara.voices.last()))
        assertEquals(aliSeat, heard.seat)
        assertEquals(4, heard.seq)
        assertTrue(ali.voices.isEmpty(), "Ali does not hear himself")
        ali.client.close()
        sara.client.close()
    }

    @Test
    fun `a friend with the app open is seen online and gets the invitation`() = runBlocking {
        serverUp()
        val ali = Phone("Ali")
        val sara = Phone("Sara")
        until("both connected") { ali.client.link.value == Link.UP && sara.client.link.value == Link.UP }

        // Sara adds Ali by the code on his profile.
        sara.client.send(ClientMessage.FindPlayer(com.teera.core.shortCodeOf("id-Ali")))
        until("Ali is found") { sara.heard.any { it is ServerMessage.PlayerFound && it.playerId == "id-Ali" } }

        ali.client.send(ClientMessage.CreateRoom(Rules.DEFAULT))
        until("room") { ali.room != null }
        ali.client.send(ClientMessage.WhoIsOnline(listOf("id-Sara", "id-Nobody")))
        until("Sara is online") { ali.heard.any { it is ServerMessage.Online && it.playerIds == listOf("id-Sara") } }

        ali.client.send(ClientMessage.Invite("id-Sara", ali.room!!.code))
        until("Sara is asked to the table") {
            sara.heard.any { it is ServerMessage.Invited && it.code == ali.room!!.code && it.fromName == "Ali" }
        }
        until("Ali hears it got there") { ali.heard.any { it is ServerMessage.InviteSent && it.delivered } }

        ali.client.send(ClientMessage.Invite("id-Nobody", ali.room!!.code))
        until("and when it could not") { ali.heard.any { it is ServerMessage.InviteSent && !it.delivered } }
        ali.client.close()
        sara.client.close()
    }
}
