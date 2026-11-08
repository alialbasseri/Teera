package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A player who walks away or drops keeps their chair; a friend who is watching can play
 * it for them meanwhile, and hands it back the moment they return.
 */
class StandInTest {
    private var now = 50_000L

    private fun dealtRoom(): Room = Room("KHAN", hostId = "a", clock = { now }).apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar", "d" to "Noor").forEach { (id, name) -> join(id, name) }
        start("a", seed = 7)
        join("w", "Hassan") // a watcher: the table was already playing
    }

    @Test
    fun `leaving mid-match keeps the chair, and coming back sits them in it, not with the watchers`() {
        val room = dealtRoom()
        val seat = room.seatOf("b")
        room.leave("b")
        assertEquals(seat, room.seatOf("b"), "the chair is still theirs")
        assertFalse(room.info().seats.first { it.seat == seat }.connected)

        room.join("b", "Sara")
        assertEquals(seat, room.seatOf("b"))
        assertFalse(room.isWatching("b"))
        assertFalse(assertNotNull(room.viewFor("b")).spectating)
    }

    @Test
    fun `leaving before the deal still frees the chair`() {
        val room = Room("KHAN", hostId = "a", clock = { now })
        room.join("a", "Ali")
        room.join("b", "Sara")
        room.leave("b")
        assertNull(room.seatOf("b"))
    }

    @Test
    fun `a watcher can play a dropped player's chair, and only a dropped one`() {
        val room = dealtRoom()
        val seat = assertNotNull(room.seatOf("c"))
        assertEquals(Room.Output.NOTHING, room.standIn("w", seat), "Omar is still here")

        room.disconnect("c")
        room.standIn("w", seat)
        assertEquals(seat, room.seatOf("w"))
        assertEquals("Hassan", assertNotNull(room.viewFor("a")).seatInfo(seat)?.standIn)
        val view = assertNotNull(room.viewFor("w"))
        assertFalse(view.spectating, "the stand-in plays")
        assertEquals(room.game!!.handOf(seat), view.hand)

        // No bot takes the turn from somebody who is actually there.
        now += Room.RECONNECT_GRACE_MS * 4
        if (room.game!!.actingSeat == seat) assertFalse(room.awaitingBot())
    }

    @Test
    fun `the stand-in's moves count for the chair`() {
        val room = dealtRoom()
        val seat = assertNotNull(room.game!!.actingSeat)
        val owner = room.info().seats.first { it.seat == seat }.playerId
        room.disconnect(owner)
        room.standIn("w", seat)
        val move = assertNotNull(room.viewFor("w")).legalMoves.first()
        room.move("w", move)
        assertTrue(room.game!!.actingSeat != seat || room.game!!.phase != Phase.IN_PLAY, "the move was played")
    }

    @Test
    fun `the owner coming back takes the chair, and the stand-in goes back to watching`() {
        val room = dealtRoom()
        val seat = assertNotNull(room.seatOf("d"))
        room.disconnect("d")
        room.standIn("w", seat)
        room.join("d", "Noor")

        assertEquals(seat, room.seatOf("d"))
        assertNull(room.seatOf("w"))
        assertTrue(room.isWatching("w"))
        assertTrue(assertNotNull(room.viewFor("w")).spectating)
        assertNull(assertNotNull(room.viewFor("a")).seatInfo(seat)?.standIn)
    }

    @Test
    fun `a stand-in can hand the chair back, and one who drops lets it go`() {
        val room = dealtRoom()
        val seat = assertNotNull(room.seatOf("d"))
        room.disconnect("d")
        room.standIn("w", seat)
        room.standIn("w", null)
        assertNull(room.seatOf("w"))

        room.standIn("w", seat)
        room.disconnect("w")
        assertNull(room.standInAt(seat))
    }
}
