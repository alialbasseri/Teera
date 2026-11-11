package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** People who come to a full or running table watch it, talk at it, and see tiles only when shown. */
class WatcherTest {

    private fun dealt(): Room = Room("KHAN", hostId = "a").apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar", "d" to "Noor").forEach { (id, name) -> join(id, name) }
        start("a", seed = 9)
    }

    @Test
    fun `somebody arriving at a running table watches instead of being turned away`() {
        val room = dealt()
        val output = room.join("w", "Hassan")
        assertTrue(output.direct.none { it.second is ServerMessage.Failure })
        assertTrue(room.isWatching("w"))
        assertEquals(listOf("Hassan"), room.info().spectators.map { it.name })
        val view = assertNotNull(room.viewFor("w"))
        assertTrue(view.spectating)
        assertFalse(view.isYourMove, "a watcher is never asked to move")
        assertTrue(view.hand.isEmpty(), "nobody's tiles until somebody shows them")
        assertTrue(view.legalMoves.isEmpty())
        assertEquals(listOf("Hassan"), assertNotNull(room.viewFor("a")).spectators.map { it.name }, "the players see who is watching")
    }

    @Test
    fun `a player can let the watchers see their tiles, and the watcher can choose whose`() {
        val room = dealt()
        room.join("w", "Hassan")
        room.showHand("c", shown = true)
        room.watch("w", seat = 2)
        val view = assertNotNull(room.viewFor("w"))
        assertEquals(room.game!!.handOf(2), view.hand)
        assertEquals(listOf(2), view.showingHands)

        room.watch("w", seat = 1)
        assertTrue(assertNotNull(room.viewFor("w")).hand.isEmpty(), "Sara did not show hers")
        room.showHand("c", shown = false)
        room.watch("w", seat = 2)
        assertTrue(assertNotNull(room.viewFor("w")).hand.isEmpty(), "and Omar put his away")
    }

    @Test
    fun `watchers talk and chat under seats after the chairs, and can take a chair that frees before the deal`() {
        val room = Room("KHAN", hostId = "a").apply {
            listOf("a", "b", "c", "d").forEach { join(it, it) }
        }
        room.join("w", "Hassan")
        assertEquals(4, room.voiceSeatOf("w"))
        assertEquals(4, room.chat("w", "يلا")?.seat)
        assertTrue("w" in room.humanIds(), "watchers hear the table")

        room.leave("d")
        room.takeSeat("w", 3)
        assertEquals(3, room.seatOf("w"))
        assertFalse(room.isWatching("w"))
    }

    @Test
    fun `a watcher sees every hand that is shown, and the players still see none but their own`() {
        val room = dealt()
        room.join("w", "Hassan")
        val shown = listOf("a", "c").map { room.seatOf(it)!! }
        room.showHand("a", true)
        room.showHand("c", true)

        val watching = room.viewFor("w")!!
        for (seat in 0 until 4) {
            val expected = if (seat in shown) room.game!!.handOf(seat) else emptyList()
            assertEquals(expected, watching.revealedAt(seat), "seat $seat")
        }
        assertTrue(room.viewFor("b")!!.revealedHands.isEmpty(), "a player is never handed anybody else's tiles")
    }

    @Test
    fun `a screenshot is told to everyone else at the table, watchers included, and not to whoever took it`() {
        val room = dealt()
        room.join("w", "Hassan")
        val told = room.screenCaptured("b").direct
        assertEquals(setOf("a", "c", "d", "w"), told.map { it.first }.toSet())
        assertEquals(ServerMessage.ScreenshotTaken(room.seatOf("b")!!, "Sara"), told.first().second)
        assertTrue(room.screenCaptured("b").direct.isEmpty(), "not again straight away")
    }
}
