package com.teera.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
 * The game's two hands.
 *
 * El Messiri for everything that has to be read at a glance - buttons, names, scores:
 * clear at small sizes, with enough of the pen in it to feel crafted rather than system.
 * Aref Ruqaa for the name of the game and its big moments: Ruq'ah is the hand people in
 * Iraq actually write in, so "طيرة" on the menu looks written, not typeset.
 *
 * Latin letters and digits - scores, room codes, the credits - are Readex Pro's, drawn
 * into the same font by tools/fonts/merge_latin.py, because El Messiri's own Latin did
 * not sit well beside its Arabic. All three ship under the SIL Open Font Licence
 * (assets/licenses); the merged face is renamed, as that licence asks.
 */
val Messiri = FontFamily(
    Font(com.teera.app.R.font.el_messiri_medium, FontWeight.Normal),
    Font(com.teera.app.R.font.el_messiri_medium, FontWeight.Medium),
    Font(com.teera.app.R.font.el_messiri_bold, FontWeight.SemiBold),
    Font(com.teera.app.R.font.el_messiri_bold, FontWeight.Bold),
)

val Ruqaa = FontFamily(
    Font(com.teera.app.R.font.aref_ruqaa_regular, FontWeight.Normal),
    Font(com.teera.app.R.font.aref_ruqaa_bold, FontWeight.Bold),
)

/** The felt on the table, picked on the profile. */
enum class FeltStyle(val label: String, val deep: Color, val table: Color, val light: Color) {
    GREEN("أخضر", Color(0xFF07301C), Color(0xFF0F4A2A), Color(0xFF1A6B3C)),
    RED("أحمر", Color(0xFF2E0A0E), Color(0xFF5A1620), Color(0xFF7E2430)),
    BLUE("أزرق", Color(0xFF071C33), Color(0xFF0F3256), Color(0xFF1B4C7A)),
    BLACK("أسود", Color(0xFF0B0B0C), Color(0xFF1C1C1F), Color(0xFF2E2E33)),
}

/** The bones themselves: ivory, wood, or black with bright spots. */
enum class BoneStyle(
    val label: String,
    val face: Color,
    val faceLit: Color,
    val faceEdge: Color,
    val faceShade: Color,
    val edge: Color,
    val back: Color,
    val backEdge: Color,
) {
    IVORY(
        "عاج", Color(0xFFFBF6EC), Color(0xFFFFFCF5), Color(0xFFD8CCB6), Color(0xFFE2D8C6),
        Color(0xFF6B5B44), Color(0xFF8E2727), Color(0xFF5A1414),
    ),
    WOOD(
        "خشب", Color(0xFFD9B27C), Color(0xFFEACB9C), Color(0xFFA67C4E), Color(0xFFC79E68),
        Color(0xFF5A3B1C), Color(0xFF4A2E16), Color(0xFF2E1C0C),
    ),
    BLACK(
        "أسود", Color(0xFF26262A), Color(0xFF3A3A40), Color(0xFF121214), Color(0xFF1E1E22),
        Color(0xFF000000), Color(0xFFB08A3E), Color(0xFF6E5420),
    ),
}

/** What this phone has picked; reading it in a drawing redraws that drawing when it changes. */
object Looks {
    var felt by androidx.compose.runtime.mutableStateOf(FeltStyle.GREEN)
    var bones by androidx.compose.runtime.mutableStateOf(BoneStyle.IVORY)
}

object Felt {
    val Deep: Color get() = Looks.felt.deep
    val Table: Color get() = Looks.felt.table
    val TableLight: Color get() = Looks.felt.light

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

    val Ivory: Color get() = Looks.bones.face
    val IvoryLit: Color get() = Looks.bones.faceLit
    val IvoryEdge: Color get() = Looks.bones.faceEdge
    val IvoryShade: Color get() = Looks.bones.faceShade
    val Pip = Color(0xFF2F2A24)
    val PipShadow = Color(0x4D4A3B22)
    val BoneEdge: Color get() = Looks.bones.edge
    val TileBack: Color get() = Looks.bones.back
    val TileBackEdge: Color get() = Looks.bones.backEdge

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
    // Written: the game's name, and the word thrown across the table when a game ends.
    displayLarge = TextStyle(fontFamily = Ruqaa, fontSize = 56.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = Ruqaa, fontSize = 36.sp, fontWeight = FontWeight.Bold),
    // Read: everything else.
    headlineLarge = TextStyle(fontFamily = Messiri, fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = Messiri, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = Messiri, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Messiri, fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = Messiri, fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = Messiri, fontSize = 15.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = Messiri, fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = Messiri, fontSize = 12.sp),
)

@Composable
fun TeeraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // The game is Arabic through and through, so it reads right to left on every phone,
    // whatever language the phone itself is set to.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = TeeraTypography,
            content = content,
        )
    }
}
