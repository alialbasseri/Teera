package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt
import com.teera.core.Arm
import com.teera.core.Domino
import com.teera.core.GameEnd
import com.teera.core.Move
import com.teera.core.Phase
import com.teera.core.PlayerView
import com.teera.core.teamOf

/** What the rescuer has chosen to do for the seat stuck after them. */
internal enum class RescueMode { GIVE, PLAY_FOR }

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

    Column(Modifier.fillMaxSize().background(Felt.Deep)) {

        ScoreRail(view, onLeave)
        OpponentRow(view)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            BoardView(
                board = view.board,
                modifier = Modifier.fillMaxSize(),
                liveArms = selected?.let { armsFor(view, it).toSet() } ?: emptySet(),
                onArmTapped = tapArm,
            )

            Prompt(
                view = view,
                rescueMode = rescueMode,
                onRescueMode = { rescueState.value = it },
                onMove = onMove,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
        }

        Hand(
            view = view,
            selected = selected,
            playable = handHighlights(view, rescueMode),
            onTap = tapTile,
        )
    }

    if (view.phase == Phase.GAME_OVER || view.phase == Phase.MATCH_OVER) {
        GameSummary(view, onNextGame, onLeave)
    }
}

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

@Composable
private fun ScoreRail(view: PlayerView, onLeave: () -> Unit) {
    val us = view.scores.getOrElse(view.yourTeam) { 0 }
    val them = view.scores.getOrElse(1 - view.yourTeam) { 0 }

    Box {
        WalnutSurface(Modifier.matchParentSize())
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
                    "المباراة لـ ${view.rules.target}",
                    color = Felt.Brass,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("اللي يوصل يخسر", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onLeave) { Text("خروج", color = Felt.Muted) }
        }
        HorizontalDivider(color = Felt.Brass.copy(alpha = 0.85f), thickness = 1.dp, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ScorePlate(who: String, score: Int, leading: Boolean) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF120B05).copy(alpha = 0.55f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(who, color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
        Text(
            "$score",
            color = if (leading) Felt.Playable else Felt.Behind,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** The other three seats: who is on, how many bones they hold, and who your partner is. */
@Composable
private fun OpponentRow(view: PlayerView) {
    val others = (1..3).map { (view.yourSeat + it) % 4 }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        others.forEach { seat ->
            val name = view.seatInfo(seat)?.name.orEmpty().ifEmpty { "لاعب" }
            val acting = seat == view.actingSeat
            val partner = teamOf(seat) == view.yourTeam
            val stuck = seat == view.stuckSeat
            val tint = when {
                acting -> Felt.Brass
                partner -> Felt.Playable
                else -> Felt.Muted
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
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
                        Text(
                            name.take(1),
                            color = tint,
                            style = MaterialTheme.typography.titleMedium,
                        )
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
                        stuck -> "مسكّر عليه"
                        partner -> "شريكك"
                        else -> " "
                    },
                    color = if (stuck) Felt.Danger else Felt.Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

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
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "الطيرة تنهي الجيم بدون اكس",
                    color = Felt.IvoryShade,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onMove(Move.Teera) },
                        colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                    ) { Text("طيّر") }
                    OutlinedButton(onClick = { onMove(Move.CarryOn) }) { Text("كمّل للاكس") }
                }
            }

            view.phase == Phase.REDEAL_OFFER && view.isYourMove -> Panel {
                Text("بيدك أربع دبلات", color = Felt.Parchment, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onMove(Move.Redeal) },
                        colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                    ) { Text("أعد الخلط") }
                    OutlinedButton(onClick = { onMove(Move.KeepHand) }) { Text("كمّل") }
                }
            }

            view.phase == Phase.RESCUE && view.isYourMove -> Panel {
                val stuckName = view.stuckSeat?.let { view.seatInfo(it)?.name }.orEmpty()
                Text("$stuckName مسكّر عليه", color = Felt.Parchment, style = MaterialTheme.typography.titleMedium)
                when (rescueMode) {
                    null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onRescueMode(RescueMode.PLAY_FOR) },
                            enabled = view.legalMoves.any { it is Move.PlayFor },
                            colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                        ) { Text("العب بداله") }
                        OutlinedButton(onClick = { onRescueMode(RescueMode.GIVE) }) { Text("انطيه بول") }
                    }
                    RescueMode.GIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اختر البول اللي تنطيه", color = Felt.IvoryShade)
                        TextButton(onClick = { onRescueMode(null) }) { Text("رجوع", color = Felt.Brass) }
                    }
                    RescueMode.PLAY_FOR -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اختر البول اللي تلعبه", color = Felt.IvoryShade)
                        TextButton(onClick = { onRescueMode(null) }) { Text("رجوع", color = Felt.Brass) }
                    }
                }
            }

            view.phase == Phase.RESCUE && view.stuckSeat == view.yourSeat -> Panel {
                Text("مسكّر عليك", color = Felt.Danger, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$waitingOn إما ينطيك بول أو يلعب بدالك",
                    color = Felt.IvoryShade,
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
            .clip(RoundedCornerShape(16.dp))
            .background(Felt.Deep.copy(alpha = 0.93f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun Hand(
    view: PlayerView,
    selected: Domino?,
    playable: Set<Domino>,
    onTap: (Domino) -> Unit,
) {
    Box {
        WalnutSurface(Modifier.matchParentSize())
        HorizontalDivider(color = Felt.Brass.copy(alpha = 0.85f), thickness = 1.dp)
        Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
        Text(
            text = when {
                view.phase == Phase.GAME_OVER || view.phase == Phase.MATCH_OVER -> " "
                view.isYourMove && view.board.isEmpty -> "افتح الجيم بدبل"
                view.isYourMove && view.phase == Phase.RESCUE -> "قرارك"
                view.isYourMove && selected != null -> "دوس على الجهة اللي تريدها"
                view.isYourMove && playable.isEmpty() -> "ما عندك بول يلعب"
                view.isYourMove -> "دورك — اختر بول"
                else -> "${view.actingSeat?.let { view.seatInfo(it)?.name }.orEmpty()} يفكّر…"
            },
            color = if (view.isYourMove) Felt.Brass else Felt.Muted,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(view.hand.size) { index ->
                val tile = view.hand[index]
                val live = tile in playable
                val picked = selected == tile
                // You push a bone forward before you lay it, so a playable one stands a
                // little proud of the rest and the chosen one stands further still.
                val lift by animateDpAsState(
                    targetValue = if (picked) 16.dp else if (live) 8.dp else 0.dp,
                    label = "lift",
                )
                DominoTile(
                    domino = tile,
                    modifier = Modifier
                        .padding(bottom = lift)
                        .size(46.dp, 92.dp)
                        .clickable { onTap(tile) }
                        .testTag("hand-tile-$tile"),
                    highlight = when {
                        picked -> Felt.Brass
                        live -> Felt.Playable.copy(alpha = 0.85f)
                        else -> null
                    },
                    faded = view.isYourMove && !live,
                )
            }
        }
        }
    }
}

@Composable
private fun GameSummary(view: PlayerView, onNextGame: () -> Unit, onLeave: () -> Unit) {
    val result = view.lastGame
    val matchOver = view.phase == Phase.MATCH_OVER
    val weLost = view.losingTeam == view.yourTeam

    AlertDialog(
        onDismissRequest = {},
        containerColor = Felt.Table,
        title = {
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
            )
        },
        text = {
            Column {
                if (result != null) {
                    val winner = result.winnerSeat?.let { view.seatInfo(it)?.name } ?: "لا أحد"
                    Text(
                        when (result.end) {
                            GameEnd.BLOCKED -> "ما تسجّلت نقاط، والفتحة لـ $winner"
                            GameEnd.TEERA -> "$winner طيّر بـ ${result.handPips.getOrElse(result.winnerSeat ?: 0) { 0 }}"
                            GameEnd.X -> "$winner خلّص وأخذ اكس"
                        },
                        color = Felt.Parchment,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "هذا الجيم:  نحن ${result.scored.getOrElse(view.yourTeam) { 0 }}" +
                            "   ·   هم ${result.scored.getOrElse(1 - view.yourTeam) { 0 }}",
                        color = Felt.IvoryShade,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "المجموع:  نحن ${view.scores.getOrElse(view.yourTeam) { 0 }}" +
                        "   ·   هم ${view.scores.getOrElse(1 - view.yourTeam) { 0 }}",
                    color = Felt.Parchment,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            if (matchOver) {
                TextButton(onClick = onLeave) { Text("رجوع للقائمة", color = Felt.Brass) }
            } else {
                TextButton(onClick = onNextGame) { Text("الجيم الجاي", color = Felt.Brass) }
            }
        },
    )
}

