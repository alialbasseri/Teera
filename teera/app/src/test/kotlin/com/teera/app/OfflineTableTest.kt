package com.teera.app

import com.teera.app.net.LocalGameClient
import com.teera.core.ClientMessage
import com.teera.core.Difficulty
import com.teera.core.Pack
import com.teera.core.PlayerView
import com.teera.core.Rules
import com.teera.core.ServerMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The offline table drives the same Room the server does. If a player can never get a
 * move out of it, the game is unplayable however good the screen looks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineTableTest {

    @Test
    fun `the human is asked to move, and their move is taken`() = runTest {
        val views = mutableListOf<PlayerView>()
        val client = LocalGameClient(
            scope = this,
            playerId = "me",
            playerName = "Ali",
            rules = Rules.DEFAULT,
            botThinkMillis = 0,
        )
        val collector = launch { client.incoming.collect { if (it is ServerMessage.GameUpdate) views += it.view } }

        for (seat in 1 until Pack.PLAYERS) {
            client.send(ClientMessage.SetBot(seat, enabled = true, difficulty = Difficulty.NORMAL))
        }
        client.send(ClientMessage.StartMatch)
        advanceTimeBy(5_000)

        val mine = views.filter { it.isYourMove }
        assertTrue(mine.isNotEmpty(), "the table never came round to the human")
        assertTrue(
            mine.any { it.legalMoves.isNotEmpty() },
            "the human was on the move but had nothing the referee would accept",
        )

        // Play the move the referee offered, and the table must move on from it.
        val before = views.size
        val turn = mine.first { it.legalMoves.isNotEmpty() }
        client.send(ClientMessage.MakeMove(turn.legalMoves.first()))
        advanceTimeBy(5_000)
        assertTrue(views.size > before, "the move produced nothing back")

        collector.cancel()
        client.close()
    }
}
