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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private enum class RescueMode { GIVE, PLAY_FOR }

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
    var selected by remember(view.gameNumber, view.board.size) { mutableStateOf<Domino?>(null) }
    var rescueMode by remember(view.stuckSeat) { mutableStateOf<RescueMode?>(null) }

    val plays = view.legalMoves.filterIsInstance<Move.Play>()
    val playFors = view.legalMoves.filterIsInstance<Move.PlayFor>()
    val gives = view.legalMoves.filterIsInstance<Move.Give>()

    /** Where a chosen tile may go, which is empty while the four sides are being closed. */
    fun armsFor(tile: Domino): List<Arm> = when {
        view.phase == Phase.RESCUE -> playFors.filter { it.domino == tile }.map { it.arm }
        else -> plays.filter { it.domino == tile }.mapNotNull { it.arm }
    }

    fun lay(tile: Domino, arm: Arm?) {
        if (view.phase == Phase.RESCUE) {
            onMove(Move.PlayFor(tile, arm ?: Arm.NORTH))
        } else {
            onMove(Move.Play(tile, arm))
        }
        selected = null
        rescueMode = null
    }

    fun tapTile(tile: Domino) {
        if (view.phase == Phase.RESCUE && rescueMode == RescueMode.GIVE) {
            if (gives.any { it.domino == tile }) {
                onMove(Move.Give(tile))
                rescueMode = null
            }
            return
        }
        val arms = armsFor(tile)
        when {
            arms.isEmpty() && playableNow(view, tile, plays, playFors, rescueMode) -> lay(tile, null)
            arms.size == 1 -> lay(tile, arms.first())
            arms.size > 1 -> selected = if (selected == tile) null else tile
            else -> selected = tile
        }
    }

    Column(Modifier.fillMaxSize().background(Felt.Deep)) {

        ScoreRail(view, onLeave)
        OpponentRow(view)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            BoardView(
                board = view.board,
                modifier = Modifier.fillMaxSize(),
                liveArms = selected?.let { armsFor(it).toSet() } ?: emptySet(),
                onArmTapped = { arm -> selected?.let { lay(it, arm) } },
            )

            Prompt(
                view = view,
                rescueMode = rescueMode,
                onRescueMode = { rescueMode = it },
                onMove = onMove,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
        }

        Hand(
            view = view,
            selected = selected,
            playable = handHighlights(view, plays, playFors, gives, rescueMode),
            onTap = ::tapTile,
        )
    }

    if (view.phase == Phase.GAME_OVER || view.phase == Phase.MATCH_OVER) {
        GameSummary(view, onNextGame, onLeave)
    }
}

private fun playableNow(
    view: PlayerView,
    tile: Domino,
    plays: List<Move.Play>,
    playFors: List<Move.PlayFor>,
    rescueMode: RescueMode?,
): Boolean = if (view.phase == Phase.RESCUE && rescueMode == RescueMode.PLAY_FOR) {
    playFors.any { it.domino == tile }
} else {
    plays.any { it.domino == tile }
}

/** Which tiles in hand are worth lighting up right now. */
private fun handHighlights(
    view: PlayerView,
    plays: List<Move.Play>,
    playFors: List<Move.PlayFor>,
    gives: List<Move.Give>,
    rescueMode: RescueMode?,
): Set<Domino> = when {
    view.phase == Phase.RESCUE && rescueMode == RescueMode.GIVE -> gives.map { it.domino }.toSet()
    view.phase == Phase.RESCUE && rescueMode == RescueMode.PLAY_FOR -> playFors.map { it.domino }.toSet()
    view.phase == Phase.RESCUE -> emptySet()
    else -> plays.map { it.domino }.toSet()
}

@Composable
private fun ScoreRail(view: PlayerView, onLeave: () -> Unit) {
    val us = view.scores.getOrElse(view.yourTeam) { 0 }
    val them = view.scores.getOrElse(1 - view.yourTeam) { 0 }

    Row(
        modifier = Modifier.fillMaxWidth().background(Felt.Rail).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // Scores count against you here, so the lower number is the one in front.
            Text("نحن  $us", color = if (us > them) Felt.Danger else Felt.Playable,
                style = MaterialTheme.typography.titleMedium)
            Text("هم  $them", color = Felt.IvoryShade, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("جيم ${view.gameNumber}", color = Felt.IvoryShade, style = MaterialTheme.typography.labelSmall)
            Text("لـ ${view.rules.target}", color = Felt.Brass, style = MaterialTheme.typography.labelSmall)
        }
        if (view.alerts.any { it > 0 }) {
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ألرت", color = Felt.IvoryShade, style = MaterialTheme.typography.labelSmall)
                Text(
                    "${view.alerts.getOrElse(view.yourTeam) { 0 }}-${view.alerts.getOrElse(1 - view.yourTeam) { 0 }}",
                    color = Felt.Danger,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        TextButton(onClick = onLeave) { Text("خروج", color = Felt.IvoryShade) }
    }
}

@Composable
private fun OpponentRow(view: PlayerView) {
    // Seated in turn order starting from your left, so the table reads the way it sits.
    val others = (1..3).map { (view.yourSeat + it) % 4 }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        others.forEach { seat ->
            val acting = seat == view.actingSeat
            val partner = teamOf(seat) == view.yourTeam
            val stuck = seat == view.stuckSeat

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    repeat(view.handCounts.getOrElse(seat) { 0 }.coerceAtMost(8)) { index ->
                        DominoTile(
                            null,
                            Modifier.size(12.dp, 22.dp).offset(x = (-3 * index).dp),
                            faceUp = false,
                        )
                    }
                }
                Text(
                    view.seatInfo(seat)?.name.orEmpty().ifEmpty { "لاعب" },
                    color = when {
                        acting -> Felt.Brass
                        partner -> Felt.Playable
                        else -> Felt.IvoryShade
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    when {
                        stuck -> "مسكّر عليه"
                        partner -> "شريكك · ${view.handCounts.getOrElse(seat) { 0 }}"
                        else -> "${view.handCounts.getOrElse(seat) { 0 }}"
                    },
                    color = if (stuck) Felt.Danger else Felt.IvoryShade.copy(alpha = 0.7f),
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
    Column(Modifier.fillMaxWidth().background(Felt.Rail).padding(vertical = 8.dp)) {
        Text(
            text = when {
                view.phase == Phase.GAME_OVER || view.phase == Phase.MATCH_OVER -> " "
                view.isYourMove && view.board.isEmpty -> "افتح الجيم بدبل"
                view.isYourMove && view.phase == Phase.RESCUE -> "قرارك"
                view.isYourMove -> "دورك"
                else -> "${view.actingSeat?.let { view.seatInfo(it)?.name }.orEmpty()} يفكّر…"
            },
            color = if (view.isYourMove) Felt.Brass else Felt.IvoryShade,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(view.hand.size) { index ->
                val tile = view.hand[index]
                val live = tile in playable
                DominoTile(
                    domino = tile,
                    modifier = Modifier
                        .size(44.dp, if (selected == tile) 94.dp else 86.dp)
                        .clickable { onTap(tile) },
                    highlight = when {
                        selected == tile -> Felt.Brass
                        live -> Felt.Playable.copy(alpha = 0.85f)
                        else -> null
                    },
                    dimmed = view.isYourMove && !live,
                )
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

