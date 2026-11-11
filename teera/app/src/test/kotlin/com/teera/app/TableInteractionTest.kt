package com.teera.app

import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.teera.app.ui.TableScreen
import com.teera.core.Arm
import com.teera.core.Board
import com.teera.core.Domino
import com.teera.core.Engine
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
import kotlin.test.assertTrue

private fun d(a: Int, b: Int) = Domino.of(a, b)

private fun board() = Board().open(d(6, 6))
    .place(d(6, 3), Arm.NORTH)
    .place(d(6, 2), Arm.EAST)
    .place(d(6, 1), Arm.SOUTH)
    .place(d(6, 0), Arm.WEST)

/** A table where it is your move and 3|5 answers the three. */
private fun yourMove(): PlayerView {
    val hand = listOf(d(3, 5), d(4, 4), d(5, 5))
    return PlayerView(
        roomCode = "TEST",
        rules = Rules.DEFAULT,
        seats = (0..3).map { Seat(it, "p$it", "Player $it") },
        yourSeat = 0,
        hand = hand,
        handCounts = listOf(3, 3, 3, 3),
        board = board(),
        turn = 0,
        actingSeat = 0,
        phase = Phase.IN_PLAY,
        scores = listOf(0, 0),
        alerts = listOf(0, 0),
        gameNumber = 1,
        legalMoves = listOf(Move.Play(d(3, 5), Arm.NORTH)),
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TableInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a playable bone lays it`() {
        val moves = mutableListOf<Move>()
        compose.setContent {
            TableScreen(view = yourMove(), onMove = { moves += it }, onNextGame = {}, onLeave = {})
        }

        compose.onNodeWithTag("hand-tile-3|5").performClick()
        compose.waitForIdle()

        assertEquals(listOf<Move>(Move.Play(d(3, 5), Arm.NORTH)), moves)
    }

    @Test
    fun `tapping a bone that fits two ends asks which`() {
        val view = yourMove().copy(
            hand = listOf(d(1, 3), d(4, 4)),
            legalMoves = listOf(Move.Play(d(1, 3), Arm.NORTH), Move.Play(d(1, 3), Arm.SOUTH)),
        )
        val moves = mutableListOf<Move>()
        compose.setContent {
            TableScreen(view = view, onMove = { moves += it }, onNextGame = {}, onLeave = {})
        }

        compose.onNodeWithTag("hand-tile-1|3").performClick()
        compose.waitForIdle()

        assertTrue(moves.isEmpty(), "a bone with two homes waits for the player to pick one")
        compose.onNodeWithTag("arm-NORTH").performClick()
        compose.waitForIdle()
        assertEquals(listOf<Move>(Move.Play(d(1, 3), Arm.NORTH)), moves)
    }

    @Test
    fun `the engine and the screen agree on what is playable`() {
        val view = yourMove()
        // Whatever the referee listed is exactly what the hand should light up.
        assertTrue(view.legalMoves.isNotEmpty())
        assertTrue(Engine.legalMoves(
            com.teera.core.GameState(
                rules = Rules.DEFAULT,
                seats = view.seats,
                hands = listOf(view.hand, listOf(d(2, 2)), listOf(d(0, 0)), listOf(d(1, 1))),
                board = view.board,
                turn = 0,
                scores = listOf(0, 0),
                phase = Phase.IN_PLAY,
            ),
            0,
        ).isNotEmpty())
    }

    @Test
    fun `a bone can be pressed, held and slid to a new place in the hand`() {
        compose.setContent {
            com.teera.app.ui.theme.TeeraTheme(dark = true) {
                TableScreen(yourMove().copy(actingSeat = 1, legalMoves = emptyList()), onMove = {}, onNextGame = {}, onLeave = {})
            }
        }
        fun left(tile: Domino) = compose.onNodeWithTag("hand-tile-$tile").fetchSemanticsNode().boundsInRoot.left
        val first = d(3, 5)
        val second = d(4, 4)
        // Right to left: the first bone sits on the right, the second to its left.
        assertTrue(left(first) > left(second))
        val step = left(first) - left(second)

        compose.onNodeWithTag("hand-tile-$first").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(androidx.compose.ui.geometry.Offset(-step * 1.1f, 0f))
            up()
        }
        compose.waitForIdle()
        assertTrue(left(first) < left(second), "3|5 now sits to the left of 4|4")
    }

    @Test
    fun `two fingers zoom the table, one button puts it back, and the arms still take a tap`() {
        var tapped: Arm? = null
        compose.setContent {
            com.teera.app.ui.BoardView(
                board(),
                modifier = androidx.compose.ui.Modifier
                    .size(360.dp, 600.dp)
                    .testTag("board"),
                liveArms = setOf(Arm.NORTH),
                onArmTapped = { tapped = it },
            )
        }
        compose.onNodeWithTag("board").performTouchInput {
            pinch(
                start0 = center - androidx.compose.ui.geometry.Offset(20f, 0f),
                end0 = center - androidx.compose.ui.geometry.Offset(160f, 0f),
                start1 = center + androidx.compose.ui.geometry.Offset(20f, 0f),
                end1 = center + androidx.compose.ui.geometry.Offset(160f, 0f),
            )
        }
        compose.onNodeWithTag("zoom-reset").assertExists()
        compose.onNodeWithTag("zoom-reset").performClick()
        compose.onNodeWithTag("zoom-reset").assertDoesNotExist()
        compose.onNodeWithTag("arm-NORTH").performClick()
        assertEquals(Arm.NORTH, tapped)
    }

    @Test
    fun `blocking somebody at the table, and the host sending somebody out, go through the menu`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            TableScreen(
                yourMove(), onMove = {}, onNextGame = {}, onLeave = {},
                chat = emptyList(), onChat = {},
                myId = "p0", isHost = true,
                onKick = { calls += "kick $it" },
                onLock = { calls += "lock $it" },
                onBlock = { id, _, block -> calls += "block $id $block" },
            )
        }
        compose.onNodeWithContentDescription("القائمة").performClick()
        compose.onNodeWithText("احظر واحد…").performClick()
        compose.onNodeWithTag("person-p2").performClick()
        compose.onNodeWithContentDescription("القائمة").performClick()
        compose.onNodeWithText("طلّع واحد…").performClick()
        compose.onNodeWithTag("person-p1").performClick()
        compose.onNodeWithContentDescription("القائمة").performClick()
        compose.onNodeWithText("اقفل الغرفة على جماعتك").performClick()
        assertEquals(listOf("block p2 true", "kick p1", "lock true"), calls)
    }
}
