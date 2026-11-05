package com.teera.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * choosing where to sit. So the room is drawn as the table they will sit at: you along
 * the bottom, your partner across from you, the other pair either side, and tapping an
 * empty chair moves you to it.
 */
@Composable
fun LobbyScreen(
    room: RoomInfo,
    myId: String,
    isHost: Boolean,
    offline: Boolean,
    wifiAddress: String? = null,
    onTakeSeat: (Int) -> Unit,
    onToggleBot: (seat: Int, enabled: Boolean) -> Unit,
    onSetRules: (Rules) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    val mySeat = room.seats.firstOrNull { it.playerId == myId }?.seat ?: 0
    val ready = room.seats.all { it.playerId.isNotEmpty() || it.isBot }

    WalnutRoom {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The code goes on a brass plate, big enough to read across a room.
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF120B05).copy(alpha = 0.55f))
                    .border(1.5.dp, Felt.Brass, RoundedCornerShape(12.dp))
                    .padding(horizontal = 30.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("كود الغرفة", style = MaterialTheme.typography.labelSmall, color = Felt.Muted)
                Text(room.code, fontSize = 40.sp, style = MaterialTheme.typography.displaySmall, color = Felt.Brass)
            }
            Text(
                when {
                    offline -> "لعب بدون إنترنت"
                    wifiAddress != null -> "أصحابك على نفس الواي فاي يشوفون الطاولة بالقائمة"
                    else -> "اقرأ الكود لأصحابك حتى يدخلون"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Felt.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (wifiAddress != null) {
                Text(
                    "أو يدخلون بالعنوان: $wifiAddress",
                    style = MaterialTheme.typography.labelLarge,
                    color = Felt.Brass,
                )
            }

            Spacer(Modifier.height(10.dp))

            // The table and its four chairs, laid out in rows so no chair can sit on the
            // table or run off the edge however narrow the phone.
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SeatChair(room, (mySeat + 2) % 4, myId, mySeat, isHost, onTakeSeat, onToggleBot)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SeatChair(room, (mySeat + 1) % 4, myId, mySeat, isHost, onTakeSeat, onToggleBot)
                    RoundTable(Modifier.size(118.dp))
                    SeatChair(room, (mySeat + 3) % 4, myId, mySeat, isHost, onTakeSeat, onToggleBot)
                }
                SeatChair(room, mySeat, myId, mySeat, isHost, onTakeSeat, onToggleBot)
            }

            RosetteRule(Modifier.padding(horizontal = 40.dp, vertical = 6.dp))

            Text("المباراة لـ", style = MaterialTheme.typography.labelLarge, color = Felt.Brass)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Rules.TARGETS.forEach { target ->
                    val chosen = room.rules.target == target
                    Box(
                        Modifier
                            .width(92.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (chosen) Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass, Felt.GoldDeep))
                                else Brush.verticalGradient(listOf(Color(0x99120B05), Color(0x99120B05))),
                            )
                            .border(1.dp, Felt.Brass.copy(alpha = if (chosen) 0.9f else 0.5f), RoundedCornerShape(12.dp))
                            .clickable(enabled = isHost) { onSetRules(room.rules.copy(target = target)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$target",
                            color = if (chosen) Felt.Ink else Felt.Parchment,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            Text(
                "الفريق اللي يوصل الرقم يخسر",
                style = MaterialTheme.typography.labelSmall,
                color = Felt.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(14.dp))
            if (isHost) {
                GoldButton(if (ready) "وزّع" else "ناقص لاعبين", onStart, enabled = ready)
            } else {
                Text("ننتظر صاحب الغرفة يوزّع", color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onLeave) { Text("اترك الطاولة", color = Felt.Muted) }
        }
    }
}

/** A round table in felt, with a gold rim and the opening six-six laid in the middle. */
@Composable
private fun RoundTable(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.35f), r, c.copy(y = c.y + 6f))
            drawCircle(Brush.radialGradient(listOf(Felt.TableLight, Felt.Table, Felt.Deep), c, r), r, c)
            drawCircle(Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass, Felt.GoldDeep)), r, c, style = Stroke(5f))
            drawCircle(Felt.GoldDeep.copy(alpha = 0.6f), r - 10f, c, style = Stroke(1.5f))
        }
        DominoTile(Domino(6, 6), Modifier.size(26.dp, 52.dp))
    }
}

/** One chair: who sits there and which side they are on, or an empty seat to take. */
@Composable
private fun SeatChair(
    room: RoomInfo,
    seat: Int,
    myId: String,
    mySeat: Int,
    isHost: Boolean,
    onTakeSeat: (Int) -> Unit,
    onToggleBot: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val info: Seat? = room.seats.firstOrNull { it.seat == seat }
    val human = !info?.playerId.isNullOrEmpty() && info?.isBot != true
    val bot = info?.isBot == true
    val mine = info?.playerId == myId
    val partnered = teamOf(seat) == teamOf(mySeat)
    val empty = !human && !bot

    Column(
        modifier
            .width(96.dp)
            .clickable(enabled = empty) { onTakeSeat(seat) }
            .testTag("seat-$seat"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (empty) Color(0x66120B05) else Felt.Lapis),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val ring = if (mine) 4f else 2f
                drawCircle(
                    color = Felt.Brass.copy(alpha = if (empty) 0.6f else 1f),
                    radius = size.minDimension / 2f - ring,
                    style = Stroke(
                        width = ring,
                        pathEffect = if (empty) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    ),
                )
            }
            Text(
                when {
                    empty -> "+"
                    bot -> "ب"
                    else -> info?.name.orEmpty().take(1)
                },
                color = if (empty) Felt.Brass else Felt.Parchment,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            when {
                mine -> "أنت"
                human -> info?.name.orEmpty()
                bot -> "بوت"
                else -> "كرسي فاضي"
            },
            color = if (empty) Felt.Muted else Felt.Parchment,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            if (partnered) "فريقك" else "الفريق الثاني",
            color = if (partnered) Felt.Playable else Felt.Muted,
            style = MaterialTheme.typography.labelSmall,
        )
        if (isHost && (empty || bot)) {
            Text(
                if (bot) "شيل البوت" else "حط بوت",
                color = Felt.Brass,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleBot(seat, !bot) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
