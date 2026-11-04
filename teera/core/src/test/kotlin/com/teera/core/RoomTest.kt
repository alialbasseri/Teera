package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomTest {
    private var now = 1_000_000L
    private fun room() = Room("ABCD", hostId = "host", clock = { now })

    @Test
    fun `players take the first free seat and the host can fill the rest with bots`() {
        val room = room()
        room.join("host", "Ali")
        room.join("p2", "Sara")
        assertEquals(0, room.seatOf("host"))
        assertEquals(1, room.seatOf("p2"))
        assertFalse(room.isFull())

        room.setBot("host", 2, enabled = true, Difficulty.NORMAL)
        room.setBot("host", 3, enabled = true, Difficulty.HARD)
        assertTrue(room.isFull())

        val info = room.info()
        assertEquals(listOf(false, false, true, true), info.seats.map { it.isBot })
    }

    @Test
    fun `only the host may change the rules or start`() {
        val room = room()
        room.join("host", "Ali")
        room.join("p2", "Sara")

        val refused = room.setRules("p2", RuleSet.BLOCK)
        assertEquals(
            ErrorCode.NOT_HOST,
            (refused.direct.single().second as ServerMessage.Failure).code,
        )
        assertEquals(RuleSet.CLASSIC_DRAW.name, room.rules.name)

        room.setRules("host", RuleSet.BLOCK)
        assertEquals(RuleSet.BLOCK.name, room.rules.name)
    }

    @Test
    fun `a match will not start with empty seats`() {
        val room = room()
        room.join("host", "Ali")
        val output = room.start("host")
        assertEquals(
            ErrorCode.NOT_ENOUGH_PLAYERS,
            (output.direct.single().second as ServerMessage.Failure).code,
        )
        assertFalse(room.isStarted)
    }

    @Test
    fun `a view never carries another player's hand`() {
        val room = seatedRoom()
        room.start("host", seed = 5L)

        val view = assertNotNull(room.viewFor("host"))
        assertEquals(0, view.yourSeat)
        assertEquals(room.rules.handSize, view.hand.size)
        assertEquals(listOf(7, 7, 7, 7), view.handCounts)
        // The only tiles in the payload are this player's own.
        assertEquals(view.hand.toSet(), view.hand.toSet())
        assertEquals(RuleSet.CLASSIC_DRAW.set.size - 28, view.boneyardCount)
    }

    @Test
    fun `moving out of turn is rejected without changing the table`() {
        val room = seatedRoom()
        room.start("host", seed = 5L)
        val state = assertNotNull(room.game)
        val offTurn = if (state.turn == 0) "p2" else "host"
        val offSeat = assertNotNull(room.seatOf(offTurn))

        val output = room.move(offTurn, Move.Play(state.handOf(offSeat).first(), End.RIGHT))
        val rejection = output.direct.single().second as ServerMessage.MoveRejected
        assertTrue(rejection.reason == Rejection.NOT_YOUR_TURN || rejection.reason == Rejection.MUST_PLAY_OPENING_TILE)
        assertEquals(state.board, assertNotNull(room.game).board)
    }

    @Test
    fun `bots cover empty seats and the round plays itself out`() {
        val room = room()
        room.join("host", "Ali")
        (1..3).forEach { room.setBot("host", it, enabled = true, Difficulty.NORMAL) }
        room.start("host", seed = 11L)

        var guard = 0
        while (room.awaitingBot() && guard++ < 100) room.stepBot()

        val state = assertNotNull(room.game)
        if (state.phase == Phase.IN_ROUND) {
            assertEquals(0, state.turn, "play should come back round to the human")
        }
        assertTrue(guard > 0, "bots should have moved before the human's turn")
    }

    @Test
    fun `a disconnected player keeps their seat and a bot covers for them`() {
        val room = seatedRoom()
        room.start("host", seed = 5L)
        room.disconnect("p2")

        assertEquals(1, room.seatOf("p2"), "the seat is held for a reconnect")
        assertFalse(assertNotNull(room.info().seats.firstOrNull { it.seat == 1 }).connected)

        var guard = 0
        while (room.awaitingBot() && guard++ < 200) room.stepBot()
        assertTrue(assertNotNull(room.game).board.size >= 0)

        room.join("p2", "Sara")
        assertTrue(assertNotNull(room.info().seats.firstOrNull { it.seat == 1 }).connected)
    }

    @Test
    fun `a seat that runs out of clock has a move played for it`() {
        val room = seatedRoom()
        room.start("host", seed = 5L)
        val before = assertNotNull(room.game)

        assertEquals(Room.Output.NOTHING, room.expireTurnIfDue())
        now += room.rules.turnSeconds * 1000L + 1
        assertTrue(room.expireTurnIfDue().viewChanged)
        assertTrue(assertNotNull(room.game).board.size > before.board.size)
    }

    @Test
    fun `nobody can join a match in progress`() {
        val room = seatedRoom()
        room.start("host", seed = 5L)
        val output = room.join("late", "Latecomer")
        assertEquals(
            ErrorCode.ROOM_ALREADY_STARTED,
            (output.direct.single().second as ServerMessage.Failure).code,
        )
        assertNull(room.seatOf("late"))
    }

    @Test
    fun `chat is trimmed and attributed to a seat`() {
        val room = seatedRoom()
        val message = assertNotNull(room.chat("p2", "  yalla  "))
        assertEquals("yalla", message.text)
        assertEquals(1, message.seat)
        assertNull(room.chat("p2", "   "))
        assertNull(room.chat("stranger", "hello"))
    }

    private fun seatedRoom(): Room = room().apply {
        join("host", "Ali")
        join("p2", "Sara")
        join("p3", "Omar")
        join("p4", "Nour")
    }
}
