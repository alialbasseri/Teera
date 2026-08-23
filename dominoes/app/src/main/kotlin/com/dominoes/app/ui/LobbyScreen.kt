package com.dominoes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dominoes.app.ui.theme.Felt
import com.dominoes.core.Domino
import com.dominoes.core.RoomInfo

@Composable
fun LobbyScreen(
    room: RoomInfo,
    myId: String,
    isHost: Boolean,
    offline: Boolean,
    onToggleBot: (seat: Int, enabled: Boolean) -> Unit,
    onEditRules: () -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Felt.TableLight, Felt.Deep)))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ROOM CODE", style = MaterialTheme.typography.labelSmall, color = Felt.IvoryShade)
        Text(
            room.code,
            style = MaterialTheme.typography.displaySmall,
            color = Felt.Brass,
        )
        Text(
            if (offline) "Playing offline" else "Read this out to your friends",
            style = MaterialTheme.typography.bodyMedium,
            color = Felt.IvoryShade,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Felt.Deep.copy(alpha = 0.55f)),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(room.rules.name, style = MaterialTheme.typography.titleMedium, color = Felt.Parchment)
                Text(
                    rulesSummary(room.rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Felt.IvoryShade,
                )
                if (isHost) {
                    TextButton(onClick = onEditRules) { Text("Change the rules", color = Felt.Brass) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            room.seats.forEach { seat ->
                val occupied = seat.playerId.isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Felt.Deep.copy(alpha = 0.5f))
                        .border(
                            width = if (seat.playerId == myId) 2.dp else 0.dp,
                            color = if (seat.playerId == myId) Felt.Brass else Felt.Deep,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DominoTile(
                        Domino(seat.seat.coerceAtMost(6), 6),
                        Modifier.size(22.dp, 42.dp),
                        faceUp = occupied,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (occupied) seat.name else "Empty seat",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (occupied) Felt.Parchment else Felt.IvoryShade.copy(alpha = 0.6f),
                        )
                        val label = when {
                            seat.isBot -> "Bot"
                            !seat.connected && occupied -> "Reconnecting…"
                            room.rules.partners -> "Team ${if (seat.seat % 2 == 0) "A" else "B"}"
                            else -> "Seat ${seat.seat + 1}"
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Felt.IvoryShade)
                    }
                    if (isHost && !seat.isBot && !occupied) {
                        OutlinedButton(onClick = { onToggleBot(seat.seat, true) }) { Text("Add bot") }
                    } else if (isHost && seat.isBot) {
                        TextButton(onClick = { onToggleBot(seat.seat, false) }) { Text("Remove") }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        val ready = room.seats.all { it.playerId.isNotEmpty() || it.isBot }
        if (isHost) {
            Button(
                onClick = onStart,
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
            ) {
                Text(if (ready) "Deal" else "Waiting for players…", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Text(
                "Waiting for the host to deal",
                style = MaterialTheme.typography.bodyMedium,
                color = Felt.IvoryShade,
            )
        }

        TextButton(onClick = onLeave) { Text("Leave the table", color = Felt.IvoryShade) }
    }
}
