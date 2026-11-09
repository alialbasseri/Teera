package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The server's tables give every turn thirty seconds; then a bot plays it for them. */
class TurnClockTest {

    private var now = 1_000L

    private fun room(fixed: Int? = 30): Room = Room("KHAN", hostId = "a", clock = { now }, fixedTurnSeconds = fixed).apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar", "d" to "Noor").forEach { (id, name) -> join(id, name) }
    }

    private fun Room.actingId(): String {
        val seat = assertNotNull(game?.actingSeat)
        return assertNotNull(game).seats.first { it.seat == seat }.playerId
    }

    @Test
    fun `a phone cannot switch the clock off or change it`() {
        val room = room()
        assertEquals(30, room.rules.turnSeconds)
        room.setRules("a", Rules(target = 251, turnSeconds = 0))
        assertEquals(30, room.rules.turnSeconds, "still thirty")
        assertEquals(251, room.rules.target, "the rest of the choice stands")
    }

    @Test
    fun `everybody is told how long the seat acting has left`() {
        val room = room()
        room.start("a", seed = 3)
        val view = assertNotNull(room.viewFor("b"))
        assertEquals(30_000L, view.turnMillisLeft)
        now += 12_000
        assertEquals(18_000L, room.viewFor("c")?.turnMillisLeft)
    }

    @Test
    fun `a turn left too long is played for them, and the next one gets a fresh thirty seconds`() {
        val room = room()
        room.start("a", seed = 3)
        val before = assertNotNull(room.game)
        val who = room.actingId()
        now += 29_000
        assertEquals(Room.Output.NOTHING, room.expireTurnIfDue(), "one second still to go")
        now += 1_000
        assertNotEquals(Room.Output.NOTHING, room.expireTurnIfDue())
        val after = assertNotNull(room.game)
        assertTrue(after != before, "a move was made for $who")
        assertEquals(30_000L, room.viewFor("a")?.turnMillisLeft)
    }

    @Test
    fun `a table on a phone keeps no clock unless asked`() {
        val room = room(fixed = null)
        room.start("a", seed = 3)
        assertNull(room.viewFor("a")?.turnMillisLeft)
        now += 600_000
        assertEquals(Room.Output.NOTHING, room.expireTurnIfDue())
    }
}
