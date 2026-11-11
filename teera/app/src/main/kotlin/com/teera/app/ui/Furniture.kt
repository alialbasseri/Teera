package com.teera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt

/**
 * The pieces every screen outside the table is built from, so the menu and the lobby
 * are the same room as the game rather than a different app in front of it.
 */

/** A walnut panel filling the screen, with a little gold light falling from the top. */
@Composable
fun WalnutRoom(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        WalnutSurface(Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Felt.Brass.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.12f),
                    radius = size.maxDimension * 0.6f,
                ),
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))),
            )
        }
        content()
    }
}

/** A row of small rosettes, used as a rule between sections. */
@Composable
fun RosetteRule(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2f
        drawLine(Felt.Brass.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width * 0.38f, y), 1.5f)
        drawLine(Felt.Brass.copy(alpha = 0.55f), Offset(size.width * 0.62f, y), Offset(size.width, y), 1.5f)
        drawRosette(Offset(size.width / 2f, y), radius = size.height * 0.48f)
        drawRosette(Offset(size.width * 0.44f, y), radius = size.height * 0.26f, alpha = 0.7f)
        drawRosette(Offset(size.width * 0.56f, y), radius = size.height * 0.26f, alpha = 0.7f)
    }
}

/** The main call to action: a bar of gold leaf. */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass, Felt.GoldDeep)))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Felt.Ink, style = MaterialTheme.typography.titleMedium)
    }
}

/** A second-rank action: dark, edged in gold. */
@Composable
fun BrassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .height(50.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF120B05).copy(alpha = 0.55f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Felt.Parchment, style = MaterialTheme.typography.titleMedium)
    }
}
