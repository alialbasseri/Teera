package com.teera.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.teera.app.ui.TableScreen
import com.teera.core.Arm
import com.teera.core.Board
import com.teera.core.Domino
import com.teera.core.Move
import com.teera.core.Phase
import com.teera.core.PlayerView
import com.teera.core.Rules
import com.teera.core.Seat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private fun d(a: Int, b: Int) = Domino.of(a, b)

/** The board a full game once got stuck on: long arms, an end target near the edge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w392dp-h854dp-xxhdpi")
class RescueTapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `playing for a stuck player on a long board reaches the end that was tapped`() {
        var board = Board().open(d(6, 6))
        listOf(d(6, 3), d(3, 3), d(3, 5), d(5, 1), d(1, 6), d(6, 0), d(0, 5)).forEach { board = board.place(it, Arm.NORTH) }
        listOf(d(6, 5), d(5, 5), d(5, 4), d(4, 3), d(3, 2), d(2, 2)).forEach { board = board.place(it, Arm.EAST) }
        listOf(d(6, 4), d(4, 4), d(4, 2)).forEach { board = board.place(it, Arm.SOUTH) }
        listOf(d(6, 2), d(2, 5)).forEach { board = board.place(it, Arm.WEST) }
        val view = PlayerView(
            roomCode = "T", rules = Rules.DEFAULT,
            seats = (0..3).map { Seat(it, "p$it", "P$it") },
            yourSeat = 0, hand = listOf(d(1, 3), d(0, 2)), handCounts = listOf(2, 3, 3, 3),
            board = board, turn = 0, actingSeat = 0, phase = Phase.RESCUE,
            scores = listOf(0, 0), alerts = listOf(0, 0), gameNumber = 2, stuckSeat = 3, rescuer = 0,
            legalMoves = listOf(
                Move.PlayFor(d(0, 2), Arm.EAST), Move.PlayFor(d(0, 2), Arm.SOUTH),
                Move.Give(d(1, 3)), Move.Give(d(0, 2)),
            ),
        )
        val moves = mutableListOf<Move>()
        compose.setContent { TableScreen(view, onMove = { moves += it }, onNextGame = {}, onLeave = {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("العب بداله").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("hand-tile-${d(0, 2)}").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("دوس على الجهة اللي تريدها").assertExists()
        compose.onNodeWithTag("arm-EAST").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(listOf<Move>(Move.PlayFor(d(0, 2), Arm.EAST)), moves)
    }
}
