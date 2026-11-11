package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The host swaps a player and a watcher: before the deal, or mid-match with the bones. */
class SwapTest {

    private fun fullRoom(): Room = Room("KHAN", hostId = "a").apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar", "d" to "Noor").forEach { (id, name) -> join(id, name) }
        join("e", "Hassan") // the table is full, so Hassan watches
    }

    @Test
    fun `mid-match the watcher takes the chair and its bones, and the player watches`() {
        val room = fullRoom()
        room.start("a", seed = 3)
        val before = assertNotNull(room.viewFor("b"))
        room.swap("a", seated = "b", watcherId = "e")

        val hassan = assertNotNull(room.viewFor("e"))
        assertEquals(1, hassan.yourSeat)
        assertTrue(!hassan.spectating)
        assertEquals(before.hand, hassan.hand, "Sara's bones are Hassan's now")
        assertEquals("Hassan", hassan.seats.first { it.seat == 1 }.name)
        assertTrue(assertNotNull(room.viewFor("b")).spectating, "and Sara watches")
        assertEquals(1, room.seatOf("e"))
    }

    @Test
    fun `only the host may swap`() {
        val room = fullRoom()
        assertEquals(Room.Output.NOTHING, room.swap("b", seated = "c", watcherId = "e"))
        assertEquals(2, room.seatOf("c"))
    }

    @Test
    fun `a watcher can take a bot's chair`() {
        val room = Room("KHAN", hostId = "a").apply {
            join("a", "Ali")
            join("b", "Sara")
            join("c", "Omar")
            setBot("a", 3, enabled = true, difficulty = Difficulty.NORMAL)
        }
        room.start("a", seed = 3)
        room.join("e", "Hassan")
        room.swap("a", seated = "bot-3", watcherId = "e")
        val view = assertNotNull(room.viewFor("e"))
        assertEquals(3, view.yourSeat)
        assertTrue(view.seats.none { it.isBot })
    }

    @Test
    fun `everyone sees whose microphone is on`() {
        val room = fullRoom()
        room.setMic("b", true)
        assertTrue(room.info().seats.first { it.playerId == "b" }.micOn)
        room.start("a", seed = 3)
        assertTrue(assertNotNull(room.viewFor("c")).seats.first { it.playerId == "b" }.micOn)
        room.setMic("b", false)
        assertTrue(!assertNotNull(room.viewFor("c")).seats.first { it.playerId == "b" }.micOn)
        room.setMic("e", true)
        assertTrue(assertNotNull(room.viewFor("a")).spectators.first { it.playerId == "e" }.micOn, "watchers too")
    }
}
