package com.teera.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.teera.app.ui.theme.Felt
import com.teera.core.Board
import com.teera.core.BoardLayout
import com.teera.core.End

/**
 * The line of play, folded to fit whatever room the screen has.
 *
 * The fold itself is worked out by [BoardLayout] in the shared module, so the geometry
 * is unit tested rather than eyeballed on a phone.
 */
@Composable
fun BoardView(
    board: Board,
    modifier: Modifier = Modifier,
    /** Ends the player can currently reach, marked so it is obvious where a tile can go. */
    liveEnds: Set<End> = emptySet(),
) {
    val layout = BoardLayout.compute(board.tiles, maxRowUnits = 13f)
    // Grows in as the line does, so a long chain shrinks to stay on the felt.
    val tileCount = board.size.coerceAtLeast(1)
    val settle by animateFloatAsState(targetValue = tileCount.toFloat(), label = "boardSettle")

    Box(
        modifier = modifier.background(
            Brush.radialGradient(listOf(Felt.TableLight, Felt.Table, Felt.Deep)),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (layout.placements.isEmpty()) return@Canvas

            val padding = 24f
            val usableWidth = size.width - padding * 2
            val usableHeight = size.height - padding * 2
            // One unit is the short side of a bone; pick the scale that fits both ways.
            val unit = minOf(
                usableWidth / (layout.width + 1f),
                usableHeight / (layout.height + 1f),
            ).coerceAtMost(size.height / 4.5f)

            val boardWidth = layout.width * unit
            val boardHeight = layout.height * unit
            val originX = (size.width - boardWidth) / 2f - layout.minX * unit
            val originY = (size.height - boardHeight) / 2f - layout.minY * unit

            for (placement in layout.placements) {
                val isNewest = placement.index == layout.placements.lastIndex && settle > 0f
                drawDominoAt(
                    leftFace = placement.tile.left,
                    rightFace = placement.tile.right,
                    centre = Offset(
                        originX + placement.centreX * unit,
                        originY + placement.centreY * unit,
                    ),
                    width = BoardLayout.TILE_LENGTH * unit * 0.98f,
                    height = BoardLayout.TILE_WIDTH * unit * 0.98f,
                    rotationDegrees = placement.rotationDegrees,
                    highlight = if (isNewest) Felt.Brass.copy(alpha = 0.55f) else null,
                )
            }

            // Mark the open ends so it is obvious where a tile can go.
            for (end in liveEnds) {
                val placement = if (end == End.LEFT) layout.placements.first() else layout.placements.last()
                drawCircle(
                    color = Felt.Playable.copy(alpha = 0.75f),
                    radius = unit * 0.22f,
                    center = Offset(
                        originX + placement.centreX * unit,
                        originY + placement.centreY * unit,
                    ),
                )
            }
        }

        if (board.isEmpty) {
            EmptyTableHint()
        }
    }
}

@Composable
private fun BoxScope.EmptyTableHint() {
    androidx.compose.material3.Text(
        text = "Lay the first bone",
        color = Color.White.copy(alpha = 0.35f),
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
    )
}
