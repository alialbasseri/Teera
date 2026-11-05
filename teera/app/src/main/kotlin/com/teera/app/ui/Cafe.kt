package com.teera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino

/**
 * The menu's backdrop: a café table late in the evening, seen from above. Walnut under a
 * warm lamp, bones left where the last game ended, and two istikans of tea on their
 * saucers. Everything is drawn, so it stays sharp on any screen and costs no download.
 *
 * The middle is kept dim so the menu reads clearly over it; the life is round the edges.
 */
@Composable
fun CafeTable(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        // The table top: walnut, with the lamp pooling light a little above the middle.
        WalnutSurface(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x4DFFC46B), Color(0x14FFC46B), Color(0x00000000)),
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = size.maxDimension * 0.7f,
                ),
            )
            // Two tea glasses: one by the top corner, one by the bottom.
            istikan(Offset(size.width * 0.13f, size.height * 0.075f), size.minDimension * 0.11f, spoonAngle = 145f)
            istikan(Offset(size.width * 1.0f, size.height * 0.29f), size.minDimension * 0.1f, spoonAngle = -150f)
        }

        // Bones left lying where the last game ended: half off the edges, some face down.
        LYING.forEach { tile ->
            DominoTile(
                tile.domino,
                Modifier
                    // From the top-left corner whatever the reading direction: in
                    // right-to-left "start" is the right edge, which put most bones off-screen.
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset(x = w * tile.x, y = h * tile.y)
                    .size(40.dp, 80.dp)
                    .rotate(tile.angle)
                    .alpha(0.9f),
                faceUp = tile.domino != null,
            )
        }

        // Dim the middle so the menu sits on it cleanly; leave the edges warm.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xB3120A04), Color(0x66120A04), Color(0x00120A04)),
                    center = Offset(size.width * 0.5f, size.height * 0.52f),
                    radius = size.maxDimension * 0.58f,
                ),
            )
        }

        Box(Modifier.fillMaxSize(), content = content)
    }
}

private class Lying(val domino: Domino?, val x: Float, val y: Float, val angle: Float)

/** Where the bones lie, as fractions of the screen. Chosen by eye, the same every time. */
private val LYING = listOf(
    Lying(Domino(6, 6), 0.80f, 0.03f, 24f),
    Lying(Domino(3, 5), 0.66f, 0.10f, -62f),
    Lying(null, 0.90f, 0.16f, 76f),
    Lying(Domino(1, 4), -0.04f, 0.22f, -18f),
    Lying(Domino(0, 2), -0.07f, 0.40f, 70f),
    Lying(null, 0.88f, 0.33f, -8f),
    Lying(Domino(2, 6), -0.05f, 0.66f, 14f),
    Lying(Domino(5, 5), 0.02f, 0.80f, -64f),
    Lying(null, 0.86f, 0.74f, 102f),
    Lying(Domino(4, 4), 0.58f, 0.975f, 58f),
    Lying(Domino(0, 1), 0.93f, 0.83f, -15f),
    Lying(null, 0.12f, 0.975f, 96f),
)

/**
 * An istikan from above: the saucer with its gold rim and blue band, the small glass on
 * it full of dark tea, and a spoon resting beside.
 */
private fun DrawScope.istikan(centre: Offset, radius: Float, spoonAngle: Float) {
    // Shadow, then the saucer.
    drawCircle(Color(0x55000000), radius * 1.05f, centre + Offset(radius * 0.08f, radius * 0.12f))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFFCF5), Color(0xFFE9E2D3)), center = centre, radius = radius),
        radius, centre,
    )
    drawCircle(Color(0xFFC9A24E), radius * 0.97f, centre, style = Stroke(radius * 0.05f))
    drawCircle(Color(0xFF2B4C9B), radius * 0.84f, centre, style = Stroke(radius * 0.07f))
    // Little gold leaves round the blue band.
    for (i in 0 until 12) {
        rotate(i * 30f, centre) {
            drawOval(
                Color(0xFFC9A24E),
                topLeft = Offset(centre.x - radius * 0.03f, centre.y - radius * 0.76f),
                size = Size(radius * 0.06f, radius * 0.12f),
            )
        }
    }

    // The spoon, lying on the saucer.
    rotate(spoonAngle, centre) {
        val start = Offset(centre.x + radius * 0.35f, centre.y)
        drawLine(Color(0xFFB8BCC2), start, Offset(centre.x + radius * 1.25f, centre.y), radius * 0.07f)
        drawOval(
            Color(0xFFD4D7DC),
            topLeft = Offset(start.x - radius * 0.08f, centre.y - radius * 0.09f),
            size = Size(radius * 0.26f, radius * 0.18f),
        )
    }

    // The glass: a ring of light at its rim and the tea inside, darkest in the middle.
    val glass = radius * 0.52f
    drawCircle(Color(0x66FFFFFF), glass, centre)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF4A1405), Color(0xFF8E2E0C), Color(0xFFC0561B)),
            center = centre, radius = glass * 0.9f,
        ),
        glass * 0.86f, centre,
    )
    drawCircle(Color(0xFFC9A24E), glass, centre, style = Stroke(radius * 0.035f))
    // Light caught on the tea.
    drawOval(
        Color(0x55FFE2B0),
        topLeft = Offset(centre.x - glass * 0.45f, centre.y - glass * 0.6f),
        size = Size(glass * 0.5f, glass * 0.22f),
    )
}
