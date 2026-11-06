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
        Seat(0, "me", "علي", avatar = "p:0"), Seat(1, "p1", "سارة", avatar = "p:6"),
        Seat(2, "p2", "عمر", avatar = "p:2"), Seat(3, "p3", "نور", avatar = "p:3"),
    )

    /** Seat pictures as the app draws them. */
    private val pictures: @Composable (PlayerView, Int, androidx.compose.ui.unit.Dp) -> Unit = { view, seat, size ->
        val info = view.seatInfo(seat)
        com.teera.app.ui.Avatar(info?.avatar.orEmpty(), info?.name.orEmpty(), size)
    }

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
        MenuScreen(
            "علي", {}, {}, {}, {}, connecting = false,
            wifiTables = listOf(com.teera.app.net.WifiTable("سارة", "192.168.1.7", 8765, "KHAN")),
            hasInternetServer = true,
            lastRoom = "KHAN",
        )
    }

    /** A narrower phone with its text turned up, which is where the menu wrapped before. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi", fontScale = 1.3f)
    fun menuLargeText() = shoot("menu-large-text") {
        MenuScreen("علي", {}, {}, {}, {}, connecting = false, lastRoom = "KHAN")
    }

    /** A "ناخذ خاوة" being voted on, this player not having answered yet. */
    @Test
    fun alertVote() = shoot("alert-vote") {
        val v = midGame().copy(
            alertVote = com.teera.core.AlertVote(
                raisedBy = 2, againstTeam = 1, voters = listOf(0, 1, 2, 3), yes = listOf(2), no = listOf(1),
                deadlineEpochMs = System.currentTimeMillis() + 21_000,
            ),
        )
        TableScreen(v, onMove = {}, onNextGame = {}, onLeave = {}, onRaiseAlert = {}, avatarFor = { seat, size -> pictures(v, seat, size) })
    }

    /**
     * The four sides being closed, seen from seat 1: seat 1 laid 6|2 on its own side, so
     * from its chair that tile lies at the bottom, nearest the player.
     */
    @Test
    fun openingFromSeatOne() = shoot("opening-seat-1") {
        val board = Board().open(d(6, 6)).place(d(6, 3), Arm.SOUTH).place(d(6, 2), Arm.EAST)
        val v = midGame().copy(
            board = board, yourSeat = 1, actingSeat = 2, legalMoves = emptyList(),
            events = listOf(GameEvent.Played(1, d(6, 2), Arm.EAST)),
        )
        TableScreen(v, onMove = {}, onNextGame = {}, onLeave = {}, avatarFor = { seat, size -> pictures(v, seat, size) })
    }

    /** Watching over Omar's shoulder, with a line of chat coming in. */
    @Test
    fun watching() = shoot("watching") {
        val v = midGame().copy(
            yourSeat = 2, spectating = true, hand = listOf(d(5, 5), d(2, 3), d(0, 6), d(4, 1), d(3, 3)),
            legalMoves = emptyList(), showingHands = listOf(2), actingSeat = 3,
            spectators = listOf(Seat(4, "w1", "حسن", avatar = "p:7"), Seat(5, "w2", "سيف", avatar = "p:5")),
        )
        TableScreen(
            v, onMove = {}, onNextGame = {}, onLeave = {},
            talk = com.teera.app.ui.Talk(speaking = setOf(4)),
            chat = listOf(com.teera.app.ChatLine("حسن", "عمر لا تفتح الخمسة!", 4, 1)),
            onChat = {},
            avatarFor = { seat, size -> pictures(v, seat, size) },
        )
    }

    @Test
    fun table() = shoot("table") {
        val v = midGame()
        TableScreen(v, onMove = {}, onNextGame = {}, onLeave = {}, avatarFor = { seat, size -> pictures(v, seat, size) })
    }

    @Test
    fun profile() = shoot("profile") {
        com.teera.app.ui.ProfileScreen("علي", "p:1", null, {}, {}, {}, {})
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

    /** The moment a game ends, before the sums come up. */
    @Test
    fun celebration() = shoot("celebration", settleMillis = 700) {
        val v = midGame()
        TableScreen(
            v.copy(
                phase = Phase.GAME_OVER, actingSeat = null, legalMoves = emptyList(),
                lastGame = GameResult(4, GameEnd.TEERA, 0, listOf(1, 9, 4, 12), listOf(1, 21), 0),
                events = emptyList(),
            ),
            onMove = {}, onNextGame = {}, onLeave = {},
        )
    }

    /** A bot pushing a tile onto you, and saying so. */
    @Test
    fun emote() = shoot("emote") {
        val v = midGame()
        TableScreen(
            v.copy(
                actingSeat = 2, events = emptyList(),
                // Omar's line has dropped: the table waits for him, and says so.
                seats = v.seats.map { if (it.seat == 2) it.copy(connected = false) else it },
            ),
            onMove = {}, onNextGame = {}, onLeave = {},
            emotes = listOf(com.teera.app.ShownEmote(1, com.teera.core.Emote.GULP, 1)),
            // Sara is talking, and so is this player: mic open.
            talk = com.teera.app.ui.Talk(micOn = true, talking = true, speaking = setOf(1)),
        )
    }

    @Test
    fun rules() = shoot("rules") { com.teera.app.ui.RulesScreen(onClose = {}) }

    @Test
    fun lobby() = shoot("lobby") {
        LobbyScreen(
            room = RoomInfo(
                code = "KHAN", hostId = "me", rules = Rules.DEFAULT,
                seats = listOf(seats[0], seats[1], Seat(2, "bot-2", "Bot 3", isBot = true), Seat(3, "", "")),
            ),
            myId = "me", isHost = true, offline = false,
            onTakeSeat = {}, onToggleBot = { _, _ -> }, onSetRules = {}, onStart = {}, onLeave = {},
            onShare = {},
            talk = com.teera.app.ui.Talk(speaking = setOf(1)),
            avatarFor = { seat, size -> com.teera.app.ui.Avatar(seat?.avatar.orEmpty(), seat?.name.orEmpty(), size) },
        )
    }
}
