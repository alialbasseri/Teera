package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "ناخذ خاوة": raised by one seat against the other team, settled by the table's vote. */
class AlertVoteTest {

    private var now = 1_000L

    private fun room(): Room = Room("KHAN", hostId = "a", clock = { now }).apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar", "d" to "Noor").forEach { (id, name) -> join(id, name) }
        start("a", seed = 3)
    }

    private fun Room.result(output: Room.Output): ServerMessage.AlertResult? =
        output.direct.map { it.second }.filterIsInstance<ServerMessage.AlertResult>().firstOrNull()

    @Test
    fun `it passes when more than half the table agrees, and counts against the other team`() {
        val room = room()
        room.raiseAlert("a") // Ali, team 0
        val vote = assertNotNull(room.viewFor("b")?.alertVote, "everybody sees the vote")
        assertEquals(1, vote.againstTeam)
        assertEquals(3, vote.needed, "three of four")

        room.voteAlert("c", yes = true) // Ali's partner
        val settled = room.voteAlert("b", yes = true) // and one of the accused admits it
        val result = assertNotNull(room.result(settled))
        assertTrue(result.upheld)
        assertEquals(1, room.game!!.alerts[1])
        assertNull(room.viewFor("a")?.alertVote, "the vote is over")
    }

    @Test
    fun `one team cannot carry it alone`() {
        val room = room()
        room.raiseAlert("a")
        room.voteAlert("c", yes = true)
        room.voteAlert("b", yes = false)
        val settled = room.voteAlert("d", yes = false)
        val result = assertNotNull(room.result(settled))
        assertFalse(result.upheld)
        assertEquals(0, room.game!!.alerts[1])
    }

    @Test
    fun `silence counts as no once thirty seconds are up`() {
        val room = room()
        room.raiseAlert("a")
        room.voteAlert("c", yes = true)
        assertEquals(Room.Output.NOTHING, room.expireAlertIfDue(), "still open")
        now += 30_000
        val result = assertNotNull(room.result(room.expireAlertIfDue()))
        assertFalse(result.upheld)
    }

    @Test
    fun `the third upheld one costs fifty`() {
        val room = room()
        repeat(3) {
            room.raiseAlert("a")
            room.voteAlert("c", yes = true)
            room.voteAlert("b", yes = true)
        }
        assertEquals(3, room.game!!.alerts[1])
        assertEquals(50, room.game!!.scores[1])
    }

    @Test
    fun `nobody votes twice, and a second one waits for the first`() {
        val room = room()
        room.raiseAlert("a")
        assertEquals(Room.Output.NOTHING, room.voteAlert("a", yes = true), "Ali already said yes by raising it")
        assertEquals(Room.Output.NOTHING, room.raiseAlert("b"), "one at a time")
    }
}
