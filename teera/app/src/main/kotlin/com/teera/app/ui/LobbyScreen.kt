package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino
import com.teera.core.RoomInfo
import com.teera.core.Rules
import com.teera.core.Seat
import com.teera.core.teamOf

/**
 * The room before the deal.
 *
 * Teera is four players in two pairs, and the players sort the pairs out themselves by
 * choosing where to sit, so the seats are drawn as a table rather than a list: partners
 * face each other, and tapping an empty chair moves you to it.
 */
@Composable
fun LobbyScreen(
    room: RoomInfo,
    myId: String,
    isHost: Boolean,
    offline: Boolean,
    onTakeSeat: (Int) -> Unit,
    onToggleBot: (seat: Int, enabled: Boolean) -> Unit,
    onSetRules: (Rules) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    val mySeat = room.seats.firstOrNull { it.playerId == myId }?.seat ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Felt.TableLight, Felt.Deep)))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("كود الغرفة", style = MaterialTheme.typography.labelSmall, color = Felt.IvoryShade)
        Text(room.code, style = MaterialTheme.typography.displaySmall, color = Felt.Brass)
        Text(
            if (offline) "لعب بدون إنترنت" else "اقرأه لأصحابك حتى يدخلون",
            style = MaterialTheme.typography.bodyMedium,
            color = Felt.IvoryShade,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        // The table itself: seat 0 at the bottom with its partner across, opponents either side.
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            val across = (mySeat + 2) % 4
            val left = (mySeat + 1) % 4
            val right = (mySeat + 3) % 4

            SeatChip(room, across, myId, isHost, onTakeSeat, onToggleBot, Modifier.align(Alignment.TopCenter))
            SeatChip(room, left, myId, isHost, onTakeSeat, onToggleBot, Modifier.align(Alignment.CenterStart))
            SeatChip(room, right, myId, isHost, onTakeSeat, onToggleBot, Modifier.align(Alignment.CenterEnd))
            SeatChip(room, mySeat, myId, isHost, onTakeSeat, onToggleBot, Modifier.align(Alignment.BottomCenter))

            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DominoTile(Domino(6, 6), Modifier.size(26.dp, 50.dp))
                    DominoTile(Domino(0, 1), Modifier.size(26.dp, 50.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "أنت وشريكك مقابل بعض",
                    style = MaterialTheme.typography.labelSmall,
                    color = Felt.IvoryShade.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text("المباراة لـ", style = MaterialTheme.typography.labelSmall, color = Felt.Brass)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Rules.TARGETS.forEach { target ->
                val chosen = room.rules.target == target
                Button(
                    onClick = { if (isHost) onSetRules(room.rules.copy(target = target)) },
                    enabled = isHost,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (chosen) Felt.Brass else Felt.Deep,
                        contentColor = if (chosen) Felt.Ink else Felt.Parchment,
                        disabledContainerColor = if (chosen) Felt.Brass else Felt.Deep,
                        disabledContentColor = if (chosen) Felt.Ink else Felt.Parchment,
                    ),
                ) { Text("$target") }
            }
        }
        Text(
            "الفريق اللي يوصل الرقم يخسر",
            style = MaterialTheme.typography.labelSmall,
            color = Felt.IvoryShade.copy(alpha = 0.7f),
        )

        Spacer(Modifier.weight(1f))

        val ready = room.seats.all { it.playerId.isNotEmpty() || it.isBot }
        if (isHost) {
            Button(
                onClick = onStart,
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
            ) {
                Text(
                    if (ready) "وزّع" else "ناقص لاعبين",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Text("ننتظر صاحب الغرفة يوزّع", color = Felt.IvoryShade, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onLeave) { Text("اترك الطاولة", color = Felt.IvoryShade) }
    }
}

@Composable
private fun SeatChip(
    room: RoomInfo,
    seat: Int,
    myId: String,
    isHost: Boolean,
    onTakeSeat: (Int) -> Unit,
    onToggleBot: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val info: Seat? = room.seats.firstOrNull { it.seat == seat }
    val taken = !info?.playerId.isNullOrEmpty()
    val mine = info?.playerId == myId
    val mySeat = room.seats.firstOrNull { it.playerId == myId }?.seat
    val partnered = mySeat != null && teamOf(seat) == teamOf(mySeat)

    Column(
        modifier = modifier
            .width(126.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (partnered) Felt.Table else Felt.Deep.copy(alpha = 0.6f))
            .border(
                width = if (mine) 2.dp else 0.dp,
                color = if (mine) Felt.Brass else Felt.Deep,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = !taken && !mine) {
                if (info?.isBot == true) onToggleBot(seat, false) else onTakeSeat(seat)
            }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                mine -> "أنت"
                taken -> info?.name.orEmpty()
                info?.isBot == true -> "بوت"
                else -> "كرسي فاضي"
            },
            color = if (taken || info?.isBot == true) Felt.Parchment else Felt.IvoryShade.copy(alpha = 0.6f),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (partnered) "فريقك" else "الفريق الثاني",
            color = if (partnered) Felt.Playable else Felt.IvoryShade.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
        if (isHost && !taken) {
            if (info?.isBot == true) {
                TextButton(onClick = { onToggleBot(seat, false) }) { Text("شيل", color = Felt.Brass) }
            } else {
                OutlinedButton(onClick = { onToggleBot(seat, true) }) { Text("بوت") }
            }
        }
    }
}
