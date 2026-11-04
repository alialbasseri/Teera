package com.teera.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A card room, not a toy shop: deep felt, warm ivory bones, one brass accent.
 * Everything on the table is drawn from these, so a change here restyles the game.
 */
/**
 * Amiri, a classical naskh. A proper Arabic face is the single thing that most changes
 * how the game reads, and it ships with the app under the Open Font Licence.
 */
val Amiri = FontFamily(
    Font(com.teera.app.R.font.amiri_regular),
    Font(com.teera.app.R.font.amiri_bold, androidx.compose.ui.text.font.FontWeight.Bold),
)

object Felt {
    val Deep = Color(0xFF07301C)
    val Table = Color(0xFF0F4A2A)
    val TableLight = Color(0xFF1A6B3C)

    // The cabinet the felt is set into: walnut, with gold laid along its edges.
    val Rail = Color(0xFF3A2616)
    val RailTop = Color(0xFF55371F)
    val RailFoot = Color(0xFF1E1309)
    val GoldDeep = Color(0xFFA67C2E)
    val GoldLit = Color(0xFFF0D79A)
    val Lapis = Color(0xFF16355F)
    val Muted = Color(0xFFA79274)
    val Brass = Color(0xFFD9B166)
    val BrassDim = Color(0xFF8A6A2A)

    val Ivory = Color(0xFFFBF6EC)
    val IvoryLit = Color(0xFFFFFCF5)
    val IvoryEdge = Color(0xFFD8CCB6)
    val IvoryShade = Color(0xFFE2D8C6)
    val Pip = Color(0xFF2F2A24)
    val PipShadow = Color(0x4D4A3B22)
    val BoneEdge = Color(0xFF6B5B44)
    val TileBack = Color(0xFF8E2727)
    val TileBackEdge = Color(0xFF5A1414)

    val Ink = Color(0xFF14110C)
    val Parchment = Color(0xFFFBF7EF)
    val Danger = Color(0xFFC0392B)
    val Playable = Color(0xFF8DE8A8)
    val Behind = Color(0xFFE8907F)
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

private val TeeraTypography = Typography(
    displayLarge = TextStyle(fontFamily = Amiri, fontSize = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = Amiri, fontSize = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = Amiri, fontSize = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Amiri, fontSize = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = Amiri, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = Amiri, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = Amiri, fontSize = 17.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = Amiri, fontSize = 13.sp),
)

@Composable
fun TeeraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = TeeraTypography,
        content = content,
    )
}
