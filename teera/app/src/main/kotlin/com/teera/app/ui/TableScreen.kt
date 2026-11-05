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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.VoiceOverOff
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    Column(Modifier.fillMaxSize().background(Felt.Deep)) {

        ScoreRail(view, sound, onLeave = { if (over) onLeave() else confirmLeave = true }, talk = talk)
        OpponentRow(view, emotes, talk?.speaking.orEmpty())

        Box(Modifier.weight(1f).fillMaxWidth()) {
            BoardView(
                board = view.board,
                modifier = Modifier.fillMaxSize(),
                liveArms = selected?.let { armsFor(view, it).toSet() } ?: emptySet(),
                onArmTapped = tapArm,
                arrival = arrivalOf(view),
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

            emotes.lastOrNull { it.seat == view.yourSeat }?.let {
                EmoteBubble(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }
        }

        Hand(
            view = view,
            selected = selected,
            playable = handHighlights(view, rescueMode),
            onTap = tapTile,
            onEmote = onEmote,
            talk = talk,
        )
    }

    if (over && showSummary) GameSummary(view, onNextGame, onLeave)

    if (confirmLeave) {
        Dialog(onDismissRequest = { confirmLeave = false }) {
            Plaque {
                Text("تطلع من الطاولة؟", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
                Text(
                    "المباراة تضيع، وإذا أونلاين يكمل بوت بدالك",
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
        1 -> SeatSpot.TOP_START
        2 -> SeatSpot.TOP_MIDDLE
        else -> SeatSpot.TOP_END
    }

// -- the rail ----------------------------------------------------------------

@Composable
private fun ScoreRail(view: PlayerView, sound: SoundSettings, onLeave: () -> Unit, talk: Talk? = null) {
    val us = view.scores.getOrElse(view.yourTeam) { 0 }
    val them = view.scores.getOrElse(1 - view.yourTeam) { 0 }

    Box {
        WalnutSurface(Modifier.matchParentSize())
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Points are a burden here, so whoever has fewer is the one in front.
            ScorePlate("نحن", us, leading = us <= them)
            Spacer(Modifier.width(8.dp))
            ScorePlate("هم", them, leading = them < us)

            Spacer(Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text("جيم ${view.gameNumber}", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
                Text(
                    "لـ ${view.rules.target}",
                    color = Felt.Brass,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (view.alerts.any { it > 0 }) {
                    Text(
                        "ألرت ${view.alerts.getOrElse(view.yourTeam) { 0 }} · ${view.alerts.getOrElse(1 - view.yourTeam) { 0 }}",
                        color = Felt.Danger,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Column {
                IconButton(onClick = { sound.onMusic(!sound.musicOn) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (sound.musicOn) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff,
                        contentDescription = "موسيقى",
                        tint = if (sound.musicOn) Felt.Brass else Felt.Muted,
                    )
                }
                IconButton(onClick = { sound.onEffects(!sound.effectsOn) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (sound.effectsOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = "أصوات",
                        tint = if (sound.effectsOn) Felt.Brass else Felt.Muted,
                    )
                }
            }
            if (talk != null) {
                // Muting everybody else; your own mic is the button by your hand.
                IconButton(onClick = talk.onHearing, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (talk.hearing) Icons.Rounded.RecordVoiceOver else Icons.Rounded.VoiceOverOff,
                        contentDescription = if (talk.hearing) "اكتم أصوات اللاعبين" else "اسمع اللاعبين",
                        tint = if (talk.hearing) Felt.Brass else Felt.Muted,
                    )
                }
            }
            IconButton(onClick = onLeave) {
                Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = "خروج", tint = Felt.Muted)
            }
        }
        HorizontalDivider(
            color = Felt.Brass.copy(alpha = 0.85f),
            thickness = 1.dp,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** An engraved plaque, its number rolling to the new score rather than jumping to it. */
@Composable
private fun ScorePlate(who: String, score: Int, leading: Boolean) {
    val shown by animateIntAsState(score, animationSpec = tween(900), label = "score")
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF120B05).copy(alpha = 0.55f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(who, color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
        Text(
            "$shown",
            color = if (leading) Felt.Playable else Felt.Behind,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

// -- the seats ----------------------------------------------------------------

/** The other three seats: who is on, how many bones they hold, and who your partner is. */
@Composable
private fun OpponentRow(view: PlayerView, emotes: List<ShownEmote>, speaking: Set<Int> = emptySet()) {
    val others = (1..3).map { (view.yourSeat + it) % 4 }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring",
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        others.forEach { seat ->
            val name = view.seatInfo(seat)?.name.orEmpty().ifEmpty { "لاعب" }
            val acting = seat == view.actingSeat
            val partner = teamOf(seat) == view.yourTeam
            val stuck = seat == view.stuckSeat
            val dropped = view.isDropped(seat)
            val tint = when {
                acting -> Felt.Brass
                partner -> Felt.Playable
                else -> Felt.Muted
            }

            Box(contentAlignment = Alignment.TopCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        if (seat in speaking) SpeakingHalo(52.dp)
                        // Whoever the table is waiting on breathes, so you can see it from
                        // across the room.
                        if (acting) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .scale(ring)
                                    .alpha(1.6f - ring)
                                    .border(2.dp, Felt.Brass, CircleShape),
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Felt.Lapis)
                                .border(
                                    width = if (acting) 2.dp else 1.dp,
                                    color = Felt.Brass.copy(alpha = if (acting) 1f else 0.6f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(name.take(1), color = tint, style = MaterialTheme.typography.titleMedium)
                        }
                        // Tile counts are public in Teera, so they sit on the seat itself.
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 10.dp, y = 4.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Felt.Rail)
                                .border(1.dp, Felt.Brass.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "${view.handCounts.getOrElse(seat) { 0 }}",
                                color = Felt.Parchment,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = tint, style = MaterialTheme.typography.labelSmall)
                    Text(
                        when {
                            dropped -> "انقطع"
                            stuck -> "مسكّر عليه"
                            partner -> "شريكك"
                            else -> " "
                        },
                        color = if (stuck || dropped) Felt.Danger else Felt.Muted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                emotes.lastOrNull { it.seat == seat }?.let { EmoteBubble(it, Modifier.offset(y = 30.dp)) }
            }
        }
    }
}

private fun Emote.words(): String = when (this) {
    Emote.GULP -> "ابلع!"
    Emote.DRUMROLL -> "طبول…"
    Emote.SAD -> "راحت عليك"
    Emote.SLAP -> "خذ!"
}

/** A soundboard press, popping up over whoever pressed it. */
@Composable
private fun EmoteBubble(shown: ShownEmote, modifier: Modifier = Modifier) {
    key(shown.id) {
        val pop = remember { Animatable(0.4f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
        Box(
            modifier
                .scale(pop.value)
                .clip(RoundedCornerShape(14.dp))
                .background(Felt.Parchment)
                .border(1.5.dp, Felt.Brass, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(shown.emote.words(), color = Felt.Ink, style = MaterialTheme.typography.titleMedium)
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
        result?.end == GameEnd.BLOCKED -> "سدّ"
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
                    "الطيرة تنهي الجيم بدون اكس" + if (view.handPips == 1) "، والواحد ينحسب عليكم" else "",
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
                Text("بيدك أربع دبلات", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
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
                Text("$stuckName مسكّر عليه", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
                when (rescueMode) {
                    null -> {
                        Text("تلعب تنطي؟", color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onRescueMode(RescueMode.PLAY_FOR) },
                                enabled = view.legalMoves.any { it is Move.PlayFor },
                                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                            ) { Text("العب بداله") }
                            OutlinedButton(onClick = { onRescueMode(RescueMode.GIVE) }) {
                                Text("انطيه بول", color = Felt.Parchment)
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
                Text("مسكّر عليك", color = Felt.Danger, style = MaterialTheme.typography.titleLarge)
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
) {
    Box {
        WalnutSurface(Modifier.matchParentSize())
        HorizontalDivider(color = Felt.Brass.copy(alpha = 0.85f), thickness = 1.dp)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        view.phase == Phase.GAME_OVER || view.phase == Phase.MATCH_OVER -> " "
                        view.isYourMove && view.board.isEmpty -> "افتح الجيم بدبل"
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
                if (talk != null) MicButton(talk, size = 40.dp)
                Soundboard(onEmote)
            }

            Spacer(Modifier.height(6.dp))

            // Bones sized to the hand: seven sit comfortably, and a hand swollen by tiles
            // pushed onto it still fits on one row without scrolling.
            BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 10.dp)) {
                val count = view.hand.size.coerceAtLeast(1)
                val gap = 5.dp
                val tileWidth = ((maxWidth - gap * (count - 1)) / count).coerceIn(30.dp, 48.dp)
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    view.hand.forEachIndexed { index, tile ->
                        key(view.gameNumber, tile) {
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

/** Four buttons that everybody at the table hears. */
@Composable
private fun Soundboard(onEmote: (Emote) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(Emote.GULP to "ابلع", Emote.SLAP to "خذ", Emote.DRUMROLL to "طبل", Emote.SAD to "حسرة")
            .forEach { (emote, label) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF120B05).copy(alpha = 0.5f))
                        .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onEmote(emote) }
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                        .testTag("emote-${emote.name}"),
                ) {
                    Text(label, color = Felt.Parchment, style = MaterialTheme.typography.labelSmall)
                }
            }
    }
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
private fun GameSummary(view: PlayerView, onNextGame: () -> Unit, onLeave: () -> Unit) {
    val result = view.lastGame
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
                    result?.end == GameEnd.BLOCKED -> "الجيم انسدّ"
                    result?.end == GameEnd.TEERA -> "طيرة"
                    result?.winnerSeat == view.yourSeat -> "خلّصت — اكس"
                    else -> "خلص الجيم"
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
                SumRow("هذا الجيم", result.scored.getOrElse(view.yourTeam) { 0 }, result.scored.getOrElse(1 - view.yourTeam) { 0 }, signed = true)
            }
            SumRow("المجموع", view.scores.getOrElse(view.yourTeam) { 0 }, view.scores.getOrElse(1 - view.yourTeam) { 0 }, signed = false)
            Text(
                "اللي يوصل ${view.rules.target} يخسر",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = if (matchOver) onLeave else onNextGame,
                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (matchOver) "رجوع للقائمة" else "الجيم الجاي", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SumRow(label: String, us: Int, them: Int, signed: Boolean) {
    fun show(n: Int) = if (signed && n > 0) "+$n" else "$n"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Felt.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("نحن ${show(us)}", color = Felt.Parchment, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(14.dp))
        Text("هم ${show(them)}", color = Felt.Parchment, style = MaterialTheme.typography.titleMedium)
    }
}

/** A person (not a bot) whose connection has dropped; the referee is holding their seat. */
internal fun PlayerView.isDropped(seat: Int): Boolean =
    seatInfo(seat)?.let { !it.isBot && it.playerId.isNotEmpty() && !it.connected } == true
