package com.teera.app

import com.teera.app.sound.Cue
import com.teera.app.sound.Mood
import com.teera.app.sound.moodOf
import com.teera.app.sound.react
import com.teera.app.ui.RescueMode
import com.teera.app.ui.Tap
import com.teera.app.ui.decideTap
import com.teera.core.Arm
import com.teera.core.Board
import com.teera.core.Domino
import com.teera.core.Emote
import com.teera.core.GameEnd
import com.teera.core.GameEvent
import com.teera.core.GameResult
import com.teera.core.Move
import com.teera.core.Phase
import com.teera.core.PlayerView
import com.teera.core.Rules
import com.teera.core.Seat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun d(a: Int, b: Int) = Domino.of(a, b)

private fun view(
    events: List<GameEvent> = emptyList(),
    acting: Int? = 1,
    phase: Phase = Phase.IN_PLAY,
    scores: List<Int> = listOf(0, 0),
    counts: List<Int> = listOf(7, 7, 7, 7),
    legal: List<Move> = emptyList(),
    board: Board = Board().open(d(6, 6)),
    losingTeam: Int? = null,
) = PlayerView(
    roomCode = "T",
    rules = Rules.DEFAULT,
    seats = (0..3).map { Seat(it, "p$it", "P$it") },
    yourSeat = 0,
    hand = listOf(d(6, 1), d(6, 0), d(4, 4)),
    handCounts = counts,
    board = board,
    turn = acting ?: 0,
    actingSeat = acting,
    phase = phase,
    scores = scores,
    alerts = listOf(0, 0),
    gameNumber = 1,
    legalMoves = legal,
    events = events,
    losingTeam = losingTeam,
)

class ReactionsTest {

    @Test
    fun `a laid tile clacks and a given one slides`() {
        assertEquals(listOf(Cue.CLACK), react(view(), view(listOf(GameEvent.Played(1, d(6, 2), Arm.NORTH)))).cues)
        assertEquals(listOf(Cue.SLIDE), react(view(), view(listOf(GameEvent.Gave(1, 2)))).cues)
    }

    @Test
    fun `a stuck seat knocks the table and asks the question out loud`() {
        val reaction = react(view(), view(listOf(GameEvent.Stuck(2, 1))))
        assertEquals(listOf(Cue.KNOCK), reaction.cues)
        assertEquals("تلعب تنطي", reaction.lines.single().text)
    }

    @Test
    fun `each way a game ends has its own sound`() {
        fun ended(end: GameEnd) = react(
            view(),
            view(listOf(GameEvent.GameEnded(GameResult(1, end, 1, listOf(0, 0, 0, 0), listOf(0, 0), 1)))),
        ).cues
        assertEquals(listOf(Cue.X), ended(GameEnd.X))
        assertEquals(listOf(Cue.TEERA), ended(GameEnd.TEERA))
        assertEquals(listOf(Cue.BLOCKED), ended(GameEnd.BLOCKED))
    }

    @Test
    fun `the end of a match sounds like which side you were on`() {
        val lost = view(listOf(GameEvent.MatchEnded(0, listOf(205, 40))))
        assertEquals(listOf(Cue.MATCH_LOST), react(view(), lost).cues)
        val won = view(listOf(GameEvent.MatchEnded(1, listOf(40, 205))))
        assertEquals(listOf(Cue.MATCH_WON), react(view(), won).cues)
    }

    @Test
    fun `the chime is for the turn arriving, not for every look at it`() {
        assertTrue(Cue.YOUR_TURN in react(view(acting = 3), view(acting = 0)).cues)
        assertTrue(Cue.YOUR_TURN !in react(view(acting = 0), view(acting = 0)).cues)
    }

    @Test
    fun `the music tightens near the target or when someone is nearly out`() {
        assertEquals(Mood.CALM, moodOf(view()))
        assertEquals(Mood.TENSE, moodOf(view(scores = listOf(160, 20))))
        assertEquals(Mood.TENSE, moodOf(view(counts = listOf(5, 2, 6, 6))))
    }

    @Test
    fun `the soundboard says what it means`() {
        assertEquals("ابلع", react(Emote.GULP).lines.single().text)
        assertEquals(listOf(Cue.EMOTE_DRUMROLL), react(Emote.DRUMROLL).cues)
    }
}

/** The bug Ali recorded: during the four-sides phase a lit bone must lay on one press. */
class DecideTapTest {

    @Test
    fun `a bone pressed while the opening double's sides are closing is laid at once`() {
        val v = view(acting = 0, legal = listOf(Move.Play(d(6, 1), null), Move.Play(d(6, 0), null)))
        assertEquals(Tap.Send(Move.Play(d(6, 1), null)), decideTap(v, d(6, 1), null, null))
    }

    @Test
    fun `a bone with one home is laid there, with two it waits for an end`() {
        val one = view(acting = 0, legal = listOf(Move.Play(d(6, 1), Arm.EAST)))
        assertEquals(Tap.Send(Move.Play(d(6, 1), Arm.EAST)), decideTap(one, d(6, 1), null, null))

        val two = view(acting = 0, legal = listOf(Move.Play(d(6, 1), Arm.EAST), Move.Play(d(6, 1), Arm.WEST)))
        assertEquals(Tap.Select(d(6, 1)), decideTap(two, d(6, 1), null, null))
        assertEquals(Tap.Select(null), decideTap(two, d(6, 1), d(6, 1), null), "a second press puts it back")
    }

    @Test
    fun `nothing happens off turn or on a bone that cannot go`() {
        val v = view(acting = 2, legal = emptyList())
        assertEquals(Tap.Ignore, decideTap(v, d(6, 1), null, null))
        val mine = view(acting = 0, legal = listOf(Move.Play(d(6, 1), null)))
        assertEquals(Tap.Ignore, decideTap(mine, d(4, 4), null, null))
    }

    @Test
    fun `a rescuer gives or plays for the stuck seat depending on what they chose`() {
        val v = view(
            acting = 0,
            phase = Phase.RESCUE,
            legal = listOf(Move.Give(d(4, 4)), Move.Give(d(6, 1)), Move.PlayFor(d(6, 1), Arm.SOUTH)),
        )
        assertEquals(Tap.Ignore, decideTap(v, d(4, 4), null, null), "the choice comes first")
        assertEquals(Tap.Send(Move.Give(d(4, 4))), decideTap(v, d(4, 4), null, RescueMode.GIVE))
        assertEquals(Tap.Send(Move.PlayFor(d(6, 1), Arm.SOUTH)), decideTap(v, d(6, 1), null, RescueMode.PLAY_FOR))
    }
}
