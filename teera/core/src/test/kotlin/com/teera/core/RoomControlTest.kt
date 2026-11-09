package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The host can send somebody out, and close the room to anyone not invited from inside. */
class RoomControlTest {
    private fun lobby(): Room = Room("KHAN", hostId = "a").apply {
        join("a", "Ali")
        join("b", "Sara")
    }

    @Test
    fun `a locked room lets in only the invited and those already there`() {
        val room = lobby()
        room.setLocked("b", true)
        assertEquals(false, room.info().locked, "only the host locks it")
        room.setLocked("a", true)
        assertEquals(ErrorCode.ROOM_LOCKED, room.refusal("stranger"))
        assertNull(room.refusal("b"), "already here")
        room.invited("omar")
        assertNull(room.refusal("omar"))
        room.setLocked("a", false)
        assertNull(room.refusal("stranger"))
    }

    @Test
    fun `a player sent out before the deal leaves the chair free and cannot come back uninvited`() {
        val room = lobby()
        val out = room.kick("a", "b")
        assertEquals(ErrorCode.KICKED, (out.direct.single { it.first == "b" }.second as ServerMessage.Failure).code)
        assertNull(room.seatOf("b"))
        assertEquals(ErrorCode.KICKED, room.refusal("b"))
        room.invited("b")
        assertNull(room.refusal("b"), "invited again, they may")
        assertEquals(Room.Output.NOTHING, room.kick("b", "a"), "only the host sends people out")
    }

    @Test
    fun `sent out mid-match, a bot takes the chair and the game goes on`() {
        val room = Room("KHAN", hostId = "a")
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar", "d" to "Noor").forEach { (id, n) -> room.join(id, n) }
        room.start("a", seed = 4)
        room.join("w", "Hassan")
        val seat = room.seatOf("c")!!
        room.kick("a", "c")
        room.kick("a", "w")
        assertTrue(!room.isWatching("w"))
        val chair = room.viewFor("a")!!.seatInfo(seat)!!
        assertTrue(chair.isBot && chair.connected)
        if (room.game!!.actingSeat == seat) assertTrue(room.awaitingBot())
    }
}
