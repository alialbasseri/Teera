package com.teera.server

import com.teera.core.GameEvent
import com.teera.core.Leaderboard
import com.teera.core.MatchDetail
import com.teera.core.MatchSummary
import com.teera.core.PlayedGame
import com.teera.core.RankedPair
import com.teera.core.RankedPlayer
import com.teera.core.Ranking
import com.teera.core.PlayerRecord
import com.teera.core.Protocol
import com.teera.core.Room
import com.teera.core.teamOf
import com.teera.room.AdminStats
import com.teera.room.FeedbackItem
import com.teera.room.PlayerRow
import com.teera.room.DayStat
import com.teera.room.Records
import java.util.concurrent.ConcurrentHashMap
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
) : Records, TokenStore {

    /** Kept with the player, so a restart does not forget where to reach them. Called off the game's threads. */
    override fun save(playerId: String, token: String?) {
        withConnection { db ->
            db.prepareStatement("UPDATE players SET push_token = ? WHERE id = ?").use {
                if (token == null) it.setNull(1, Types.VARCHAR) else it.setString(1, token)
                it.setString(2, playerId)
                it.executeUpdate()
            }
        }
    }

    override fun load(playerId: String): String? = withConnection { db ->
        db.prepareStatement("SELECT push_token FROM players WHERE id = ?").use {
            it.setString(1, playerId)
            it.executeQuery().use { row -> if (row.next()) row.getString(1) else null }
        }
    }

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

    /** Days a player's opening of the app is already counted for, so each counts once a day. */
    private val visitsCounted = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override fun playerSeen(playerId: String, name: String, avatar: String, appVersion: Int) = write("player") { db ->
        val now = clock()
        val updated = db.prepareStatement("UPDATE players SET name = ?, avatar = ?, last_seen = ?, app_version = ? WHERE id = ?").use {
            it.setString(1, name); it.setString(2, avatar); it.setLong(3, now); it.setInt(4, appVersion); it.setString(5, playerId)
            it.executeUpdate()
        }
        if (updated == 0) {
            db.prepareStatement("INSERT INTO players (id, name, avatar, first_seen, last_seen, app_version) VALUES (?, ?, ?, ?, ?, ?)").use {
                it.setString(1, playerId); it.setString(2, name); it.setString(3, avatar)
                it.setLong(4, now); it.setLong(5, now); it.setInt(6, appVersion)
                it.executeUpdate()
            }
        }
        // Who opened the app which day, for the developer's numbers.
        val day = dayOf(now)
        if (visitsCounted.add("$playerId|$day")) {
            val seen = db.prepareStatement("SELECT 1 FROM visits WHERE player_id = ? AND on_day = ?").use {
                it.setString(1, playerId); it.setString(2, day)
                it.executeQuery().use { row -> row.next() }
            }
            if (!seen) {
                db.prepareStatement("INSERT INTO visits (player_id, on_day) VALUES (?, ?)").use {
                    it.setString(1, playerId); it.setString(2, day)
                    it.executeUpdate()
                }
            }
        }
    }

    override fun setReminders(playerId: String, on: Boolean) = write("reminders") { db ->
        db.prepareStatement("UPDATE players SET reminders_off = ? WHERE id = ?").use {
            it.setBoolean(1, !on); it.setString(2, playerId)
            it.executeUpdate()
        }
    }

    // -- reminders ------------------------------------------------------------------

    /** Somebody a reminder can reach: a phone address, reminders on. */
    data class Reachable(val playerId: String, val name: String, val lastSeen: Long)

    fun reachable(): List<Reachable> = withConnection { db ->
        db.prepareStatement(
            "SELECT id, name, last_seen FROM players WHERE push_token IS NOT NULL AND (reminders_off IS NULL OR reminders_off = FALSE)",
        ).use {
            it.executeQuery().use { row ->
                buildList { while (row.next()) add(Reachable(row.getString(1), row.getString(2), row.getLong(3))) }
            }
        }
    }

    /** How many reminders this player has had in all; it picks the next line, so none repeats soon. */
    fun remindersSent(playerId: String): Int = withConnection { db ->
        db.prepareStatement("SELECT COUNT(*) FROM reminders WHERE player_id = ?").use {
            it.setString(1, playerId)
            it.executeQuery().use { row -> if (row.next()) row.getInt(1) else 0 }
        }
    }

    fun reminded(playerId: String, day: String, slot: Int): Boolean = withConnection { db ->
        db.prepareStatement("SELECT 1 FROM reminders WHERE player_id = ? AND on_day = ? AND slot = ?").use {
            it.setString(1, playerId); it.setString(2, day); it.setInt(3, slot)
            it.executeQuery().use { row -> row.next() }
        }
    }

    fun noteReminder(playerId: String, day: String, slot: Int, line: Int) = withConnection { db ->
        db.prepareStatement("INSERT INTO reminders (player_id, on_day, slot, line, sent_at) VALUES (?, ?, ?, ?, ?)").use {
            it.setString(1, playerId); it.setString(2, day); it.setInt(3, slot); it.setInt(4, line); it.setLong(5, clock())
            it.executeUpdate()
        }
    }

    // -- the developer's numbers --------------------------------------------------------

    override suspend fun adminStats(): AdminStats? = withContext(Dispatchers.IO) {
        try {
            withConnection { db -> readAdmin(db) }
        } catch (e: Exception) {
            log.warn("could not read the numbers: {}", e.message)
            null
        }
    }

    private fun readAdmin(db: Connection): AdminStats {
        val now = clock()
        val today = dayOf(now)
        val since = now - 15L * 24 * 3600 * 1000
        val month = now - 30L * 24 * 3600 * 1000
        fun count(sql: String, vararg args: Any): Int = db.prepareStatement(sql).use {
            args.forEachIndexed { i, a -> if (a is Long) it.setLong(i + 1, a) else it.setString(i + 1, a.toString()) }
            it.executeQuery().use { row -> if (row.next()) row.getInt(1) else 0 }
        }

        // Matches and games of the last two weeks, bucketed by Baghdad's day.
        class M(val id: String, val started: Long, val ended: Long?)
        val matches = db.prepareStatement("SELECT id, started_at, ended_at FROM matches WHERE started_at >= ?").use {
            it.setLong(1, since)
            it.executeQuery().use { row ->
                buildList { while (row.next()) add(M(row.getString(1), row.getLong(2), row.getLong(3).takeUnless { row.wasNull() })) }
            }
        }
        val lastGame = HashMap<String, Long>()
        val gamesByDay = HashMap<String, Int>()
        db.prepareStatement("SELECT match_id, ended_at FROM games WHERE ended_at >= ?").use {
            it.setLong(1, since)
            it.executeQuery().use { row ->
                while (row.next()) {
                    val at = row.getLong(2)
                    lastGame.merge(row.getString(1), at, ::maxOf)
                    gamesByDay.merge(dayOf(at), 1, Int::plus)
                }
            }
        }
        fun minutes(m: M): Int {
            val end = m.ended ?: lastGame[m.id] ?: m.started
            return ((end - m.started) / 60_000).toInt().coerceIn(0, 6 * 60)
        }
        val seatedByMatch = HashMap<String, MutableSet<String>>()
        if (matches.isNotEmpty()) {
            matches.map { it.id }.chunked(500).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                db.prepareStatement("SELECT match_id, player_id FROM match_seats WHERE player_id IS NOT NULL AND match_id IN ($marks)").use {
                    chunk.forEachIndexed { i, id -> it.setString(i + 1, id) }
                    it.executeQuery().use { row -> while (row.next()) seatedByMatch.getOrPut(row.getString(1)) { HashSet() } += row.getString(2) }
                }
            }
        }
        val openedByDay = HashMap<String, Int>()
        db.prepareStatement("SELECT on_day, COUNT(*) FROM visits WHERE on_day >= ? GROUP BY on_day").use {
            it.setString(1, dayOf(since))
            it.executeQuery().use { row -> while (row.next()) openedByDay[row.getString(1)] = row.getInt(2) }
        }
        val newByDay = HashMap<String, Int>()
        db.prepareStatement("SELECT first_seen FROM players WHERE first_seen >= ?").use {
            it.setLong(1, since)
            it.executeQuery().use { row -> while (row.next()) newByDay.merge(dayOf(row.getLong(1)), 1, Int::plus) }
        }
        val days = (13 downTo 0).map { back ->
            val day = dayOf(now - back * 24L * 3600 * 1000)
            val ofDay = matches.filter { dayOf(it.started) == day }
            DayStat(
                day = day,
                opened = openedByDay[day] ?: 0,
                played = ofDay.flatMap { seatedByMatch[it.id].orEmpty() }.toSet().size,
                matches = ofDay.size,
                games = gamesByDay[day] ?: 0,
                minutes = ofDay.sumOf(::minutes),
                newPlayers = newByDay[day] ?: 0,
            )
        }
        // Today's matches by the hour they started, Baghdad time.
        val hours = IntArray(24)
        matches.filter { dayOf(it.started) == today }.forEach {
            hours[java.time.Instant.ofEpochMilli(it.started).atZone(ZONE).hour]++
        }
        val todays = days.last()
        val finished = matches.filter { it.ended != null }

        val versions = LinkedHashMap<Int, Int>()
        db.prepareStatement("SELECT app_version, COUNT(*) FROM players WHERE last_seen >= ? GROUP BY app_version ORDER BY app_version DESC").use {
            it.setLong(1, month)
            it.executeQuery().use { row -> while (row.next()) versions[row.getInt(1)] = row.getInt(2) }
        }
        val top = db.prepareStatement(
            """SELECT p.name, COUNT(*) FROM match_seats s
               JOIN matches m ON m.id = s.match_id JOIN players p ON p.id = s.player_id
               WHERE m.started_at >= ? GROUP BY p.id, p.name ORDER BY 2 DESC""",
        ).use {
            it.setLong(1, month)
            it.maxRows = 10
            it.executeQuery().use { row -> buildList { while (row.next()) add(row.getString(1) to row.getInt(2)) } }
        }
        return AdminStats(
            totalPlayers = count("SELECT COUNT(*) FROM players"),
            newToday = count("SELECT COUNT(*) FROM players WHERE first_seen >= ?", startOfDay(now)),
            openedToday = todays.opened,
            playedToday = todays.played,
            matchesToday = todays.matches,
            finishedToday = finished.count { dayOf(it.ended!!) == today },
            gamesToday = todays.games,
            minutesToday = todays.minutes,
            totalMatches = count("SELECT COUNT(*) FROM matches"),
            totalGames = count("SELECT COUNT(*) FROM games"),
            averageMatchMinutes = if (finished.isEmpty()) 0 else finished.sumOf(::minutes) / finished.size,
            days = days,
            versions = versions,
            topPlayers = top,
            reachable = count("SELECT COUNT(*) FROM players WHERE push_token IS NOT NULL AND (reminders_off IS NULL OR reminders_off = FALSE)"),
            remindersToday = count("SELECT COUNT(*) FROM reminders WHERE on_day = ?", today),
            tournaments = count("SELECT COUNT(*) FROM tournaments"),
            hours = hours.toList(),
        )
    }

    // -- the developer's page: problems and suggestions, players, settings --------------

    override fun saveFeedback(item: FeedbackItem, image: ByteArray?) = write("feedback") { db ->
        db.prepareStatement(
            "INSERT INTO feedback (id, player_id, name, kind, body, app_version, room_code, at_ms, done, image) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)",
        ).use {
            if (image == null) it.setNull(9, Types.VARCHAR) else it.setString(9, java.util.Base64.getEncoder().encodeToString(image))
            it.setString(1, item.id); it.setString(2, item.playerId); it.setString(3, item.name.take(48))
            it.setString(4, item.kind); it.setString(5, item.text); it.setInt(6, item.appVersion)
            if (item.roomCode == null) it.setNull(7, Types.VARCHAR) else it.setString(7, item.roomCode)
            it.setLong(8, item.atEpochMs)
            it.executeUpdate()
        }
    }

    override suspend fun feedback(limit: Int): List<FeedbackItem> = read(emptyList()) { db ->
        db.prepareStatement(
            "SELECT id, player_id, name, kind, body, app_version, room_code, at_ms, done, CASE WHEN image IS NULL THEN 0 ELSE 1 END FROM feedback ORDER BY at_ms DESC",
        ).use {
            it.maxRows = limit
            it.executeQuery().use { row ->
                buildList {
                    while (row.next()) add(
                        FeedbackItem(
                            id = row.getString(1), playerId = row.getString(2), name = row.getString(3),
                            kind = row.getString(4), text = row.getString(5), appVersion = row.getInt(6),
                            roomCode = row.getString(7), atEpochMs = row.getLong(8), done = row.getBoolean(9),
                            hasImage = row.getInt(10) == 1,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun feedbackImage(id: String): ByteArray? = read(null) { db ->
        db.prepareStatement("SELECT image FROM feedback WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { row ->
                if (row.next()) row.getString(1)?.let { text -> java.util.Base64.getDecoder().decode(text) } else null
            }
        }
    }

    override fun setFeedbackDone(id: String, done: Boolean) = write("feedback done") { db ->
        db.prepareStatement("UPDATE feedback SET done = ? WHERE id = ?").use {
            it.setBoolean(1, done); it.setString(2, id)
            it.executeUpdate()
        }
    }

    override fun deleteFeedback(id: String) = write("feedback delete") { db ->
        db.prepareStatement("DELETE FROM feedback WHERE id = ?").use {
            it.setString(1, id)
            it.executeUpdate()
        }
    }

    override suspend fun players(query: String, limit: Int): List<PlayerRow> = read(emptyList()) { db ->
        val all = db.prepareStatement(
            """SELECT p.id, p.name, p.first_seen, p.last_seen, COALESCE(p.app_version, 0),
                      CASE WHEN p.push_token IS NULL THEN 0 ELSE 1 END,
                      CASE WHEN p.banned = TRUE THEN 1 ELSE 0 END, COALESCE(p.ban_reason, ''),
                      (SELECT COUNT(*) FROM match_seats s WHERE s.player_id = p.id)
               FROM players p ORDER BY p.last_seen DESC""",
        ).use {
            it.maxRows = 5_000
            it.executeQuery().use { row ->
                buildList {
                    while (row.next()) add(
                        PlayerRow(
                            id = row.getString(1), code = com.teera.core.shortCodeOf(row.getString(1)), name = row.getString(2),
                            firstSeen = row.getLong(3), lastSeen = row.getLong(4), appVersion = row.getInt(5),
                            reachable = row.getInt(6) == 1, banned = row.getInt(7) == 1, banReason = row.getString(8),
                            matches = row.getInt(9),
                        ),
                    )
                }
            }
        }
        val q = query.trim()
        (if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) || it.code.equals(q, ignoreCase = true) })
            .take(limit)
    }

    override fun setBanned(playerId: String, banned: Boolean, reason: String) = write("ban") { db ->
        val updated = db.prepareStatement("UPDATE players SET banned = ?, ban_reason = ? WHERE id = ?").use {
            it.setBoolean(1, banned); it.setString(2, reason.take(200)); it.setString(3, playerId)
            it.executeUpdate()
        }
        if (updated == 0 && banned) {
            // Barred before the books ever saw them: keep a row so the bar survives a restart.
            val now = clock()
            db.prepareStatement("INSERT INTO players (id, name, avatar, first_seen, last_seen, banned, ban_reason) VALUES (?, '', '', ?, ?, TRUE, ?)").use {
                it.setString(1, playerId); it.setLong(2, now); it.setLong(3, now); it.setString(4, reason.take(200))
                it.executeUpdate()
            }
        }
    }

    override suspend fun bannedPlayers(): Map<String, String> = read(emptyMap()) { db ->
        db.prepareStatement("SELECT id, COALESCE(ban_reason, '') FROM players WHERE banned = TRUE").use {
            it.executeQuery().use { row -> buildMap { while (row.next()) put(row.getString(1), row.getString(2)) } }
        }
    }

    override suspend fun loadSettings(): Map<String, String> = read(emptyMap()) { db ->
        db.prepareStatement("SELECT setting, content FROM settings").use {
            it.executeQuery().use { row -> buildMap { while (row.next()) put(row.getString(1), row.getString(2)) } }
        }
    }

    override fun saveSetting(key: String, value: String) = write("setting") { db ->
        val updated = db.prepareStatement("UPDATE settings SET content = ? WHERE setting = ?").use {
            it.setString(1, value); it.setString(2, key)
            it.executeUpdate()
        }
        if (updated == 0) {
            db.prepareStatement("INSERT INTO settings (setting, content) VALUES (?, ?)").use {
                it.setString(1, key); it.setString(2, value)
                it.executeUpdate()
            }
        }
    }

    override suspend fun pushable(): List<String> = read(emptyList()) { db ->
        db.prepareStatement("SELECT id FROM players WHERE push_token IS NOT NULL").use {
            it.executeQuery().use { row -> buildList { while (row.next()) add(row.getString(1)) } }
        }
    }

    /** A read for the developer's page: off the game's threads, and empty rather than failing. */
    private suspend fun <T> read(fallback: T, block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        try {
            withConnection(block)
        } catch (e: Exception) {
            log.warn("could not read for the page: {}", e.message)
            fallback
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
                id = match.id,
                seat = match.seat,
                partner = seats[(match.seat + 2) % 4].orEmpty(),
                opponents = listOfNotNull(seats[(match.seat + 1) % 4], seats[(match.seat + 3) % 4]),
                ourScore = match.scores[team],
                theirScore = match.scores[1 - team],
                games = match.games,
            )
        }
        val board = readLeaderboard(db)
        val place = board.players.indexOfFirst { it.playerId == playerId }
        val streak = lastStreaks[playerId] ?: 0
        val badges = listOfNotNull(
            "أول فوز".takeIf { wins >= 1 },
            "10 فوزات".takeIf { wins >= 10 },
            "50 فوز".takeIf { wins >= 50 },
            "أول طيرة".takeIf { teeras >= 1 },
            "5 طيرات".takeIf { teeras >= 5 },
            "أول اكس".takeIf { finishes >= 1 },
            "25 اكس".takeIf { finishes >= 25 },
            "50 گيم".takeIf { games >= 50 },
            "200 گيم".takeIf { games >= 200 },
            "3 فوزات ورا بعض".takeIf { streak >= 3 },
            "5 فوزات ورا بعض".takeIf { streak >= 5 },
        )
        return PlayerRecord(
            matches, wins, losses, games, finishes, teeras, firstSeen, recent,
            rating = board.players.getOrNull(place)?.rating,
            rank = if (place >= 0) place + 1 else null,
            badges = badges,
            titles = board.titles.filter { it.playerId == playerId }.map { it.title },
        )
    }

    override fun saveTournament(tournament: com.teera.core.Tournament) = write("tournament") { db ->
        val data = Protocol.json.encodeToString(com.teera.core.Tournament.serializer(), tournament)
        val updated = db.prepareStatement("UPDATE tournaments SET data = ?, updated_at = ? WHERE id = ?").use {
            it.setString(1, data); it.setLong(2, clock()); it.setString(3, tournament.id)
            it.executeUpdate()
        }
        if (updated == 0) {
            db.prepareStatement("INSERT INTO tournaments (id, data, updated_at) VALUES (?, ?, ?)").use {
                it.setString(1, tournament.id); it.setString(2, data); it.setLong(3, clock())
                it.executeUpdate()
            }
        }
    }

    override suspend fun loadTournament(id: String): com.teera.core.Tournament? = withContext(Dispatchers.IO) {
        try {
            withConnection { db ->
                db.prepareStatement("SELECT data FROM tournaments WHERE id = ?").use {
                    it.setString(1, id)
                    it.executeQuery().use { row ->
                        if (row.next()) Protocol.json.decodeFromString(com.teera.core.Tournament.serializer(), row.getString(1)) else null
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("could not read a tournament: {}", e.message)
            null
        }
    }

    override suspend fun leaderboard(): Leaderboard? = withContext(Dispatchers.IO) {
        try {
            withConnection { db -> readLeaderboard(db) }
        } catch (e: Exception) {
            log.warn("could not read the ranking: {}", e.message)
            null
        }
    }

    /** Every finished match, oldest first, worked into ratings and pairs. */
    private fun readLeaderboard(db: Connection): Leaderboard {
        val seats = LinkedHashMap<String, Array<String?>>()
        val losing = HashMap<String, Int>()
        db.prepareStatement(
            """SELECT m.id, m.losing_team, s.seat, s.player_id
               FROM matches m JOIN match_seats s ON s.match_id = m.id
               WHERE m.ended_at IS NOT NULL AND m.losing_team IS NOT NULL
               ORDER BY m.ended_at, m.id, s.seat""",
        ).use {
            it.executeQuery().use { row ->
                while (row.next()) {
                    val id = row.getString(1)
                    losing[id] = row.getInt(2)
                    val seat = row.getInt(3)
                    if (seat in 0 until 4) seats.getOrPut(id) { arrayOfNulls(4) }[seat] = row.getString(4)
                }
            }
        }
        val played = seats.map { (id, players) -> Ranking.Played(players.toList(), losing.getValue(id)) }
        val standings = Ranking.standings(played)
        val pairs = Ranking.pairs(played)

        // Names and pictures as each player has them now.
        val people = HashMap<String, Pair<String, String>>()
        val ids = (standings.keys + pairs.keys.flatMap { listOf(it.first, it.second) }).toList()
        ids.chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.prepareStatement("SELECT id, name, avatar FROM players WHERE id IN ($marks)").use {
                chunk.forEachIndexed { index, id -> it.setString(index + 1, id) }
                it.executeQuery().use { row -> while (row.next()) people[row.getString(1)] = row.getString(2) to row.getString(3) }
            }
        }
        fun name(id: String) = people[id]?.first ?: "لاعب"
        fun avatar(id: String) = people[id]?.second.orEmpty().takeIf { it != "photo" }.orEmpty()

        val titles = readTitles(db, played, standings, ::name, ::avatar)
        lastStreaks = streaks(played)

        return Leaderboard(
            titles = titles,
            players = standings.entries
                .sortedWith(compareByDescending<Map.Entry<String, Ranking.Standing>> { it.value.rating }.thenByDescending { it.value.wins })
                .take(LEADERBOARD_SIZE)
                .map { (id, s) -> RankedPlayer(id, name(id), avatar(id), Ranking.shown(s.rating), s.matches, s.wins) },
            pairs = pairs.entries
                .sortedWith(
                    compareByDescending<Map.Entry<Pair<String, String>, Ranking.PairStanding>> { it.value.wins.toDouble() / it.value.matches }
                        .thenByDescending { it.value.matches },
                )
                .take(LEADERBOARD_SIZE)
                .map { (key, s) -> RankedPair(listOf(name(key.first), name(key.second)), listOf(avatar(key.first), avatar(key.second)), s.matches, s.wins) },
        )
    }

    /** Each player's longest run of ranked wins, as of the last ranking read. */
    @Volatile
    private var lastStreaks: Map<String, Int> = emptyMap()

    private fun streaks(played: List<Ranking.Played>): Map<String, Int> {
        val current = HashMap<String, Int>()
        val best = HashMap<String, Int>()
        for (match in played.filter(Ranking::counts)) {
            for (seat in 0 until 4) {
                val id = match.players[seat] ?: continue
                val won = match.losingTeam != teamOf(seat)
                val run = if (won) (current[id] ?: 0) + 1 else 0
                current[id] = run
                if (run > (best[id] ?: 0)) best[id] = run
            }
        }
        return best
    }

    /**
     * The titles: one holder each, from the books. The most teeras and the most X from
     * finished games; the most upheld alerts, credited to the side that raised them; the
     * longest run of wins and the most matches, from ranked matches.
     */
    private fun readTitles(
        db: Connection,
        played: List<Ranking.Played>,
        standings: Map<String, Ranking.Standing>,
        name: (String) -> String,
        avatar: (String) -> String,
    ): List<com.teera.core.TitleHolder> {
        val teeras = HashMap<String, Int>()
        val finishes = HashMap<String, Int>()
        db.prepareStatement(
            """SELECT s.player_id,
                      SUM(CASE WHEN g.end_kind = 'TEERA' THEN 1 ELSE 0 END),
                      SUM(CASE WHEN g.end_kind = 'X' THEN 1 ELSE 0 END)
               FROM games g JOIN match_seats s ON s.match_id = g.match_id AND s.seat = g.winner_seat
               WHERE s.player_id IS NOT NULL
               GROUP BY s.player_id""",
        ).use {
            it.executeQuery().use { row ->
                while (row.next()) {
                    teeras[row.getString(1)] = row.getInt(2)
                    finishes[row.getString(1)] = row.getInt(3)
                }
            }
        }

        // Alerts are in the games' steps: credit whoever sat on the side that raised them.
        val alerts = HashMap<String, Int>()
        val alertGames = ArrayList<Pair<String, String>>()
        db.prepareStatement("SELECT match_id, steps FROM games WHERE steps LIKE ?").use {
            it.setString(1, "%\"alert\"%")
            it.executeQuery().use { row -> while (row.next()) alertGames += row.getString(1) to row.getString(2) }
        }
        if (alertGames.isNotEmpty()) {
            val seatsOf = HashMap<String, Array<String?>>()
            alertGames.map { it.first }.distinct().chunked(500).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                db.prepareStatement("SELECT match_id, seat, player_id FROM match_seats WHERE match_id IN ($marks)").use {
                    chunk.forEachIndexed { i, id -> it.setString(i + 1, id) }
                    it.executeQuery().use { row ->
                        while (row.next()) {
                            val seat = row.getInt(2)
                            if (seat in 0 until 4) seatsOf.getOrPut(row.getString(1)) { arrayOfNulls(4) }[seat] = row.getString(3)
                        }
                    }
                }
            }
            for ((match, json) in alertGames) {
                val steps = runCatching { Protocol.json.decodeFromString(STEPS, json) }.getOrDefault(emptyList())
                for (upheld in steps.filterIsInstance<GameEvent.AlertUpheld>()) {
                    val raisers = (0 until 4).filter { teamOf(it) != upheld.againstTeam }
                    raisers.mapNotNull { seatsOf[match]?.getOrNull(it) }.forEach { alerts[it] = (alerts[it] ?: 0) + 1 }
                }
            }
        }

        val streak = streaks(played)
        fun holder(title: String, counts: Map<String, Int>, words: (Int) -> String): com.teera.core.TitleHolder? {
            val best = counts.entries.filter { it.value > 0 }.maxByOrNull { it.value } ?: return null
            return com.teera.core.TitleHolder(title, best.key, name(best.key), avatar(best.key), words(best.value))
        }
        return listOfNotNull(
            holder("ملك الطيرة", teeras) { "$it طيرة" },
            holder("ملك الاكس", finishes) { "$it اكس" },
            holder("بطل الخاوات", alerts) { "$it خاوة" },
            holder("الحديد", streak) { "$it فوز ورا بعض" },
            holder("الملازم", standings.mapValues { it.value.matches }) { "$it مباراة" },
        )
    }

    override suspend fun matchDetail(matchId: String): MatchDetail? = withContext(Dispatchers.IO) {
        try {
            withConnection { db -> readMatch(db, matchId) }
        } catch (e: Exception) {
            log.warn("could not read a match: {}", e.message)
            null
        }
    }

    private fun readMatch(db: Connection, matchId: String): MatchDetail? {
        class Head(val code: String, val started: Long, val target: Int, val losing: Int?, val scores: List<Int>)
        val head = db.prepareStatement(
            "SELECT room_code, started_at, target, losing_team, score0, score1 FROM matches WHERE id = ?",
        ).use {
            it.setString(1, matchId)
            it.executeQuery().use { row ->
                if (!row.next()) return null
                Head(row.getString(1), row.getLong(2), row.getInt(3), row.getInt(4).takeUnless { row.wasNull() }, listOf(row.getInt(5), row.getInt(6)))
            }
        }
        val names = Array(4) { "" }
        db.prepareStatement("SELECT seat, name, is_bot FROM match_seats WHERE match_id = ?").use {
            it.setString(1, matchId)
            it.executeQuery().use { row ->
                while (row.next()) {
                    val seat = row.getInt(1)
                    if (seat in 0 until 4) names[seat] = if (row.getBoolean(3)) "بوت" else row.getString(2)
                }
            }
        }
        val games = db.prepareStatement(
            """SELECT game_number, end_kind, winner_seat, scored0, scored1, score0, score1, steps
               FROM games WHERE match_id = ? ORDER BY game_number""",
        ).use {
            it.setString(1, matchId)
            it.executeQuery().use { row ->
                buildList {
                    while (row.next()) {
                        val end = runCatching { com.teera.core.GameEnd.valueOf(row.getString(2)) }.getOrNull() ?: continue
                        val winner = row.getInt(3).takeUnless { row.wasNull() }
                        val steps = runCatching { Protocol.json.decodeFromString(STEPS, row.getString(8)) }.getOrDefault(emptyList())
                        add(
                            PlayedGame(
                                gameNumber = row.getInt(1), end = end, winnerSeat = winner,
                                scored = listOf(row.getInt(4), row.getInt(5)),
                                scores = listOf(row.getInt(6), row.getInt(7)),
                                steps = steps,
                            ),
                        )
                    }
                }
            }
        }
        return MatchDetail(matchId, head.code, head.started, names.toList(), head.scores, head.target, head.losing, games)
    }

    companion object {
        /** The game's friends are in Iraq: days begin at midnight Baghdad time. */
        val ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Baghdad")

        fun dayOf(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().toString()

        fun startOfDay(epochMs: Long): Long =
            java.time.Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant().toEpochMilli()

        private const val RECENT = 10
        private const val LEADERBOARD_SIZE = 50
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
                sql.execute("ALTER TABLE players ADD COLUMN IF NOT EXISTS push_token VARCHAR(512)")
                sql.execute("ALTER TABLE players ADD COLUMN IF NOT EXISTS app_version INT")
                sql.execute("ALTER TABLE players ADD COLUMN IF NOT EXISTS reminders_off BOOLEAN")
                sql.execute("ALTER TABLE players ADD COLUMN IF NOT EXISTS banned BOOLEAN")
                sql.execute("ALTER TABLE players ADD COLUMN IF NOT EXISTS ban_reason VARCHAR(200)")
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS feedback (
                        id VARCHAR(36) PRIMARY KEY,
                        player_id VARCHAR(64) NOT NULL,
                        name VARCHAR(48) NOT NULL,
                        kind VARCHAR(16) NOT NULL,
                        body TEXT NOT NULL,
                        app_version INT NOT NULL,
                        room_code VARCHAR(8),
                        at_ms BIGINT NOT NULL,
                        done BOOLEAN NOT NULL DEFAULT FALSE)""",
                )
                sql.execute("ALTER TABLE feedback ADD COLUMN IF NOT EXISTS image TEXT")
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS settings (
                        setting VARCHAR(40) PRIMARY KEY,
                        content TEXT NOT NULL)""",
                )
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS visits (
                        player_id VARCHAR(64) NOT NULL,
                        on_day VARCHAR(10) NOT NULL,
                        PRIMARY KEY (player_id, on_day))""",
                )
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS reminders (
                        player_id VARCHAR(64) NOT NULL,
                        on_day VARCHAR(10) NOT NULL,
                        slot INT NOT NULL,
                        line INT NOT NULL,
                        sent_at BIGINT NOT NULL,
                        PRIMARY KEY (player_id, on_day, slot))""",
                )
                sql.execute(
                    """CREATE TABLE IF NOT EXISTS tournaments (
                        id VARCHAR(12) PRIMARY KEY,
                        data TEXT NOT NULL,
                        updated_at BIGINT NOT NULL)""",
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
