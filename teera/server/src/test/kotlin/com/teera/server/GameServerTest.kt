package com.teera.server

import com.teera.core.ClientMessage
import com.teera.core.Difficulty
import com.teera.core.Move
import com.teera.core.Pack
import com.teera.core.Protocol
import com.teera.core.Rules
import com.teera.core.ServerMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout

private suspend fun WebSocketSession.say(message: ClientMessage) = send(Protocol.encode(message))

/** Reads frames until one matches, so unrelated broadcasts do not break a test. */
private suspend inline fun <reified T : ServerMessage> WebSocketSession.await(
    crossinline accept: (T) -> Boolean = { true },
): T = withTimeout(10_000) {
    while (true) {
        val frame = incoming.receive() as? Frame.Text ?: continue
        val message = Protocol.decodeServer(frame.readText())
        if (message is T && accept(message)) return@withTimeout message
    }
    error("unreachable")
}

class GameServerTest {

    @Test
    fun `health endpoint reports the room count`() = testApplication {
        application { gameServer() }
        assertTrue(client.get("/health").bodyAsText().startsWith("ok rooms="))
    }

    @Test
    fun `a friend joins with the room code and both see the lobby`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Ali"))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = host.await<ServerMessage.RoomUpdate>().room.code
        assertEquals(RoomRegistry.CODE_LENGTH, code.length)

        val guest = ws.webSocketSession("/ws")
        guest.say(ClientMessage.Hello("Sara"))
        guest.await<ServerMessage.Welcome>()
        guest.say(ClientMessage.JoinRoom(code.lowercase()))   // codes are case-insensitive

        val lobby = guest.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.name == "Sara" } == 1 }
        assertEquals("Ali", lobby.room.seats[0].name)
        assertEquals("Sara", lobby.room.seats[1].name)

        host.close()
        guest.close()
    }

    @Test
    fun `a wrong code is refused rather than dropping the socket`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }
        val session = ws.webSocketSession("/ws")
        session.say(ClientMessage.Hello("Ali"))
        session.await<ServerMessage.Welcome>()
        session.say(ClientMessage.JoinRoom("ZZZZ"))
        assertEquals(com.teera.core.ErrorCode.ROOM_NOT_FOUND, session.await<ServerMessage.Failure>().code)
        session.close()
    }

    @Test
    fun `a dealt hand reaches its owner and nobody else`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Ali"))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = host.await<ServerMessage.RoomUpdate>().room.code

        val guest = ws.webSocketSession("/ws")
        guest.say(ClientMessage.Hello("Sara"))
        guest.await<ServerMessage.Welcome>()
        guest.say(ClientMessage.JoinRoom(code))
        guest.await<ServerMessage.RoomUpdate>()

        host.say(ClientMessage.SetBot(2, enabled = true, difficulty = Difficulty.NORMAL))
        host.say(ClientMessage.SetBot(3, enabled = true, difficulty = Difficulty.NORMAL))
        host.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 2 }

        host.say(ClientMessage.StartMatch)
        val hostView = host.await<ServerMessage.GameUpdate>().view
        val guestView = guest.await<ServerMessage.GameUpdate>().view

        assertEquals(0, hostView.yourSeat)
        assertEquals(1, guestView.yourSeat)
        assertEquals(Pack.HAND_SIZE, hostView.hand.size)
        assertEquals(Pack.HAND_SIZE, guestView.hand.size)
        assertTrue(hostView.hand.intersect(guestView.hand.toSet()).isEmpty(), "hands must not overlap")
        assertEquals(List(Pack.PLAYERS) { Pack.HAND_SIZE }, hostView.handCounts)

        host.close()
        guest.close()
    }

    @Test
    fun `the server refuses a move from the wrong seat`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Ali"))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        host.await<ServerMessage.RoomUpdate>()
        (1..3).forEach { host.say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        host.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 3 }
        host.say(ClientMessage.StartMatch)
        val view = host.await<ServerMessage.GameUpdate>().view

        // Claim a tile that is not in hand: the referee lives on the server, not the phone.
        val notHeld = Pack.tiles.first { it !in view.hand }
        host.say(ClientMessage.MakeMove(Move.Play(notHeld, null)))
        val rejected = host.await<ServerMessage.MoveRejected>()
        assertNotNull(rejected.view)

        host.close()
    }

    @Test
    fun `bots play on their own once the match starts`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Ali"))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        host.await<ServerMessage.RoomUpdate>()
        (1..3).forEach { host.say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        host.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 3 }
        host.say(ClientMessage.StartMatch)

        // The bots keep the line going on their own; the human only answers when the turn
        // actually reaches their seat.
        var tilesOnTable = 0
        var humanPlays = 0
        while (tilesOnTable < 4) {
            val view = host.await<ServerMessage.GameUpdate>().view
            tilesOnTable = maxOf(tilesOnTable, view.board.size)
            if (view.isYourMove) {
                val move = view.legalMoves.firstOrNull() ?: break
                host.say(ClientMessage.MakeMove(move))
                humanPlays++
            }
        }
        assertTrue(tilesOnTable - humanPlays >= 2, "bots played their own turns unprompted")

        host.close()
    }
}
