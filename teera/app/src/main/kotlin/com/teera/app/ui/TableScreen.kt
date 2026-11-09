package com.teera.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.VoiceOverOff
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.teera.app.ShownEmote
import com.teera.app.ui.theme.Felt
import com.teera.core.Arm
import com.teera.core.Domino
import com.teera.core.Emote
import com.teera.core.GameEnd
import com.teera.core.GameEvent
import com.teera.core.Move
import com.teera.core.Phase
import com.teera.core.PlayerView
import com.teera.core.teamOf
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** What the rescuer has chosen to do for the seat stuck after them. */
internal enum class RescueMode { GIVE, PLAY_FOR }

/** The two switches a player reaches for mid-game. */
data class SoundSettings(
    val effectsOn: Boolean = true,
    val musicOn: Boolean = true,
    val onEffects: (Boolean) -> Unit = {},
    val onMusic: (Boolean) -> Unit = {},
)

/**
 * The table.
 *
 * Everything drawn here comes from the [PlayerView] the referee sent, the list of legal
 * moves included, so the screen can never offer a move the server would refuse.
 */
@Composable
fun TableScreen(
    view: PlayerView,
    onMove: (Move) -> Unit,
    onNextGame: () -> Unit,
    onLeave: () -> Unit,
    emotes: List<ShownEmote> = emptyList(),
    onEmote: (Emote) -> Unit = {},
    sound: SoundSettings = SoundSettings(),
    /** Table talk; null at a table of bots, where there is nobody to talk to. */
    talk: Talk? = null,
    /** Table chat, oldest first; null where there is nobody to write to (bots). */
    chat: List<com.teera.app.ChatLine> = emptyList(),
    onChat: ((String) -> Unit)? = null,
    /** A player showing their tiles to the watchers; a watcher choosing whose. */
    onShowHand: (Boolean) -> Unit = {},
    onWatch: (Int) -> Unit = {},
    /** A watcher playing a dropped player's chair; null hands it back. */
    onStandIn: (Int?) -> Unit = {},
    /** Asking the table to play to another score, and answering somebody who asked. */
    onProposeTarget: ((Int) -> Unit)? = null,
    onVoteTarget: (Boolean) -> Unit = {},
    /** Lets anyone watch this table from the list of games being played; null with bots. */
    onListed: ((Boolean) -> Unit)? = null,
    /** Who this player is, and whether they run the room: the host sends people out and locks it. */
    myId: String = "",
    isHost: Boolean = false,
    locked: Boolean = false,
    onKick: ((String) -> Unit)? = null,
    onSwap: ((seated: String, watcher: String) -> Unit)? = null,
    onLock: ((Boolean) -> Unit)? = null,
    /** Blocking, kept on this phone: who is blocked, and blocking or unblocking somebody. */
    blocked: Set<String> = emptySet(),
    onBlock: ((id: String, name: String, block: Boolean) -> Unit)? = null,
    /** "ناخذ خاوة" on the other team; null where there is nobody to vote (bots). */
    onRaiseAlert: (() -> Unit)? = null,
    onVoteAlert: (Boolean) -> Unit = {},
    /** Draws a seat's picture; by default the first letter of their name. */
    avatarFor: @Composable (seat: Int, size: androidx.compose.ui.unit.Dp) -> Unit =
        { seat, size -> Initial(view.seatInfo(seat)?.name.orEmpty(), size) },
    /** What happened this game, move by move; only shown once the game is over. */
    steps: List<GameEvent> = emptyList(),
) {
    // These two state holders live for the whole table and are never swapped out. Keying
    // them on the board, as this screen once did, handed a fresh holder to each new frame
    // while a click handler remembered from an earlier one kept writing to the old one —
    // so a tap on a lit bone went nowhere. They are cleared in place instead.
    val selectedState = remember { mutableStateOf<Domino?>(null) }
    val rescueState = remember { mutableStateOf<RescueMode?>(null) }
    LaunchedEffect(view.gameNumber, view.board.size) { selectedState.value = null }
    LaunchedEffect(view.stuckSeat) { rescueState.value = null }

    // Handlers below read the table through these, so however old the handler, it acts
    // on the table as it stands now.
    val currentView by rememberUpdatedState(view)
    val send by rememberUpdatedState(onMove)

    val tapTile: (Domino) -> Unit = remember {
        { tile ->
            when (val tap = decideTap(currentView, tile, selectedState.value, rescueState.value)) {
                is Tap.Send -> {
                    send(tap.move)
                    selectedState.value = null
                    rescueState.value = null
                }
                is Tap.Select -> selectedState.value = tap.tile
                Tap.Ignore -> Unit
            }
        }
    }
    val tapArm: (Arm) -> Unit = remember {
        { arm ->
            val chosen = selectedState.value
            if (chosen != null) {
                send(placeOn(currentView, chosen, arm))
                selectedState.value = null
                rescueState.value = null
            }
        }
    }

    val selected = selectedState.value
    val rescueMode = rescueState.value
    var confirmLeave by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var askAlert by remember { mutableStateOf(false) }
    var askTarget by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<PeopleAction?>(null) }

    // The game ends with a moment, and only then the sums.
    val over = view.phase == Phase.GAME_OVER || view.phase == Phase.MATCH_OVER
    var showSummary by remember { mutableStateOf(false) }
    LaunchedEffect(over, view.gameNumber) {
        showSummary = false
        if (over) {
            delay(CELEBRATION_MS)
            showSummary = true
        }
    }

    // The phone's back gesture is easy to make by accident: ask, as the menu's button does.
    androidx.activity.compose.BackHandler(enabled = !over && !view.spectating) { confirmLeave = true }

    // A stand-in plays somebody else's chair; they can hand it back.
    val standingIn = !view.spectating && view.seatInfo(view.yourSeat)?.standIn != null

    Column(Modifier.fillMaxSize().background(Felt.Deep)) {

        // Watchers choose whose shoulder to look over, or take a dropped player's chair;
        // players choose whether to be looked at.
        val options = if (view.spectating) {
            (0 until 4).map { seat ->
                val showing = if (seat in view.showingHands) " (بولاته تنشاف)" else ""
                MenuOption("تفرّج من ورا ${view.seatName(seat)}$showing", lit = seat == view.yourSeat) { onWatch(seat) }
            } + view.openChairs().map { seat ->
                MenuOption("العب بدال ${view.seatName(seat)}") { onStandIn(seat) }
            }
        } else if (standingIn) {
            listOf(MenuOption("رجّع الكرسي وارجع تفرّج") { onStandIn(null) })
        } else if (onChat != null) {
            val showing = view.yourSeat in view.showingHands
            listOf(
                MenuOption(if (showing) "المتفرجين يشوفون بولاتي" else "بولاتي مخفية عن المتفرجين", lit = showing) { onShowHand(!showing) },
            )
        } else {
            emptyList()
        } + listOfNotNull(
            onLock?.takeIf { isHost && onChat != null }?.let { lock ->
                MenuOption(if (locked) "الغرفة مقفولة لجماعتك" else "اقفل الغرفة على جماعتك", lit = locked) { lock(!locked) }
            },
            onKick?.takeIf { isHost && onChat != null && view.others(myId).isNotEmpty() }?.let {
                MenuOption("طلّع واحد…") { picking = PeopleAction.KICK }
            },
            onSwap?.takeIf { isHost && onChat != null && view.spectators.isNotEmpty() && !over }?.let {
                MenuOption("بدّل لاعب ويا متفرج…") { picking = PeopleAction.SWAP }
            },
            onBlock?.takeIf { onChat != null && view.others(myId).isNotEmpty() }?.let {
                MenuOption("احظر واحد…") { picking = PeopleAction.BLOCK }
            },
            onListed?.takeIf { !view.spectating && onChat != null }?.let { set ->
                MenuOption(if (view.listed) "لعبتنا تبين للكل" else "خلي لعبتنا تبين للكل", lit = view.listed) { set(!view.listed) }
            },
            onProposeTarget?.takeIf { !view.spectating && !over && view.targetVote == null && view.targetChoices().isNotEmpty() }
                ?.let { MenuOption("غيّر النقاط (هسه ${view.rules.target})") { askTarget = true } },
        )
        ScoreRail(
            view, sound, onLeave = { if (over || view.spectating || standingIn) onLeave() else confirmLeave = true },
            talk = talk, onRules = { showRules = true }, options = options,
        )
        // The other three where they sit: partner across the table, the two of the other
        // team either side. In right-to-left order the first in a row is on the right,
        // and play passes to the right, so the next player is there.
        val speaking = talk?.speaking.orEmpty()
        fun seatAt(offset: Int) = (view.yourSeat + offset) % 4
        @Composable
        fun Seated(offset: Int, side: Side, modifier: Modifier = Modifier) {
            val seat = seatAt(offset)
            Box(modifier, contentAlignment = Alignment.Center) {
                SeatAtTable(view, seat, side, speaking = seat in speaking, avatar = { size -> avatarFor(seat, size) })
            }
        }
        Seated(2, Side.TOP, Modifier.fillMaxWidth())
        if (view.spectators.isNotEmpty()) Watchers(view, speaking)

        Row(Modifier.weight(1f).fillMaxWidth()) {
        Seated(1, Side.RIGHT, Modifier.fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()) {
            BoardView(
                board = view.board,
                modifier = Modifier.fillMaxSize(),
                liveArms = selected?.let { armsFor(view, it).toSet() } ?: emptySet(),
                onArmTapped = tapArm,
                arrival = arrivalOf(view),
                // Seen from this player's chair: their own side of the double faces them.
                quarterTurns = view.yourSeat,
            )

            GivenTile(view)

            Prompt(
                view = view,
                rescueMode = rescueMode,
                onRescueMode = { rescueState.value = it },
                onMove = onMove,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )

            Celebration(view, visible = over && !showSummary, modifier = Modifier.fillMaxSize())

            // Sounds and faces show over the edge of the table nearest whoever sent them.
            // The table keeps its size, so a long line never nudges it aside.
            listOf(
                seatAt(1) to Alignment.CenterStart,
                seatAt(2) to Alignment.TopCenter,
                seatAt(3) to Alignment.CenterEnd,
                view.yourSeat to Alignment.BottomCenter,
            ).forEach { (seat, edge) ->
                emotes.lastOrNull { it.seat == seat }?.let {
                    EmoteBubble(it, Modifier.align(edge).padding(horizontal = 6.dp, vertical = 8.dp))
                }
            }

            if (onChat != null) ChatToast(chat, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))

            view.alertVote?.let { vote ->
                AlertVotePanel(view, vote, onVoteAlert, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            }
            view.targetVote?.takeIf { view.alertVote == null }?.let { vote ->
                TargetVotePanel(view, vote, onVoteTarget, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            }
        }
        Seated(3, Side.LEFT, Modifier.fillMaxHeight())
        }

        Hand(
            view = view,
            selected = selected,
            playable = handHighlights(view, rescueMode),
            onTap = { tile -> if (!view.spectating) tapTile(tile) },
            onEmote = onEmote,
            talk = talk,
            chat = chat,
            onChat = onChat,
            onRaiseAlert = onRaiseAlert?.takeIf { view.alertVote == null && !over }?.let { raise -> { askAlert = true } },
            onStandIn = onStandIn.takeIf { view.spectating && onChat != null && !over },
            avatar = { size -> avatarFor(view.yourSeat, size) },
        )

    if (picking == PeopleAction.SWAP) {
        SwapPicker(view.seats, view.spectators, onSwap = { a, b -> onSwap?.invoke(a, b) }, onClose = { picking = null })
    }

    picking?.takeIf { it != PeopleAction.SWAP }?.let { action ->
        PeopleDialog(
            title = if (action == PeopleAction.KICK) "طلّع منو؟" else "احظر منو؟",
            note = if (action == PeopleAction.KICK) "يطلع من الغرفة وما يرجع إلا بعزيمة. إذا جان يلعب، يكمل بوت بكرسيه."
            else "ما توصلك عزايمه، وما تسمع صوته ولا تشوف كتابته. الحظر بتليفونك بس.",
            people = view.others(myId),
            label = { id -> if (action == PeopleAction.KICK) "طلّع" else if (id in blocked) "شيل الحظر" else "احظر" },
            onPick = { id, name ->
                if (action == PeopleAction.KICK) onKick?.invoke(id) else onBlock?.invoke(id, name, id !in blocked)
                picking = null
            },
            onClose = { picking = null },
        )
    }

    if (askTarget) {
        Dialog(onDismissRequest = { askTarget = false }) {
            Plaque {
                Text("غيّر النقاط", color = Felt.Brass, style = MaterialTheme.typography.displaySmall)
                Text(
                    "هسه اللعبة لـ ${view.rules.target}. تتغير إذا وافق كل اللي بالطاولة.",
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    view.targetChoices().forEach { target ->
                        Button(
                            onClick = { askTarget = false; onProposeTarget?.invoke(target) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (target > view.rules.target) Felt.Brass else Felt.Rail,
                                contentColor = if (target > view.rules.target) Felt.Ink else Felt.Parchment,
                            ),
                            modifier = Modifier.testTag("target-$target"),
                        ) { Text("$target") }
                    }
                }
                OutlinedButton(onClick = { askTarget = false }) { Text("رجوع", color = Felt.Parchment) }
            }
        }
    }

    if (askAlert) {
        Dialog(onDismissRequest = { askAlert = false }) {
            Plaque {
                Text("ناخذ خاوة؟", color = Felt.Brass, style = MaterialTheme.typography.displaySmall)
                Text(
                    "على ${view.teamName(1 - view.yourTeam)}. الطاولة تصوّت، وتنقبل إذا وافق أكثر من النص.",
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                GoldButton("إي، ناخذها", { askAlert = false; onRaiseAlert?.invoke() })
                TextButton(onClick = { askAlert = false }) { Text("لا، خليها", color = Felt.Muted) }
            }
        }
    }
    }

    if (over && showSummary) GameSummary(view, steps, onNextGame, onLeave)

    // The rules, over the table, without leaving it.
    if (showRules) {
        Dialog(
            onDismissRequest = { showRules = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            RulesScreen(onClose = { showRules = false })
        }
    }

    if (confirmLeave) {
        Dialog(onDismissRequest = { confirmLeave = false }) {
            Plaque {
                Text("تطلع من الطاولة؟", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (onChat != null) {
                        "مكانك يبقى محفوظ: يلعب بوت بدالك لحد ما ترجع من «ارجع للطاولة» بالقائمة"
                    } else {
                        "الگيم ويا البوتات راح ينتهي"
                    },
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { confirmLeave = false; onLeave() },
                        colors = ButtonDefaults.buttonColors(containerColor = Felt.Danger),
                    ) { Text("أطلع") }
                    OutlinedButton(onClick = { confirmLeave = false }) { Text("أبقى", color = Felt.Parchment) }
                }
            }
        }
    }
}

private const val CELEBRATION_MS = 1_900L

// -- deciding taps -----------------------------------------------------------

/** What pressing a bone in hand comes to. */
internal sealed interface Tap {
    /** The tap settles it: send this move. */
    data class Send(val move: Move) : Tap

    /** The bone could go more than one way, so hold it up and let the table be tapped. */
    data class Select(val tile: Domino?) : Tap

    data object Ignore : Tap
}

/**
 * Decides what a tap on [tile] means, from nothing but the table as it stands.
 *
 * Kept free of Compose so it can be checked in a plain test, and so the click handler
 * that calls it carries no state of its own that could go stale.
 */
internal fun decideTap(view: PlayerView, tile: Domino, selected: Domino?, rescueMode: RescueMode?): Tap {
    if (!view.isYourMove) return Tap.Ignore

    if (view.phase == Phase.RESCUE) {
        return when (rescueMode) {
            RescueMode.GIVE ->
                if (view.legalMoves.any { it is Move.Give && it.domino == tile }) Tap.Send(Move.Give(tile))
                else Tap.Ignore
            RescueMode.PLAY_FOR -> {
                val arms = armsFor(view, tile)
                when (arms.size) {
                    0 -> Tap.Ignore
                    1 -> Tap.Send(Move.PlayFor(tile, arms.first()))
                    else -> Tap.Select(if (selected == tile) null else tile)
                }
            }
            null -> Tap.Ignore
        }
    }

    val plays = view.legalMoves.filterIsInstance<Move.Play>().filter { it.domino == tile }
    if (plays.isEmpty()) return Tap.Ignore

    // While the opening double's sides are being closed the engine picks the side, and
    // the moves carry no arm; a single home also needs no question asking.
    val arms = plays.mapNotNull { it.arm }.distinct()
    return when {
        arms.size <= 1 -> Tap.Send(plays.first())
        else -> Tap.Select(if (selected == tile) null else tile)
    }
}

/** Where a held bone may go: the arms the referee will accept it on. */
internal fun armsFor(view: PlayerView, tile: Domino): List<Arm> = when (view.phase) {
    Phase.RESCUE -> view.legalMoves.filterIsInstance<Move.PlayFor>().filter { it.domino == tile }.map { it.arm }
    else -> view.legalMoves.filterIsInstance<Move.Play>().filter { it.domino == tile }.mapNotNull { it.arm }
}

/** The move that lays a held bone on [arm]. */
internal fun placeOn(view: PlayerView, tile: Domino, arm: Arm): Move =
    if (view.phase == Phase.RESCUE) Move.PlayFor(tile, arm) else Move.Play(tile, arm)

/** Which tiles in hand are worth lighting up right now. */
private fun handHighlights(view: PlayerView, rescueMode: RescueMode?): Set<Domino> {
    if (!view.isYourMove) return emptySet()
    val moves = view.legalMoves
    return when {
        view.phase == Phase.RESCUE && rescueMode == RescueMode.GIVE ->
            moves.filterIsInstance<Move.Give>().map { it.domino }.toSet()
        view.phase == Phase.RESCUE && rescueMode == RescueMode.PLAY_FOR ->
            moves.filterIsInstance<Move.PlayFor>().map { it.domino }.toSet()
        view.phase == Phase.RESCUE -> emptySet()
        else -> moves.filterIsInstance<Move.Play>().map { it.domino }.toSet()
    }
}

/** The tile the last move put down, and the seat it came from, so it can fly in. */
private fun arrivalOf(view: PlayerView): Arrival? =
    view.events.firstNotNullOfOrNull {
        when (it) {
            is GameEvent.Opened -> Arrival(it.domino, spotOf(view, it.seat))
            is GameEvent.Played -> Arrival(it.domino, spotOf(view, it.seat))
            is GameEvent.PlayedFor -> Arrival(it.domino, spotOf(view, it.rescuer))
            else -> null
        }
    }

internal fun spotOf(view: PlayerView, seat: Int): SeatSpot =
    when ((seat - view.yourSeat + 4) % 4) {
        0 -> SeatSpot.BOTTOM
        1 -> SeatSpot.SIDE_START
        2 -> SeatSpot.TOP_MIDDLE
        else -> SeatSpot.SIDE_END
    }

// -- the rail ----------------------------------------------------------------

@Composable
private fun ScoreRail(
    view: PlayerView,
    sound: SoundSettings,
    onLeave: () -> Unit,
    talk: Talk? = null,
    onRules: () -> Unit = {},
    options: List<MenuOption> = emptyList(),
) {
    val us = view.scores.getOrElse(view.yourTeam) { 0 }
    val them = view.scores.getOrElse(1 - view.yourTeam) { 0 }

    Box {
        WalnutSurface(Modifier.matchParentSize())
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Three plates of one size in a row: us, them, and which game of how many points.
            // Points are a burden here, so whoever has fewer is the one in front.
            ScorePlate(view.teamName(view.yourTeam), "$us", if (us <= them) Felt.Playable else Felt.Behind, animated = us)
            Spacer(Modifier.width(8.dp))
            ScorePlate(view.teamName(1 - view.yourTeam), "$them", if (them < us) Felt.Playable else Felt.Behind, animated = them)
            Spacer(Modifier.width(8.dp))
            if (view.rules.byGames) {
                // Which game of how many: a locked game is played again, so it does not count.
                ScorePlate("گيم ${minOf(view.gamesPlayed + 1, view.rules.games)}", "من ${view.rules.games}", Felt.Brass)
            } else {
                ScorePlate("گيم ${view.gameNumber}", "لـ ${view.rules.target}", Felt.Brass)
            }

            Spacer(Modifier.weight(1f))

            // Everything else a player reaches for mid-game, behind one button.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("table-menu")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "القائمة", tint = Felt.Brass)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(Felt.Rail),
                ) {
                    @Composable
                    fun Entry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, lit: Boolean = true, action: () -> Unit) =
                        DropdownMenuItem(
                            text = { Text(label, color = if (lit) Felt.Parchment else Felt.Muted, style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { Icon(icon, contentDescription = null, tint = if (lit) Felt.Brass else Felt.Muted) },
                            onClick = action,
                        )
                    Entry(
                        if (sound.musicOn) "الموسيقى شغّالة" else "الموسيقى طافية",
                        if (sound.musicOn) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff,
                        sound.musicOn,
                    ) { sound.onMusic(!sound.musicOn) }
                    Entry(
                        if (sound.effectsOn) "أصوات اللعب شغّالة" else "أصوات اللعب طافية",
                        if (sound.effectsOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        sound.effectsOn,
                    ) { sound.onEffects(!sound.effectsOn) }
                    if (talk != null) {
                        Entry(
                            if (talk.hearing) "أسمع جماعتي" else "جماعتي مكتومين",
                            if (talk.hearing) Icons.Rounded.RecordVoiceOver else Icons.Rounded.VoiceOverOff,
                            talk.hearing,
                        ) { talk.onHearing() }
                    }
                    options.forEach { option ->
                        Entry(option.label, Icons.Rounded.Visibility, option.lit) { menuOpen = false; option.action() }
                    }
                    Entry("القوانين", Icons.AutoMirrored.Rounded.MenuBook) { menuOpen = false; onRules() }
                    HorizontalDivider(color = Felt.Brass.copy(alpha = 0.4f))
                    Entry("اطلع من الطاولة", Icons.AutoMirrored.Rounded.ExitToApp) { menuOpen = false; onLeave() }
                }
            }
        }
        if (view.alerts.any { it > 0 }) {
            Text(
                "خاوة ${view.alerts.getOrElse(view.yourTeam) { 0 }} · ${view.alerts.getOrElse(1 - view.yourTeam) { 0 }}",
                color = Felt.Danger,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            )
        }
        HorizontalDivider(
            color = Felt.Brass.copy(alpha = 0.85f),
            thickness = 1.dp,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/**
 * An engraved plaque: a small label over a big value. A score rolls to its new number
 * rather than jumping to it.
 */
@Composable
private fun ScorePlate(label: String, value: String, colour: Color, animated: Int? = null) {
    val rolled = animated?.let { target -> animateIntAsState(target, animationSpec = tween(900), label = "score").value }
    Column(
        modifier = Modifier
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF120B05).copy(alpha = 0.55f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = Felt.Muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 92.dp),
        )
        Text(
            // A score can go below zero; isolated so the minus stays in front of it.
            "\u2066" + (rolled?.toString() ?: value) + "\u2069",
            color = colour,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
    }
}

// -- the seats ----------------------------------------------------------------

private fun Emote.words(): String = when (this) {
    Emote.GULP -> "ابلع!"
    Emote.DRUMROLL -> "طبول…"
    Emote.SAD -> "راحت عليك"
    Emote.SLAP -> "خذ!"
    Emote.NO_WAY -> "بالله ما طيغ!"
    Emote.CHAT -> "يلا شباب خلي نسولف"
    Emote.SHEEL -> "شيل!"
    Emote.SPLOOSH -> "سبلوش!"
    Emote.WALLAH -> ""
    Emote.GHASHANA -> "غشعنا على وجه الخير"
    Emote.KHATLA -> "خاتلة!"
    else -> ""
}

/** A soundboard press, popping up over whoever pressed it. */
@Composable
private fun EmoteBubble(shown: ShownEmote, modifier: Modifier = Modifier) {
    if (shown.emote.isFace) {
        FaceBurst(shown, modifier)
        return
    }
    key(shown.id) {
        val pop = remember { Animatable(0.4f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
        Box(
            modifier
                .scale(pop.value)
                .clip(RoundedCornerShape(14.dp))
                .background(Felt.Parchment)
                .border(1.5.dp, Felt.Brass, RoundedCornerShape(14.dp))
                .widthIn(max = 200.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                shown.emote.words(),
                color = Felt.Ink,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

// -- moments -------------------------------------------------------------------

/**
 * A tile handed to a stuck player crosses the table face down, from the hand that gave
 * it to the hand that has to take it.
 */
@Composable
private fun GivenTile(view: PlayerView) {
    val gave = view.events.filterIsInstance<GameEvent.Gave>().lastOrNull() ?: return
    val from = spotOf(view, gave.from)
    val to = spotOf(view, gave.to)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val travel = remember(view) { Animatable(0f) }
    LaunchedEffect(view) { travel.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
    if (travel.value >= 1f) return

    Canvas(Modifier.fillMaxSize()) {
        val start = from.anchor(size.width, size.height, rtl)
        val end = to.anchor(size.width, size.height, rtl)
        val t = travel.value
        val arc = -size.height * 0.18f * sin(Math.PI * t).toFloat()
        val at = Offset(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t + arc)
        val w = size.minDimension * 0.07f
        rotate(t * 200f, at) {
            translate(at.x - w / 2f, at.y - w) {
                drawDomino(null, null, w, w * 2f, vertical = true, faceUp = false)
            }
        }
    }
}

/**
 * The moment a game ends: the word for it, large, with gold thrown in the air. Kept short,
 * then the sums take over.
 */
@Composable
private fun Celebration(view: PlayerView, visible: Boolean, modifier: Modifier = Modifier) {
    val result = view.lastGame
    val matchOver = view.phase == Phase.MATCH_OVER
    val weLost = view.losingTeam == view.yourTeam
    val ourGame = result?.winnerSeat?.let { teamOf(it) == view.yourTeam } ?: false

    val word = when {
        matchOver -> if (weLost) "خسرنا" else "فزنا"
        result?.end == GameEnd.TEERA -> "طيرة!"
        result?.end == GameEnd.X -> "اكس!"
        result?.end == GameEnd.BLOCKED -> "قفلت"
        else -> ""
    }
    val festive = (matchOver && !weLost) || (!matchOver && ourGame && result?.end != GameEnd.BLOCKED)

    AnimatedVisibility(
        visible = visible && word.isNotEmpty(),
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.4f),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            if (festive || result?.end == GameEnd.TEERA) GoldBurst(seed = view.gameNumber)
            Text(
                word,
                color = if (festive) Felt.Brass else Felt.Parchment,
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displayLarge,
            )
        }
    }
}

/** Gold leaf thrown up from the middle of the table and falling back. */
@Composable
private fun GoldBurst(seed: Int) {
    val progress = remember(seed) { Animatable(0f) }
    LaunchedEffect(seed) { progress.animateTo(1f, tween(1700, easing = LinearEasing)) }
    val flakes = remember(seed) {
        val rng = Random(seed * 7919 + 11)
        List(46) {
            Triple(rng.nextFloat() * 360f, 0.35f + rng.nextFloat() * 0.65f, rng.nextFloat())
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        val origin = Offset(size.width / 2f, size.height * 0.48f)
        val reach = size.minDimension * 0.62f
        flakes.forEach { (angle, speed, shade) ->
            val radians = Math.toRadians(angle.toDouble())
            val distance = reach * speed * (1f - (1f - t) * (1f - t))
            val fall = size.height * 0.28f * t * t
            val at = Offset(
                origin.x + (cos(radians) * distance).toFloat(),
                origin.y + (sin(radians) * distance).toFloat() * 0.75f + fall,
            )
            val colour = if (shade > 0.5f) Felt.GoldLit else Felt.Brass
            val s = 3.5f + shade * 5f
            rotate(angle + t * 540f * (shade - 0.5f), at) {
                drawRect(colour.copy(alpha = (1f - t).coerceIn(0f, 1f)), Offset(at.x - s, at.y - s / 2f),
                    androidx.compose.ui.geometry.Size(s * 2f, s))
            }
        }
    }
}

// -- what the table is waiting for ---------------------------------------------

/** The one thing the table is waiting for, said plainly. */
@Composable
private fun Prompt(
    view: PlayerView,
    rescueMode: RescueMode?,
    onRescueMode: (RescueMode?) -> Unit,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val waitingOn = view.actingSeat?.let { view.seatInfo(it)?.name } ?: ""

    Box(modifier) {
        when {
            view.phase == Phase.TEERA_OFFER && view.isYourMove -> Panel {
                Text(
                    if (view.handPips == 0) "تقدر تطير بصفر" else "تقدر تطير بواحد",
                    color = Felt.Brass,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "الطيرة تنهي الگيم بدون اكس" + if (view.handPips == 1) "، والواحد ينحسب عليكم" else "",
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onMove(Move.Teera) },
                        colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                    ) { Text("طيّر", style = MaterialTheme.typography.labelLarge) }
                    OutlinedButton(onClick = { onMove(Move.CarryOn) }) {
                        Text("كمّل للاكس", color = Felt.Parchment)
                    }
                }
            }

            view.phase == Phase.REDEAL_OFFER && view.isYourMove -> Panel {
                Text("بيدك أربع چفتات", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
                Text("تقدر تعيد الخلط، أو تكمّل بيدك", color = Felt.Parchment, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onMove(Move.Redeal) },
                        colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                    ) { Text("أعد الخلط") }
                    OutlinedButton(onClick = { onMove(Move.KeepHand) }) { Text("كمّل", color = Felt.Parchment) }
                }
            }

            view.phase == Phase.RESCUE && view.isYourMove -> Panel {
                val stuckName = view.stuckSeat?.let { view.seatInfo(it)?.name }.orEmpty()
                Text("$stuckName گلى", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
                when (rescueMode) {
                    null -> {
                        Text("تلعب تنطي؟", color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onRescueMode(RescueMode.PLAY_FOR) },
                                enabled = view.legalMoves.any { it is Move.PlayFor },
                                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                            ) { Text("العب بداله", maxLines = 1, softWrap = false) }
                            OutlinedButton(onClick = { onRescueMode(RescueMode.GIVE) }) {
                                Text("انطيه بول", color = Felt.Parchment, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    RescueMode.GIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اختر البول اللي تنطيه", color = Felt.Parchment)
                        TextButton(onClick = { onRescueMode(null) }) { Text("رجوع", color = Felt.Brass) }
                    }
                    RescueMode.PLAY_FOR -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اختر البول اللي تلعبه", color = Felt.Parchment)
                        TextButton(onClick = { onRescueMode(null) }) { Text("رجوع", color = Felt.Brass) }
                    }
                }
            }

            view.phase == Phase.RESCUE && view.stuckSeat == view.yourSeat -> Panel {
                Text("گليت", color = Felt.Danger, style = MaterialTheme.typography.titleLarge)
                Text(
                    "$waitingOn إما ينطيك بول أو يلعب بدالك",
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Felt.Rail.copy(alpha = 0.96f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

// -- your hand ------------------------------------------------------------------

@Composable
private fun Hand(
    view: PlayerView,
    selected: Domino?,
    playable: Set<Domino>,
    onTap: (Domino) -> Unit,
    onEmote: (Emote) -> Unit,
    talk: Talk? = null,
    onRaiseAlert: (() -> Unit)? = null,
    chat: List<com.teera.app.ChatLine> = emptyList(),
    onChat: ((String) -> Unit)? = null,
    onStandIn: ((Int) -> Unit)? = null,
    /** The watched player's picture, for a watcher: they sit where this player's own chair would be. */
    avatar: (@Composable (Dp) -> Unit)? = null,
) {
    var trayOpen by remember { mutableStateOf(false) }
    var facesOpen by remember { mutableStateOf(false) }
    val standingIn = !view.spectating && view.seatInfo(view.yourSeat)?.standIn != null
    var chatOpen by remember { mutableStateOf(false) }
    // Lines that arrived while the chat was closed.
    var seenChat by remember { mutableStateOf(chat.size) }
    if (chatOpen) seenChat = chat.size
    Box {
        WalnutSurface(Modifier.matchParentSize())
        HorizontalDivider(color = Felt.Brass.copy(alpha = 0.85f), thickness = 1.dp)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (view.isYourMove && view.turnMillisLeft != null) {
                    OwnTurnClock(view)
                    Spacer(Modifier.width(8.dp))
                }
                if (view.spectating && avatar != null) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).border(1.dp, Felt.Brass.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { avatar(32.dp) }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        standingIn && !view.isYourMove -> "تلعب بدال ${view.seatInfo(view.yourSeat)?.name.orEmpty()}"
                        view.spectating -> "تتفرّج من ورا ${view.seatName(view.yourSeat)}" + when {
                            view.isDropped(view.yourSeat) -> " — انقطع"
                            view.actingSeat == view.yourSeat -> " — دوره"
                            view.hand.isEmpty() -> " — بولاته مخفية"
                            else -> ""
                        }
                        view.phase == Phase.GAME_OVER || view.phase == Phase.MATCH_OVER -> " "
                        view.isYourMove && view.board.isEmpty -> "افتح الگيم بچفت"
                        // A bone picked that fits two ends, playing for somebody stuck too.
                        view.isYourMove && selected != null -> "دوس على الجهة اللي تريدها"
                        view.isYourMove && view.phase == Phase.RESCUE -> "قرارك"
                        view.isYourMove && view.phase == Phase.TEERA_OFFER -> "قرارك"
                        view.isYourMove && selected != null -> "دوس على الجهة اللي تريدها"
                        view.isYourMove -> "دورك — اختر بول"
                        view.actingSeat?.let(view::isDropped) == true ->
                            "${view.actingSeat?.let { view.seatInfo(it)?.name }.orEmpty()} انقطع… ننتظره شوي"
                        else -> "${view.actingSeat?.let { view.seatInfo(it)?.name }.orEmpty()} يفكّر…"
                    },
                    color = if (view.isYourMove) Felt.Brass else Felt.Muted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (onStandIn != null) {
                    view.openChairs().firstOrNull { view.isDropped(it) }?.let { seat ->
                        StandInButton(view.seatName(seat)) { onStandIn(seat) }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                if (onRaiseAlert != null) {
                    AlertButton(onRaiseAlert)
                    Spacer(Modifier.width(4.dp))
                }
                if (talk != null) MicButton(talk, size = 40.dp)
                if (onChat != null) {
                    Spacer(Modifier.width(4.dp))
                    ChatButton(unread = (chat.size - seenChat).coerceAtLeast(0)) { chatOpen = true }
                }
                if (!view.spectating) {
                    Spacer(Modifier.width(4.dp))
                    FacesButton(facesOpen) { facesOpen = !facesOpen; trayOpen = false }
                    Spacer(Modifier.width(4.dp))
                    SoundboardButton(trayOpen) { trayOpen = !trayOpen; facesOpen = false }
                }
            }
            // The tray floats above the hand rather than pushing it down, so the bones stay
            // exactly where the thumb expects them.
            if (trayOpen) {
                androidx.compose.ui.window.Popup(
                    popupPositionProvider = AboveAnchor,
                    onDismissRequest = { trayOpen = false },
                ) {
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Felt.Rail)
                            .border(1.dp, Felt.Brass.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                            .padding(vertical = 6.dp),
                    ) {
                        SoundTray { emote ->
                            onEmote(emote)
                            trayOpen = false
                        }
                    }
                }
            }

            if (facesOpen) {
                androidx.compose.ui.window.Popup(
                    popupPositionProvider = AboveAnchor,
                    onDismissRequest = { facesOpen = false },
                ) {
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Felt.Rail)
                            .border(1.dp, Felt.Brass.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                            .padding(vertical = 6.dp),
                    ) {
                        FacesTray { face ->
                            onEmote(face)
                            facesOpen = false
                        }
                    }
                }
            }

            if (chatOpen && onChat != null) {
                ChatDialog(chat, onSend = onChat, onClose = { chatOpen = false })
            }

            Spacer(Modifier.height(6.dp))

            // Bones sized to the hand: seven sit comfortably, and a hand swollen by tiles
            // pushed onto it still fits on one row without scrolling.
            BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 10.dp)) {
                val count = view.hand.size.coerceAtLeast(1)
                val gap = 5.dp
                val tileWidth = ((maxWidth - gap * (count - 1)) / count).coerceIn(30.dp, 48.dp)

                // The player's own arrangement of their bones: press, hold and slide one to
                // move it. Kept for the whole game; tiles that arrive go on the end.
                var order by remember(view.gameNumber) { mutableStateOf(listOf<Domino>()) }
                val shown = order.filter { it in view.hand } + view.hand.filter { it !in order }
                var dragging by remember { mutableStateOf<Domino?>(null) }
                var dragX by remember { mutableStateOf(0f) }
                val density = LocalDensity.current
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val step = with(density) { (tileWidth + gap).toPx() }
                val rowWidth = with(density) { maxWidth.toPx() }
                val currentShown by rememberUpdatedState(shown)

                Row(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(step, rowWidth, rtl) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { at ->
                                    val tiles = currentShown
                                    val start = (rowWidth - (step * tiles.size - (step - with(density) { tileWidth.toPx() }))) / 2f
                                    val fromLeft = ((at.x - start) / step).toInt().coerceIn(0, tiles.lastIndex.coerceAtLeast(0))
                                    dragging = tiles.getOrNull(if (rtl) tiles.lastIndex - fromLeft else fromLeft)
                                    dragX = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val tile = dragging ?: return@detectDragGesturesAfterLongPress
                                    dragX += amount.x
                                    val shift = (dragX / step).let { if (it > 0.5f) 1 else if (it < -0.5f) -1 else 0 }
                                    if (shift != 0) {
                                        val tiles = currentShown.toMutableList()
                                        val from = tiles.indexOf(tile)
                                        // Right to left, the list runs from the right edge.
                                        val to = (from + if (rtl) -shift else shift).coerceIn(0, tiles.lastIndex)
                                        if (to != from) {
                                            tiles.removeAt(from)
                                            tiles.add(to, tile)
                                            order = tiles
                                        }
                                        dragX -= shift * step
                                    }
                                },
                                onDragEnd = { dragging = null; dragX = 0f },
                                onDragCancel = { dragging = null; dragX = 0f },
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // A watcher behind somebody who keeps their bones hidden sees their backs.
                    if (view.spectating && shown.isEmpty()) {
                        repeat(view.handCounts.getOrElse(view.yourSeat) { 0 }) {
                            DominoTile(null, Modifier.size(tileWidth, tileWidth * 2), faceUp = false)
                        }
                    }
                    shown.forEachIndexed { index, tile ->
                        key(view.gameNumber, tile) {
                            val lifted = tile == dragging
                            Box(
                                Modifier
                                    .zIndex(if (lifted) 1f else 0f)
                                    .graphicsLayer {
                                        if (lifted) {
                                            translationX = dragX
                                            translationY = -14f
                                            scaleX = 1.08f
                                            scaleY = 1.08f
                                        }
                                    },
                            ) {
                                HandTile(
                                    tile = tile,
                                    index = index,
                                    width = tileWidth,
                                    live = tile in playable,
                                    picked = selected == tile,
                                    faded = view.isYourMove && tile !in playable,
                                    onTap = onTap,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandTile(
    tile: Domino,
    index: Int,
    width: androidx.compose.ui.unit.Dp,
    live: Boolean,
    picked: Boolean,
    faded: Boolean,
    onTap: (Domino) -> Unit,
) {
    // Dealt in one at a time, as a hand is picked up off the table.
    val dealt = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        dealt.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    // You push a bone forward before you lay it, so a playable one stands a little proud
    // of the rest and the chosen one stands further still.
    val lift by animateDpAsState(
        targetValue = if (picked) 16.dp else if (live) 8.dp else 0.dp,
        label = "lift",
    )
    DominoTile(
        domino = tile,
        modifier = Modifier
            .padding(bottom = lift)
            .size(width, width * 2)
            .scale(0.6f + 0.4f * dealt.value)
            .alpha(dealt.value.coerceIn(0f, 1f))
            .clickable { onTap(tile) }
            .testTag("hand-tile-$tile"),
        highlight = when {
            picked -> Felt.Brass
            live -> Felt.Playable.copy(alpha = 0.85f)
            else -> null
        },
        faded = faded,
    )
}

/**
 * The soundboard: a button that opens a tray of sounds everybody at the table hears.
 * Six are too many to sit beside the mic all the time, so they come up when wanted.
 */
@Composable
private fun SoundboardButton(open: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (open) Felt.Brass else Color(0xFF120B05).copy(alpha = 0.5f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onToggle)
            .testTag("soundboard"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Campaign,
            contentDescription = "الأصوات",
            tint = if (open) Felt.Ink else Felt.Brass,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SoundTray(onEmote: (Emote) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Emote.BOARD.forEach { emote ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF120B05).copy(alpha = 0.55f))
                    .border(1.dp, Felt.Brass.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .clickable { onEmote(emote) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .testTag("emote-${emote.name}"),
            ) {
                Text(emote.label(), color = Felt.Parchment, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One more line in the table's ⋮ menu. */
data class MenuOption(val label: String, val lit: Boolean = true, val action: () -> Unit)

/** Who is watching: small pictures under the partner's seat, ringed while they talk. */
@Composable
private fun Watchers(view: PlayerView, speaking: Set<Int>) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("يتفرجون:", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        view.spectators.take(6).forEach { watcher ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 3.dp)) {
                if (watcher.seat in speaking) SpeakingHalo(32.dp)
                Box(Modifier.size(26.dp).clip(CircleShape).border(1.dp, Felt.Brass.copy(alpha = 0.6f), CircleShape)) {
                    Avatar(watcher.avatar.takeIf { it != "photo" }.orEmpty(), watcher.name, 26.dp)
                }
            }
        }
    }
}

/** Opens the table's chat; a gold count of lines not read yet. */
@Composable
private fun ChatButton(unread: Int, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF120B05).copy(alpha = 0.5f))
                .border(1.dp, Felt.Brass.copy(alpha = 0.7f), CircleShape)
                .clickable(onClick = onClick)
                .testTag("chat"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "الكتابة", tint = Felt.Brass)
        }
        if (unread > 0) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(Felt.Danger),
                contentAlignment = Alignment.Center,
            ) { Text("${unread.coerceAtMost(9)}", color = Felt.Parchment, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** The table's chat: what has been said, and a line to say more. */
@Composable
private fun ChatDialog(chat: List<com.teera.app.ChatLine>, onSend: (String) -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text("السوالف", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
            val list = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(chat.size) { if (chat.isNotEmpty()) list.scrollToItem(chat.size - 1) }
            androidx.compose.foundation.lazy.LazyColumn(
                state = list,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (chat.isEmpty()) {
                    item { Text("محد كتب شي بعد", color = Felt.Muted, style = MaterialTheme.typography.bodyMedium) }
                }
                items(chat.size) { index ->
                    val line = chat[index]
                    Column {
                        Text(line.name, color = if (line.seat >= 4) Felt.Muted else Felt.Brass, style = MaterialTheme.typography.labelSmall)
                        Text(line.text, color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(200) },
                    placeholder = { Text("اكتب شي…", color = Felt.Muted) },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Felt.Brass,
                        unfocusedBorderColor = Felt.Brass.copy(alpha = 0.5f),
                        focusedTextColor = Felt.Parchment,
                        unfocusedTextColor = Felt.Parchment,
                        cursorColor = Felt.Brass,
                    ),
                    modifier = Modifier.weight(1f).testTag("chat-input"),
                )
                Spacer(Modifier.width(8.dp))
                BrassButton("دزّ", { onSend(draft); draft = "" }, enabled = draft.isNotBlank())
            }
        }
    }
}

/** The newest chat line, held over the table for a few seconds as it arrives. */
@Composable
private fun ChatToast(chat: List<com.teera.app.ChatLine>, modifier: Modifier) {
    val last = chat.lastOrNull() ?: return
    var shown by remember(last.id, last.text) { mutableStateOf(true) }
    LaunchedEffect(last.id, last.text) {
        delay(4_000)
        shown = false
    }
    if (!shown) return
    Row(
        modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6120B05))
            .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("${last.name}: ", color = Felt.Brass, style = MaterialTheme.typography.labelLarge)
        Text(last.text, color = Felt.Parchment, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}

/** The "خاوة" button: a red-edged plaque, so it is not pressed by mistake for a sound. */
@Composable
private fun StandInButton(name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Felt.Brass)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag("stand-in"),
        contentAlignment = Alignment.Center,
    ) {
        Text("العب بدال $name", color = Felt.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun AlertButton(onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF3A0E09))
            .border(1.dp, Felt.Danger, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag("raise-alert"),
        contentAlignment = Alignment.Center,
    ) {
        Text("خاوة", color = Felt.Parchment, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The vote, over the top of the table: who raised it against whom, the clock running
 * down, each voter's answer as it comes in, and this player's two buttons if they have
 * not answered yet.
 */
@Composable
private fun AlertVotePanel(view: PlayerView, vote: com.teera.core.AlertVote, onVote: (Boolean) -> Unit, modifier: Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vote.deadlineEpochMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val left = ((vote.deadlineEpochMs - now) / 1000).coerceIn(0, 99)
    val raiser = view.seatInfo(vote.raisedBy)?.name.orEmpty()
    val mine = view.yourSeat
    val waitingOnMe = mine in vote.voters && mine !in vote.yes && mine !in vote.no
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Felt.Rail)
            .border(1.5.dp, Felt.Danger, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ناخذ خاوة!", color = Felt.Brass, style = MaterialTheme.typography.displaySmall, fontSize = 28.sp)
        Text(
            "$raiser يريد خاوة على ${view.teamName(vote.againstTeam)}",
            color = Felt.Parchment,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            vote.voters.forEach { seat ->
                val answer = when (seat) {
                    in vote.yes -> "✓"
                    in vote.no -> "✗"
                    else -> "…"
                }
                val colour = when (seat) {
                    in vote.yes -> Felt.Playable
                    in vote.no -> Felt.Danger
                    else -> Felt.Muted
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(view.seatInfo(seat)?.name.orEmpty().take(6), color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
                    Text(answer, color = colour, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Text("يحتاج ${vote.needed} · باقي $left ثانية", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
        if (waitingOnMe) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { onVote(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                    modifier = Modifier.testTag("vote-yes"),
                ) { Text("أوافق") }
                OutlinedButton(onClick = { onVote(false) }, modifier = Modifier.testTag("vote-no")) {
                    Text("لا", color = Felt.Parchment)
                }
            }
        }
    }
}

/** Asking everyone at the table to play the match to another score. */
@Composable
private fun TargetVotePanel(view: PlayerView, vote: com.teera.core.TargetVote, onVote: (Boolean) -> Unit, modifier: Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vote.deadlineEpochMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val left = ((vote.deadlineEpochMs - now) / 1000).coerceIn(0, 99)
    val mine = view.yourSeat
    val waitingOnMe = !view.spectating && mine in vote.voters && mine !in vote.yes && mine !in vote.no
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Felt.Rail)
            .border(1.5.dp, Felt.Brass, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${view.rules.target} ← ${vote.target}", color = Felt.Brass, style = MaterialTheme.typography.displaySmall, fontSize = 28.sp)
        Text(
            "${view.seatName(vote.proposedBy)} يريد اللعبة لـ ${vote.target}",
            color = Felt.Parchment,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            vote.voters.forEach { seat ->
                val (answer, colour) = when (seat) {
                    in vote.yes -> "✓" to Felt.Playable
                    in vote.no -> "✗" to Felt.Danger
                    else -> "…" to Felt.Muted
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(view.seatName(seat).take(6), color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
                    Text(answer, color = colour, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Text("لازم الكل يوافق · باقي $left ثانية", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
        if (waitingOnMe) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { onVote(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                    modifier = Modifier.testTag("target-yes"),
                ) { Text("أوافق") }
                OutlinedButton(onClick = { onVote(false) }, modifier = Modifier.testTag("target-no")) {
                    Text("لا", color = Felt.Parchment)
                }
            }
        }
    }
}

private enum class PeopleAction { KICK, BLOCK, SWAP }

/** The other people here, players and watchers, by id and name; no bots. */
internal fun PlayerView.others(myId: String): List<Pair<String, String>> =
    (seats.filter { !it.isBot && it.playerId.isNotBlank() } + spectators)
        .filter { it.playerId != myId && it.playerId.isNotBlank() }
        .distinctBy { it.playerId }
        .map { it.playerId to it.name.ifBlank { "لاعب" } }

/**
 * Swapping a player and a watcher is two picks: the chair (a person or a bot), then the
 * watcher who takes it and its bones.
 */
@Composable
internal fun SwapPicker(
    seats: List<com.teera.core.Seat>,
    watchers: List<com.teera.core.Seat>,
    onSwap: (seated: String, watcher: String) -> Unit,
    onClose: () -> Unit,
) {
    var chair by remember { mutableStateOf<Pair<String, String>?>(null) }
    val picked = chair
    if (picked == null) {
        PeopleDialog(
            title = "منو يطلع يتفرج؟",
            note = "تختار اللاعب، وبعدين المتفرج اللي ياخذ كرسيه وبولاته.",
            people = seats.sortedBy { it.seat }.map { s ->
                (if (s.isBot) "bot-${s.seat}" else s.playerId) to (if (s.isBot) "بوت ${s.seat + 1}" else s.name.ifBlank { "لاعب" })
            }.filter { it.first.isNotBlank() },
            label = { "هذا" },
            onPick = { id, name -> chair = id to name },
            onClose = onClose,
        )
    } else {
        PeopleDialog(
            title = "منو يلعب بدال ${picked.second}؟",
            note = "${picked.second} يصير يتفرج من ورا كرسيه.",
            people = watchers.map { it.playerId to it.name.ifBlank { "متفرج" } },
            label = { "يلعب" },
            onPick = { id, _ -> onSwap(picked.first, id); onClose() },
            onClose = onClose,
        )
    }
}

/** Picks one of the people here for something done to them: sending out, or blocking. */
@Composable
private fun PeopleDialog(
    title: String,
    note: String,
    people: List<Pair<String, String>>,
    label: (String) -> String,
    onPick: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text(title, color = Felt.Brass, style = MaterialTheme.typography.headlineSmall)
            Text(note, color = Felt.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            people.forEach { (id, name) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Felt.Parchment, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { onPick(id, name) }, modifier = Modifier.testTag("person-$id")) {
                        Text(label(id), color = Felt.Parchment)
                    }
                }
            }
            OutlinedButton(onClick = onClose) { Text("رجوع", color = Felt.Parchment) }
        }
    }
}

/** Scores the table could move to: off the list, not the current one, above both scores. */
internal fun PlayerView.targetChoices(): List<Int> =
    if (rules.byGames) emptyList()
    else com.teera.core.TARGET_CHOICES.filter { it != rules.target && it > (scores.maxOrNull() ?: 0) && it in 151..301 }

/** Puts a popup just above whatever it is anchored to, centred across it. */
private object AboveAnchor : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ) = androidx.compose.ui.unit.IntOffset(
        x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceAtLeast(0),
        y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0),
    )
}

/** What a soundboard button says on it. */
private fun Emote.label(): String = when (this) {
    Emote.GULP -> "ابلع"
    Emote.NO_WAY -> "بالله ما طيغ!"
    Emote.SHEEL -> "شيل"
    Emote.SPLOOSH -> "سبلوش"
    Emote.WALLAH -> ""
    Emote.CHAT -> "يلا نسولف"
    Emote.DRUMROLL -> "طبل"
    Emote.SAD -> "حسرة"
    Emote.SLAP -> "خذ"
    Emote.GHASHANA -> "غشعنا على وجه الخير"
    Emote.KHATLA -> "خاتلة"
    else -> ""
}

// -- the sums -----------------------------------------------------------------

/** A card in the cabinet's materials: walnut, with gold along its edge. */
@Composable
internal fun Plaque(content: @Composable () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Felt.Brass, RoundedCornerShape(16.dp)),
    ) {
        WalnutSurface(Modifier.matchParentSize())
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun GameSummary(view: PlayerView, steps: List<GameEvent>, onNextGame: () -> Unit, onLeave: () -> Unit) {
    val result = view.lastGame
    var reviewing by remember { mutableStateOf(false) }
    if (reviewing) StepsDialog(view, steps, onClose = { reviewing = false })
    val matchOver = view.phase == Phase.MATCH_OVER
    val weLost = view.losingTeam == view.yourTeam

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Plaque {
            Text(
                when {
                    matchOver && weLost -> "خسرتوا المباراة"
                    matchOver -> "فزتوا بالمباراة"
                    result?.end == GameEnd.BLOCKED -> "قفلت"
                    result?.end == GameEnd.TEERA -> "طيرة"
                    result?.winnerSeat == view.yourSeat -> "خلّصت — اكس"
                    else -> "خلص الگيم"
                },
                color = Felt.Brass,
                style = MaterialTheme.typography.displaySmall,
            )
            if (result != null) {
                val winner = result.winnerSeat?.let { view.seatInfo(it)?.name } ?: "لا أحد"
                Text(
                    when (result.end) {
                        GameEnd.BLOCKED -> "ما تسجّلت نقاط، والفتحة لـ ${view.seatInfo(result.nextOpener)?.name.orEmpty()}"
                        GameEnd.TEERA -> "$winner طيّر بـ ${result.handPips.getOrElse(result.winnerSeat ?: 0) { 0 }}"
                        GameEnd.X -> "$winner خلّص وأخذ اكس"
                    },
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                HorizontalDivider(color = Felt.Brass.copy(alpha = 0.4f))
            }
            // The sums as a little table: one column per team, named for its players.
            val teams = listOf(view.yourTeam, 1 - view.yourTeam)
            SumRow("", teams.map { Cell.Name(view.teamName(it)) })
            if (result != null) {
                SumRow(
                    "هذا الگيم",
                    teams.map { team ->
                        val tookX = result.end == GameEnd.X && result.winnerSeat?.let(::teamOf) == team
                        if (tookX) Cell.X else Cell.Points(result.scored.getOrElse(team) { 0 }, signed = true)
                    },
                )
            }
            SumRow("المجموع", teams.map { Cell.Points(view.scores.getOrElse(it) { 0 }, signed = false) })
            Text(
                if (view.rules.byGames) {
                    val left = view.rules.games - view.gamesPlayed
                    if (left > 0) "باقي $left من ${view.rules.games} گيمات، وبالآخر الأكثر نقاط يخسر"
                    else "خلصت ${view.rules.games} گيمات، والأكثر نقاط يخسر"
                } else {
                    "اللي يوصل ${view.rules.target} يخسر"
                },
                color = Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))
            if (steps.isNotEmpty()) {
                OutlinedButton(
                    onClick = { reviewing = true },
                    border = BorderStroke(1.dp, Felt.Brass.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Felt.Brass),
                    modifier = Modifier.fillMaxWidth().testTag("reviewSteps"),
                ) { Text("شوف خطوات الگيم", style = MaterialTheme.typography.labelLarge) }
            }
            Button(
                onClick = if (matchOver) onLeave else onNextGame,
                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (matchOver) "رجوع للقائمة" else "الگيم الجاي", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One cell of the sums: a team's name, its points, or the X it took. */
private sealed interface Cell {
    data class Name(val text: String) : Cell
    data class Points(val value: Int, val signed: Boolean) : Cell
    data object X : Cell
}

@Composable
private fun SumRow(label: String, cells: List<Cell>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Felt.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.9f))
        cells.forEach { cell ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (cell) {
                    is Cell.Name -> Text(
                        cell.text,
                        color = Felt.Brass,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    // Isolated left-to-right, or the bidi algorithm puts the sign after the digits: "25-".
                    is Cell.Points -> Text(
                        "\u2066" + when {
                            cell.signed && cell.value > 0 -> "+${cell.value}"
                            cell.value < 0 -> "−${-cell.value}"
                            else -> "${cell.value}"
                        } + "\u2069",
                        color = Felt.Parchment,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // The X is a mark, not a number: it gets its own gold seal.
                    Cell.X -> Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass, Felt.GoldDeep)))
                            .border(1.dp, Felt.GoldLit, RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 1.dp),
                    ) {
                        Text("X", color = Felt.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/**
 * A team as its players: "علي/عمر". In seat order, so every phone at the table calls a
 * team by the same name.
 */
internal fun PlayerView.teamName(team: Int): String =
    seats.filter { teamOf(it.seat) == team }
        .sortedBy { it.seat }
        .joinToString("/") { seat ->
            when {
                seat.isBot -> "بوت"
                seat.name.isBlank() -> "لاعب"
                else -> seat.name.trim().split(" ").first()
            }
        }

/** A person (not a bot) whose connection has dropped; the referee is holding their seat. */
internal fun PlayerView.isDropped(seat: Int): Boolean =
    seatInfo(seat)?.let { !it.isBot && it.playerId.isNotEmpty() && !it.connected && it.standIn == null } == true

/** Chairs a watcher could play: a dropped player's or a bot's, with nobody standing in yet. */
internal fun PlayerView.openChairs(): List<Int> = (0 until 4).filter { seat ->
    val info = seatInfo(seat)
    info != null && info.standIn == null && (info.isBot || isDropped(seat))
}

/** A chair's name as the table says it. */
internal fun PlayerView.seatName(seat: Int): String {
    val info = seatInfo(seat)
    return when {
        info == null || info.name.isBlank() -> "لاعب"
        info.isBot -> "البوت"
        else -> info.name.trim()
    }
}
