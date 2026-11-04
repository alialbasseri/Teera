package com.teera.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
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
 */
@Composable
fun BoardView(
    board: Board,
    modifier: Modifier = Modifier,
    /** Arms the chosen tile can reach. Tapping one plays it there. */
    liveArms: Set<Arm> = emptySet(),
    onArmTapped: (Arm) -> Unit = {},
) {
    val layout = BoardLayout.compute(board)
    val measurer = rememberTextMeasurer()

    // The last tile laid settles onto the table rather than appearing on it.
    val settle by animateFloatAsState(
        targetValue = board.size.toFloat(),
        animationSpec = tween(220),
        label = "settle",
    )

    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(Felt.TableLight, Felt.Table, Felt.Deep),
                radius = 1200f,
            ),
        ),
    ) {
        if (board.isEmpty) {
            Text(
                text = "الفتحة بالدبل",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(board, liveArms) {
                    detectTapGestures { tap ->
                        val frame = frameFor(layout, size.width.toFloat(), size.height.toFloat())
                        liveArms
                            .map { it to frame.pointOf(armTarget(layout, it)) }
                            .minByOrNull { (_, point) -> (point - tap).getDistance() }
                            ?.takeIf { (_, point) -> (point - tap).getDistance() < frame.unit * 1.3f }
                            ?.let { (arm, _) -> onArmTapped(arm) }
                    }
                },
        ) {
            // The corners of the table fall away, so the eye goes to the line of play.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                    center = Offset(size.width / 2f, size.height * 0.45f),
                    radius = size.maxDimension * 0.75f,
                ),
            )

            val frame = frameFor(layout, size.width, size.height)
            val newest = layout.placements.lastOrNull()

            for (placement in layout.placements) {
                // A tile is drawn lying down and then turned, so the face nearer the
                // middle of the table has to end up pointing inwards.
                val inward = placement.arm == Arm.WEST || placement.arm == Arm.NORTH
                val scale = if (placement === newest) (settle - board.size + 1f).coerceIn(0.4f, 1f) else 1f

                drawDominoAt(
                    leftFace = if (inward) placement.outer else placement.inner,
                    rightFace = if (inward) placement.inner else placement.outer,
                    centre = frame.pointOf(placement.centreX to placement.centreY),
                    width = BoardLayout.TILE_LENGTH * frame.unit * 0.97f,
                    height = BoardLayout.TILE_WIDTH * frame.unit * 0.97f,
                    rotationDegrees = placement.rotationDegrees,
                    highlight = if (placement.isCentre) Felt.Brass.copy(alpha = 0.75f) else null,
                    scale = scale,
                )
            }

            for (arm in liveArms) {
                drawTarget(frame, layout, arm, measurer)
            }
        }
    }
}

/** Where a tile may go: a target big enough to hit with a thumb, showing the number. */
private fun DrawScope.drawTarget(
    frame: Frame,
    layout: BoardLayout.Layout,
    arm: Arm,
    measurer: TextMeasurer,
) {
    val end = layout.placements.lastOrNull { it.arm == arm } ?: return
    val centre = frame.pointOf(armTarget(layout, arm))

    drawCircle(Felt.Playable.copy(alpha = 0.18f), frame.unit * 0.52f, centre)
    drawCircle(
        color = Felt.Playable,
        radius = frame.unit * 0.34f,
        center = centre,
        style = Stroke(width = frame.unit * 0.07f),
    )
    val text = measurer.measure(
        text = end.outer.toString(),
        style = TextStyle(
            color = Felt.Playable,
            fontSize = (frame.unit * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    drawText(
        textLayoutResult = text,
        topLeft = Offset(centre.x - text.size.width / 2f, centre.y - text.size.height / 2f),
    )
}

/** How the table in tile units maps onto the pixels we have. */
private class Frame(val unit: Float, val originX: Float, val originY: Float) {
    fun pointOf(point: Pair<Float, Float>) =
        Offset(originX + point.first * unit, originY + point.second * unit)
}

private const val TARGET_REACH = BoardLayout.TILE_LENGTH / 2f + 0.5f

private fun frameFor(layout: BoardLayout.Layout, width: Float, height: Float): Frame {
    // Leave room for the targets, which sit a little beyond the ends of the arms.
    val padding = 14f
    val unit = minOf(
        (width - padding * 2) / (layout.width + TARGET_REACH * 2),
        (height - padding * 2) / (layout.height + TARGET_REACH * 2),
    ).coerceAtLeast(4f)

    val originX = (width - layout.width * unit) / 2f - layout.minX * unit
    val originY = (height - layout.height * unit) / 2f - layout.minY * unit
    return Frame(unit, originX, originY)
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
