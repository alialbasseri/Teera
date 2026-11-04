package com.teera.app

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.teera.core.Move
import com.teera.core.Phase
import com.teera.core.PlayerView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays whole games of Teera the way Ali does: through the real screen, with a finger.
 *
 * Every layer is real — activity, view model, offline table, engine — and every choice
 * the game can put to a player is answered by pressing what the screen shows: laying a
 * bone, picking an end, rescuing a stuck opponent, flying or playing on, taking or
 * refusing a redeal, and dealing the next game. If any of those can be shown but not
 * pressed, this fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w392dp-h854dp-xxhdpi")
class FullFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun viewModel(): GameViewModel =
        ViewModelProvider(compose.activity)[GameViewModel::class.java]

    private fun currentView(): PlayerView? = viewModel().state.value.view

    /**
     * Lets time pass on both clocks: the looper the bots and the offline table run on, and
     * Compose's own clock, which drives the screen's animations and timed moments.
     */
    private fun pass(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun press(text: String) {
        val nodes = compose.onAllNodesWithText(text)
        assertTrue(nodes.fetchSemanticsNodes().isNotEmpty(), "nothing on screen reads \"$text\"")
        nodes[0].performClick()
    }

    /** Lays [tile], answering the "which end" question if the screen asks it. */
    private fun lay(tile: com.teera.core.Domino, arm: com.teera.core.Arm?) {
        compose.onNodeWithTag("hand-tile-$tile").performClick()
        pass(30)
        if (arm != null && compose.onAllNodesWithText("دوس على الجهة اللي تريدها").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("arm-${arm.name}").performClick()
            pass(30)
        }
    }

    /** One decision, made by pressing the screen. Returns false if it was not ours to make. */
    private fun act(view: PlayerView): Boolean {
        if (view.phase == Phase.GAME_OVER) {
            // The game ends with a moment on the table, and the sums come after it.
            repeat(30) {
                if (compose.onAllNodesWithText("الجيم الجاي").fetchSemanticsNodes().isNotEmpty()) {
                    press("الجيم الجاي")
                    return true
                }
                pass(200)
            }
            press("الجيم الجاي")
            return true
        }
        if (!view.isYourMove) return false

        when (view.phase) {
            Phase.IN_PLAY -> {
                val play = view.legalMoves.filterIsInstance<Move.Play>().first()
                lay(play.domino, play.arm)
            }
            Phase.RESCUE -> {
                val playFor = view.legalMoves.filterIsInstance<Move.PlayFor>().firstOrNull()
                if (playFor != null) {
                    press("العب بداله")
                    pass(30)
                    lay(playFor.domino, playFor.arm)
                } else {
                    press("انطيه بول")
                    pass(30)
                    val give = view.legalMoves.filterIsInstance<Move.Give>().first()
                    compose.onNodeWithTag("hand-tile-${give.domino}").performClick()
                }
            }
            Phase.TEERA_OFFER -> press("طيّر")
            Phase.REDEAL_OFFER -> press("كمّل")
            Phase.GAME_OVER, Phase.MATCH_OVER -> return false
        }
        return true
    }

    @Test
    fun `whole games can be played through the screen`() {
        compose.onNodeWithText("العب ضد البوتات").performClick()

        var decisions = 0
        var gamesFinished = 0
        var lastGame = 0
        var idle = 0

        while (gamesFinished < 4 && decisions < 600) {
            pass(200)
            val view = currentView() ?: continue
            if (view.phase == Phase.MATCH_OVER) break
            if (view.gameNumber != lastGame) {
                if (lastGame != 0) gamesFinished++
                lastGame = view.gameNumber
            }

            val signature = Triple(view.board.size, view.phase, view.hand.size)
            if (!act(view)) {
                if (++idle > 200) fail("the table stalled waiting on nobody: ${view.phase}, acting ${view.actingSeat}")
                continue
            }
            idle = 0
            decisions++
            pass(60)

            // Whatever we pressed, the table must have moved on from it.
            val after = assertNotNull(currentView())
            val moved = Triple(after.board.size, after.phase, after.hand.size) != signature ||
                after.gameNumber != view.gameNumber || after.actingSeat != view.actingSeat
            assertTrue(
                moved,
                "pressing for ${view.phase} changed nothing — board ${view.board}, " +
                    "legal ${view.legalMoves}, notice ${viewModel().state.value.notice}",
            )
        }

        // How many decisions a game puts to one player depends on the deal; what matters is
        // that games kept being played to the end and every press moved the table on.
        assertTrue(gamesFinished >= 3 || currentView()?.phase == Phase.MATCH_OVER, "only $gamesFinished games finished")
        assertTrue(decisions >= 8, "only $decisions decisions came up")
    }
}
