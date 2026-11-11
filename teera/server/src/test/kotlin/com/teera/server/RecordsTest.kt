package com.teera.server

import com.teera.core.ClientMessage
import com.teera.core.Difficulty
import com.teera.core.GameEvent
import com.teera.core.Protocol
import com.teera.core.Room
import com.teera.core.Rules
import com.teera.core.ServerMessage
import com.teera.core.Phase
import com.teera.room.gameServer
import com.zaxxer.hikari.HikariConfig
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import org.h2.jdbcx.JdbcDataSource

/** Plays [room] to the end, each person taking their first legal move, the bots their own. */
private fun playOut(room: Room, vararg humans: String): List<Room.Happening> {
    val seen = mutableListOf<Room.Happening>()
    repeat(20_000) {
        seen += room.takeHappenings()
        val game = room.game ?: return seen
        when {
            game.phase == Phase.MATCH_OVER -> return seen + room.takeHappenings()
            game.phase == Phase.GAME_OVER -> room.nextGame(humans.first())
            room.awaitingBot() -> room.stepBot()
            else -> {
                val mover = humans.firstOrNull { room.viewFor(it)?.isYourMove == true } ?: return seen
                room.move(mover, room.viewFor(mover)!!.legalMoves.first())
            }
        }
    }
    return seen
}

/**
 * The books, written and read back. Against H2 always; against a real Postgres too when
 * TEERA_TEST_DATABASE_URL names one.
 */
class RecordsTest {
    private fun h2(): SqlRecords = SqlRecords(
        JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        },
    )

    private fun databases(): List<Pair<String, SqlRecords>> = listOfNotNull(
        "h2" to h2(),
        System.getenv("TEERA_TEST_DATABASE_URL")?.let { "postgres" to SqlRecords.connect(it) },
    )

    /** A finished match: the human in seat 0, three bots. */
    private fun match(playerId: String, name: String, target: Int, seed: Long): List<Room.Happening> {
        val room = Room("BOOK", playerId, Rules(target = target))
        room.join(playerId, name)
        (1..3).forEach { room.setBot(playerId, it, true, Difficulty.NORMAL) }
        room.start(playerId, seed)
        return playOut(room, playerId)
    }

    @Test
    fun `a played match is kept and reads back as the player's record`() {
        for ((label, records) in databases()) {
            val me = "ali-" + UUID.randomUUID()
            records.playerSeen(me, "Ali", "p:2")
            val played = (1..2).map { match(me, "Ali", target = 60, seed = it.toLong()) }
            played.forEachIndexed { index, happenings ->
                val id = UUID.randomUUID().toString()
                happenings.forEach { records.happened(id, "BK0$index", it) }
            }
            records.flush()

            val record = assertNotNull(runBlocking { records.recordOf(me) }, label)
            val ends = played.map { it.filterIsInstance<Room.Happening.MatchFinished>().single() }
            val games = played.sumOf { it.count { h -> h is Room.Happening.GameFinished } }
            assertEquals(2, record.matches, label)
            assertEquals(ends.count { it.losingTeam != 0 }, record.wins, label)
            assertEquals(ends.count { it.losingTeam == 0 }, record.losses, label)
            assertEquals(games, record.games, label)
            assertNotNull(record.firstSeenEpochMs, label)
            assertEquals(2, record.recent.size, label)
            val latest = record.recent.first()
            assertEquals("بوت", latest.partner, label)
            assertEquals(ends.last().scores[0], latest.ourScore, label)
            assertEquals(ends.last().losingTeam != 0, latest.won, label)
        }
    }

    @Test
    fun `each game's steps are kept, readable as the game's own events`() {
        val records = h2()
        val happenings = match("p", "P", target = 40, seed = 3)
        happenings.forEach { records.happened("m1", "STEP", it) }
        records.flush()
        val first = happenings.filterIsInstance<Room.Happening.GameFinished>().first()
        records.source.connection.use { db ->
            db.prepareStatement("SELECT steps FROM games WHERE match_id = 'm1' AND game_number = 1").use {
                it.executeQuery().use { row ->
                    assertTrue(row.next())
                    val steps = Protocol.json.decodeFromString(ListSerializer(GameEvent.serializer()), row.getString(1))
                    assertEquals(first.steps, steps)
                }
            }
        }
    }

    @Test
    fun `matches between people rank them, and a match opens game by game with its steps`() {
        for ((label, records) in databases()) {
            val ali = "ali-" + UUID.randomUUID()
            val sara = "sara-" + UUID.randomUUID()
            records.playerSeen(ali, "Ali", "p:1")
            records.playerSeen(sara, "Sara", "p:2")
            val room = Room("RANK", ali, Rules(target = 60))
            room.join(ali, "Ali")
            room.join(sara, "Sara")
            room.setBot(ali, 2, true, Difficulty.NORMAL)
            room.setBot(ali, 3, true, Difficulty.NORMAL)
            room.start(ali, seed = 11)
            val happenings = playOut(room, ali, sara)
            val id = UUID.randomUUID().toString()
            happenings.forEach { records.happened(id, "RANK", it) }
            records.flush()

            val board = assertNotNull(runBlocking { records.leaderboard() }, label)
            val loser = happenings.filterIsInstance<Room.Happening.MatchFinished>().single().losingTeam
            val winner = if (loser == 0) sara else ali
            val ranked = board.players.filter { it.playerId == ali || it.playerId == sara }
            assertEquals(2, ranked.size, label)
            assertEquals(winner, ranked.maxBy { it.rating }.playerId, label)
            assertTrue(ranked.first { it.playerId == winner }.rating > com.teera.core.Ranking.START, label)

            val record = assertNotNull(runBlocking { records.recordOf(winner) }, label)
            assertNotNull(record.rank, label)
            assertEquals(id, record.recent.first().id, label)

            val detail = assertNotNull(runBlocking { records.matchDetail(id) }, label)
            val games = happenings.filterIsInstance<Room.Happening.GameFinished>()
            assertEquals(games.size, detail.games.size, label)
            assertEquals(games.first().steps, detail.games.first().steps, label)
            assertEquals(listOf("Ali", "Sara", "بوت", "بوت"), detail.names, label)
            assertEquals(loser, detail.losingTeam, label)
        }
    }

    @Test
    fun `a tournament is kept and read back, and titles go to whoever leads`() {
        for ((label, records) in databases()) {
            val t = com.teera.core.Tournament.create("T" + UUID.randomUUID().toString().take(4).uppercase(), "بطولة", "ali", listOf("a", "b", "c", "d"), 1)!!
                .withWinner(0, 0, 1)!!
            records.saveTournament(t)
            records.saveTournament(t.withWinner(0, 1, 3)!!)
            records.flush()
            val back = assertNotNull(runBlocking { records.loadTournament(t.id) }, label)
            assertEquals(3, back.rounds[1][0].b, label)

            val ali = "ali-" + UUID.randomUUID()
            val sara = "sara-" + UUID.randomUUID()
            records.playerSeen(ali, "Ali", "")
            records.playerSeen(sara, "Sara", "")
            val room = Room("TTL", ali, Rules(target = 60))
            room.join(ali, "Ali"); room.join(sara, "Sara")
            room.setBot(ali, 2, true, Difficulty.NORMAL); room.setBot(ali, 3, true, Difficulty.NORMAL)
            room.start(ali, seed = 21)
            val id = UUID.randomUUID().toString()
            playOut(room, ali, sara).forEach { records.happened(id, "TTL", it) }
            records.flush()
            val board = assertNotNull(runBlocking { records.leaderboard() }, label)
            assertTrue(board.titles.any { it.title == "الملازم" }, label)
            val winner = board.players.first { it.playerId == ali || it.playerId == sara }.playerId
            val record = assertNotNull(runBlocking { records.recordOf(winner) }, label)
            assertTrue("أول فوز" in record.badges, label)
        }
    }

    @Test
    fun `the developer's numbers count today's people, matches and games`() {
        val records = h2()
        records.playerSeen("ali", "Ali", "", 25)
        records.playerSeen("ali", "Ali", "", 25) // twice in a day counts once
        records.playerSeen("sara", "Sara", "", 22)
        val happenings = match("ali", "Ali", target = 40, seed = 5)
        happenings.forEach { records.happened("m-stats", "STAT", it) }
        records.flush()
        val stats = assertNotNull(runBlocking { records.adminStats() })
        assertEquals(2, stats.openedToday)
        assertEquals(2, stats.totalPlayers)
        assertEquals(1, stats.matchesToday)
        assertEquals(1, stats.playedToday)
        assertTrue(stats.gamesToday > 0)
        assertEquals(mapOf(25 to 1, 22 to 1), stats.versions)
        assertEquals(14, stats.days.size)
    }

    @Test
    fun `a stranger has an empty record, and a dead database is no record rather than a crash`() {
        val records = h2()
        val empty = assertNotNull(runBlocking { records.recordOf("nobody") })
        assertEquals(0, empty.matches)
        assertNull(empty.firstSeenEpochMs)

        val dead = SqlRecords.connect("postgres://u:p@127.0.0.1:1/none")
        dead.playerSeen("x", "X", "")
        dead.flush()
        assertNull(runBlocking { dead.recordOf("x") })
    }

    @Test
    fun `host addresses become what the Java driver reads`() {
        val config = HikariConfig()
        SqlRecords.applyAddress(
            config,
            "postgresql://teera_owner:s3cr%40t@ep-cool-1.eu-central-1.aws.neon.tech/teera?sslmode=require&channel_binding=require",
        )
        assertEquals("jdbc:postgresql://ep-cool-1.eu-central-1.aws.neon.tech/teera?sslmode=require", config.jdbcUrl)
        assertEquals("teera_owner", config.username)
        assertEquals("s3cr@t", config.password)

        SqlRecords.applyAddress(config, "postgres://a:b@db.example.com:6543/x")
        assertEquals("jdbc:postgresql://db.example.com:6543/x", config.jdbcUrl)
    }

    @Test
    fun `the server keeps the books for a match played through it, and hands a player their record`() = testApplication {
        val records = h2()
        application { gameServer(records = records) }
        val ws = createClient { install(WebSockets) }
        val host = ws.webSocketSession("/ws")
        suspend fun say(message: ClientMessage) = host.send(Protocol.encode(message))
        suspend fun <T> next(pick: (ServerMessage) -> T?): T = withTimeout(10_000) {
            while (true) {
                val frame = host.incoming.receive() as? Frame.Text ?: continue
                pick(Protocol.decodeServer(frame.readText()))?.let { return@withTimeout it }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }

        say(ClientMessage.Hello("Ali", playerId = "ali-1", appVersion = 99))
        next { it as? ServerMessage.Welcome }
        say(ClientMessage.CreateRoom(Rules.DEFAULT))
        next { it as? ServerMessage.RoomUpdate }
        (1..3).forEach { say(ClientMessage.SetBot(it, enabled = true, difficulty = Difficulty.NORMAL)) }
        next { (it as? ServerMessage.RoomUpdate)?.takeIf { u -> u.room.seats.count { s -> s.isBot } == 3 } }
        say(ClientMessage.StartMatch)
        next { it as? ServerMessage.GameUpdate }
        records.flush()

        say(ClientMessage.MyRecord)
        val record = assertNotNull(next { it as? ServerMessage.Record }.record)
        // Started, not finished: in the list, not yet counted as won or lost.
        assertEquals(0, record.matches)
        assertEquals(1, record.recent.size)
        assertNull(record.recent.single().won)
        assertEquals(listOf("بوت", "بوت"), record.recent.single().opponents)
    }

    @Test
    fun `a server with no books says so`() = testApplication {
        application { gameServer() }
        val ws = createClient { install(WebSockets) }
        val host = ws.webSocketSession("/ws")
        host.send(Protocol.encode(ClientMessage.Hello("Ali", appVersion = 99)))
        host.send(Protocol.encode(ClientMessage.MyRecord))
        val answer = withTimeout(10_000) {
            var found: ServerMessage.Record? = null
            while (found == null) {
                val frame = host.incoming.receive() as? Frame.Text ?: continue
                found = Protocol.decodeServer(frame.readText()) as? ServerMessage.Record
            }
            found
        }
        assertNull(answer.record)
    }

    @Test
    fun `the developer's page keeps problems, bars and settings`() = runBlocking {
        for ((which, books) in databases()) {
            val stamp = UUID.randomUUID().toString().take(8)
            books.playerSeen("p-$stamp", "Hassan $stamp", "", appVersion = 27)
            books.saveFeedback(
                com.teera.room.FeedbackItem("f-$stamp", "p-$stamp", "Hassan", "problem", "البول ما ركب", 27, "KHAN", atEpochMs = 1_000),
            )
            books.setBanned("p-$stamp", banned = true, reason = "تجربة")
            books.saveSetting("minAppVersion-$stamp", "26")
            books.saveSetting("minAppVersion-$stamp", "27")
            books.flush()

            val item = books.feedback().first { it.id == "f-$stamp" }
            assertEquals("البول ما ركب" to "KHAN", item.text to item.roomCode, which)
            books.setFeedbackDone("f-$stamp", true)
            books.flush()
            assertTrue(books.feedback().first { it.id == "f-$stamp" }.done, which)

            val row = books.players("hassan $stamp").single()
            assertEquals(27, row.appVersion, which)
            assertTrue(row.banned, which)
            assertEquals(row, books.players(row.code).single(), "found by the short code too ($which)")
            assertEquals("تجربة", books.bannedPlayers()["p-$stamp"], which)
            assertEquals("27", books.loadSettings()["minAppVersion-$stamp"], which)

            books.setBanned("p-$stamp", banned = false)
            books.deleteFeedback("f-$stamp")
            books.flush()
            assertTrue("p-$stamp" !in books.bannedPlayers().keys, which)
            assertTrue(books.feedback().none { it.id == "f-$stamp" }, which)
        }
    }
}
