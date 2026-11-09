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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
    /** Sends the room code through WhatsApp or whatever the phone has; null hides it. */
    onShare: ((String) -> Unit)? = null,
    /** Table talk; null when the other chairs can only hold bots. */
    talk: Talk? = null,
    avatarFor: @Composable (Seat?, androidx.compose.ui.unit.Dp) -> Unit = { seat, size -> Initial(seat?.name.orEmpty(), size) },
    /** The host's say: sending somebody out, and locking the room to the invited. */
    onKick: ((String) -> Unit)? = null,
    onLock: ((Boolean) -> Unit)? = null,
) {
    val mySeat = room.seats.firstOrNull { it.playerId == myId }?.seat ?: 0
    val ready = room.seats.all { it.playerId.isNotEmpty() || it.isBot }

    WalnutRoom {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // One screen, fixed: nothing scrolls. The chairs round the table take whatever
        // height is left and shrink to fit it, so the buttons are always on screen.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                Text(room.code, fontSize = 40.sp, style = MaterialTheme.typography.headlineLarge, color = Felt.Brass)
            }
            val hint = when {
                offline -> "لعب بدون إنترنت"
                wifiAddress != null -> "أصحابك على نفس الواي فاي يشوفون الطاولة بالقائمة"
                else -> ""
            }
            if (hint.isNotEmpty()) Text(
                when {
                    offline -> "لعب بدون إنترنت"
                    wifiAddress != null -> "أصحابك على نفس الواي فاي يشوفون الطاولة بالقائمة"
                    else -> ""
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
            // A full table: newcomers watch, and can sit down if a chair comes free.
            if (room.spectators.any { it.playerId == myId }) {
                Text(
                    "إنت تتفرّج: الكراسي مليانة. إذا فرغ كرسي دوس عليه وتگعد.",
                    color = Felt.GoldLit,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (room.spectators.isNotEmpty()) {
                Text(
                    "يتفرجون: " + room.spectators.joinToString("، ") { it.name },
                    color = Felt.Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Sharing the code and opening the mic share one row: the chairs need the height.
            val sharing = onShare != null && !offline && wifiAddress == null
            if (sharing || talk != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (sharing) BrassButton("دزّ اللنك", { onShare?.invoke(room.code) })
                    if (talk != null) {
                        Spacer(Modifier.width(10.dp))
                        MicButton(talk, size = 40.dp)
                        if (!sharing) {
                            Text(
                                if (talk.micOn) "المايك مفتوح، تكلّم" else "افتح المايك وسولف وياهم",
                                color = if (talk.micOn) Felt.Playable else Felt.Muted,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

          }

            // The table and its four chairs, laid out in rows so no chair can sit on the
            // table or run off the edge however narrow the phone; shrunk to the height left.
            FitHeight(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SeatChair(room, (mySeat + 2) % 4, myId, mySeat, isHost, onTakeSeat, onToggleBot, speaking = talk.speakingSeats(myId, room), avatar = avatarFor, onKick = onKick)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SeatChair(room, (mySeat + 1) % 4, myId, mySeat, isHost, onTakeSeat, onToggleBot, speaking = talk.speakingSeats(myId, room), avatar = avatarFor, onKick = onKick)
                    RoundTable(Modifier.size(118.dp))
                    SeatChair(room, (mySeat + 3) % 4, myId, mySeat, isHost, onTakeSeat, onToggleBot, speaking = talk.speakingSeats(myId, room), avatar = avatarFor, onKick = onKick)
                }
                SeatChair(room, mySeat, myId, mySeat, isHost, onTakeSeat, onToggleBot, speaking = talk.speakingSeats(myId, room), avatar = avatarFor, onKick = onKick)
            }
            }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RosetteRule(Modifier.padding(horizontal = 40.dp, vertical = 2.dp))

            room.tournament?.let { label ->
                Text("🏆 $label", style = MaterialTheme.typography.titleSmall, color = Felt.GoldLit, textAlign = TextAlign.Center)
                Text("كل فريق يگعد بالكراسي اللي عليها اسمه", style = MaterialTheme.typography.labelSmall, color = Felt.Muted)
                Spacer(Modifier.height(6.dp))
            }
            Text("المباراة لـ", style = MaterialTheme.typography.labelLarge, color = Felt.Brass)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // To a score (the team reaching it loses), or a set number of games.
                (Rules.TARGETS.map { it to "$it" } + (0 to "${Rules.GAMES} گيمات")).forEach { (target, label) ->
                    val chosen = if (target == 0) room.rules.byGames else !room.rules.byGames && room.rules.target == target
                    val pick = if (target == 0) room.rules.copy(games = Rules.GAMES) else room.rules.copy(target = target, games = 0)
                    Box(
                        Modifier
                            .width(if (target == 0) 104.dp else 84.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (chosen) Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass, Felt.GoldDeep))
                                else Brush.verticalGradient(listOf(Color(0x99120B05), Color(0x99120B05))),
                            )
                            .border(1.dp, Felt.Brass.copy(alpha = if (chosen) 0.9f else 0.5f), RoundedCornerShape(12.dp))
                            .clickable(enabled = isHost) { onSetRules(pick) }
                            .testTag("rules-$label"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (chosen) Felt.Ink else Felt.Parchment,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            Text(
                if (room.rules.byGames) "بعد ${Rules.GAMES} گيمات، الفريق الأكثر نقاط يخسر" else "الفريق اللي يوصل الرقم يخسر",
                style = MaterialTheme.typography.labelSmall,
                color = Felt.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            // The host can close the room to everybody but the people they invite.
            if (isHost && onLock != null) {
                Text(
                    if (room.locked) "🔒 مقفولة: بس اللي تعزمه يدخل" else "🔓 مفتوحة: يدخل أي واحد عنده اللنك",
                    color = if (room.locked) Felt.GoldLit else Felt.Muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onLock(!room.locked) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("lock"),
                )
            } else if (room.locked) {
                Text("🔒 الغرفة مقفولة", color = Felt.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(10.dp))
            if (isHost) {
                GoldButton(if (ready) "وزّع" else "ناقص لاعبين", onStart, enabled = ready)
            } else {
                Text("ننتظر صاحب الغرفة يوزّع", color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onLeave) { Text("اترك الطاولة", color = Felt.Muted) }
          }
        }
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
    speaking: Set<Int> = emptySet(),
    onKick: ((String) -> Unit)? = null,
    avatar: @Composable (Seat?, androidx.compose.ui.unit.Dp) -> Unit = { seat, size -> Initial(seat?.name.orEmpty(), size) },
) {
    val info: Seat? = room.seats.firstOrNull { it.seat == seat }
    val human = !info?.playerId.isNullOrEmpty() && info?.isBot != true
    val bot = info?.isBot == true
    val mine = info?.playerId == myId
    val partnered = teamOf(seat) == teamOf(mySeat)
    val empty = !human && !bot

    Column(
        modifier
            .width(104.dp)
            .clickable(enabled = empty) { onTakeSeat(seat) }
            .testTag("seat-$seat"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The ring is drawn past the chair's edge rather than making the chair bigger.
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        if (seat in speaking) SpeakingHalo(60.dp, Modifier.requiredSize(60.dp))
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
            if (empty || bot) {
                Text(
                    if (empty) "+" else "ب",
                    color = if (empty) Felt.Brass else Felt.Parchment,
                    style = MaterialTheme.typography.titleLarge,
                )
            } else {
                Box(Modifier.size(46.dp).clip(CircleShape), contentAlignment = Alignment.Center) { avatar(info, 46.dp) }
            }
        }
        }
        Text(
            when {
                mine -> "أنت"
                human -> info?.name.orEmpty()
                bot -> "بوت"
                else -> "فاضي"
            },
            color = if (empty) Felt.Muted else Felt.Parchment,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            // A tournament match names the teams; otherwise it is yours or theirs.
            room.teamNames.getOrNull(seat % 2) ?: if (partnered) "فريقك" else "الخصم",
            color = if (partnered) Felt.Playable else Felt.Muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        // The host can send somebody else out of their chair.
        if (isHost && onKick != null && info != null && !bot && !empty && info.playerId != myId) {
            Text(
                "طلّع",
                color = Felt.Danger,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onKick(info.playerId) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("kick-${info.playerId}"),
            )
        }
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

/**
 * Lays out its content at full size, then shrinks it (from the middle) to fit the height
 * it is given. Taps land where they look, since the shrinking is a drawing layer.
 */
@Composable
private fun FitHeight(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity),
        )
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        val k = if (placeable.height > height && placeable.height > 0) height.toFloat() / placeable.height else 1f
        layout(width, height) {
            placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                scaleX = k
                scaleY = k
            }
        }
    }
}
