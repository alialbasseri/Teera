package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Friends playing over mobile data drop out for a few seconds all the time. The table
 * waits for them before a bot steps in, and gives them their seat back when they return.
 */
class ReconnectTest {

    private var now = 50_000L

    /** Four people, no bots, match dealt. */
    private fun dealtRoom(): Room = Room("KHAN", hostId = "a", clock = { now }).apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar", "d" to "Noor").forEach { (id, name) -> join(id, name) }
        start("a", seed = 7)
    }

    private fun Room.actingId(): String {
        val seat = assertNotNull(game?.actingSeat)
        return assertNotNull(info().seats.first { it.seat == seat }.playerId)
    }

    @Test
    fun `a dropped player gets a grace period before a bot covers them`() {
        val room = dealtRoom()
        val who = room.actingId()
        room.disconnect(who)

        assertFalse(room.awaitingBot(), "the table waits rather than playing for them at once")
        now += Room.RECONNECT_GRACE_MS - 1
        assertFalse(room.awaitingBot())
        now += 1
        assertTrue(room.awaitingBot(), "once they have been gone long enough, a bot steps in")
    }

    @Test
    fun `coming back inside the grace period keeps the seat and the turn`() {
        val room = dealtRoom()
        val who = room.actingId()
        val seat = room.seatOf(who)
        val before = room.game

        room.disconnect(who)
        assertFalse(room.info().seats.first { it.seat == seat }.connected, "everyone can see they dropped")
        val someoneElse = listOf("a", "b", "c", "d").first { it != who }
        assertFalse(assertNotNull(room.viewFor(someoneElse)).seats.first { it.seat == seat }.connected, "at the table too")
        now += 10_000
        val back = room.join(who, "Ali")

        assertTrue(back.viewChanged, "they are sent the table as it stands")
        assertEquals(seat, room.seatOf(who))
        assertEquals(before, room.game, "nothing was played for them while they were away")
        assertFalse(room.awaitingBot())
        assertTrue(room.info().seats.first { it.seat == seat }.connected)
    }

    @Test
    fun `a bot that covered for somebody hands the seat back when they return`() {
        val room = dealtRoom()
        val who = room.actingId()
        room.disconnect(who)
        now += Room.RECONNECT_GRACE_MS
        assertTrue(room.stepBot().viewChanged, "the bot played their turn")

        room.join(who, "Ali")
        // Whenever it is next their turn, it waits for them again.
        while (room.game?.isOver == false && room.actingId() != who) {
            if (!room.awaitingBot()) {
                val acting = room.actingId()
                room.move(acting, assertNotNull(Engine.legalMoves(room.game!!, room.seatOf(acting)!!).firstOrNull()))
            } else {
                room.stepBot()
            }
        }
        if (room.game?.isOver == false) assertFalse(room.awaitingBot(), "back in their own hands")
    }
}
