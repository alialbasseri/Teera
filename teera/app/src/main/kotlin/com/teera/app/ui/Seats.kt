package com.teera.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt
import com.teera.core.PlayerView
import com.teera.core.teamOf

/** Where a seat sits round the table, seen from this player's chair at the bottom. */
enum class Side { LEFT, TOP, RIGHT }

/**
 * Someone else at the table, where they would actually be sitting: their picture, their
 * name, what is happening to them, and their bones lying face down in front of them -
 * counted by eye, the way you would across a real table, rather than as a number.
 */
@Composable
fun SeatAtTable(
    view: PlayerView,
    seat: Int,
    side: Side,
    speaking: Boolean,
    modifier: Modifier = Modifier,
    avatar: @Composable (size: Dp) -> Unit = { size -> Initial(view.seatInfo(seat)?.name.orEmpty(), size) },
) {
    val info = view.seatInfo(seat)
    val name = when {
        info == null || info.name.isBlank() -> "لاعب"
        info.isBot -> "بوت"
        else -> info.name.trim()
    }
    val acting = seat == view.actingSeat
    val partner = teamOf(seat) == view.yourTeam
    val stuck = seat == view.stuckSeat
    val dropped = view.isDropped(seat)
    val tiles = view.handCounts.getOrElse(seat) { 0 }
    val status = when {
        dropped -> "انقطع"
        stuck -> "گلى"
        acting -> "دوره"
        // A watcher is nobody's partner: the one across the table is theirs.
        partner -> if (view.spectating) "شريكه" else "شريكك"
        else -> ""
    }
    val statusColour = when {
        dropped || stuck -> Felt.Danger
        acting -> Felt.Brass
        partner -> Felt.Playable
        else -> Felt.Muted
    }

    val face = @Composable {
        Box(contentAlignment = Alignment.Center) {
            if (speaking) SpeakingHalo(50.dp)
            if (acting) TurnRing(42.dp)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(if (acting) 2.dp else 1.dp, Felt.Brass.copy(alpha = if (acting) 1f else 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { avatar(40.dp) }
        }
    }
    val label = @Composable { align: TextAlign ->
        Column(horizontalAlignment = if (side == Side.TOP) Alignment.Start else Alignment.CenterHorizontally) {
            Text(
                name,
                color = if (acting) Felt.Brass else Felt.Parchment,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
            )
            if (status.isNotEmpty()) {
                Text(status, color = statusColour, style = MaterialTheme.typography.labelSmall, textAlign = align)
            }
        }
    }

    if (side == Side.TOP) {
        Row(
            modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            face()
            Spacer(Modifier.width(8.dp))
            label(TextAlign.Start)
            Spacer(Modifier.width(12.dp))
            TileBacks(tiles, lying = false)
        }
    } else {
        Column(
            modifier.width(SIDE_WIDTH).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            face()
            Spacer(Modifier.height(3.dp))
            label(TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            TileBacks(tiles, lying = true)
        }
    }
}

/** How wide a seat on the side of the table is; the board fits between two of them. */
val SIDE_WIDTH = 62.dp

/** The breathing ring round whoever the table is waiting on. */
@Composable
private fun TurnRing(size: Dp) {
    val pulse = rememberInfiniteTransition(label = "turn")
    val ring by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring",
    )
    Box(
        Modifier
            .size(size)
            .scale(ring)
            .alpha((1.6f - ring).coerceIn(0f, 1f))
            .border(2.dp, Felt.Brass, CircleShape),
    )
}

/**
 * A player's bones, backs up. Standing in a row for the partner across the table, lying
 * in a stack for the players either side: what you would see from your chair.
 */
@Composable
fun TileBacks(count: Int, lying: Boolean) {
    if (count <= 0) return
    // A long hand folds into a second row or column rather than running off the table.
    val perLine = 7
    val lines = (count + perLine - 1) / perLine
    val long = 15.dp
    val short = 8.dp
    val gap = 2.dp
    val across = if (lying) long else short
    val along = if (lying) short else long
    val inLine = minOf(count, perLine)
    val width = if (lying) across * lines + gap * (lines - 1) else (across + gap) * inLine - gap
    val height = if (lying) (along + gap) * inLine - gap else along * lines + gap * (lines - 1)
    Canvas(Modifier.size(width, height)) {
        val a = across.toPx()
        val b = along.toPx()
        val g = gap.toPx()
        for (i in 0 until count) {
            val line = i / perLine
            val at = i % perLine
            val topLeft = if (lying) Offset(line * (a + g), at * (b + g)) else Offset(at * (a + g), line * (b + g))
            val size = Size(a, b)
            drawRoundRect(Felt.TileBack, topLeft, size, CornerRadius(2.dp.toPx()))
            drawRoundRect(
                Felt.Brass.copy(alpha = 0.55f), topLeft, size, CornerRadius(2.dp.toPx()),
                style = Stroke(1.dp.toPx() * 0.8f),
            )
        }
    }
}

/** A picture-less player: the first letter of their name on lapis. */
@Composable
fun Initial(name: String, size: Dp) {
    Box(Modifier.size(size).background(Felt.Lapis), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).ifEmpty { "؟" },
            color = Felt.Parchment,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
