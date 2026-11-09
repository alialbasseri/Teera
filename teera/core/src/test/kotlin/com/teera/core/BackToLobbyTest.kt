package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The score sheet travels with the view; a finished match sends the table back to its lobby. */
class BackToLobbyTest {
    private fun table(): Room = Room("BOOK", "ali", Rules(target = 60), clock = { 0L }).apply {
        join("ali", "Ali")
        (1..3).forEach { setBot("ali", it, true, Difficulty.NORMAL) }
        start("ali", seed = 7)
    }

    @Test
    fun `every finished game is on the score sheet, and it adds up`() {
        val room = table()
        playOut(room, "ali")
        val view = assertNotNull(room.viewFor("ali"))
        assertEquals(Phase.MATCH_OVER, view.phase)
        assertTrue(view.history.isNotEmpty())
        assertEquals(view.history.map { it.gameNumber }, view.history.map { it.gameNumber }.sorted())
        (0..1).forEach { team ->
            assertEquals(view.scores[team], view.history.sumOf { it.scored[team] }, "team $team adds up")
        }
    }

    @Test
    fun `after the match the table goes back to the lobby together, and can play again`() {
        val room = table()
        assertEquals(Room.Output.NOTHING, room.backToLobby("ali"), "not while the match is on")
        playOut(room, "ali")
        val output = room.backToLobby("ali")
        assertTrue(output.roomChanged)
        assertFalse(room.isStarted)
        assertFalse(room.info().started)
        assertEquals(0, room.seatOf("ali"), "same chair")
        assertTrue(room.start("ali", seed = 9).viewChanged, "and another match starts")
        assertTrue(assertNotNull(room.viewFor("ali")).history.isEmpty(), "with a fresh sheet")
    }
}
