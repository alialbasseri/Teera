package com.teera.server

import com.teera.room.Pusher
import com.teera.room.RoomRegistry
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.h2.jdbcx.JdbcDataSource

class RemindersTest {
    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(SqlRecords.ZONE).toInstant().toEpochMilli()

    private var now = at(9, 12, 0)

    private fun books() = SqlRecords(
        JdbcDataSource().apply { setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1") },
        clock = { now },
    )

    private val sent = CopyOnWriteArrayList<Pair<String, String>>()
    private val pusher = object : Pusher {
        override fun notify(playerId: String, title: String, body: String, data: Map<String, String>) {
            sent += playerId to "$title|$body"
        }
    }

    @Test
    fun `a month of three a day never repeats a line`() {
        val lines = (0 until 90).map { ReminderLines.pick("player-1", it, "Ali", null, null, 0) }
        assertEquals(90, lines.map { it.first }.toSet().size, "every line once")
        assertEquals(90, lines.map { it.second }.toSet().size, "and every one reads differently")
        assertEquals(ReminderLines.LINES.size, ReminderLines.LINES.toSet().size)
    }

    @Test
    fun `reminders come in the evening windows, once a window, and not to somebody playing`() {
        val books = books()
        val registry = RoomRegistry()
        books.playerSeen("ali", "Ali Albasseri", "", 25)
        books.playerSeen("sara", "Sara", "", 25)
        books.flush()
        books.save("ali", "token-ali-000000000000000")
        books.save("sara", "token-sara-00000000000000")
        val reminders = Reminders(books, pusher, registry, clock = { now })

        // Morning: nothing. Late in the first window: Ali (away three hours) gets one.
        now = at(9, 12, 0) + 3 * 3600_000L
        assertEquals(0, reminders.round())
        now = at(9, 18, 59)
        // Sara has the app open: no nudge for her.
        registry.online["sara"] = RoomRegistry.Present("Sara", "", java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()))
        assertEquals(1, reminders.round())
        assertEquals("ali", sent.single().first)
        // Same window again: nothing new.
        assertEquals(0, reminders.round())
        // The next window: another, and a different line.
        now = at(9, 21, 59)
        assertEquals(1, reminders.round())
        assertTrue(sent[0].second != sent[1].second)
        // After a quarter to midnight: nothing.
        now = at(9, 23, 50)
        assertEquals(0, reminders.round())
    }

    @Test
    fun `turned off, they stop`() {
        val books = books()
        books.playerSeen("ali", "Ali", "", 25)
        books.flush()
        books.save("ali", "token-ali-000000000000000")
        books.setReminders("ali", false)
        books.flush()
        now = at(9, 18, 59)
        assertEquals(0, Reminders(books, pusher, RoomRegistry(), clock = { now }).round())
    }
}
