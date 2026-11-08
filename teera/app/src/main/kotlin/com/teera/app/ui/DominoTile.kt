package com.teera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino
import com.teera.core.TileArt
import kotlin.math.min

/**
 * The bones.
 *
 * Every tile is drawn from geometry rather than loaded from an image: it stays sharp at
 * any size, costs nothing to ship, and the whole set restyles from one place. The spot
 * positions and colours come from [TileArt] in the shared model, so a picture rendered
 * for review and the tile on the phone are the same tile.
 */

fun pipColour(value: Int): Color = Color(TileArt.colourOf(value) or 0xFF000000.toInt())

/**
 * The spots of one face. [TileArt] lays them out for a bone standing up; on a bone lying
 * down they are turned with it, so the six's two rows of three always run along the
 * bone's length, as on a real one.
 */
private fun DrawScope.drawFace(value: Int, area: Rect, standing: Boolean) {
    val radius = min(area.width, area.height) * 0.095f
    val colour = pipColour(value)
    for (pip in TileArt.pipsOf(value)) {
        val x = if (standing) pip.x else pip.y
        val y = if (standing) pip.y else pip.x
        val centre = Offset(area.left + x * area.width, area.top + y * area.height)
        // A drilled spot: its own shadow beneath, a touch of light on the top left.
        drawCircle(Felt.PipShadow, radius * 1.1f, centre.copy(y = centre.y + radius * 0.22f))
        drawCircle(colour, radius, centre)
        drawCircle(
            Color.White.copy(alpha = 0.35f),
            radius * 0.32f,
            Offset(centre.x - radius * 0.28f, centre.y - radius * 0.3f),
        )
    }
}

/**
 * Draws a bone of [width] by [height] at the current origin.
 *
 * [faceA] is the top face when [vertical], the left face otherwise.
 */
fun DrawScope.drawDomino(
    faceA: Int?,
    faceB: Int?,
    width: Float,
    height: Float,
    vertical: Boolean = false,
    faceUp: Boolean = true,
    highlight: Color? = null,
    faded: Boolean = false,
) {
    val corner = CornerRadius(min(width, height) * 0.17f)
    val alpha = if (faded) 0.82f else 1f

    // The tile sits on the felt rather than being printed on it.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.3f * alpha),
        topLeft = Offset(0f, height * 0.035f),
        size = Size(width, height),
        cornerRadius = corner,
    )

    if (!faceUp) {
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Felt.TileBack, Felt.TileBackEdge),
                start = Offset.Zero,
                end = Offset(width * 0.3f, height),
            ),
            size = Size(width, height),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Felt.Brass.copy(alpha = 0.4f),
            topLeft = Offset(width * 0.13f, height * 0.09f),
            size = Size(width * 0.74f, height * 0.82f),
            cornerRadius = CornerRadius(corner.x * 0.6f),
            style = Stroke(width = min(width, height) * 0.035f),
        )
        return
    }

    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Felt.IvoryLit, Felt.Ivory, Felt.IvoryEdge),
            start = Offset.Zero,
            end = Offset(width * 0.25f, height),
        ),
        size = Size(width, height),
        cornerRadius = corner,
        alpha = alpha,
    )
    // The bevel: a bright lip inside the edge, then the edge itself.
    drawRoundRect(
        color = Color.White.copy(alpha = 0.6f * alpha),
        topLeft = Offset(1f, 1f),
        size = Size(width - 2f, height - 2f),
        cornerRadius = CornerRadius(corner.x - 1f),
        style = Stroke(width = 1f),
    )
    drawRoundRect(
        color = Felt.BoneEdge.copy(alpha = 0.35f * alpha),
        size = Size(width, height),
        cornerRadius = corner,
        style = Stroke(width = 1f),
    )

    val divider = min(width, height) * 0.05f
    if (vertical) {
        drawLine(
            color = Felt.BoneEdge.copy(alpha = 0.45f * alpha),
            start = Offset(width * 0.14f, height / 2f),
            end = Offset(width * 0.86f, height / 2f),
            strokeWidth = divider,
            cap = StrokeCap.Round,
        )
        drawFace(faceA ?: 0, Rect(0f, 0f, width, height / 2f), standing = true)
        drawFace(faceB ?: 0, Rect(0f, height / 2f, width, height), standing = true)
    } else {
        drawLine(
            color = Felt.BoneEdge.copy(alpha = 0.45f * alpha),
            start = Offset(width / 2f, height * 0.14f),
            end = Offset(width / 2f, height * 0.86f),
            strokeWidth = divider,
            cap = StrokeCap.Round,
        )
        drawFace(faceA ?: 0, Rect(0f, 0f, width / 2f, height), standing = false)
        drawFace(faceB ?: 0, Rect(width / 2f, 0f, width, height), standing = false)
    }

    if (highlight != null) {
        drawRoundRect(
            color = highlight,
            size = Size(width, height),
            cornerRadius = corner,
            style = Stroke(width = min(width, height) * 0.055f),
        )
    }
}

/** A single tile as a composable, for the hand and for decoration. */
@Composable
fun DominoTile(
    domino: Domino?,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
    faceUp: Boolean = true,
    highlight: Color? = null,
    faded: Boolean = false,
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
            faded = faded,
        )
    }
}

/**
 * Draws a laid tile turned about its own centre. The faces are given as they lie, so a
 * bone on the table shows which way round it was played.
 */
fun DrawScope.drawDominoAt(
    leftFace: Int,
    rightFace: Int,
    centre: Offset,
    width: Float,
    height: Float,
    rotationDegrees: Float,
    highlight: Color? = null,
    scale: Float = 1f,
) {
    rotate(rotationDegrees, centre) {
        val w = width * scale
        val h = height * scale
        translate(centre.x - w / 2f, centre.y - h / 2f) {
            drawDomino(
                faceA = leftFace,
                faceB = rightFace,
                width = w,
                height = h,
                vertical = false,
                highlight = highlight,
            )
        }
    }
}
