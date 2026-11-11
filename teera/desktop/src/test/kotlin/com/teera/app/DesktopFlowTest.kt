package com.teera.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.teera.app.ui.theme.TeeraTheme
import com.teera.core.Move
import com.teera.core.Phase
import com.teera.core.PlayerView
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays whole games on the Windows app the way a player would: with the mouse, through
 * the real window content, the shared view model and the offline table against bots.
 * Pictures of each screen land in desktop/build/shots.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopFlowTest {

    private val dataDir = kotlin.io.path.createTempDirectory("teera-test").toFile()
    private val model = GameViewModel(DesktopApp(dataDir))

    private fun DesktopComposeUiTest.pass(millis: Long) {
        // The bots think on the window's own thread, in real time.
        var left = millis
        while (left > 0) {
            Thread.sleep(minOf(left, 50))
            left -= 50
            waitForIdle()
        }
    }

    private fun DesktopComposeUiTest.shot(name: String) {
        val dir = File(System.getProperty("teera.shots") ?: return).apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SemanticsNodeInteractionsProvider.has(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.press(text: String) {
        val nodes = onAllNodesWithText(text)
        assertTrue(nodes.fetchSemanticsNodes().isNotEmpty(), "nothing on screen reads \"$text\"")
        nodes[0].performClick()
    }

    private fun DesktopComposeUiTest.lay(tile: com.teera.core.Domino, arm: com.teera.core.Arm?) {
        onNodeWithTag("hand-tile-$tile").performClick()
        pass(60)
        if (arm != null && onAllNodesWithTag("arm-${arm.name}").fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag("arm-${arm.name}").performClick()
            pass(60)
        }
    }

    private fun DesktopComposeUiTest.act(view: PlayerView): Boolean {
        if (view.phase == Phase.GAME_OVER) {
            repeat(40) {
                if (has("الگيم الجاي")) {
                    shot("game-over")
                    press("الگيم الجاي")
                    return true
                }
                pass(200)
            }
            press("الگيم الجاي")
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
                    pass(60)
                    lay(playFor.domino, playFor.arm)
                } else {
                    press("انطيه بول")
                    pass(60)
                    val give = view.legalMoves.filterIsInstance<Move.Give>().first()
                    onNodeWithTag("hand-tile-${give.domino}").performClick()
                }
            }
            Phase.TEERA_OFFER -> press("طيّر")
            Phase.REDEAL_OFFER -> press("كمّل")
            Phase.GAME_OVER, Phase.MATCH_OVER -> return false
        }
        return true
    }

    @Test
    fun `games against the bots play through to the end with the mouse`() = runDesktopComposeUiTest(1280, 800) {
        setContent { TeeraTheme(dark = true) { TeeraApp(sound = null, viewModel = model) } }
        pass(300)
        shot("menu")

        press("العب ضد البوتات")
        pass(100)
        shot("levels")
        onNodeWithTag("level-NORMAL").performClick()

        var decisions = 0
        var gamesFinished = 0
        var lastGame = 0
        var idle = 0
        var tableShot = false
        while (gamesFinished < 2 && decisions < 400) {
            pass(150)
            val view = model.state.value.view ?: continue
            if (view.phase == Phase.MATCH_OVER) break
            if (view.gameNumber != lastGame) {
                if (lastGame != 0) gamesFinished++
                lastGame = view.gameNumber
            }
            if (!tableShot && view.isYourMove && view.phase == Phase.IN_PLAY && view.board.size >= 3) {
                shot("table")
                tableShot = true
            }
            val signature = Triple(view.board.size, view.phase, view.hand.size)
            if (!act(view)) {
                if (++idle > 400) fail("the table stalled waiting on nobody: ${view.phase}, acting ${view.actingSeat}")
                continue
            }
            idle = 0
            decisions++
            pass(100)
            val after = assertNotNull(model.state.value.view)
            val moved = Triple(after.board.size, after.phase, after.hand.size) != signature ||
                after.gameNumber != view.gameNumber || after.actingSeat != view.actingSeat
            assertTrue(moved, "pressing for ${view.phase} changed nothing: legal ${view.legalMoves}")
        }
        assertTrue(gamesFinished >= 2 || model.state.value.view?.phase == Phase.MATCH_OVER, "only $gamesFinished games finished")
        assertTrue(decisions >= 6, "only $decisions decisions came up")

        // Esc is the phone's back button: from the table it asks before leaving.
        model.leave()
        pass(200)
        assertTrue(has("العب ضد البوتات"), "leaving did not come back to the menu")
        model.close()
    }

    @Test
    fun `settings survive a restart`() {
        val prefs = DesktopPrefs(File(dataDir, "s.properties"))
        prefs.edit().putString("playerName", "علي").putBoolean("x", true).putInt("n", 7).apply()
        val again = DesktopPrefs(File(dataDir, "s.properties"))
        kotlin.test.assertEquals("علي", again.getString("playerName", null))
        kotlin.test.assertEquals(true, again.getBoolean("x", false))
        kotlin.test.assertEquals(7, again.getInt("n", 0))
        again.edit().remove("playerName").apply()
        kotlin.test.assertEquals(null, DesktopPrefs(File(dataDir, "s.properties")).getString("playerName", null))
    }

    @Test
    fun `pictures go out as small JPEGs and come back`() {
        val big = java.awt.image.BufferedImage(1600, 1000, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val base64 = assertNotNull(Pictures.jpegBase64(big, 720, 1280, 70))
        val bytes = java.util.Base64.getDecoder().decode(base64)
        val back = ImageIO.read(bytes.inputStream())
        kotlin.test.assertEquals(720, back.width)
        assertNotNull(Pictures.decode(bytes))
    }
}
