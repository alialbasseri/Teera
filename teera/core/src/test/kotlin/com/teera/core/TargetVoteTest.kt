package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Playing to 251 instead of 201, mid-match: only when everybody at the table agrees. */
class TargetVoteTest {
    private var now = 10_000L

    private fun dealt(): Room = Room("KHAN", hostId = "a", clock = { now }).apply {
        listOf("a" to "Ali", "b" to "Sara", "c" to "Omar").forEach { (id, name) -> join(id, name) }
        setBot("a", 3, true, Difficulty.NORMAL)
        start("a", seed = 3)
    }

    private fun results(output: Room.Output) = output.direct.map { it.second }.filterIsInstance<ServerMessage.TargetResult>()

    @Test
    fun `everyone at the table agreeing changes the target, bots have no say`() {
        val room = dealt()
        room.proposeTarget("a", 251)
        val vote = assertNotNull(room.viewFor("b")?.targetVote)
        assertEquals(listOf(0, 1, 2), vote.voters, "the three people, not the bot")
        room.voteTarget("b", true)
        val last = room.voteTarget("c", true)

        assertEquals(listOf(ServerMessage.TargetResult(251, true)), results(last).distinct())
        assertEquals(251, room.game!!.rules.target)
        assertEquals(251, room.info().rules.target)
        assertNull(room.viewFor("a")?.targetVote)
    }

    @Test
    fun `one no ends it, and so does the clock`() {
        val room = dealt()
        room.proposeTarget("a", 251)
        val refused = room.voteTarget("b", false)
        assertEquals(false, results(refused).first().passed)
        assertEquals(201, room.game!!.rules.target)

        room.proposeTarget("b", 301)
        now += Rules.DEFAULT.alertVoteSeconds * 1000L
        val expired = room.expireAlertIfDue()
        assertEquals(false, results(expired).first().passed)
        assertEquals(201, room.game!!.rules.target)
    }

    @Test
    fun `a target the score has already passed, or one off the list, is not put to the table`() {
        val room = dealt()
        assertEquals(Room.Output.NOTHING, room.proposeTarget("a", 250))
        assertEquals(Room.Output.NOTHING, room.proposeTarget("a", 201))
        assertNull(room.viewFor("a")?.targetVote)
    }

    @Test
    fun `alone with bots, asking is agreeing`() {
        val room = Room("SOLO", hostId = "a", clock = { now })
        room.join("a", "Ali")
        (1..3).forEach { room.setBot("a", it, true, Difficulty.NORMAL) }
        room.start("a", seed = 1)
        val output = room.proposeTarget("a", 151)
        assertIs<ServerMessage.TargetResult>(output.direct.single().second)
        assertEquals(151, room.game!!.rules.target)
    }
}
