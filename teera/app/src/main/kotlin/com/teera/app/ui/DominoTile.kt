package com.teera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino
import kotlin.math.min

/**
 * The bones themselves.
 *
 * Every tile is drawn from geometry rather than loaded from an image: it stays crisp at
 * any size, costs nothing to ship, and the whole set can be restyled by editing colours
 * in one place.
 */

/**
 * The colour each number is drilled in, following the coloured sets people actually
 * play with. The count and arrangement of the pips still carries the meaning, so the
 * colour is a help rather than the signal — a player who cannot tell them apart can
 * still read every tile.
 */
fun pipColour(value: Int): Color = when (value) {
    1 -> Color(0xFF35A8DE)   // light blue
    2 -> Color(0xFF1F7A3A)   // dark green
    3 -> Color(0xFFC9437F)   // pink
    4 -> Color(0xFF8C8176)   // taupe
    5 -> Color(0xFF2B4C9B)   // navy
    6 -> Color(0xFFD9A21B)   // yellow
    else -> Felt.Pip
}

/** Pip positions for a face, as fractions of the half-tile they sit in. */
fun pipLayout(value: Int): List<Offset> {
    val a = 0.26f
    val b = 0.5f
    val c = 0.74f
    return when (value) {
        0 -> emptyList()
        1 -> listOf(Offset(b, b))
        2 -> listOf(Offset(a, a), Offset(c, c))
        3 -> listOf(Offset(a, a), Offset(b, b), Offset(c, c))
        4 -> listOf(Offset(a, a), Offset(c, a), Offset(a, c), Offset(c, c))
        5 -> listOf(Offset(a, a), Offset(c, a), Offset(b, b), Offset(a, c), Offset(c, c))
        6 -> listOf(
            Offset(a, 0.2f), Offset(a, b), Offset(a, 0.8f),
            Offset(c, 0.2f), Offset(c, b), Offset(c, 0.8f),
        )
        7 -> pipLayout(6) + Offset(b, b)
        8 -> pipLayout(6) + listOf(Offset(b, 0.32f), Offset(b, 0.68f))
        9 -> listOf(0.22f, b, 0.78f).flatMap { x -> listOf(0.2f, b, 0.8f).map { y -> Offset(x, y) } }
        else -> {
            // Ten and up only turn up in the bigger packs: fill a three by four grid.
            val slots = listOf(0.22f, b, 0.78f).flatMap { x ->
                listOf(0.17f, 0.39f, 0.61f, 0.83f).map { y -> Offset(x, y) }
            }
            slots.take(value.coerceAtMost(12))
        }
    }
}

private fun DrawScope.drawFace(value: Int, area: Rect) {
    val radius = min(area.width, area.height) * 0.085f
    val colour = pipColour(value)
    for (pip in pipLayout(value)) {
        val centre = Offset(area.left + pip.x * area.width, area.top + pip.y * area.height)
        // A hint of a drilled hole: the spot sitting over its own shadow.
        drawCircle(Color.Black.copy(alpha = 0.22f), radius * 1.15f, centre.copy(y = centre.y + radius * 0.2f))
        drawCircle(colour, radius, centre)
        drawCircle(Color.White.copy(alpha = 0.3f), radius * 0.34f, centre.copy(
            x = centre.x - radius * 0.3f,
            y = centre.y - radius * 0.32f,
        ))
    }
}

/**
 * Draws a bone into [size] at the current origin. Shared by the hand, the board and the
 * lobby so a tile looks the same everywhere.
 *
 * [faceA] is the top face when [vertical], the left face otherwise.
 *
 * @param vertical true when the tile stands on its end, as a double laid crossways does.
 */
fun DrawScope.drawDomino(
    faceA: Int?,
    faceB: Int?,
    width: Float,
    height: Float,
    vertical: Boolean = false,
    faceUp: Boolean = true,
    highlight: Color? = null,
    dimmed: Boolean = false,
) {
    val corner = CornerRadius(min(width, height) * 0.16f)
    val body = Rect(0f, 0f, width, height)

    // Shadow first, so tiles read as sitting on the felt rather than printed on it.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(0f, height * 0.035f),
        size = Size(width, height),
        cornerRadius = corner,
    )

    if (!faceUp) {
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Felt.TileBack, Felt.TileBackEdge)),
            size = Size(width, height),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Felt.Brass.copy(alpha = 0.45f),
            topLeft = Offset(width * 0.12f, height * 0.09f),
            size = Size(width * 0.76f, height * 0.82f),
            cornerRadius = CornerRadius(corner.x * 0.6f),
            style = Stroke(width = min(width, height) * 0.035f),
        )
        return
    }

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Felt.Ivory, Felt.IvoryShade)),
        size = Size(width, height),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = Felt.Pip.copy(alpha = 0.20f),
        size = Size(width, height),
        cornerRadius = corner,
        style = Stroke(width = min(width, height) * 0.03f),
    )

    val divider = min(width, height) * 0.045f
    if (vertical) {
        drawLine(
            color = Felt.Pip.copy(alpha = 0.55f),
            start = Offset(width * 0.12f, height / 2f),
            end = Offset(width * 0.88f, height / 2f),
            strokeWidth = divider,
        )
        drawFace(faceA ?: 0, Rect(0f, 0f, width, height / 2f))
        drawFace(faceB ?: 0, Rect(0f, height / 2f, width, height))
    } else {
        drawLine(
            color = Felt.Pip.copy(alpha = 0.55f),
            start = Offset(width / 2f, height * 0.12f),
            end = Offset(width / 2f, height * 0.88f),
            strokeWidth = divider,
        )
        drawFace(faceA ?: 0, Rect(0f, 0f, width / 2f, height))
        drawFace(faceB ?: 0, Rect(width / 2f, 0f, width, height))
    }

    if (dimmed) {
        drawRoundRect(
            color = Felt.Deep.copy(alpha = 0.45f),
            size = Size(width, height),
            cornerRadius = corner,
        )
    }
    if (highlight != null) {
        drawRoundRect(
            color = highlight,
            size = Size(width, height),
            cornerRadius = corner,
            style = Stroke(width = min(width, height) * 0.07f),
        )
    }
}

/**
 * A single tile as a composable, for hands and lobby decoration.
 *
 * [low] is drawn at the top when [vertical], on the left otherwise, matching how the
 * tile is actually lying on the table.
 */
@Composable
fun DominoTile(
    domino: Domino?,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
    faceUp: Boolean = true,
    highlight: Color? = null,
    dimmed: Boolean = false,
) {
    Canvas(modifier) {
        drawDomino(
            faceA = domino?.low,
            faceB = domino?.high,
            width = size.width,
            height = size.height,
            vertical = vertical,
            faceUp = faceUp,
            highlight = highlight,
            dimmed = dimmed,
        )
    }
}

/**
 * Draws a laid tile rotated about its own centre, used by the board's folded rows.
 * The faces come from the [com.teera.core.PlacedTile], so a bone shows which way
 * round it was actually played.
 */
fun DrawScope.drawDominoAt(
    leftFace: Int,
    rightFace: Int,
    centre: Offset,
    width: Float,
    height: Float,
    rotationDegrees: Float,
    highlight: Color? = null,
) {
    rotate(rotationDegrees, centre) {
        translate(centre.x - width / 2f, centre.y - height / 2f) {
            drawDomino(
                faceA = leftFace,
                faceB = rightFace,
                width = width,
                height = height,
                vertical = false,
                highlight = highlight,
            )
        }
    }
}
