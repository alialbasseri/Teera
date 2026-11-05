package com.teera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.CafeTable
import com.teera.app.ui.DominoTile
import com.teera.app.ui.PresetAvatar
import com.teera.app.ui.RosetteRule
import com.teera.app.ui.TileBacks
import com.teera.app.ui.drawRosette
import com.teera.app.ui.theme.Felt
import com.teera.app.ui.theme.TeeraTheme
import com.teera.core.Domino
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Teaser pictures for Ali's friends, drawn with the game's own art and type: 1080 by 1350,
 * the shape a phone shows best in a chat. The game is not named in any of them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h450dp-xxhdpi")
class Teasers {

    @get:Rule
    val compose = createComposeRule()

    private val out = File(System.getProperty("teera.teasers") ?: "build/teasers").apply { mkdirs() }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { TeeraTheme(dark = true) { content() } }
        repeat(30) {
            compose.mainClock.advanceTimeBy(100)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val view = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>)
            .first { it.width > 0 }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("TEASER=${File(out, "$name.png").absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    /** The café table with everything pushed to the corners, leaving the middle for words. */
    private val corners = listOf(
        com.teera.app.ui.Lying(Domino(6, 6), 0.80f, 0.02f, 24f),
        com.teera.app.ui.Lying(Domino(3, 5), 0.63f, 0.07f, -62f),
        com.teera.app.ui.Lying(null, -0.07f, 0.52f, 70f),
        com.teera.app.ui.Lying(Domino(2, 6), 0.0f, 0.83f, -64f),
        com.teera.app.ui.Lying(null, 0.18f, 0.97f, 96f),
        com.teera.app.ui.Lying(Domino(0, 1), 0.93f, 0.62f, -15f),
    )
    /** For a headline across the very top: nothing above the middle but one glass, tucked in. */
    private val sides = corners.drop(2) + com.teera.app.ui.Lying(Domino(5, 5), 0.89f, 0.33f, 24f)
    private val topCups = listOf(
        com.teera.app.ui.Cup(0.04f, 0.035f, 0.08f, 145f),
        com.teera.app.ui.Cup(0.88f, 0.93f, 0.11f, -40f),
    )

    private val cups = listOf(
        com.teera.app.ui.Cup(0.13f, 0.075f, 0.11f, 145f),
        com.teera.app.ui.Cup(0.88f, 0.93f, 0.11f, -40f),
    )

    @Composable
    private fun Soon(modifier: Modifier = Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            RosetteRule(Modifier.padding(horizontal = 90.dp))
            Spacer(Modifier.height(6.dp))
            Text("قريباً", color = Felt.GoldLit, style = MaterialTheme.typography.titleLarge, fontSize = 24.sp)
        }
    }

    /** The café table, and the line that says what is coming without saying its name. */
    @Test
    fun theTableIsBack() = shoot("teaser-1-table") {
        CafeTable(bones = corners, cups = cups) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "الگعدة رجعت…",
                    color = Felt.Brass,
                    fontSize = 52.sp,
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "بس هالمرة بتلفونك",
                    color = Felt.Parchment,
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "چاي، وچفتات، وجماعتك كلهم… من كل مكان",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(26.dp))
                Soon()
            }
        }
    }

    /** One double-six under a lamp, waiting for somebody to put it down. */
    @Test
    fun whoOpens() = shoot("teaser-2-who-opens") {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Felt.TableLight, Felt.Table, Color(0xFF041A0E)),
                        center = Offset(540f, 560f),
                        radius = 900f,
                    ),
                ),
        ) {
            // The lamp's pool of light on the felt.
            ComposeCanvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x40FFE2A0), Color(0x00FFE2A0)), center = Offset(size.width / 2, size.height * 0.42f), radius = size.width * 0.45f),
                    radius = size.width * 0.45f,
                    center = Offset(size.width / 2, size.height * 0.42f),
                )
                drawRosette(Offset(size.width * 0.12f, size.height * 0.09f), 26f, alpha = 0.5f)
                drawRosette(Offset(size.width * 0.88f, size.height * 0.91f), 26f, alpha = 0.5f)
            }
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DominoTile(Domino(6, 6), Modifier.size(84.dp, 168.dp).rotate(-12f))
                Spacer(Modifier.height(34.dp))
                Text("منو يفتح؟", color = Felt.GoldLit, fontSize = 56.sp, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "جهّزوا الچاي… وحضّروا أعصابكم",
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Soon()
            }
        }
    }

    /** Four chairs round one table, two bones-down hands showing. */
    @Test
    fun twoAgainstTwo() = shoot("teaser-3-two-against-two") {
        CafeTable(bones = sides, cups = topCups) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    "ثنين ضد ثنين…",
                    color = Felt.Brass,
                    fontSize = 42.sp,
                    style = MaterialTheme.typography.displayLarge,
                )
                Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Felt.TableLight, Felt.Table, Felt.Deep)))
                            .border(2.dp, Felt.Brass, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        DominoTile(Domino(6, 6), Modifier.size(26.dp, 52.dp))
                    }
                    Chair(0, Modifier.align(Alignment.TopCenter))
                    Chair(6, Modifier.align(Alignment.CenterStart))
                    Chair(2, Modifier.align(Alignment.CenterEnd))
                    Chair(3, Modifier.align(Alignment.BottomCenter))
                    // Beside the top and bottom chairs, clear of them whichever way text runs.
                    Box(Modifier.align(Alignment.TopCenter).absoluteOffset(x = 66.dp, y = 22.dp)) { TileBacks(7, lying = false) }
                    Box(Modifier.align(Alignment.BottomCenter).absoluteOffset(x = (-66).dp, y = (-22).dp)) { TileBacks(7, lying = false) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "والبقية تعرفونها",
                        color = Felt.Parchment,
                        fontSize = 24.sp,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "اختاروا شريككم من هسه",
                        color = Felt.Muted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Soon()
            }
        }
    }

    @Composable
    private fun Chair(picture: Int, modifier: Modifier) {
        Box(
            modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(2.dp, Felt.Brass, CircleShape),
        ) { PresetAvatar(picture, Modifier.size(56.dp)) }
    }
}
