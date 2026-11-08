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
import androidx.compose.foundation.layout.padding
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

    /** The games being played that anyone may watch. */
    @Test
    fun liveGames() = shoot("live-games") {
        com.teera.app.ui.LiveGamesScreen(
            listOf(
                com.teera.core.LiveGame("KHAN", listOf("علي", "سارة", "عمر", "نور"), listOf("p:0", "p:6", "p:2", "p:3"), listOf(88, 140), 201, 7, 2),
                com.teera.core.LiveGame("BGDD", listOf("حسن", "سيف", "عبود", "بوت"), listOf("p:7", "p:5", "", ""), listOf(12, -25), 251, 2, 0),
            ),
            onWatch = {}, onClose = {},
        )
    }

    /** A short phone, text up a little, a room to go back to: still one screen, nothing scrolls. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h680dp-xxhdpi", fontScale = 1.15f)
    fun menuShortPhone() = shoot("menu-short-phone") {
        MenuScreen("علي", {}, {}, {}, {}, connecting = false, lastRoom = "QCNU", liveCount = 2)
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

    /** A newer app is out. */
    @Test
    fun update() = shoot("update") {
        com.teera.app.ui.UpdateDialog(
            com.teera.core.ServerMessage.UpdateAvailable(20, "1.4.2", "https://example.com/teera.apk"),
            onDownload = {}, onLater = {},
        )
    }

    /** Somebody asked to play to 251: everyone has to agree. */
    @Test
    fun targetVote() = shoot("target-vote") {
        val v = midGame().copy(
            actingSeat = 1, legalMoves = emptyList(),
            targetVote = com.teera.core.TargetVote(2, 251, listOf(0, 1, 2, 3), listOf(2, 1), emptyList(), System.currentTimeMillis() + 20_000),
        )
        TableScreen(v, onMove = {}, onNextGame = {}, onLeave = {}, avatarFor = { seat, size -> pictures(v, seat, size) })
    }

    /** Two players showing their bones, one of them dropped: the watcher sees both and can sit in. */
    @Test
    fun watchingAll() = shoot("watching-all") {
        val hand0 = listOf(d(5, 4), d(4, 3), d(3, 3), d(2, 0), d(1, 5), d(0, 0))
        val hand1 = listOf(d(6, 5), d(2, 2), d(1, 3), d(0, 5))
        val v = midGame().copy(
            yourSeat = 2, spectating = true, hand = emptyList(),
            legalMoves = emptyList(), showingHands = listOf(0, 1), actingSeat = 0,
            seats = midGame().seats.map { if (it.seat == 0) it.copy(connected = false) else it },
            revealedHands = listOf(hand0, hand1, emptyList(), emptyList()),
            spectators = listOf(Seat(4, "w1", "حسن", avatar = "p:7")),
        )
        TableScreen(
            v, onMove = {}, onNextGame = {}, onLeave = {},
            chat = emptyList(), onChat = {},
            avatarFor = { seat, size -> pictures(v, seat, size) },
        )
    }

    @Test
    fun table() = shoot("table") {
        val v = midGame()
        TableScreen(v, onMove = {}, onNextGame = {}, onLeave = {}, avatarFor = { seat, size -> pictures(v, seat, size) })
    }

    /** The profile's record, as the server's books tell it. */
    @Test
    fun record() = shoot("record") {
        val day = 86_400_000L
        val now = 1_762_500_000_000L
        com.teera.app.ui.WalnutRoom {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.padding(androidx.compose.ui.unit.Dp(24f)),
            ) {
                com.teera.app.ui.RecordCard(
                    com.teera.core.PlayerRecord(
                        matches = 12, wins = 8, losses = 4, games = 97, finishes = 31, teeras = 6,
                        firstSeenEpochMs = now - 3 * day,
                        recent = listOf(
                            com.teera.core.MatchSummary(now - 3_600_000, "KHAN", null, "عمر", listOf("سارة", "نور"), 88, 140, 7),
                            com.teera.core.MatchSummary(now - 5 * 3_600_000, "KHAN", true, "عمر", listOf("سارة", "نور"), 96, 205, 11),
                            com.teera.core.MatchSummary(now - day, "BGDD", false, "حسن", listOf("سيف", "بوت"), 212, 150, 13),
                            com.teera.core.MatchSummary(now - 3 * day, "TEST", true, "بوت", listOf("بوت", "بوت"), -40, 201, 9),
                        ),
                    ),
                    loading = false, kept = true, now = now,
                )
            }
        }
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

    /** A finished game told move by move, from the button on the sums. */
    @Test
    fun steps() = shoot("steps") {
        val v = midGame().copy(phase = Phase.GAME_OVER, actingSeat = null, legalMoves = emptyList())
        com.teera.app.ui.StepsDialog(v, sampleSteps(), onClose = {})
    }

    /** The updates page, from the menu's version line. */
    @Test
    fun changelog() = shoot("changelog") { com.teera.app.ui.ChangelogDialog(onClose = {}) }

    private fun sampleSteps() = listOf(
        GameEvent.Dealt(4, 0, null),
        GameEvent.Opened(0, d(6, 6)),
        GameEvent.Played(1, d(6, 3), Arm.NORTH),
        GameEvent.Played(2, d(6, 2), Arm.EAST),
        GameEvent.Stuck(3, 2),
        GameEvent.Gave(2, 3),
        GameEvent.Played(0, d(6, 1), Arm.SOUTH),
        GameEvent.PlayedFor(0, 1, d(6, 0), Arm.WEST),
        GameEvent.Played(2, d(3, 5), Arm.NORTH),
        GameEvent.TeeraCalled(3, 1),
        GameEvent.GameEnded(GameResult(4, GameEnd.X, 0, listOf(0, 9, 4, 12), listOf(-25, 21), 0)),
    )

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

    /** Ali's phone: an empty chair on the other team must still offer "حط بوت", and his own chair show. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi", fontScale = 1.15f)
    fun lobbyLargeText() = shoot("lobby-large-text") {
        LobbyScreen(
            room = RoomInfo(
                code = "RV4K", hostId = "me", rules = Rules.DEFAULT,
                seats = listOf(seats[0], Seat(1, "bot-1", "Bot", isBot = true), Seat(2, "bot-2", "Bot", isBot = true), Seat(3, "", "")),
            ),
            myId = "me", isHost = true, offline = false,
            onTakeSeat = {}, onToggleBot = { _, _ -> }, onSetRules = {}, onStart = {}, onLeave = {},
            onShare = {}, talk = com.teera.app.ui.Talk(),
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
            onShare = {},
            talk = com.teera.app.ui.Talk(speaking = setOf(1)),
            avatarFor = { seat, size -> com.teera.app.ui.Avatar(seat?.avatar.orEmpty(), seat?.name.orEmpty(), size) },
        )
    }
}
