package com.teera.server

import com.teera.room.RoomRegistry
import com.teera.room.gameServer

import com.teera.core.ClientMessage
import com.teera.core.Difficulty
import com.teera.core.ErrorCode
import com.teera.core.GameEvent
import com.teera.core.Move
import com.teera.core.Pack
import com.teera.core.Protocol
import com.teera.core.Rules
import com.teera.core.ServerMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.call.body
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
        host.say(ClientMessage.Hello("Ali", appVersion = 99))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = host.await<ServerMessage.RoomUpdate>().room.code
        assertEquals(RoomRegistry.CODE_LENGTH, code.length)

        val guest = ws.webSocketSession("/ws")
        guest.say(ClientMessage.Hello("Sara", appVersion = 99))
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
        session.say(ClientMessage.Hello("Ali", appVersion = 99))
        session.await<ServerMessage.Welcome>()
        session.say(ClientMessage.JoinRoom("ZZZZ"))
        assertEquals(com.teera.core.ErrorCode.ROOM_NOT_FOUND, session.await<ServerMessage.Failure> { it.code != com.teera.core.ErrorCode.INTERNAL }.code)
        session.close()
    }

    @Test
    fun `a dealt hand reaches its owner and nobody else`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Ali", appVersion = 99))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = host.await<ServerMessage.RoomUpdate>().room.code

        val guest = ws.webSocketSession("/ws")
        guest.say(ClientMessage.Hello("Sara", appVersion = 99))
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
        host.say(ClientMessage.Hello("Ali", appVersion = 99))
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
        host.say(ClientMessage.Hello("Ali", appVersion = 99))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        host.await<ServerMessage.RoomUpdate>()
        (1..3).forEach { host.say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        host.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 3 }
        host.say(ClientMessage.StartMatch)

        // The bots keep the line going on their own; the human only answers when the turn
        // actually reaches their seat. Counted from what the referee says each seat did, so
        // a deal where the human has to rescue (give, not lay) cannot fool the count.
        val botTiles = mutableSetOf<com.teera.core.Domino>()
        var views = 0
        while (botTiles.size < 2 && views++ < 200) {
            val view = host.await<ServerMessage.GameUpdate>().view
            view.events.forEach { event ->
                when (event) {
                    is GameEvent.Opened -> if (event.seat != view.yourSeat) botTiles += event.domino
                    is GameEvent.Played -> if (event.seat != view.yourSeat) botTiles += event.domino
                    else -> Unit
                }
            }
            if (view.isYourMove) {
                val move = view.legalMoves.firstOrNull() ?: break
                host.say(ClientMessage.MakeMove(move))
            }
        }
        assertTrue(botTiles.size >= 2, "bots played their own turns unprompted")

        host.close()
    }
}

class OpenRoomsTest {
    @Test
    fun `an old socket closing after the phone reconnected does not unseat it`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val first = ws.webSocketSession("/ws")
        first.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        first.await<ServerMessage.Welcome>()
        first.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = first.await<ServerMessage.RoomUpdate>().room.code
        (1..3).forEach { first.say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        first.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 3 }
        first.say(ClientMessage.StartMatch)
        first.await<ServerMessage.GameUpdate>()

        // The phone's line changed: a new socket rejoins while the old one is still open.
        val second = ws.webSocketSession("/ws")
        second.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        second.await<ServerMessage.Welcome>()
        second.say(ClientMessage.JoinRoom(code, rejoin = true))
        assertTrue(!second.await<ServerMessage.GameUpdate>().view.spectating)

        first.close()
        // The table still talks to the new socket, and still has them seated and present.
        second.say(ClientMessage.Chat("hi"))
        second.await<ServerMessage.ChatMessage>()
        second.say(ClientMessage.ShowHand(true))
        val view = second.await<ServerMessage.GameUpdate> { 0 in it.view.showingHands }.view
        assertTrue(view.seatInfo(0)?.connected == true, "not marked gone by the old socket")
        second.close()
    }

    @Test
    fun `waiting rooms are listed so a friend can find one by address alone`() = testApplication {
        application { gameServer() }
        assertEquals("[]", client.get("/rooms").bodyAsText())

        val ws = createClient { install(WebSockets) }
        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Ali", appVersion = 99))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = host.await<ServerMessage.RoomUpdate>().room.code

        assertEquals("[\"$code\"]", client.get("/rooms").bodyAsText())
        host.close()
    }
}

class VoiceRelayTest {

    @Test
    fun `talk reaches everyone else at the table with the speaker's seat, and not the speaker`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", appVersion = 99))
        ali.await<ServerMessage.Welcome>()
        ali.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = ali.await<ServerMessage.RoomUpdate>().room.code

        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", appVersion = 99))
        sara.await<ServerMessage.Welcome>()
        sara.say(ClientMessage.JoinRoom(code))
        sara.await<ServerMessage.RoomUpdate>()
        ali.await<ServerMessage.RoomUpdate> { it.room.seats.any { s -> s.name == "Sara" } }

        // A stranger who never joined the room cannot speak into it.
        val stranger = ws.webSocketSession("/ws")
        stranger.say(ClientMessage.Hello("Nobody", appVersion = 99))
        stranger.await<ServerMessage.Welcome>()
        stranger.send(Frame.Binary(true, byteArrayOf(1, 0, 9, 9, 9, 9, 9)))

        sara.send(Frame.Binary(true, byteArrayOf(1, 0, 7, 10, 20, 30, 40)))
        val heard = withTimeout(5_000) {
            var frame: Frame
            do { frame = ali.incoming.receive() } while (frame !is Frame.Binary)
            frame.data
        }
        assertEquals(listOf<Byte>(1, 1, 0, 7, 10, 20, 30, 40), heard.toList(), "Sara's seat, then her packet untouched")

        // And it was not sent back to Sara herself: the next thing she gets is a pong.
        sara.say(ClientMessage.Ping)
        val next = withTimeout(5_000) { sara.incoming.receive() }
        assertTrue(next is Frame.Text, "no echo of her own voice")

        ali.close(); sara.close(); stranger.close()
    }
}

class AvatarTest {

    @Test
    fun `a player's own photo reaches whoever joins, once, and nobody has to send it again`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", avatar = "photo", photo = "QUxJ", appVersion = 99))
        ali.await<ServerMessage.Welcome>()
        ali.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = ali.await<ServerMessage.RoomUpdate>().room.code

        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", avatar = "p:6", appVersion = 99))
        sara.await<ServerMessage.Welcome>()
        sara.say(ClientMessage.JoinRoom(code))

        // The seat first, then the picture to put on it.
        val room = sara.await<ServerMessage.RoomUpdate> { it.room.seats.any { s -> s.name == "Sara" } }.room
        val photo = sara.await<ServerMessage.Avatar>()
        assertEquals("QUxJ", photo.photo)
        assertEquals("photo", room.seats.first { it.name == "Ali" }.avatar)
        assertEquals("p:6", room.seats.first { it.name == "Sara" }.avatar, "a drawn picture travels as its name")

        ali.close(); sara.close()
    }
}

class RoomLinkTest {

    @Test
    fun `a room link opens a page that hands the code to the app, and Android can check the app`() = testApplication {
        application { gameServer() }
        val page = client.get("/j/khan").bodyAsText()
        assertTrue("intent://join/KHAN" in page, "the button opens the app on that room")
        val links = client.get("/.well-known/assetlinks.json").bodyAsText()
        assertTrue("com.teera.app" in links && "sha256_cert_fingerprints" in links)
        assertTrue("/download" in page, "a friend without the game is shown where to get it")
    }

    @Test
    fun `an app from before update windows is told in words, a newer old one gets the window`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val old = ws.webSocketSession("/ws")
        old.say(ClientMessage.Hello("Ali"))
        val told = old.await<ServerMessage.Failure>()
        assertEquals(ErrorCode.INTERNAL, told.code, "a code old apps show the words of, without closing")
        assertTrue("/download" in told.message && com.teera.room.LatestApp.name in told.message)

        val newer = ws.webSocketSession("/ws")
        newer.say(ClientMessage.Hello("Sara", appVersion = 1))
        val offer = newer.await<ServerMessage.UpdateAvailable>()
        assertEquals(com.teera.room.LatestApp.code, offer.versionCode)
        assertEquals(com.teera.room.APK_URL, offer.url)
    }

    @Test
    fun `an app from before update windows cannot sit at a table until it is updated`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        // A 1.4.1-or-later app opens a room; that one may play.
        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Sara", appVersion = 1))
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = host.await<ServerMessage.RoomUpdate>().room.code

        val old = ws.webSocketSession("/ws")
        old.say(ClientMessage.Hello("Ali"))
        old.await<ServerMessage.Failure>()

        old.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val noRoom = old.await<ServerMessage.Failure>()
        assertEquals(ErrorCode.INTERNAL, noRoom.code)
        assertTrue("نسختك قديمة" in noRoom.message && "/download" in noRoom.message)

        old.say(ClientMessage.JoinRoom(code))
        assertTrue("نسختك قديمة" in old.await<ServerMessage.Failure>().message)
        old.say(ClientMessage.JoinRoom(code, rejoin = true))
        assertTrue("نسختك قديمة" in old.await<ServerMessage.Failure>().message)

        // Nobody old ever reached the table.
        host.say(ClientMessage.JoinRoom(code))
        val room = host.await<ServerMessage.RoomUpdate>().room
        assertTrue(room.seats.none { it.name == "Ali" } && room.spectators.none { it.name == "Ali" })
    }

    @Test
    fun `a listed game shows to anybody asking, and they can come in and watch it`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val host = ws.webSocketSession("/ws")
        host.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        host.await<ServerMessage.Welcome>()
        host.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = host.await<ServerMessage.RoomUpdate>().room.code
        (1..3).forEach { host.say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        host.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 3 }
        host.say(ClientMessage.StartMatch)
        host.await<ServerMessage.GameUpdate>()

        val passerBy = ws.webSocketSession("/ws")
        passerBy.say(ClientMessage.Hello("Hassan", appVersion = 99))
        passerBy.await<ServerMessage.Welcome>()
        passerBy.say(ClientMessage.ListLiveGames)
        val hidden = passerBy.await<ServerMessage.LiveGames>().games.single()
        assertTrue(!hidden.open && hidden.code.isEmpty(), "not listed yet: shown, but only to ask into")

        host.say(ClientMessage.SetListed(true))
        host.await<ServerMessage.GameUpdate> { it.view.listed }
        passerBy.say(ClientMessage.ListLiveGames)
        val listed = passerBy.await<ServerMessage.LiveGames>().games.single()
        assertEquals(code, listed.code)
        assertTrue(listed.open)

        passerBy.say(ClientMessage.JoinRoom(listed.code))
        assertTrue(passerBy.await<ServerMessage.GameUpdate>().view.spectating)
    }

    @Test
    fun `a newer build named in the repository is offered without a restart`() = testApplication {
        try {
            assertTrue(com.teera.room.LatestApp.update("""{"code": 9999, "name": "9.9.9", "notes": ["بولات <ذهب>"]}"""))
            assertTrue(!com.teera.room.LatestApp.update("not json"), "a broken file changes nothing")
            application { gameServer() }
            val ws = createClient { install(WebSockets) }
            val app = ws.webSocketSession("/ws")
            app.say(ClientMessage.Hello("Ali", appVersion = 22))
            val offer = app.await<ServerMessage.UpdateAvailable>()
            assertEquals(9999, offer.versionCode)
            assertEquals("9.9.9", offer.versionName)
            val page = client.get("/download").bodyAsText()
            assertTrue("9.9.9" in page && "شنو الجديد" in page)
            assertTrue("بولات &lt;ذهب&gt;" in page, "the notes are shown as words, never as markup")
        } finally {
            com.teera.room.LatestApp.reset()
        }
    }

    @Test
    fun `an invitation to somebody without the app open goes to their phone, and so does a waiting turn`() = testApplication {
        val told = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()
        val pusher = object : com.teera.room.Pusher {
            val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()
            override fun remember(playerId: String, token: String) { tokens[playerId] = token }
            override fun canReach(playerId: String) = tokens.containsKey(playerId)
            override fun notify(playerId: String, title: String, body: String, data: Map<String, String>) {
                told += playerId to title
            }
        }
        application { gameServer(pusher = pusher) }
        val ws = createClient { install(WebSockets) }

        // Sara's phone told the server where to reach it, then she closed the app.
        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", playerId = "sara", appVersion = 99))
        sara.await<ServerMessage.Welcome>()
        sara.say(ClientMessage.PushToken("token-of-saras-phone-0000"))
        sara.close()

        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.await<ServerMessage.Welcome>()
        ali.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = ali.await<ServerMessage.RoomUpdate>().room.code
        withTimeout(10_000) { while (!pusher.canReach("sara")) kotlinx.coroutines.delay(20) }
        ali.say(ClientMessage.Invite("sara", code))
        val sent = ali.await<ServerMessage.InviteSent>()
        assertTrue(!sent.delivered && sent.pushed)
        assertEquals("sara" to "عزيمة على طيرة", told.first())

        // Ali starts against bots, then puts the app away: when his turn comes, his phone hears.
        ali.say(ClientMessage.PushToken("token-of-alis-phone-00000"))
        (1..3).forEach { ali.say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        ali.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 3 }
        ali.say(ClientMessage.StartMatch)
        ali.say(ClientMessage.Away(true))
        withTimeout(15_000) { while (told.none { it == ("ali" to "دورك بطيرة!") }) kotlinx.coroutines.delay(50) }
    }

    @Test
    fun `a room nobody listed shows by name without its code, and comes in on a yes`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        val code = ali.await<ServerMessage.RoomUpdate>().room.code
        ali.say(ClientMessage.RenameRoom("گعدة الجمعة"))
        assertEquals("گعدة الجمعة", ali.await<ServerMessage.RoomUpdate> { it.room.name == "گعدة الجمعة" }.room.name)

        // An older app cannot ask, so it does not see the room at all.
        val old = ws.webSocketSession("/ws")
        old.say(ClientMessage.Hello("Omar", playerId = "omar", appVersion = 27))
        old.say(ClientMessage.ListLiveGames)
        assertTrue(old.await<ServerMessage.LiveGames>().games.isEmpty())

        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", playerId = "sara", appVersion = 99))
        sara.say(ClientMessage.ListLiveGames)
        val listed = sara.await<ServerMessage.LiveGames>().games.single()
        assertEquals("گعدة الجمعة", listed.name)
        assertEquals("", listed.code, "the code is not given away")
        assertTrue(!listed.open && !listed.started && listed.freeChairs == 3)

        sara.say(ClientMessage.AskToJoin(listed.ref))
        val asked = ali.await<ServerMessage.JoinAsked>()
        assertEquals("sara" to "Sara", asked.playerId to asked.name)
        ali.say(ClientMessage.AnswerJoin("sara", yes = true))
        val answer = sara.await<ServerMessage.JoinAnswer>()
        assertTrue(answer.yes)
        assertEquals(code, answer.code)
        assertEquals("Ali", answer.by)

        sara.say(ClientMessage.JoinRoom(answer.code!!))
        sara.await<ServerMessage.RoomUpdate> { it.room.seats.any { s -> s.name == "Sara" } }
    }

    @Test
    fun `a no keeps them out, and only the first answer counts`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        ali.await<ServerMessage.RoomUpdate>()
        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", playerId = "sara", appVersion = 99))
        sara.say(ClientMessage.ListLiveGames)
        val ref = sara.await<ServerMessage.LiveGames>().games.single().ref
        sara.say(ClientMessage.AskToJoin(ref))
        ali.await<ServerMessage.JoinAsked>()
        ali.say(ClientMessage.AnswerJoin("sara", yes = false))
        ali.say(ClientMessage.AnswerJoin("sara", yes = true))
        val answer = sara.await<ServerMessage.JoinAnswer>()
        assertTrue(!answer.yes && answer.code == null)
    }

    @Test
    fun `a player away is called back once, however many of their turns go by`() = testApplication {
        val told = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()
        val pusher = object : com.teera.room.Pusher {
            override fun canReach(playerId: String) = true
            override fun notify(playerId: String, title: String, body: String, data: Map<String, String>) {
                told += playerId to title
            }
        }
        // A one-second clock, so Ali's turns are played for him and come round quickly.
        application { gameServer(registry = RoomRegistry(turnSeconds = 1), pusher = pusher) }
        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.await<ServerMessage.Welcome>()
        ali.say(ClientMessage.CreateRoom(Rules.DEFAULT))
        ali.await<ServerMessage.RoomUpdate>()
        (1..3).forEach { ali.say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        ali.await<ServerMessage.RoomUpdate> { it.room.seats.count { s -> s.isBot } == 3 }
        ali.say(ClientMessage.StartMatch)
        ali.say(ClientMessage.Away(true))

        // Count Ali's turns as the table plays them for him.
        var hisTurns = 0
        var lastKey = ""
        withTimeout(40_000) {
            while (hisTurns < 3) {
                val view = ali.await<ServerMessage.GameUpdate>().view
                val key = "${view.gameNumber}:${view.board.size}:${view.phase}"
                if (view.isYourMove && key != lastKey) { hisTurns++; lastKey = key }
            }
        }
        assertEquals(1, told.count { it == ("ali" to "دورك بطيرة!") }, "three turns away, one notification: $told")
    }

    @Test
    fun `a tournament is made, its match opens a room with the teams on the chairs, and the organiser settles results`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }
        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", playerId = "ali", appVersion = 99))
        ali.await<ServerMessage.Welcome>()

        ali.say(ClientMessage.CreateTournament("بطولة الجمعة", listOf("علي وعبود", "حسن وسيف", "عمر ونور", "سارة ومريم")))
        val made = assertNotNull(ali.await<ServerMessage.TournamentIs>().tournament)
        assertEquals(2, made.rounds.size)

        ali.say(ClientMessage.PlayTournamentMatch(made.id, 0, 0))
        val room = ali.await<ServerMessage.RoomUpdate>().room
        assertEquals(listOf("علي وعبود", "حسن وسيف"), room.teamNames)
        assertTrue(room.tournament!!.startsWith("بطولة الجمعة"))
        val withRoom = assertNotNull(ali.await<ServerMessage.TournamentIs>().tournament)
        assertEquals(room.code, withRoom.rounds[0][0].roomCode)

        // A friend opens it by its code and sees the same; only the organiser settles it.
        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", playerId = "sara", appVersion = 99))
        sara.await<ServerMessage.Welcome>()
        sara.say(ClientMessage.SetTournamentWinner(made.id, 0, 1, 2))
        assertEquals(null, sara.await<ServerMessage.TournamentIs>().tournament!!.rounds[0][1].winner)
        ali.say(ClientMessage.SetTournamentWinner(made.id, 0, 1, 2))
        val settled = ali.await<ServerMessage.TournamentIs> { it.tournament?.rounds?.get(0)?.get(1)?.winner == 2 }.tournament!!
        assertEquals(2, settled.rounds[1][0].b)
    }

    @Test
    fun `the developer's numbers open only with the password`() = testApplication {
        application { gameServer(adminKey = "secret123") }
        val ask = client.get("/admin")
        assertEquals(200, ask.status.value)
        assertTrue("type=\"password\"" in ask.bodyAsText() && "فاتحين اللعبة" !in ask.bodyAsText())
        val wrong = client.get("/admin?key=wrong")
        assertEquals(403, wrong.status.value)
        assertTrue("الباسورد غلط" in wrong.bodyAsText() && "فاتحين اللعبة" !in wrong.bodyAsText())
        val page = client.get("/admin?key=secret123").bodyAsText()
        assertTrue("طيرة · الأرقام" in page && "فاتحين اللعبة" in page)
        // A stray space from copying the password still opens it.
        assertTrue("فاتحين اللعبة" in client.get("/admin?key=%20secret123%20").bodyAsText())
    }

    @Test
    fun `the developer's page says when Render has no password`() = testApplication {
        application { gameServer(adminKey = null) }
        val none = client.get("/admin?key=anything")
        assertEquals(503, none.status.value)
        assertTrue("ADMIN_KEY" in none.bodyAsText() && "فاتحين اللعبة" !in none.bodyAsText())
    }

    @Test
    fun `the developer's page refuses a short password`() = testApplication {
        application { gameServer(adminKey = "abc") }
        val short = client.get("/admin?key=abc")
        assertEquals(503, short.status.value)
        assertTrue("أقصر من 6" in short.bodyAsText() && "فاتحين اللعبة" !in short.bodyAsText())
    }

    @Test
    fun `the download page hands out the latest build`() = testApplication {
        application { gameServer() }
        val page = client.get("/download").bodyAsText()
        assertTrue("href=\"/teera.apk\"" in page && "نزّل اللعبة" in page && com.teera.room.LatestApp.name in page)
        assertTrue("{{" !in page, "every blank in the page is filled")
        assertEquals(page, client.get("/").bodyAsText(), "the bare address is the same page")
        // The pictures it shows are all there, and nothing else is handed out.
        Regex("/site/([a-z0-9-]+\\.(?:jpg|png))").findAll(page).map { it.groupValues[1] }.toSet().forEach {
            val file = client.get("/site/$it")
            assertEquals(200, file.status.value, it)
            assertTrue(file.body<ByteArray>().size > 1000, it)
        }
        assertEquals(404, client.get("/site/index.html").status.value)
        assertEquals(404, client.get("/site/..%2Fsite%2Findex.html").status.value)
        val noFollow = createClient { followRedirects = false }
        val apk = noFollow.get("/teera.apk")
        assertEquals(302, apk.status.value)
        assertEquals(com.teera.room.APK_URL, apk.headers["Location"])
    }

    @Test
    fun `following a room's link after the server lost it puts everyone back at one table`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }

        val ali = ws.webSocketSession("/ws")
        ali.say(ClientMessage.Hello("Ali", appVersion = 99))
        ali.await<ServerMessage.Welcome>()
        ali.say(ClientMessage.JoinRoom("KHAN", rejoin = true))
        assertEquals("KHAN", ali.await<ServerMessage.RoomUpdate>().room.code, "reopened under the same code")

        val sara = ws.webSocketSession("/ws")
        sara.say(ClientMessage.Hello("Sara", appVersion = 99))
        sara.await<ServerMessage.Welcome>()
        sara.say(ClientMessage.JoinRoom("KHAN", rejoin = true))
        val room = sara.await<ServerMessage.RoomUpdate> { it.room.seats.any { s -> s.name == "Sara" } }.room
        assertTrue(room.seats.any { it.name == "Ali" }, "the same table, not a second one")

        // A code typed by hand that is not a room is still refused, not opened.
        sara.say(ClientMessage.JoinRoom("ZZZZ"))
        assertEquals(ErrorCode.ROOM_NOT_FOUND, sara.await<ServerMessage.Failure> { it.code != ErrorCode.INTERNAL }.code)

        ali.close(); sara.close()
    }
}
