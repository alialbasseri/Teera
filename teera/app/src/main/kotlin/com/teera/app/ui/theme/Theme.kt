package com.teera.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A card room, not a toy shop: deep felt, warm ivory bones, one brass accent.
 * Everything on the table is drawn from these, so a change here restyles the game.
 */
object Felt {
    val Deep = Color(0xFF0E3B22)
    val Table = Color(0xFF14532D)
    val TableLight = Color(0xFF1B6B3A)
    val Rail = Color(0xFF3E2A18)
    val Brass = Color(0xFFD9A441)
    val BrassDim = Color(0xFF8A6A2A)

    val Ivory = Color(0xFFF6EFE2)
    val IvoryShade = Color(0xFFE2D8C6)
    val Pip = Color(0xFF2F2A24)
    val TileBack = Color(0xFF7A1F1F)
    val TileBackEdge = Color(0xFF531212)

    val Ink = Color(0xFF14110C)
    val Parchment = Color(0xFFFBF7EF)
    val Danger = Color(0xFFC0392B)
    val Playable = Color(0xFF7BE495)
}

private val DarkScheme = darkColorScheme(
    primary = Felt.Brass,
    onPrimary = Felt.Ink,
    secondary = Felt.TableLight,
    onSecondary = Felt.Parchment,
    background = Felt.Deep,
    onBackground = Felt.Parchment,
    surface = Felt.Table,
    onSurface = Felt.Parchment,
    surfaceVariant = Felt.Deep,
    onSurfaceVariant = Felt.IvoryShade,
    error = Felt.Danger,
)

private val LightScheme = lightColorScheme(
    primary = Felt.BrassDim,
    onPrimary = Felt.Parchment,
    secondary = Felt.Table,
    onSecondary = Felt.Parchment,
    background = Felt.Table,
    onBackground = Felt.Parchment,
    surface = Felt.TableLight,
    onSurface = Felt.Parchment,
    error = Felt.Danger,
)

private val DominoesTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.5.sp),
)

@Composable
fun DominoesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = DominoesTypography,
        content = content,
    )
}
