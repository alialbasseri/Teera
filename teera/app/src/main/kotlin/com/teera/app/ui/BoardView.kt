package com.teera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    /** Arms the selected tile could go on; tapping one plays it there. */
    liveArms: Set<Arm> = emptySet(),
    onArmTapped: (Arm) -> Unit = {},
) {
    val layout = BoardLayout.compute(board)

    Box(
        modifier = modifier.background(
            Brush.radialGradient(listOf(Felt.TableLight, Felt.Table, Felt.Deep)),
        ),
    ) {
        if (board.isEmpty) {
            Text(
                text = "الفتحة بالدبل",
                color = Color.White.copy(alpha = 0.35f),
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
                            .map { it to frame.pointOf(armMarker(layout, it)) }
                            .minByOrNull { (_, point) -> (point - tap).getDistance() }
                            ?.takeIf { (_, point) -> (point - tap).getDistance() < frame.unit * 1.2f }
                            ?.let { (arm, _) -> onArmTapped(arm) }
                    }
                },
        ) {
            val frame = frameFor(layout, size.width, size.height)

            for (placement in layout.placements) {
                // A tile is drawn lying down and then turned, so work out which face ends
                // up where: the one nearer the middle of the table always points inwards.
                val leftFace = if (placement.arm == Arm.WEST || placement.arm == Arm.NORTH) {
                    placement.outer
                } else {
                    placement.inner
                }
                val rightFace = if (placement.arm == Arm.WEST || placement.arm == Arm.NORTH) {
                    placement.inner
                } else {
                    placement.outer
                }

                drawDominoAt(
                    leftFace = leftFace,
                    rightFace = rightFace,
                    centre = frame.pointOf(placement.centreX to placement.centreY),
                    width = BoardLayout.TILE_LENGTH * frame.unit * 0.96f,
                    height = BoardLayout.TILE_WIDTH * frame.unit * 0.96f,
                    rotationDegrees = placement.rotationDegrees,
                    highlight = if (placement.isCentre) Felt.Brass.copy(alpha = 0.4f) else null,
                )
            }

            // Mark the ends the chosen tile can reach, so it is obvious where it may go.
            for (arm in liveArms) {
                val centre = frame.pointOf(armMarker(layout, arm))
                drawCircle(Felt.Playable.copy(alpha = 0.22f), frame.unit * 0.62f, centre)
                drawCircle(Felt.Playable, frame.unit * 0.26f, centre)
            }
        }
    }
}

/** How the table in tile units maps onto the pixels we have. */
private class Frame(val unit: Float, val originX: Float, val originY: Float) {
    fun pointOf(point: Pair<Float, Float>) =
        Offset(originX + point.first * unit, originY + point.second * unit)
}

private fun frameFor(layout: BoardLayout.Layout, width: Float, height: Float): Frame {
    val padding = 28f
    // The table only ever grows, so scale to whatever it has become and keep it centred.
    val unit = minOf(
        (width - padding * 2) / (layout.width + 1.5f),
        (height - padding * 2) / (layout.height + 1.5f),
    ).coerceIn(6f, height / 5f)

    val originX = (width - layout.width * unit) / 2f - layout.minX * unit
    val originY = (height - layout.height * unit) / 2f - layout.minY * unit
    return Frame(unit, originX, originY)
}

/** Where the playable end of an arm sits, in tile units. */
private fun armMarker(layout: BoardLayout.Layout, arm: Arm): Pair<Float, Float> {
    val last = layout.placements.lastOrNull { it.arm == arm }
        ?: return 0f to 0f
    val reach = BoardLayout.TILE_LENGTH / 2f + 0.35f
    return when (arm) {
        Arm.EAST -> (last.centreX + reach) to last.centreY
        Arm.WEST -> (last.centreX - reach) to last.centreY
        Arm.NORTH -> last.centreX to (last.centreY - reach)
        Arm.SOUTH -> last.centreX to (last.centreY + reach)
    }
}
