package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Plays [room] to the end, the human taking their first legal move, the bots their own. */
fun playOut(room: Room, human: String, steps: Int = 20_000): List<Room.Happening> {
    val seen = mutableListOf<Room.Happening>()
    repeat(steps) {
        seen += room.takeHappenings()
        val game = room.game ?: return seen
        when {
            game.phase == Phase.MATCH_OVER -> return seen + room.takeHappenings()
            game.phase == Phase.GAME_OVER -> room.nextGame(human)
            room.awaitingBot() -> room.stepBot()
            else -> {
                val move = room.viewFor(human)?.legalMoves?.firstOrNull() ?: return seen
                room.move(human, move)
            }
        }
    }
    return seen
}

class HappeningsTest {
    private fun table(target: Int = 60): Room {
        val room = Room("BOOK", "ali", Rules(target = target), clock = { 0L })
        room.join("ali", "Ali")
        (1..3).forEach { room.setBot("ali", it, true, Difficulty.NORMAL) }
        room.start("ali", seed = 7)
        return room
    }

    @Test
    fun `a match is told from its start to its end, each game with its own steps`() {
        val room = table()
        val happenings = playOut(room, "ali")

        val start = assertIs<Room.Happening.MatchStarted>(happenings.first())
        assertEquals(listOf("Ali", "Bot 2", "Bot 3", "Bot 4"), start.seats.map { it.name })

        val end = assertIs<Room.Happening.MatchFinished>(happenings.last())
        assertEquals(room.game!!.losingTeam, end.losingTeam)
        assertEquals(1, happenings.count { it is Room.Happening.MatchFinished })

        val games = happenings.filterIsInstance<Room.Happening.GameFinished>()
        assertTrue(games.isNotEmpty())
        assertEquals((1..games.size).toList(), games.map { it.result.gameNumber })
        for (game in games) {
            // Each game's steps begin with its own deal and end with how it ended.
            val firstDeal = game.steps.first()
            assertTrue(firstDeal is GameEvent.Dealt || firstDeal is GameEvent.Redealt, "starts with the deal: $firstDeal")
            assertEquals(GameEvent.GameEnded(game.result), game.steps.filterIsInstance<GameEvent.GameEnded>().single())
            val laid = game.steps.count { it is GameEvent.Opened || it is GameEvent.Played || it is GameEvent.PlayedFor }
            assertTrue(laid > 0)
        }
        assertEquals(end.scores, room.game!!.scores)
    }

    @Test
    fun `nothing is told twice`() {
        val room = table()
        room.takeHappenings()
        assertEquals(emptyList(), room.takeHappenings())
    }
}
