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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
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
    // Which way the cross was drawn last frame, so it only turns for a clear gain.
    val turnedLast = remember { mutableStateOf(false) }
    LaunchedEffect(board.isEmpty) { if (board.isEmpty) turnedLast.value = false }

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
                text = "الفتحة بالچفت",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val frame = frameFor(layout, widthPx, heightPx, wasTurned = turnedLast.value)
        SideEffect { turnedLast.value = frame.turned }

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
                    rotationDegrees = placement.rotationDegrees + frame.twist + spin * (1f - t),
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
                    // Absolute, not offset, and from the top-left corner: the tiles are
                    // painted left to right whatever the phone's language. Right to left,
                    // "start" is the right edge, and the targets landed off the table.
                    .align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
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

/**
 * How the table in tile units maps onto the pixels we have.
 *
 * When [turned], the whole cross is drawn a quarter turn round. The four arms are alike,
 * so nothing about the game changes; it only lets a cross that has grown wide use the
 * height of a phone held upright instead of shrinking to fit its width.
 */
internal class Frame(val unit: Float, val originX: Float, val originY: Float, val turned: Boolean) {
    fun pointOf(x: Float, y: Float): Offset =
        if (turned) Offset(originX - y * unit, originY + x * unit)
        else Offset(originX + x * unit, originY + y * unit)

    /** Extra rotation every tile takes when the cross is turned. */
    val twist: Float get() = if (turned) 90f else 0f
}

private const val TARGET_REACH = BoardLayout.TILE_LENGTH / 2f + 0.5f

/** How large a tile may get, as a share of the table's short side: a lone double stays a tile. */
private const val MAX_UNIT_SHARE = 1f / 7.5f

/** Bigger tiles from turning the cross only count if they are bigger by at least this much. */
private const val TURN_GAIN = 1.12f

internal fun frameFor(
    layout: BoardLayout.Layout,
    width: Float,
    height: Float,
    wasTurned: Boolean = false,
): Frame {
    // Leave room for the targets, which sit a little beyond the ends of the arms.
    val padding = 14f
    fun fit(across: Float, down: Float) = minOf(
        (width - padding * 2) / (across + TARGET_REACH * 2),
        (height - padding * 2) / (down + TARGET_REACH * 2),
    )
    val straight = fit(layout.width, layout.height)
    val sideways = fit(layout.height, layout.width)

    // Turn only for a clear gain, and turn back only for a clear loss, so a growing cross
    // does not flip from one tile to the next.
    val turned = if (wasTurned) sideways * TURN_GAIN >= straight else sideways >= straight * TURN_GAIN
    val unit = (if (turned) sideways else straight)
        .coerceAtMost(minOf(width, height) * MAX_UNIT_SHARE)
        .coerceAtLeast(4f)

    return if (turned) {
        // Turned a quarter: x runs from -maxY to -minY and y from minX to maxX.
        Frame(
            unit = unit,
            originX = (width - layout.height * unit) / 2f + layout.maxY * unit,
            originY = (height - layout.width * unit) / 2f - layout.minX * unit,
            turned = true,
        )
    } else {
        Frame(
            unit = unit,
            originX = (width - layout.width * unit) / 2f - layout.minX * unit,
            originY = (height - layout.height * unit) / 2f - layout.minY * unit,
            turned = false,
        )
    }
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
    BOTTOM, SIDE_START, TOP_MIDDLE, SIDE_END;

    /**
     * The point on the table nearest that seat: the partner across the top, the other two
     * at either side, the next player on the reading-start side.
     */
    fun anchor(width: Float, height: Float, rtl: Boolean): Offset = when (this) {
        BOTTOM -> Offset(width / 2f, height * 0.98f)
        TOP_MIDDLE -> Offset(width / 2f, height * 0.02f)
        SIDE_START -> Offset(if (rtl) width * 0.98f else width * 0.02f, height * 0.5f)
        SIDE_END -> Offset(if (rtl) width * 0.02f else width * 0.98f, height * 0.5f)
    }
}

/** A tile that has just gone down, and where it came from. */
data class Arrival(val domino: com.teera.core.Domino, val from: SeatSpot)
