package com.teera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.teera.app.ui.theme.Felt

/**
 * The pictures a player can choose instead of a photo: things from an Iraqi evening of
 * dominoes. Each is drawn on a 100 by 100 square and scaled, so they are crisp at any
 * size and cost nothing to send - a seat only carries "p:3".
 */
val PRESET_AVATARS = listOf("وردة عشتار", "طيرة", "استكان", "نخلة", "هلال", "زقورة", "خرزة زرگة", "مسگوف")

/**
 * Whatever picture a player has: one of the drawn ones, their own photo, or failing
 * both the first letter of their name.
 */
@Composable
fun Avatar(ref: String, name: String, size: Dp, photo: ImageBitmap? = null) {
    val preset = ref.removePrefix("p:").toIntOrNull()?.takeIf { ref.startsWith("p:") && it in PRESET_AVATARS.indices }
    when {
        ref == "photo" && photo != null ->
            Image(photo, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        preset != null -> PresetAvatar(preset, Modifier.size(size))
        else -> Initial(name, size)
    }
}

@Composable
fun PresetAvatar(index: Int, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            scale(size.minDimension / 100f, pivot = Offset.Zero) {
                when (index) {
                    0 -> rosette()
                    1 -> teeraTile()
                    2 -> teaGlass()
                    3 -> palm()
                    4 -> crescent()
                    5 -> ziggurat()
                    6 -> blueBead()
                    else -> fish()
                }
            }
        }
    }
}

private val Gold = Color(0xFFE2B85C)
private val GoldDark = Color(0xFFA67C2E)
private val Ivory = Color(0xFFFBF6EC)

private fun DrawScope.ground(top: Color, bottom: Color) =
    drawRect(Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = 100f), size = Size(100f, 100f))

/** The Ishtar gate's eight-petalled rosette, gold on glazed lapis. */
private fun DrawScope.rosette() {
    ground(Color(0xFF2C5E9C), Color(0xFF12305A))
    for (i in 0 until 8) {
        rotate(i * 45f, Offset(50f, 50f)) {
            drawOval(Gold, topLeft = Offset(43f, 16f), size = Size(14f, 34f))
        }
    }
    drawCircle(GoldDark, 9f, Offset(50f, 50f))
    drawCircle(Gold, 5f, Offset(50f, 50f))
}

/** The zero-one: the tile a teera is flown with. */
private fun DrawScope.teeraTile() {
    ground(Color(0xFF1A6B3C), Color(0xFF0B3A20))
    rotate(-14f, Offset(50f, 50f)) {
        drawRoundRect(Color(0x55000000), Offset(33f, 17f), Size(36f, 70f), CornerRadius(7f))
        drawRoundRect(Ivory, Offset(31f, 14f), Size(36f, 70f), CornerRadius(7f))
        drawLine(Color(0xFFB3A487), Offset(36f, 49f), Offset(62f, 49f), 2f)
        drawCircle(Color(0xFF35A8DE), 5f, Offset(49f, 67f))
    }
}

/** An istikan of tea on its saucer, seen from the side. */
private fun DrawScope.teaGlass() {
    ground(Color(0xFF9C2F1E), Color(0xFF581409))
    drawOval(Color(0xFFF2EEE6), Offset(18f, 74f), Size(64f, 14f))
    drawOval(Color(0xFF2B4C9B), Offset(24f, 76f), Size(52f, 9f), style = Stroke(1.6f))
    // The glass narrows at the waist, the way an istikan does.
    val glass = Path().apply {
        moveTo(32f, 20f); lineTo(68f, 20f)
        cubicTo(68f, 34f, 58f, 40f, 58f, 50f)
        cubicTo(58f, 60f, 66f, 66f, 64f, 78f)
        lineTo(36f, 78f)
        cubicTo(34f, 66f, 42f, 60f, 42f, 50f)
        cubicTo(42f, 40f, 32f, 34f, 32f, 20f)
        close()
    }
    drawPath(glass, Color(0x44FFFFFF))
    val tea = Path().apply {
        moveTo(34.5f, 28f); lineTo(65.5f, 28f)
        cubicTo(64f, 37f, 57f, 41f, 57f, 50f)
        cubicTo(57f, 60f, 64.5f, 66f, 63f, 77f)
        lineTo(37f, 77f)
        cubicTo(35.5f, 66f, 43f, 60f, 43f, 50f)
        cubicTo(43f, 41f, 36f, 37f, 34.5f, 28f)
        close()
    }
    drawPath(tea, Brush.verticalGradient(listOf(Color(0xFFD06A24), Color(0xFF6E1E06)), startY = 28f, endY = 77f))
    drawPath(glass, Gold, style = Stroke(1.8f))
    drawLine(Color(0x99FFFFFF), Offset(38f, 31f), Offset(41f, 44f), 2.2f)
}

/** A date palm against a dusk sky. */
private fun DrawScope.palm() {
    ground(Color(0xFFE59A4E), Color(0xFF7A3B1E))
    drawCircle(Color(0x66FFE2A0), 16f, Offset(70f, 34f))
    val trunk = Path().apply {
        moveTo(46f, 95f); cubicTo(48f, 75f, 52f, 55f, 55f, 38f)
        lineTo(59f, 38f); cubicTo(57f, 55f, 54f, 75f, 54f, 95f); close()
    }
    drawPath(trunk, Color(0xFF3B2412))
    for (k in 0 until 6) {
        drawLine(Color(0xFF2A190B), Offset(48.5f + k * 1.1f, 88f - k * 9f), Offset(53.5f + k * 0.9f, 86f - k * 9f), 1.4f)
    }
    val crown = Offset(57f, 37f)
    for (angle in listOf(-160f, -125f, -95f, -60f, -25f, 10f, 40f)) {
        rotate(angle, crown) {
            val frond = Path().apply {
                moveTo(crown.x, crown.y)
                quadraticBezierTo(crown.x + 16f, crown.y - 9f, crown.x + 33f, crown.y + 4f)
                quadraticBezierTo(crown.x + 16f, crown.y - 2f, crown.x, crown.y)
                close()
            }
            drawPath(frond, Color(0xFF1F4D24))
        }
    }
    drawCircle(Color(0xFF8E3B12), 3f, Offset(55f, 44f))
    drawCircle(Color(0xFF8E3B12), 3f, Offset(60f, 45f))
}

/** A crescent and a star over a night sky. */
private fun DrawScope.crescent() {
    ground(Color(0xFF16355F), Color(0xFF07152A))
    drawCircle(Gold, 26f, Offset(46f, 50f))
    drawCircle(Color(0xFF10284A), 23f, Offset(57f, 45f))
    val star = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 9f else 3.8f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val p = Offset(70f + r * kotlin.math.cos(a).toFloat(), 48f + r * kotlin.math.sin(a).toFloat())
        if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
    }
    star.close()
    drawPath(star, Gold)
}

/** The ziggurat of Ur, stepped against a pale sky. */
private fun DrawScope.ziggurat() {
    ground(Color(0xFFF1D9A6), Color(0xFFC79A55))
    drawRect(Color(0xFFB07A3A), Offset(0f, 82f), Size(100f, 18f))
    val tiers = listOf(Triple(14f, 70f, 72f), Triple(24f, 56f, 52f), Triple(34f, 42f, 32f))
    tiers.forEach { (x, y, w) ->
        drawRect(Color(0xFF8C5A2B), Offset(x, y), Size(w, 14f))
        drawRect(Color(0xFF6B421E), Offset(x, y + 11f), Size(w, 3f))
    }
    drawRect(Color(0xFF8C5A2B), Offset(44f, 32f), Size(12f, 10f))
    // The stair up the middle.
    drawRect(Color(0xFFA9763F), Offset(46f, 42f), Size(8f, 40f))
}

/** The blue bead against the evil eye. */
private fun DrawScope.blueBead() {
    ground(Color(0xFFF4EFE6), Color(0xFFD9CFBF))
    drawCircle(Color(0xFF1F4FA8), 34f, Offset(50f, 50f))
    drawCircle(Color(0xFFFFFFFF), 22f, Offset(50f, 50f))
    drawCircle(Color(0xFF6BC3F0), 14f, Offset(50f, 50f))
    drawCircle(Color(0xFF0C1C3A), 7f, Offset(50f, 50f))
    drawCircle(Color(0x99FFFFFF), 4f, Offset(41f, 39f))
}

/** Masgouf: the Tigris carp, split and grilled by the river. */
private fun DrawScope.fish() {
    ground(Color(0xFF2E7F86), Color(0xFF134247))
    val body = Path().apply {
        moveTo(14f, 50f)
        cubicTo(28f, 26f, 62f, 24f, 74f, 46f)
        lineTo(90f, 32f); lineTo(86f, 50f); lineTo(90f, 68f); lineTo(74f, 54f)
        cubicTo(62f, 76f, 28f, 74f, 14f, 50f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Gold, Color(0xFFC0561B)), startY = 28f, endY = 72f))
    for (k in 0 until 4) drawLine(Color(0x553B1A06), Offset(34f + k * 9f, 38f), Offset(30f + k * 9f, 62f), 2f)
    drawCircle(Color(0xFF1B0F05), 3f, Offset(25f, 46f))
    drawPath(body, Color(0xFF6E2C08), style = Stroke(1.5f))
}
