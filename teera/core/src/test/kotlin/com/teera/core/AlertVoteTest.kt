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

    // -- with bots at the table ------------------------------------------------------

    /** Ali and Omar (team 0) against two bots: the pair that tried to carry it alone. */
    private fun twoAgainstBots(): Room = Room("KHAN", hostId = "a", clock = { now }).apply {
        join("a", "Ali")
        setBot("a", 1, enabled = true, difficulty = Difficulty.NORMAL)
        join("c", "Omar")
        setBot("a", 3, enabled = true, difficulty = Difficulty.NORMAL)
        start("a", seed = 3)
    }

    @Test
    fun `a team cannot pass it on its own against bots`() {
        val room = twoAgainstBots()
        assertEquals(0, room.viewFor("a")?.yourSeat)
        assertEquals(2, room.viewFor("c")?.yourSeat)
        // The accused bots stand by their team, so three of four can never be reached:
        // it is settled the moment it is raised.
        val result = assertNotNull(room.result(room.raiseAlert("a")))
        assertFalse(result.upheld)
        assertEquals(3, result.needed)
        assertEquals(0, room.game!!.alerts[1])
        assertNull(room.viewFor("a")?.alertVote)
    }

    @Test
    fun `a bot backs its partner, and one of the accused agreeing passes it`() {
        val room = Room("KHAN", hostId = "a", clock = { now }).apply {
            join("a", "Ali")
            join("b", "Sara")
            setBot("a", 2, enabled = true, difficulty = Difficulty.NORMAL)
            join("d", "Noor")
            start("a", seed = 3)
        }
        assertEquals(3, room.viewFor("d")?.yourSeat)
        room.raiseAlert("a")
        val vote = assertNotNull(room.viewFor("b")?.alertVote)
        assertTrue(2 in vote.yes, "Ali's bot partner backs him")
        assertNull(room.result(room.voteAlert("b", yes = false)), "Noor can still make it three")
        val result = assertNotNull(room.result(room.voteAlert("d", yes = true)))
        assertTrue(result.upheld)
    }

    @Test
    fun `somebody gone from the accused team cannot be counted as agreeing`() {
        val room = room()
        room.disconnect("b")
        room.raiseAlert("a")
        room.voteAlert("c", yes = true)
        assertNull(room.result(room.voteAlert("c", yes = true)), "still waiting on the accused")
        now += 30_000
        val result = assertNotNull(room.result(room.expireAlertIfDue()))
        assertFalse(result.upheld)
    }
}
