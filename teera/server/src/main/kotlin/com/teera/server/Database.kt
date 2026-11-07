package com.teera.server

import com.teera.core.GameEvent
import com.teera.core.MatchSummary
import com.teera.core.PlayerRecord
import com.teera.core.Protocol
import com.teera.core.Room
import com.teera.core.teamOf
import com.teera.room.Records
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.sql.Types
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Database")

/**
 * The server's books in a SQL database: Postgres in the cloud, H2 in the tests.
 *
 * Writes go onto one background thread in the order they happened, so a slow or sleeping
 * database never holds a table up; if it is down, the record is lost and the game goes
 * on. Times are kept as milliseconds since 1970, the way the rest of the game keeps them.
 */
class SqlRecords(
    internal val source: DataSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : Records {

    // One writer, a bounded queue: a database that is gone for good must not eat the memory.
    private val writer = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(10_000),
        { task -> Thread(task, "records").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy(),
    )

    @Volatile
    private var ready = false

    private fun <T> withConnection(block: (Connection) -> T): T {
        source.connection.use { connection ->
            if (!ready) {
                createTables(connection)
                ready = true
            }
            return block(connection)
        }
    }

    private fun write(what: String, block: (Connection) -> Unit) {
        writer.execute {
            try {
                withConnection(block)
            } catch (e: Exception) {
                log.warn("could not record {}: {}", what, e.message)
            }
        }
    }

    /** Waits for every write so far to land. For tests and shutdown. */
    fun flush() {
        writer.submit {}.get(30, TimeUnit.SECONDS)
    }

    override fun playerSeen(playerId: String, name: String, avatar: String) = write("player") { db ->
        val now = clock()
        val updated = db.prepareStatement("UPDATE players SET name = ?, avatar = ?, last_seen = ? WHERE id = ?").use {
            it.setString(1, name); it.setString(2, avatar); it.setLong(3, now); it.setString(4, playerId)
            it.executeUpdate()
        }
        if (updated == 0) {
            db.prepareStatement("INSERT INTO players (id, name, avatar, first_seen, last_seen) VALUES (?, ?, ?, ?, ?)").use {
                it.setString(1, playerId); it.setString(2, name); it.setString(3, avatar)
                it.setLong(4, now); it.setLong(5, now)
                it.executeUpdate()
            }
        }
    }

    override fun happened(matchId: String, roomCode: String, happening: Room.Happening) = when (happening) {
        is Room.Happening.MatchStarted -> write("match start") { db ->
            db.autoCommit = false
            try {
                db.prepareStatement(
                    "INSERT INTO matches (id, room_code, started_at, target, score0, score1, games) VALUES (?, ?, ?, ?, 0, 0, 0)",
                ).use {
                    it.setString(1, matchId); it.setString(2, roomCode); it.setLong(3, clock())
                    it.setInt(4, happening.rules.target)
                    it.executeUpdate()
                }
                db.prepareStatement(
                    "INSERT INTO match_seats (match_id, seat, player_id, name, is_bot) VALUES (?, ?, ?, ?, ?)",
                ).use { insert ->
                    for (seat in happening.seats) {
                        insert.setString(1, matchId)
                        insert.setInt(2, seat.seat)
                        if (seat.isBot || seat.playerId.isBlank()) insert.setNull(3, Types.VARCHAR) else insert.setString(3, seat.playerId)
                        insert.setString(4, seat.name.take(48))
                        insert.setBoolean(5, seat.isBot)
                        insert.addBatch()
                    }
                    insert.executeBatch()
                }
                db.commit()
            } catch (e: Exception) {
                db.rollback()
                throw e
            } finally {
                db.autoCommit = true
            }
        }

        is Room.Happening.GameFinished -> write("game") { db ->
            val result = happening.result
            db.prepareStatement(
                """INSERT INTO games (match_id, game_number, ended_at, end_kind, winner_seat,
                   scored0, scored1, score0, score1, hand_pips, steps)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            ).use {
                it.setString(1, matchId)
                it.setInt(2, result.gameNumber)
                it.setLong(3, clock())
                it.setString(4, result.end.name)
                if (result.winnerSeat == null) it.setNull(5, Types.INTEGER) else it.setInt(5, result.winnerSeat!!)
                it.setInt(6, result.scored.getOrElse(0) { 0 })
                it.setInt(7, result.scored.getOrElse(1) { 0 })
                it.setInt(8, happening.scores.getOrElse(0) { 0 })
                it.setInt(9, happening.scores.getOrElse(1) { 0 })
                it.setString(10, result.handPips.joinToString(","))
                it.setString(11, Protocol.json.encodeToString(STEPS, happening.steps))
                it.executeUpdate()
            }
            db.prepareStatement("UPDATE matches SET score0 = ?, score1 = ?, games = ? WHERE id = ?").use {
                it.setInt(1, happening.scores.getOrElse(0) { 0 })
                it.setInt(2, happening.scores.getOrElse(1) { 0 })
                it.setInt(3, result.gameNumber)
                it.setString(4, matchId)
                it.executeUpdate()
            }
        }

        is Room.Happening.MatchFinished -> write("match end") { db ->
            db.prepareStatement(
                "UPDATE matches SET ended_at = ?, losing_team = ?, score0 = ?, score1 = ?, games = ? WHERE id = ?",
            ).use {
                it.setLong(1, clock())
                it.setInt(2, happening.losingTeam)
                it.setInt(3, happening.scores.getOrElse(0) { 0 })
                it.setInt(4, happening.scores.getOrElse(1) { 0 })
                it.setInt(5, happening.games)
                it.setString(6, matchId)
                it.executeUpdate()
            }
        }
    }

    override suspend fun recordOf(playerId: String): PlayerRecord? = withContext(Dispatchers.IO) {
        try {
            withConnection { db -> readRecord(db, playerId) }
        } catch (e: Exception) {
            log.warn("could not read a record: {}", e.message)
            null
        }
    }

    private fun readRecord(db: Connection, playerId: String): PlayerRecord {
        val firstSeen = db.prepareStatement("SELECT first_seen FROM players WHERE id = ?").use {
            it.setString(1, playerId)
            it.executeQuery().use { row -> if (row.next()) row.getLong(1) else null }
        }

        var matches = 0; var wins = 0; var losses = 0
        db.prepareStatement(
            """SELECT COUNT(*),
                      SUM(CASE WHEN m.losing_team <> MOD(s.seat, 2) THEN 1 ELSE 0 END),
                      SUM(CASE WHEN m.losing_team = MOD(s.seat, 2) THEN 1 ELSE 0 END)
               FROM matches m JOIN match_seats s ON s.match_id = m.id
               WHERE s.player_id = ? AND m.ended_at IS NOT NULL""",
        ).use {
            it.setString(1, playerId)
            it.executeQuery().use { row ->
                if (row.next()) { matches = row.getInt(1); wins = row.getInt(2); losses = row.getInt(3) }
            }
        }

        var games = 0; var finishes = 0; var teeras = 0
        db.prepareStatement(
            """SELECT COUNT(*),
                      SUM(CASE WHEN g.end_kind = 'X' AND g.winner_seat = s.seat THEN 1 ELSE 0 END),
                      SUM(CASE WHEN g.end_kind = 'TEERA' AND g.winner_seat = s.seat THEN 1 ELSE 0 END)
               FROM games g JOIN match_seats s ON s.match_id = g.match_id
               WHERE s.player_id = ?""",
        ).use {
            it.setString(1, playerId)
            it.executeQuery().use { row ->
                if (row.next()) { games = row.getInt(1); finishes = row.getInt(2); teeras = row.getInt(3) }
            }
        }

        class Mine(val id: String, val started: Long, val code: String, val ended: Boolean, val losing: Int?,
                   val scores: List<Int>, val games: Int, val seat: Int)
        val mine = db.prepareStatement(
            """SELECT m.id, m.started_at, m.room_code, m.ended_at, m.losing_team, m.score0, m.score1, m.games, s.seat
               FROM matches m JOIN match_seats s ON s.match_id = m.id
               WHERE s.player_id = ?
               ORDER BY m.started_at DESC""",
        ).use {
            it.setString(1, playerId)
            it.maxRows = RECENT
            it.executeQuery().use { row ->
                buildList {
                    while (row.next()) {
                        add(
                            Mine(
                                row.getString(1), row.getLong(2), row.getString(3), row.getObject(4) != null,
                                row.getInt(5).takeUnless { row.wasNull() }, listOf(row.getInt(6), row.getInt(7)),
                                row.getInt(8), row.getInt(9),
                            ),
                        )
                    }
                }
            }
        }

        val names = HashMap<String, Map<Int, String>>()
        if (mine.isNotEmpty()) {
            val marks = mine.joinToString(",") { "?" }
            db.prepareStatement("SELECT match_id, seat, name, is_bot FROM match_seats WHERE match_id IN ($marks)").use {
                mine.forEachIndexed { index, match -> it.setString(index + 1, match.id) }
                it.executeQuery().use { row ->
                    while (row.next()) {
                        val name = if (row.getBoolean(4)) "بوت" else row.getString(3)
                        names[row.getString(1)] = names[row.getString(1)].orEmpty() + (row.getInt(2) to name)
                    }
                }
            }
        }

        val recent = mine.map { match ->
            val seats = names[match.id].orEmpty()
            val team = teamOf(match.seat)
            MatchSummary(
                startedAtEpochMs = match.started,
                roomCode = match.code,
                won = if (match.ended && match.losing != null) match.losing != team else null,
                partner = seats[(match.seat + 2) % 4].orEmpty(),
                opponents = listOfNotNull(seats[(match.seat + 1) % 4], seats[(match.seat + 3) % 4]),
                ourScore = match.scores[team],
                theirScore = match.scores[1 - team],
                games = match.games,
            )
        }
        return PlayerRecord(matches, wins, losses, games, finishes, teeras, firstSeen, recent)
    }

    companion object {
        private const val RECENT = 10
        private val STEPS = ListSerializer(GameEvent.serializer())

        /** The tables, made if they are not there yet. Plain SQL that Postgres and H2 both read. */
        fun createTables(db: Connection) {
            db.createStatement().use { sql ->
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS players (
                        id VARCHAR(64) PRIMARY KEY,
                        name VARCHAR(48) NOT NULL,
                        avatar VARCHAR(16) NOT NULL DEFAULT '',
                        first_seen BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL)""",
                )
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS matches (
                        id VARCHAR(36) PRIMARY KEY,
                        room_code VARCHAR(8) NOT NULL,
                        started_at BIGINT NOT NULL,
                        ended_at BIGINT,
                        target INT NOT NULL,
                        losing_team INT,
                        score0 INT NOT NULL DEFAULT 0,
                        score1 INT NOT NULL DEFAULT 0,
                        games INT NOT NULL DEFAULT 0)""",
                )
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS match_seats (
                        match_id VARCHAR(36) NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
                        seat INT NOT NULL,
                        player_id VARCHAR(64),
                        name VARCHAR(48) NOT NULL,
                        is_bot BOOLEAN NOT NULL,
                        PRIMARY KEY (match_id, seat))""",
                )
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS games (
                        match_id VARCHAR(36) NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
                        game_number INT NOT NULL,
                        ended_at BIGINT NOT NULL,
                        end_kind VARCHAR(8) NOT NULL,
                        winner_seat INT,
                        scored0 INT NOT NULL,
                        scored1 INT NOT NULL,
                        score0 INT NOT NULL,
                        score1 INT NOT NULL,
                        hand_pips VARCHAR(32) NOT NULL,
                        steps TEXT NOT NULL,
                        PRIMARY KEY (match_id, game_number))""",
                )
                sql.execute("CREATE INDEX IF NOT EXISTS match_seats_player ON match_seats (player_id)")
                sql.execute("CREATE INDEX IF NOT EXISTS matches_started ON matches (started_at)")
            }
        }

        /**
         * Opens the database named by a `postgres://user:password@host/db` address, the form
         * Neon, Render and most hosts hand out, or by a `jdbc:` one.
         */
        fun connect(address: String): SqlRecords {
            val config = HikariConfig().apply {
                applyAddress(this, address.trim())
                poolName = "records"
                maximumPoolSize = 3
                minimumIdle = 0
                // A free database sleeps when idle and drops its connections; never hold one long.
                idleTimeout = 60_000
                maxLifetime = 300_000
                connectionTimeout = 20_000
                // Start even when the database is asleep or down: the game does not need it.
                initializationFailTimeout = -1
            }
            return SqlRecords(HikariDataSource(config))
        }

        internal fun applyAddress(config: HikariConfig, address: String) {
            if (address.startsWith("jdbc:")) {
                config.jdbcUrl = address
                return
            }
            val uri = URI(address)
            require(uri.scheme == "postgres" || uri.scheme == "postgresql") { "Not a Postgres address" }
            uri.rawUserInfo?.split(":", limit = 2)?.let { parts ->
                config.username = URLDecoder.decode(parts[0], Charsets.UTF_8)
                if (parts.size > 1) config.password = URLDecoder.decode(parts[1], Charsets.UTF_8)
            }
            // Keep sslmode and the like; drop what the Java driver does not read.
            val query = uri.rawQuery?.split("&")
                ?.filter { it.isNotBlank() && !it.startsWith("channel_binding=") }
                ?.joinToString("&")
                .orEmpty()
            val port = if (uri.port > 0) ":${uri.port}" else ""
            config.jdbcUrl = "jdbc:postgresql://${uri.host}$port${uri.rawPath}" + if (query.isEmpty()) "" else "?$query"
        }
    }
}
