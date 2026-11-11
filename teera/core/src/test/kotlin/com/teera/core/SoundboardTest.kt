package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundboardTest {

    private var now = 10_000L

    private fun room() = Room("ABCD", hostId = "a", clock = { now }).apply {
        join("a", "Ali")
        join("b", "Sara")
        join("c", "Omar")
    }

    @Test
    fun `a press reaches everybody at the table, from the seat that pressed it`() {
        val output = room().emote("b", Emote.GULP)
        assertEquals(setOf("a", "b", "c"), output.direct.map { it.first }.toSet())
        output.direct.forEach { (_, message) ->
            assertEquals(ServerMessage.EmoteShown(seat = 1, emote = Emote.GULP), message)
        }
    }

    @Test
    fun `a held thumb cannot drown the game out`() {
        val room = room()
        assertTrue(room.emote("a", Emote.SLAP).direct.isNotEmpty())
        assertEquals(Room.Output.NOTHING, room.emote("a", Emote.SLAP), "too soon after the last")
        assertTrue(room.emote("b", Emote.SLAP).direct.isNotEmpty(), "the limit is per player")
        now += 2_000
        assertTrue(room.emote("a", Emote.SLAP).direct.isNotEmpty(), "and it lifts after a moment")
    }

    @Test
    fun `strangers and seated humans cannot speak for a bot`() {
        val room = room()
        assertEquals(Room.Output.NOTHING, room.emote("nobody", Emote.GULP))
        assertEquals(Room.Output.NOTHING, room.botEmote(0, Emote.GULP), "seat 0 is Ali, not a bot")
        assertTrue(room.botEmote(3, Emote.GULP).direct.isNotEmpty())
    }

    @Test
    fun `the retired wallah line is off the board and nobody hears an old phone's press`() {
        assertTrue(Emote.WALLAH !in Emote.BOARD)
        assertEquals(Room.Output.NOTHING, room().emote("b", Emote.WALLAH))
        val old = ClientMessage.SendEmote(Emote.WALLAH)
        assertEquals(old, Protocol.decodeClient(Protocol.encode(old)), "still read, so an old phone gets no error")
    }

    @Test
    fun `an emote survives the wire`() {
        val sent = ClientMessage.SendEmote(Emote.DRUMROLL)
        assertEquals(sent, Protocol.decodeClient(Protocol.encode(sent)))
        val shown = ServerMessage.EmoteShown(2, Emote.SAD)
        assertEquals(shown, Protocol.decodeServer(Protocol.encode(shown)))
    }
}
