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

/** Plays [room] to the end, the human taking their first legal move, the bots their own. */
private fun playOut(room: Room, human: String): List<Room.Happening> {
    val seen = mutableListOf<Room.Happening>()
    repeat(20_000) {
        seen += room.takeHappenings()
        val game = room.game ?: return seen
        when {
            game.phase == Phase.MATCH_OVER -> return seen + room.takeHappenings()
            game.phase == Phase.GAME_OVER -> room.nextGame(human)
            room.awaitingBot() -> room.stepBot()
            else -> room.move(human, room.viewFor(human)?.legalMoves?.firstOrNull() ?: return seen)
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

        say(ClientMessage.Hello("Ali", playerId = "ali-1"))
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
        host.send(Protocol.encode(ClientMessage.Hello("Ali")))
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
}
