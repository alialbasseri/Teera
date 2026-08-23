package com.dominoes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominoes.app.ui.theme.Felt
import com.dominoes.core.Domino
import com.dominoes.core.End
import com.dominoes.core.Move
import com.dominoes.core.Phase
import com.dominoes.core.PlayerView
import com.dominoes.core.RoundEndReason

/**
 * The table.
 *
 * Everything drawn here comes from the [PlayerView] the referee sent, including which
 * moves are legal, so the screen cannot offer a move the server would refuse.
 */
@Composable
fun TableScreen(
    view: PlayerView,
    onMove: (Move) -> Unit,
    onNextRound: () -> Unit,
    onLeave: () -> Unit,
) {
    var selected by remember(view.roundNumber) { mutableStateOf<Domino?>(null) }

    val plays = view.legalMoves.filterIsInstance<Move.Play>()
    val playableTiles = plays.map { it.domino }.toSet()
    val endsFor = { tile: Domino -> plays.filter { it.domino == tile }.map { it.end }.toSet() }

    Column(Modifier.fillMaxSize().background(Felt.Deep)) {

        ScoreBar(view, onLeave)

        OpponentRow(view)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            BoardView(
                board = view.board,
                modifier = Modifier.fillMaxSize(),
                liveEnds = selected?.let(endsFor) ?: emptySet(),
            )

            if (view.isYourTurn && plays.isEmpty()) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                    StuckControls(view, onMove)
                }
            }
        }

        HandRow(
            view = view,
            selected = selected,
            playable = playableTiles,
            onSelect = { tile ->
                val ends = endsFor(tile)
                when {
                    !view.isYourTurn -> selected = tile
                    ends.size == 1 -> { onMove(Move.Play(tile, ends.first())); selected = null }
                    ends.size > 1 -> selected = if (selected == tile) null else tile
                    else -> selected = tile
                }
            },
        )

        // A bone that fits at both ends needs the player to say which.
        val choice = selected?.takeIf { view.isYourTurn && endsFor(it).size > 1 }
        if (choice != null) {
            EndChooser(
                left = view.board.leftEnd,
                right = view.board.rightEnd,
                onPick = { end -> onMove(Move.Play(choice, end)); selected = null },
                onCancel = { selected = null },
            )
        }
    }

    if (view.phase == Phase.ROUND_OVER || view.phase == Phase.MATCH_OVER) {
        RoundSummary(view, onNextRound, onLeave)
    }
}

@Composable
private fun ScoreBar(view: PlayerView, onLeave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Felt.Rail)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (view.rules.partners) "Us ${view.scores.getOrElse(view.yourSeat % 2) { 0 }}"
                else "You ${view.scores.getOrElse(view.yourSeat) { 0 }}",
                color = Felt.Brass,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (view.rules.partners) "Them ${view.scores.getOrElse((view.yourSeat + 1) % 2) { 0 }}"
                else view.scores.withIndex()
                    .filter { it.index != view.yourSeat }
                    .joinToString(" · ") { "${it.value}" },
                color = Felt.IvoryShade,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROUND ${view.roundNumber}", color = Felt.IvoryShade, style = MaterialTheme.typography.labelSmall)
            Text("to ${view.rules.targetScore}", color = Felt.IvoryShade, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("BONEYARD", color = Felt.IvoryShade, style = MaterialTheme.typography.labelSmall)
            Text("${view.boneyardCount}", color = Felt.Parchment, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onLeave) { Text("Exit", color = Felt.IvoryShade) }
    }
}

/** The other seats, with a face-down fan showing how close each is to going out. */
@Composable
private fun OpponentRow(view: PlayerView) {
    val others = view.seats.filter { it.seat != view.yourSeat }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        others.forEach { seat ->
            val onTurn = seat.seat == view.turn && view.phase == Phase.IN_ROUND
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    val count = view.handCounts.getOrElse(seat.seat) { 0 }
                    repeat(count.coerceAtMost(7)) { index ->
                        DominoTile(
                            null,
                            Modifier
                                .size(13.dp, 24.dp)
                                .offset(x = (-4 * index).dp),
                            faceUp = false,
                        )
                    }
                }
                Text(
                    seat.name,
                    color = if (onTurn) Felt.Brass else Felt.IvoryShade,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "${view.handCounts.getOrElse(seat.seat) { 0 }}" +
                        if (view.rules.partners && seat.seat % 2 == view.yourSeat % 2) " · partner" else "",
                    color = if (onTurn) Felt.Brass else Felt.IvoryShade.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun HandRow(
    view: PlayerView,
    selected: Domino?,
    playable: Set<Domino>,
    onSelect: (Domino) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Felt.Rail)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = when {
                view.phase != Phase.IN_ROUND -> " "
                view.isYourTurn && playable.isEmpty() -> "No move — draw or pass"
                view.isYourTurn -> "Your turn"
                else -> "${view.seatInfo(view.turn)?.name ?: "Someone"} is thinking…"
            },
            color = if (view.isYourTurn) Felt.Brass else Felt.IvoryShade,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(view.hand.size) { index ->
                val tile = view.hand[index]
                val isPlayable = tile in playable
                DominoTile(
                    domino = tile,
                    modifier = Modifier
                        .size(46.dp, if (selected == tile) 96.dp else 88.dp)
                        .clickable { onSelect(tile) },
                    highlight = when {
                        selected == tile -> Felt.Brass
                        isPlayable && view.isYourTurn -> Felt.Playable.copy(alpha = 0.8f)
                        else -> null
                    },
                    dimmed = view.isYourTurn && !isPlayable,
                )
            }
        }
    }
}

@Composable
private fun StuckControls(view: PlayerView, onMove: (Move) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (view.legalMoves.contains(Move.Draw)) {
            Button(
                onClick = { onMove(Move.Draw) },
                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
            ) {
                Text("Draw (${view.boneyardCount})")
            }
        }
        if (view.legalMoves.contains(Move.Pass)) {
            Button(onClick = { onMove(Move.Pass) }) { Text("Pass") }
        }
    }
}

@Composable
private fun EndChooser(left: Int?, right: Int?, onPick: (End) -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Felt.Deep)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onPick(End.LEFT) }) { Text("Play on the $left end") }
        Button(onClick = { onPick(End.RIGHT) }) { Text("Play on the $right end") }
        TextButton(onClick = onCancel) { Text("Cancel", color = Felt.IvoryShade) }
    }
}

@Composable
private fun RoundSummary(view: PlayerView, onNextRound: () -> Unit, onLeave: () -> Unit) {
    val result = view.lastRound
    val matchOver = view.phase == Phase.MATCH_OVER
    val myKey = if (view.rules.partners) view.yourSeat % 2 else view.yourSeat
    val wonMatch = view.matchWinner == myKey

    AlertDialog(
        onDismissRequest = {},
        containerColor = Felt.Table,
        title = {
            Text(
                when {
                    matchOver && wonMatch -> "You win the match"
                    matchOver -> "Match over"
                    result?.winnerSeat == view.yourSeat -> "You went out"
                    result?.reason == RoundEndReason.BLOCKED -> "Blocked"
                    else -> "Round over"
                },
                color = Felt.Brass,
            )
        },
        text = {
            Column {
                if (result != null) {
                    val winnerName = result.winnerSeat?.let { view.seatInfo(it)?.name } ?: "Nobody"
                    Text("$winnerName took ${result.points} points", color = Felt.Parchment)
                    Spacer(Modifier.height(8.dp))
                    result.handPips.forEachIndexed { seat, pips ->
                        Text(
                            "${view.seatInfo(seat)?.name ?: "Seat ${seat + 1}"}: $pips spots left",
                            color = Felt.IvoryShade,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    view.scores.mapIndexed { key, score ->
                        if (view.rules.partners) "Team ${if (key == 0) "A" else "B"}: $score" else "Seat ${key + 1}: $score"
                    }.joinToString("   "),
                    color = Felt.Parchment,
                )
            }
        },
        confirmButton = {
            if (matchOver) {
                TextButton(onClick = onLeave) { Text("Back to the menu", color = Felt.Brass) }
            } else {
                TextButton(onClick = onNextRound) { Text("Deal the next round", color = Felt.Brass) }
            }
        },
    )
}
