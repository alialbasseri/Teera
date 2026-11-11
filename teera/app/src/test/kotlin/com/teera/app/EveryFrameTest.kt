package com.teera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.teera.core.Emote
import com.teera.core.Phase
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the app frame by frame, the way a phone does, rather than only once it settles.
 *
 * Ali's phone closed the app about a second after the menu appeared, with nobody touching
 * it. Every other test here looked at screens after their animations had finished, so a
 * value that only goes wrong partway through an animation - a spring overshooting past
 * the end of its range - could never show up. This one draws every frame of the opening.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h854dp-mdpi")
class EveryFrameTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    // One canvas reused for every frame: thousands of full-size bitmaps run out of memory.
    private var canvasBitmap: Bitmap? = null

    @Suppress("UNCHECKED_CAST")
    private fun drawEveryWindow() {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>
        for (view in views.filter { it.width > 0 && it.height > 0 }) {
            val bitmap = canvasBitmap?.takeIf { it.width >= view.width && it.height >= view.height }
                ?: Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                    canvasBitmap?.recycle()
                    canvasBitmap = it
                }
            view.draw(Canvas(bitmap))
        }
    }

    private fun frames(millis: Long) {
        compose.mainClock.autoAdvance = false
        repeat((millis / FRAME_MS).toInt()) {
            // Both clocks: Compose's for the animations, the looper's for the bots thinking.
            compose.mainClock.advanceTimeBy(FRAME_MS)
            shadowOf(Looper.getMainLooper()).idleFor(FRAME_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            drawEveryWindow()
        }
    }

    @Test
    fun `the menu survives its opening animation, frame by frame`() {
        frames(3_000)
    }

    /**
     * Plays offline, drawing every frame, through deals, tiles flying in, rescues, emotes,
     * the end-of-game moment and the next deal - every animation the table has.
     */
    @Test
    fun `the table survives whole games, frame by frame`() {
        frames(500)
        compose.onNodeWithText("العب ضد البوتات").performClick()
        frames(300) // the level picker opens
        compose.onNodeWithTag("level-NORMAL").performClick()

        val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        var gamesSeen = 0
        var lastGame = 0
        var elapsed = 0L
        while (gamesSeen < 2 && elapsed < 90_000) {
            frames(FRAME_MS * 6)
            elapsed += FRAME_MS * 6
            val view = model.state.value.view ?: continue
            if (view.gameNumber != lastGame) {
                if (lastGame != 0) gamesSeen++
                lastGame = view.gameNumber
            }
            when {
                // Let the end-of-game moment play out in full before dealing again.
                view.phase == Phase.GAME_OVER -> {
                    frames(2_400)
                    elapsed += 2_400
                    compose.runOnUiThread { model.nextGame() }
                }
                view.isYourMove -> {
                    model.sendEmote(Emote.GULP)
                    compose.runOnUiThread { model.play(view.legalMoves.first()) }
                }
            }
        }
        assertTrue(gamesSeen >= 1, "no game finished while drawing frames")
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
