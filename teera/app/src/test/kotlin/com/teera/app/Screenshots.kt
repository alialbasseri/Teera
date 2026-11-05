package com.teera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.teera.app.ui.LobbyScreen
import com.teera.app.ui.MenuScreen
import com.teera.app.ui.TableScreen
import com.teera.app.ui.theme.TeeraTheme
import com.teera.core.Arm
import com.teera.core.Board
import com.teera.core.Domino
import com.teera.core.GameEnd
import com.teera.core.GameEvent
import com.teera.core.GameResult
import com.teera.core.Move
import com.teera.core.Phase
import com.teera.core.PlayerView
import com.teera.core.RoomInfo
import com.teera.core.Rules
import com.teera.core.Seat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens to pictures, so the look can be reviewed without a phone.
 *
 * Not an assertion of taste: it writes PNGs to build/screenshots and checks only that a
 * screen drew something. What it is for is being looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h854dp-xxhdpi")
class Screenshots {

    @get:Rule
    val compose = createComposeRule()

    private val out = File(System.getProperty("teera.screenshots") ?: "build/screenshots").apply { mkdirs() }

    /**
     * Renders [content] and writes every window it opens: the screen itself, and a dialog
     * over it if there is one. The clock is driven by hand because the table has a
     * breathing ring that never stops, so "wait until idle" would wait forever.
     */
    private fun shoot(name: String, settleMillis: Long = 2_500, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { TeeraTheme(dark = true) { content() } }
        repeat((settleMillis / 100).toInt()) {
            compose.mainClock.advanceTimeBy(100)
            shadowOf(Looper.getMainLooper()).idle()
        }

        // Every window on screen, oldest first: the activity, then any dialog over it.
        // Drawn straight into a bitmap rather than through captureToImage, which waits for
        // a redraw on the same thread Robolectric would need to produce it.
        val views = windowRoots()
        val screen = views.first()
        val bitmap = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        screen.draw(canvas)
        for (dialog in views.drop(1)) {
            canvas.drawColor(android.graphics.Color.argb(150, 0, 0, 0))
            canvas.save()
            canvas.translate(
                (screen.width - dialog.width) / 2f,
                (screen.height - dialog.height) / 2f,
            )
            dialog.draw(canvas)
            canvas.restore()
        }

        val file = File(out, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT=${file.absolutePath}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowRoots(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(instance) as List<View>).filter { it.width > 0 && it.height > 0 }
    }

    private fun d(a: Int, b: Int) = Domino.of(a, b)

    private val seats = listOf(
        Seat(0, "me", "علي"), Seat(1, "p1", "سارة"), Seat(2, "p2", "عمر"), Seat(3, "p3", "نور"),
    )

    private fun midGame(): PlayerView {
        val board = Board().open(d(6, 6))
            .place(d(6, 3), Arm.NORTH).place(d(6, 2), Arm.EAST)
            .place(d(6, 1), Arm.SOUTH).place(d(6, 0), Arm.WEST)
            .place(d(3, 5), Arm.NORTH).place(d(5, 5), Arm.NORTH)
            .place(d(2, 4), Arm.EAST).place(d(1, 1), Arm.SOUTH).place(d(0, 4), Arm.WEST)
        return PlayerView(
            roomCode = "KHAN",
            rules = Rules.DEFAULT,
            seats = seats,
            yourSeat = 0,
            hand = listOf(d(5, 4), d(4, 3), d(3, 3), d(2, 0), d(1, 5), d(0, 0)),
            handCounts = listOf(6, 4, 5, 3),
            board = board,
            turn = 0,
            actingSeat = 0,
            phase = Phase.IN_PLAY,
            scores = listOf(37, 112),
            alerts = listOf(0, 0),
            gameNumber = 4,
            legalMoves = listOf(
                Move.Play(d(5, 4), Arm.NORTH), Move.Play(d(5, 4), Arm.EAST), Move.Play(d(5, 4), Arm.WEST),
                Move.Play(d(1, 5), Arm.NORTH), Move.Play(d(1, 5), Arm.SOUTH),
            ),
            events = listOf(GameEvent.Played(3, d(0, 4), Arm.WEST)),
        )
    }

    @Test
    fun menu() = shoot("menu") {
        MenuScreen("علي", {}, {}, {}, {}, connecting = false)
    }

    @Test
    fun table() = shoot("table") {
        TableScreen(midGame(), onMove = {}, onNextGame = {}, onLeave = {})
    }

    @Test
    fun rescue() = shoot("rescue") {
        val v = midGame()
        TableScreen(
            v.copy(
                phase = Phase.RESCUE, stuckSeat = 1, rescuer = 0, actingSeat = 0,
                legalMoves = v.hand.map { Move.Give(it) } + Move.PlayFor(d(5, 4), Arm.NORTH),
                events = listOf(GameEvent.Stuck(1, 0)),
            ),
            onMove = {}, onNextGame = {}, onLeave = {},
        )
    }

    @Test
    fun teeraOffer() = shoot("teera-offer") {
        val v = midGame()
        TableScreen(
            v.copy(
                phase = Phase.TEERA_OFFER, teeraSeat = 0, actingSeat = 0,
                hand = listOf(d(0, 0), d(0, 1)), handCounts = listOf(2, 4, 5, 3),
                legalMoves = listOf(Move.Teera, Move.CarryOn), events = emptyList(),
            ),
            onMove = {}, onNextGame = {}, onLeave = {},
        )
    }

    @Test
    fun gameOver() = shoot("game-over", settleMillis = 2_600) {
        val v = midGame()
        TableScreen(
            v.copy(
                phase = Phase.GAME_OVER, actingSeat = null, legalMoves = emptyList(),
                lastGame = GameResult(4, GameEnd.X, 0, listOf(0, 9, 4, 12), listOf(-25, 21), 0),
                scores = listOf(12, 133),
                events = emptyList(),
            ),
            onMove = {}, onNextGame = {}, onLeave = {},
        )
    }

    @Test
    fun lobby() = shoot("lobby") {
        LobbyScreen(
            room = RoomInfo(
                code = "KHAN", hostId = "me", rules = Rules.DEFAULT,
                seats = listOf(seats[0], seats[1], Seat(2, "bot-2", "Bot 3", isBot = true), Seat(3, "", "")),
            ),
            myId = "me", isHost = true, offline = false,
            onTakeSeat = {}, onToggleBot = { _, _ -> }, onSetRules = {}, onStart = {}, onLeave = {},
        )
    }
}
