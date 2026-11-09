package com.teera.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.teera.app.ui.TableScreen
import com.teera.app.ui.stepsOf
import com.teera.core.Arm
import com.teera.core.Board
import com.teera.core.Domino
import com.teera.core.GameEnd
import com.teera.core.GameEvent
import com.teera.core.GameResult
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

private fun view(events: List<GameEvent>, game: Int = 1, phase: Phase = Phase.IN_PLAY) = PlayerView(
    roomCode = "TEST",
    rules = Rules.DEFAULT,
    seats = listOf(Seat(0, "a", "علي"), Seat(1, "b", "سارة"), Seat(2, "c", "عمر"), Seat(3, "d", "نور")),
    yourSeat = 0,
    hand = emptyList(),
    handCounts = listOf(0, 3, 3, 3),
    board = Board().open(d(6, 6)),
    turn = 0,
    actingSeat = null,
    phase = phase,
    scores = listOf(0, 0),
    alerts = listOf(0, 0),
    gameNumber = game,
    events = events,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepsTest {
    @get:Rule
    val compose = createComposeRule()

    private val dealt = GameEvent.Dealt(1, 0, null)
    private val opened = GameEvent.Opened(0, d(6, 6))
    private val played = GameEvent.Played(1, d(6, 3), Arm.NORTH)
    private val teera = GameEvent.TeeraCalled(1, 3)

    @Test
    fun `each view adds only what is new, and a deal starts again`() {
        val v1 = view(listOf(dealt))
        val v2 = view(listOf(opened))
        val v3 = view(listOf(played))
        val v4 = view(listOf(played, teera)) // a call tacked on after the move
        var steps = gatherSteps(emptyList(), null, v1)
        steps = gatherSteps(steps, v1, v2)
        steps = gatherSteps(steps, v2, v3)
        steps = gatherSteps(steps, v3, v3) // the same view again: nothing happened
        steps = gatherSteps(steps, v3, v4)
        assertEquals(listOf(dealt, opened, played, teera), steps)

        val next = view(listOf(GameEvent.Dealt(2, 1, null)), game = 2)
        assertEquals(next.events, gatherSteps(steps, v4, next))
    }

    @Test
    fun `steps read as who did what`() {
        val lines = stepsOf(view(emptyList()), listOf(opened, GameEvent.Stuck(2, 1), GameEvent.Gave(1, 2), teera))
            .map { it.text }
        assertEquals(listOf("علي فتح", "عمر گلى", "سارة انطى بول لـ عمر", "سارة طيّر بـ 3"), lines)
    }

    @Test
    fun `the review opens from the sums once the game is over`() {
        val over = view(emptyList(), phase = Phase.GAME_OVER).copy(
            lastGame = GameResult(1, GameEnd.X, 0, listOf(0, 9, 4, 12), listOf(-25, 21), 0),
        )
        compose.setContent {
            TableScreen(over, onMove = {}, onNextGame = {}, onLeave = {}, steps = listOf(dealt, opened, played))
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("reviewSteps").performClick()
        compose.onNodeWithText("خطوات الگيم").assertIsDisplayed()
        compose.onNodeWithText("سارة لعب").assertIsDisplayed()
    }

    @Test
    fun `the table is told why the bones were dealt again, or why the opening moved`() {
        val before = view(emptyList(), phase = Phase.GAME_OVER).copy(
            lastGame = GameResult(1, GameEnd.X, 1, listOf(9, 0, 4, 12), listOf(21, -25), nextOpener = 1),
        )
        val moved = view(listOf(GameEvent.Dealt(2, 2, null)), game = 2)
        assertEquals("سارة ما عنده چفت، فالفتحة صارت لـ عمر", explainDeal(before, moved))

        val kept = view(listOf(GameEvent.Dealt(2, 1, null)), game = 2)
        assertEquals(null, explainDeal(before, kept))

        val redealt = view(
            listOf(GameEvent.Opened(0, d(4, 4)), GameEvent.Redealt(com.teera.core.RedealReason.OPPONENTS_CANNOT_ANSWER, 0), GameEvent.Dealt(2, 0, null)),
            game = 2,
        )
        assertEquals("انعاد الخلط: الخصم ما عنده رقم چفت الفتحة", explainDeal(before, redealt))
    }

    @Test
    fun `a game of an old match opens its steps, names and all`() {
        val detail = com.teera.core.MatchDetail(
            id = "m1", roomCode = "QCNU", startedAtEpochMs = 0, names = listOf("علي", "سارة", "عمر", "نور"),
            scores = listOf(10, 20), target = 201, losingTeam = null,
            games = listOf(com.teera.core.PlayedGame(1, GameEnd.X, 0, listOf(-25, 21), listOf(-25, 21), listOf(dealt, opened, played))),
        )
        compose.setContent { com.teera.app.ui.MatchScreen(detail, loading = false, mySeat = 0, onClose = {}) }
        compose.onNodeWithTag("game-1").performClick()
        compose.onNodeWithText("سارة لعب").assertIsDisplayed()
    }

    @Test
    fun `a tournament is made from its name and four teams`() {
        var made: Pair<String, List<String>>? = null
        compose.setContent { com.teera.app.ui.CreateTournamentDialog(onCreate = { n, t -> made = n to t }, onClose = {}) }
        compose.onNodeWithTag("tournament-name").performTextInput("بطولة")
        listOf("علي وعبود", "حسن وسيف", "عمر ونور", "سارة ومريم").forEachIndexed { i, team ->
            compose.onNodeWithTag("team-$i").performScrollTo().performTextInput(team)
        }
        compose.onNodeWithTag("tournament-create").performScrollTo().performClick()
        assertEquals("بطولة" to listOf("علي وعبود", "حسن وسيف", "عمر ونور", "سارة ومريم"), made)
    }
}
