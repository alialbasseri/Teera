package com.teera.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt
import com.teera.core.Arm
import com.teera.core.Board
import com.teera.core.BoardLayout

/**
 * The table.
 *
 * The opening double sits in the middle with its four arms running off it, which is the
 * shape Teera makes in life. Where every tile goes is worked out by [BoardLayout] in the
 * shared module, so the geometry is unit tested rather than eyeballed on a phone.
 *
 * The ends a chosen tile can reach are real buttons sitting over the table rather than
 * circles painted into the canvas, so they can be tapped, described to a screen reader,
 * and clicked by a test.
 */
@Composable
fun BoardView(
    board: Board,
    modifier: Modifier = Modifier,
    liveArms: Set<Arm> = emptySet(),
    onArmTapped: (Arm) -> Unit = {},
    /** The tile just laid, and where its player sits, so it can be seen arriving. */
    arrival: Arrival? = null,
) {
    val layout = BoardLayout.compute(board)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // A laid tile travels from the hand that played it, turning as it lands.
    val flight = remember { Animatable(1f) }
    LaunchedEffect(arrival, board.size) {
        if (arrival != null) {
            flight.snapTo(0f)
            flight.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(modifier = modifier.background(Felt.Rail)) {
        if (board.isEmpty) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 11f
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Felt.TableLight, Felt.Table, Felt.Deep),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.maxDimension * 0.72f,
                    ),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f),
                )
                drawWeave()
                drawFrame(inset)
            }
            Text(
                text = "الفتحة بالدبل",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val frame = frameFor(layout, widthPx, heightPx)

        Canvas(Modifier.fillMaxSize()) {
            // Felt laid inside the cabinet: the light falls in the middle and the
            // corners drop away, so the eye goes to the line of play.
            val inset = 11f
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Felt.TableLight, Felt.Table, Felt.Deep),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.maxDimension * 0.72f,
                ),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f),
            )
            drawWeave()
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    center = Offset(size.width / 2f, size.height * 0.45f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
            drawFrame(inset)

            // The arriving tile is drawn last, so it passes over the others on its way in.
            val arriving = arrival?.let { a -> layout.placements.firstOrNull { it.domino == a.domino } }
            val ordered = layout.placements.filter { it !== arriving } + listOfNotNull(arriving)

            for (placement in ordered) {
                // A tile is drawn lying down and then turned, so the face nearer the
                // middle of the table has to end up pointing inwards.
                val inward = placement.arm == Arm.WEST || placement.arm == Arm.NORTH
                val home = frame.pointOf(placement.centreX, placement.centreY)
                val t = if (placement === arriving) flight.value else 1f
                val from = arrival?.from?.anchor(size.width, size.height, rtl) ?: home
                val spin = if (arrival?.from == SeatSpot.BOTTOM) -38f else 38f

                drawDominoAt(
                    leftFace = if (inward) placement.outer else placement.inner,
                    rightFace = if (inward) placement.inner else placement.outer,
                    centre = Offset(from.x + (home.x - from.x) * t, from.y + (home.y - from.y) * t),
                    width = BoardLayout.TILE_LENGTH * frame.unit * 0.97f,
                    height = BoardLayout.TILE_WIDTH * frame.unit * 0.97f,
                    rotationDegrees = placement.rotationDegrees + spin * (1f - t),
                    highlight = when {
                        placement.isCentre -> Felt.Brass.copy(alpha = 0.75f)
                        placement === arriving && t >= 1f -> Felt.Brass.copy(alpha = 0.45f)
                        else -> null
                    },
                    // Lifted while it travels, so it reads as in the hand and then down.
                    scale = 1f + 0.28f * (1f - t),
                )
            }
        }

        for (arm in liveArms) {
            val end = layout.placements.lastOrNull { it.arm == arm } ?: continue
            val (x, y) = armTarget(layout, arm)
            val centre = frame.pointOf(x, y)
            val diameter = with(density) { (frame.unit * 1.05f).toDp() }.coerceAtLeast(44.dp)

            Box(
                modifier = Modifier
                    // Absolute, not offset: the tiles are painted left to right whatever the
                    // phone's language, and an Arabic layout would otherwise mirror the
                    // targets onto the wrong side of the table.
                    .absoluteOffset {
                        androidx.compose.ui.unit.IntOffset(
                            (centre.x - with(density) { diameter.toPx() } / 2f).toInt(),
                            (centre.y - with(density) { diameter.toPx() } / 2f).toInt(),
                        )
                    }
                    .size(diameter)
                    .clip(CircleShape)
                    .background(Felt.Playable.copy(alpha = 0.18f))
                    .border(2.dp, Felt.Playable, CircleShape)
                    .clickable { onArmTapped(arm) }
                    .testTag("arm-${arm.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${end.outer}",
                    color = Felt.Playable,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** How the table in tile units maps onto the pixels we have. */
private class Frame(val unit: Float, val originX: Float, val originY: Float) {
    fun pointOf(x: Float, y: Float) = Offset(originX + x * unit, originY + y * unit)
}

private const val TARGET_REACH = BoardLayout.TILE_LENGTH / 2f + 0.5f

private fun frameFor(layout: BoardLayout.Layout, width: Float, height: Float): Frame {
    // Leave room for the targets, which sit a little beyond the ends of the arms.
    val padding = 14f
    val unit = minOf(
        (width - padding * 2) / (layout.width + TARGET_REACH * 2),
        (height - padding * 2) / (layout.height + TARGET_REACH * 2),
    ).coerceAtLeast(4f)

    return Frame(
        unit = unit,
        originX = (width - layout.width * unit) / 2f - layout.minX * unit,
        originY = (height - layout.height * unit) / 2f - layout.minY * unit,
    )
}

private fun armTarget(layout: BoardLayout.Layout, arm: Arm): Pair<Float, Float> {
    val last = layout.placements.lastOrNull { it.arm == arm } ?: return 0f to 0f
    return when (arm) {
        Arm.EAST -> (last.centreX + TARGET_REACH) to last.centreY
        Arm.WEST -> (last.centreX - TARGET_REACH) to last.centreY
        Arm.NORTH -> last.centreX to (last.centreY - TARGET_REACH)
        Arm.SOUTH -> last.centreX to (last.centreY + TARGET_REACH)
    }
}

/**
 * Where a player sits relative to you on screen: you along the bottom, the other three
 * along the top in the order the seats are drawn.
 */
enum class SeatSpot {
    BOTTOM, TOP_START, TOP_MIDDLE, TOP_END;

    /** The point on the table nearest that seat. The seat row flips in a right-to-left layout. */
    fun anchor(width: Float, height: Float, rtl: Boolean): Offset = when (this) {
        BOTTOM -> Offset(width / 2f, height * 0.98f)
        TOP_MIDDLE -> Offset(width / 2f, height * 0.02f)
        TOP_START -> Offset(if (rtl) width * 0.83f else width * 0.17f, height * 0.02f)
        TOP_END -> Offset(if (rtl) width * 0.17f else width * 0.83f, height * 0.02f)
    }
}

/** A tile that has just gone down, and where it came from. */
data class Arrival(val domino: com.teera.core.Domino, val from: SeatSpot)
