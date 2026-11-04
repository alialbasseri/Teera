package com.teera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.teera.app.ui.theme.Felt

/**
 * The gilt frame the felt is set into.
 *
 * The motif is the eight petalled rosette off the glazed brick of the Ishtar gate, which
 * is where the game is from. It is drawn rather than drawn on: a few ellipses and two
 * hairlines, so it costs nothing and stays sharp at any size.
 */

fun DrawScope.drawRosette(centre: Offset, radius: Float, alpha: Float = 1f) {
    val petal = radius * 0.42f
    rotate(0f, centre) {
        for (index in 0 until 8) {
            rotate(index * 45f, centre) {
                drawOval(
                    color = Felt.Brass.copy(alpha = alpha),
                    topLeft = Offset(centre.x - petal / 2f, centre.y - radius),
                    size = Size(petal, radius),
                )
            }
        }
    }
    drawCircle(Felt.GoldDeep.copy(alpha = alpha), radius * 0.26f, centre)
}

/** Two gold hairlines around the felt, with a rosette at each corner and along the rails. */
fun DrawScope.drawFrame(inset: Float) {
    val area = Size(size.width - inset * 2, size.height - inset * 2)
    val topLeft = Offset(inset, inset)

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass, Felt.GoldDeep)),
        topLeft = topLeft,
        size = area,
        cornerRadius = CornerRadius(8f),
        style = Stroke(width = 2f),
        alpha = 0.9f,
    )
    drawRoundRect(
        color = Felt.GoldDeep.copy(alpha = 0.55f),
        topLeft = Offset(inset + 5f, inset + 5f),
        size = Size(area.width - 10f, area.height - 10f),
        cornerRadius = CornerRadius(5f),
        style = Stroke(width = 1f),
    )

    val corner = 15f
    listOf(
        Offset(inset + corner, inset + corner),
        Offset(inset + area.width - corner, inset + corner),
        Offset(inset + corner, inset + area.height - corner),
        Offset(inset + area.width - corner, inset + area.height - corner),
    ).forEach { drawRosette(it, radius = 11f) }

    var x = inset + 58f
    while (x < inset + area.width - 52f) {
        drawRosette(Offset(x, inset + 2f), radius = 6f, alpha = 0.75f)
        drawRosette(Offset(x, inset + area.height - 2f), radius = 6f, alpha = 0.75f)
        x += 42f
    }
}

/** The weave of the cloth, just enough to stop the felt reading as flat paint. */
fun DrawScope.drawWeave(step: Float = 7f) {
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = Color.Black.copy(alpha = 0.06f),
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = 1.4f,
        )
        x += step
    }
}

/** A walnut surface with the grain running along it, used for the rails and the tray. */
@Composable
fun WalnutSurface(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Felt.RailTop, Felt.Rail, Felt.RailFoot)))
        var y = 3f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.10f),
                start = Offset(0f, y),
                end = Offset(size.width, y + 1.5f),
                strokeWidth = 1f,
            )
            y += 6f
        }
    }
}
