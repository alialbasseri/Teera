package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A game shows in the list for anyone to watch only once one of its players says so. */
class LiveGamesTest {
    private fun dealt(): Room = Room("KHAN", hostId = "a").apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar").forEach { (id, name) -> join(id, name) }
        setBot("a", 3, true, Difficulty.NORMAL)
        start("a", seed = 5)
    }

    @Test
    fun `a table is not in the list until a player puts it there, and any one of them can`() {
        val room = dealt()
        assertNull(room.liveGame())
        room.join("w", "Hassan")
        room.setListed("w", true)
        assertNull(room.liveGame(), "a watcher cannot list somebody else's game")

        room.setListed("c", true)
        val live = assertNotNull(room.liveGame())
        assertEquals(listOf("Ali", "Sara", "Omar", "بوت"), live.names)
        assertEquals(1, live.watchers)
        assertTrue(room.viewFor("a")!!.listed, "everyone at the table can see it is listed")

        room.setListed("a", false)
        assertNull(room.liveGame())
        assertFalse(room.viewFor("b")!!.listed)
    }

    @Test
    fun `a table nobody is at any more drops out of the list`() {
        val room = dealt()
        room.setListed("a", true)
        listOf("a", "b", "c").forEach { room.disconnect(it) }
        assertNull(room.liveGame())
    }
}
