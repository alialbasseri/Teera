package com.teera.app

import com.teera.app.net.LanHost
import com.teera.app.net.WebSocketGameClient
import com.teera.app.net.openRoomAt
import com.teera.core.ClientMessage
import com.teera.core.Difficulty
import com.teera.core.Pack
import com.teera.core.PlayerView
import com.teera.core.RoomInfo
import com.teera.core.Rules
import com.teera.core.ServerMessage
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A table hosted on one phone and joined from others, over real sockets.
 *
 * The host here is the very [LanHost] the app starts, and the players are the very
 * [WebSocketGameClient] a friend's phone uses, so this is the whole path two people in
 * one living room go through — short of the radio between them.
 */
class WifiTableTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val host = LanHost()

    @After
    fun tearDown() {
        host.stop()
        scope.cancel()
    }

    /** A player on their own phone: a socket, and everything the referee has said to them. */
    private inner class Phone(name: String, port: Int) {
        val heard = CopyOnWriteArrayList<ServerMessage>()
        val client = WebSocketGameClient("ws://127.0.0.1:$port/ws", scope, "id-$name", name)

        init {
            scope.launch { client.incoming.collect { heard += it } }
        }

        val room: RoomInfo? get() = heard.filterIsInstance<ServerMessage.RoomUpdate>().lastOrNull()?.room
        val view: PlayerView? get() = heard.filterIsInstance<ServerMessage.GameUpdate>().lastOrNull()?.view
    }

    private suspend fun until(what: String, check: () -> Boolean) {
        withTimeout(10_000) {
            while (!check()) delay(25)
        }
        assertTrue(check(), what)
    }

    @Test
    fun `friends on the same network sit down at one phone's table and are dealt in`() = runBlocking {
        val port = host.start()
        val ali = Phone("Ali", port)
        ali.client.send(ClientMessage.CreateRoom(Rules.DEFAULT))
        until("the host's room exists") { ali.room != null }
        val code = assertNotNull(ali.room).code

        // A friend who only knows the host's address can still find the room on it.
        assertEquals(code, openRoomAt("127.0.0.1", port))

        val sara = Phone("Sara", port)
        sara.client.send(ClientMessage.JoinRoom(code))
        until("Sara has a seat") { ali.room?.seats?.any { it.name == "Sara" } == true }

        // Sara moves across the table to sit as Ali's partner.
        sara.client.send(ClientMessage.TakeSeat(2))
        until("Sara is sitting across from Ali") { ali.room?.seats?.firstOrNull { it.seat == 2 }?.name == "Sara" }

        for (seat in listOf(1, 3)) ali.client.send(ClientMessage.SetBot(seat, true, Difficulty.NORMAL))
        until("the table is full") { ali.room?.seats?.all { it.playerId.isNotEmpty() } == true }

        ali.client.send(ClientMessage.StartMatch)
        until("both phones are dealt in") { ali.view != null && sara.view != null }

        val aliHand = assertNotNull(ali.view).hand
        val saraHand = assertNotNull(sara.view).hand
        assertEquals(Pack.HAND_SIZE, aliHand.size)
        assertEquals(Pack.HAND_SIZE, saraHand.size)
        assertTrue(aliHand.intersect(saraHand.toSet()).isEmpty(), "each phone sees only its own hand")

        // Whoever is asked plays what the referee offers, and the other phone sees it land.
        repeat(6) {
            until("somebody is asked to move") {
                listOf(ali, sara).any { it.view?.isYourMove == true && it.view?.legalMoves?.isNotEmpty() == true }
            }
            val mover = listOf(ali, sara).first { it.view?.isYourMove == true && it.view?.legalMoves?.isNotEmpty() == true }
            val other = if (mover === ali) sara else ali
            // Counted in updates rather than tiles: a tile handed over, a redeal or a teera
            // all change the table without changing how many tiles are on it.
            val before = other.heard.count { it is ServerMessage.GameUpdate }
            mover.client.send(ClientMessage.MakeMove(assertNotNull(mover.view).legalMoves.first()))
            until("the move reaches the other phone") {
                other.heard.count { it is ServerMessage.GameUpdate } > before
            }
            assertTrue(mover.heard.none { it is ServerMessage.MoveRejected }, "the referee refused a move it offered")
            if (assertNotNull(other.view).phase == com.teera.core.Phase.GAME_OVER) {
                mover.client.send(ClientMessage.NextGame)
            }
        }

        ali.client.close()
        sara.client.close()
    }
}
